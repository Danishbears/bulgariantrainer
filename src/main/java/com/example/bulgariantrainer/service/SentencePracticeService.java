package com.example.bulgariantrainer.service;
import com.example.bulgariantrainer.dto.SentenceDtos.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.exception.SentenceException;
import com.example.bulgariantrainer.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;
import java.time.Instant;
import java.util.*;
@Service @Transactional
public class SentencePracticeService {
 private final StudySessionRepository sessions;private final SentenceQuestionRepository questions;
 private final SentenceRepository sentences;private final SentenceWordRepository links;
 private final StudyAttemptRepository attempts;private final SentenceProgressRepository progress;
 private final SentenceAttemptWordRepository usage;private final ObjectMapper json;
 private final TypingQuestionPlanner planner;
 private final SpacedRepetitionScheduler scheduler;
 public SentencePracticeService(StudySessionRepository sessions,SentenceQuestionRepository questions,SentenceRepository sentences,
    SentenceWordRepository links,StudyAttemptRepository attempts,SentenceProgressRepository progress,
    SentenceAttemptWordRepository usage,ObjectMapper json,TypingQuestionPlanner planner,SpacedRepetitionScheduler scheduler){
  this.sessions=sessions;this.questions=questions;this.sentences=sentences;this.links=links;
  this.attempts=attempts;this.progress=progress;this.usage=usage;this.json=json;this.planner=planner;this.scheduler=scheduler;
 }
 public SessionResponse start(StartRequest request){
  int size=request.size()==null?10:request.size();
  StudyScope scope=request.scope()==null?StudyScope.MIXED:request.scope();
  var previous=sessions.findBySentenceRequestId(request.requestId());
  if(previous.isPresent()){
   StudySession session=session(previous.get().getId());
   if(session.getSentenceRequestedSize()!=size||session.getStudyScope()!=scope)throw conflict("START_REQUEST_CHANGED","Use a new request ID for different settings");
   return state(session);
  }
  Instant at=scheduler.now();
  List<Long> ids=sentences.findTrainableIds();
  Map<Long,SentenceProgress> records=new HashMap<>();
  for(SentenceProgress p:progress.findBySentenceIdIn(ids))records.put(p.getSentence().getId(),p);
  Set<Long> linkedDue=new HashSet<>(sentences.findDueLinkedIds(at));
  List<Long> ordered=planner.shuffled(ids.stream()
   .filter(id->scheduler.eligible(scheduler.category(records.get(id),at),scope)).toList());
  ordered.sort(Comparator.comparingInt((Long id)->scheduler.priority(scheduler.category(records.get(id),at)))
   .thenComparingInt(id->linkedDue.contains(id)?0:1));
  List<Sentence> selected=new ArrayList<>();
  // Lock in ID order before inserting the ordered snapshot queue.
  for(Long id:ordered.stream().limit(size).sorted().toList())
   sentences.findForUpdate(id).filter(s->s.isApproved()&&!s.isArchived()).ifPresent(selected::add);
  if(selected.isEmpty())throw conflict(scope==StudyScope.DUE?"EMPTY_REVIEW_QUEUE":scope==StudyScope.NEW?"EMPTY_NEW_QUEUE":"EMPTY_SENTENCES","No approved sentences are available for this study choice");
  Map<Long,Sentence> byId=new HashMap<>();for(Sentence s:selected)byId.put(s.getId(),s);
  StudySession session=new StudySession(TrainingMode.SENTENCE);
  session.setStudyScope(scope);
  session.configureSentences(request.requestId(),size,selected.size());sessions.saveAndFlush(session);
  int position=0;
  for(Long id:ordered){Sentence sentence=byId.get(id);if(sentence==null)continue;
   String alternatives=json.writeValueAsString(sentence.getAlternatives().stream().map(SentenceAlternative::getBulgarianText).toList());
   questions.save(new SentenceQuestion(session,sentence,++position,alternatives));
  }
  questions.flush();return state(session);
 }
 public SessionResponse current(long id){return state(session(id));}
 public SessionResponse answer(long id,AnswerRequest request){
  StudySession session=session(id);SentenceQuestion question=question(session,request.questionId());
  if(question.getAttempt()!=null){
   if(!question.getAttempt().getUserAnswer().equals(request.answer()))throw conflict("ANSWER_CONFLICT","A different answer is already saved");
   return state(session);
  }
  requireActive(session);requireCurrent(session,question);
  String normalized=VocabularyText.key(request.answer());
  if(normalized.isEmpty())throw new SentenceException(400,"INVALID_ANSWER","Enter a sentence");
  boolean match=accepted(question).stream().map(VocabularyText::key).anyMatch(normalized::equals);
  // A string mismatch is pending self-assessment, never a definitive grammar error.
  StudyAttempt attempt=attempts.saveAndFlush(new StudyAttempt(session,null,question.getSentence(),TranslationDirection.RU_TO_BG,
    request.answer(),match?AttemptResult.CORRECT:AttemptResult.PENDING));
  if(match){attempt.markAutomatic();record(question.getSentence(),AttemptResult.CORRECT);session.recordCorrectAnswer();}
  question.record(attempt);
  for(SentenceWord link:links.findBySentenceId(question.getSentence().getId()))usage.save(new SentenceAttemptWord(attempt,link.getWord()));
  questions.flush();finishIfAnswered(session);return state(session);
 }
 public SessionResponse selfRate(long id,SelfRatingRequest request){
  StudySession session=session(id);SentenceQuestion question=question(session,request.questionId());
  StudyAttempt attempt=question.getAttempt();
  if(attempt==null)throw conflict("ANSWER_REQUIRED","Submit the sentence first");
  if(attempt.getResult()!=AttemptResult.PENDING){
   if(attempt.getEvaluationSource()==EvaluationSource.SELF&&attempt.getResult()==request.result())return state(session);
   throw conflict("EVALUATION_CONFLICT","This answer has already been evaluated");
  }
  requireActive(session);requireCurrent(session,question);
  if(!Set.of(AttemptResult.CORRECT,AttemptResult.PARTIAL,AttemptResult.INCORRECT).contains(request.result()))
   throw new SentenceException(400,"INVALID_RATING","Choose a self-assessment rating");
  attempt.finalizeEvaluation(request.result(),EvaluationSource.SELF);
  record(question.getSentence(),request.result());
  if(request.result()==AttemptResult.CORRECT)session.recordCorrectAnswer();
  finishIfAnswered(session);return state(session);
 }
 public SessionResponse next(long id,QuestionRequest request){
  StudySession session=session(id);SentenceQuestion question=question(session,request.questionId());
  if(question.isAcknowledged())return state(session);
  requireCurrent(session,question);
  if(question.getAttempt()==null||question.getAttempt().getResult()==AttemptResult.PENDING)
   throw conflict("EVALUATION_REQUIRED","Finish evaluating this answer first");
  question.acknowledge();return state(session);
 }
 public SessionResponse complete(long id){
  StudySession session=session(id);List<SentenceQuestion> queue=queue(session);
  queue.stream().filter(q->q.getAttempt()!=null).forEach(SentenceQuestion::acknowledge);
  session.complete(queue.stream().anyMatch(q->q.getAttempt()==null||q.getAttempt().getResult()==AttemptResult.PENDING));
  return state(session);
 }
 public ResultsResponse results(long id){
  StudySession session=session(id);
  if(session.getStatus()==SessionStatus.IN_PROGRESS)throw conflict("SESSION_NOT_COMPLETED","Complete the session first");
  List<ReviewItem> items=queue(session).stream().map(q->{
   StudyAttempt a=q.getAttempt();return new ReviewItem(q.getPosition(),q.getRussianText(),a==null?null:a.getUserAnswer(),
    accepted(q),q.getExplanation(),a==null?AttemptResult.SKIPPED:a.getResult(),a==null?null:a.getEvaluationSource());
  }).toList();return new ResultsResponse(state(session),items);
 }
 private void record(Sentence sentence,AttemptResult result){
  // Sentence lock from answer/method session ordering serializes this sentence's progress update.
  Sentence locked=sentences.findForUpdate(sentence.getId()).orElseThrow();
  SentenceProgress record=progress.findBySentenceId(locked.getId()).orElseGet(()->new SentenceProgress(locked));
  scheduler.record(record,result);progress.save(record);
 }
 private void finishIfAnswered(StudySession session){
  if(queue(session).stream().allMatch(q->q.getAttempt()!=null&&q.getAttempt().getResult()!=AttemptResult.PENDING))session.complete(false);
 }
 private StudySession session(long id){return sessions.findForUpdate(id)
  .filter(s->s.getTrainingMode()==TrainingMode.SENTENCE&&s.getSentenceRequestId()!=null)
  .orElseThrow(()->new SentenceException(404,"SESSION_NOT_FOUND","Sentence session not found"));}
 private List<SentenceQuestion> queue(StudySession session){return questions.findBySessionIdOrderByPositionAsc(session.getId());}
 private SentenceQuestion question(StudySession session,long id){return queue(session).stream().filter(q->q.getId()==id).findFirst()
  .orElseThrow(()->conflict("STALE_QUESTION","Question does not belong to this session"));}
 private void requireActive(StudySession session){if(session.getStatus()!=SessionStatus.IN_PROGRESS)throw conflict("SESSION_CLOSED","Session is completed");}
 private void requireCurrent(StudySession session,SentenceQuestion q){
  SentenceQuestion current=queue(session).stream().filter(item->!item.isAcknowledged()).findFirst().orElse(null);
  if(current==null||!current.getId().equals(q.getId()))throw conflict("STALE_QUESTION","Use the current question");
 }
 private List<String> accepted(SentenceQuestion q){
  List<String> result=new ArrayList<>();result.add(q.getBulgarianText());
  JsonNode alternatives=json.readTree(q.getAlternativesJson());for(JsonNode item:alternatives)result.add(item.asText());
  return List.copyOf(result);
 }
 private SessionResponse state(StudySession session){
  List<SentenceQuestion> queue=queue(session);
  int answered=(int)queue.stream().filter(q->q.getAttempt()!=null&&q.getAttempt().getResult()!=AttemptResult.PENDING).count();
  int partial=(int)queue.stream().filter(q->q.getAttempt()!=null&&q.getAttempt().getResult()==AttemptResult.PARTIAL).count();
  SentenceQuestion q=session.getStatus()==SessionStatus.ABANDONED?null:queue.stream().filter(item->!item.isAcknowledged()).findFirst().orElse(null);
  QuestionResponse current=q==null?null:new QuestionResponse(q.getId(),q.getPosition(),q.getRussianText(),
   q.getAttempt()==null?null:new Feedback(q.getAttempt().getResult(),q.getAttempt().getEvaluationSource(),
    q.getAttempt().getUserAnswer(),accepted(q),q.getExplanation(),q.getAttempt().getResult()==AttemptResult.PENDING));
  return new SessionResponse(session.getId(),session.getStatus(),session.getTotalQuestions(),answered,session.getCorrectAnswers(),partial,current);
 }
 private SentenceException conflict(String code,String message){return new SentenceException(409,code,message);}
}
