package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.AspectPracticeDtos.*;
import com.example.bulgariantrainer.dto.AspectPairDtos.Entry;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.repository.*;
import com.example.bulgariantrainer.exception.AspectPairException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AspectPracticeService {
    private final AspectSessionRepository sessions;
    private final AspectQuestionRepository questions;
    private final AspectAttemptRepository attempts;
    private final AspectProgressRepository progress;
    private final AspectPairRepository pairs;
    private final AspectPairService pairService;
    private final AspectPairValidation validation;
    private final AspectPairJson json;
    private final SpacedRepetitionScheduler scheduler;
    private final AspectQuestionPlanner planner;
    private final TransactionTemplate transaction;

    public AspectPracticeService(AspectSessionRepository sessions,AspectQuestionRepository questions,
            AspectAttemptRepository attempts,AspectProgressRepository progress,AspectPairRepository pairs,
            AspectPairService pairService,AspectPairValidation validation,AspectPairJson json,
            SpacedRepetitionScheduler scheduler,AspectQuestionPlanner planner,PlatformTransactionManager manager){
        this.sessions=sessions;this.questions=questions;this.attempts=attempts;this.progress=progress;
        this.pairs=pairs;this.pairService=pairService;this.validation=validation;this.json=json;
        this.scheduler=scheduler;this.planner=planner;transaction=new TransactionTemplate(manager);
    }

    public Settings settings(Integer size,List<Integer> stages,String topic,AspectDirection direction,
                             StudyScope scope,String namespace,Boolean allowShorter){
        int requested=size==null?20:size;
        if(requested<1||requested>100)throw invalid("Size must be 1–100");
        List<Integer> selected=stages==null?List.of(1):stages;
        if(selected.isEmpty()||selected.size()>3||selected.stream().anyMatch(s->s==null||s<1||s>3)
                ||new HashSet<>(selected).size()!=selected.size())throw invalid("Select distinct stages from 1 to 3");
        selected=selected.stream().sorted().toList();
        if(topic!=null&&(topic.isBlank()||topic.length()>255))throw invalid("Topic must be nonblank and at most 255 characters");
        if(namespace!=null)validation.namespace(namespace);
        return new Settings(requested,selected,topic,direction==null?AspectDirection.MIXED:direction,
                scope==null?StudyScope.MIXED:scope,namespace,Boolean.TRUE.equals(allowShorter));
    }

    @Transactional(readOnly=true)
    public Availability availability(Settings settings){
        List<Long> ids=pairs.findPracticeIds(settings.namespace(),settings.studyStages(),settings.topic());
        var candidates=candidates(ids,settings);
        int offered=Math.min(settings.size(),candidates.size());
        return new Availability(candidates.size(),(int)candidates.stream().filter(c->c.forwardPriority()!=null).count(),
                (int)candidates.stream().filter(c->c.reversePriority()!=null).count(),settings.size(),offered,offered<settings.size());
    }

    public SessionResponse start(StartRequest request){
        Settings settings=settings(request.size(),request.studyStages(),request.topic(),request.direction(),request.scope(),request.namespace(),request.allowShorter());
        try{return transaction.execute(status->startInside(request.requestId(),settings));}
        catch(DataIntegrityViolationException race){
            // A simultaneous start UUID can win the unique constraint. Its transaction
            // must commit before a fresh transaction can safely restore that session.
            return transaction.execute(status->{
                var existing=sessions.findIdByRequestId(request.requestId());
                if(existing.isEmpty())throw race;
                return resumeStart(existing.get(),settings);
            });
        }
    }

    private SessionResponse startInside(UUID requestId,Settings settings){
        var previous=sessions.findIdByRequestId(requestId);
        if(previous.isPresent())return resumeStart(previous.get(),settings);
        List<Long> ids=pairs.findPracticeIds(settings.namespace(),settings.studyStages(),settings.topic());
        // Lock candidate pair rows in ID order before reading their content/progress.
        // This also coordinates with edits, archives and progress from other sessions.
        Map<Long,AspectPair> locked=new LinkedHashMap<>();
        for(Long id:ids)pairs.findForUpdate(id).filter(p->!p.isArchived()
                &&settings.studyStages().contains(p.getStudyStage())
                &&(settings.topic()==null||settings.topic().equals(p.getTopic())))
                .ifPresent(p->locked.put(id,p));
        List<AspectQuestionPlanner.Candidate> candidates=candidates(new ArrayList<>(locked.keySet()),settings);
        if(candidates.isEmpty())throw conflict(settings.scope()==StudyScope.DUE?"EMPTY_REVIEW_QUEUE":settings.scope()==StudyScope.NEW?"EMPTY_NEW_QUEUE":"EMPTY_ASPECT_PAIRS","No eligible aspect pairs");
        if(candidates.size()<settings.size()&&!settings.allowShorter())throw conflict("SHORTER_SESSION_REQUIRED","Accept the shorter session offered by availability");
        List<AspectQuestionPlanner.Planned> planned=planner.plan(candidates,settings.size(),settings.direction());
        AspectSession session=sessions.saveAndFlush(new AspectSession(requestId,json.write(settings),planned.size(),now()));
        for(int i=0;i<planned.size();i++){
            var item=planned.get(i);AspectPair pair=locked.get(item.pairId());
            questions.save(new AspectQuestion(session,pair,i+1,item.direction(),json.write(pairService.response(pair).entry())));
        }
        questions.flush();return state(session);
    }

    private SessionResponse resumeStart(long id,Settings settings){
        AspectSession session=session(id);
        if(!json.read(session.getSettingsJson(),Settings.class).equals(settings))throw conflict("START_REQUEST_CHANGED","Use a new request ID for changed settings");
        return state(session);
    }

    private List<AspectQuestionPlanner.Candidate> candidates(List<Long> ids,Settings settings){
        Map<Long,EnumMap<AspectDirection,AspectProgress>> records=new HashMap<>();
        if(!ids.isEmpty())for(AspectProgress p:progress.findByPairIdIn(ids))
            records.computeIfAbsent(p.getPair().getId(),ignored->new EnumMap<>(AspectDirection.class)).put(p.getDirection(),p);
        Instant at=scheduler.now();List<AspectQuestionPlanner.Candidate> result=new ArrayList<>();
        for(Long id:ids){
            Map<AspectDirection,AspectProgress> byDirection=records.getOrDefault(id,new EnumMap<>(AspectDirection.class));
            LearningCategory forward=scheduler.category(byDirection.get(AspectDirection.IMPERFECTIVE_TO_PERFECTIVE),at);
            LearningCategory reverse=scheduler.category(byDirection.get(AspectDirection.PERFECTIVE_TO_IMPERFECTIVE),at);
            Integer f=settings.direction()!=AspectDirection.PERFECTIVE_TO_IMPERFECTIVE&&scheduler.eligible(forward,settings.scope())?scheduler.priority(forward):null;
            Integer r=settings.direction()!=AspectDirection.IMPERFECTIVE_TO_PERFECTIVE&&scheduler.eligible(reverse,settings.scope())?scheduler.priority(reverse):null;
            if(f!=null||r!=null)result.add(new AspectQuestionPlanner.Candidate(id,f,r));
        }
        return result;
    }

    @Transactional public SessionResponse current(long id){return state(session(id));}

    @Transactional
    public SessionResponse answer(long id,AnswerRequest request){
        AspectSession session=session(id);AspectQuestion question=question(session,request.questionId());
        if(question.getAttempt()!=null){
            if(question.getAttempt().getOutcome()==AspectOutcome.REVEALED)throw conflict("ANSWER_REVEALED","A revealed answer cannot earn recall credit");
            if(!question.getAttempt().getUserAnswer().equals(request.answer()))throw conflict("ANSWER_CONFLICT","A different answer is already saved");
            return state(session);
        }
        requireCurrentActive(session,question);
        String normalized=AspectAnswerNormalizer.key(request.answer());
        if(normalized.isEmpty())throw invalid("Enter a nonblank answer");
        boolean correct=accepted(question).stream().map(AspectAnswerNormalizer::key).anyMatch(normalized::equals);
        record(session,question,request.answer(),correct?AspectOutcome.CORRECT:AspectOutcome.INCORRECT);
        return state(session);
    }

    @Transactional
    public SessionResponse reveal(long id,QuestionRequest request){
        AspectSession session=session(id);AspectQuestion question=question(session,request.questionId());
        if(question.getAttempt()!=null){
            if(question.getAttempt().getOutcome()==AspectOutcome.REVEALED)return state(session);
            throw conflict("ANSWER_ALREADY_SUBMITTED","Typed feedback is already saved");
        }
        requireCurrentActive(session,question);record(session,question,null,AspectOutcome.REVEALED);
        return state(session);
    }

    private void record(AspectSession session,AspectQuestion question,String answer,AspectOutcome outcome){
        AspectPair pair=pairs.findForUpdate(question.getPair().getId()).orElseThrow();
        AspectProgress record=progress.findByPairIdAndDirection(pair.getId(),question.getDirection())
                .orElseGet(()->new AspectProgress(pair,question.getDirection()));
        scheduler.record(record,outcome==AspectOutcome.CORRECT);progress.save(record);
        question.record(attempts.save(new AspectAttempt(question,answer,outcome,now())));
        if(outcome==AspectOutcome.CORRECT)session.recordCorrect();
        questions.flush();
        if(queue(session).stream().allMatch(q->q.getAttempt()!=null))session.finish(false,now());
    }

    @Transactional
    public SessionResponse next(long id,QuestionRequest request){
        AspectSession session=session(id);AspectQuestion question=question(session,request.questionId());
        if(question.isAcknowledged())return state(session);
        if(session.getStatus()==SessionStatus.ABANDONED)throw conflict("SESSION_CLOSED","Session has ended");
        if(question.getPosition()!=session.getCurrentPosition())throw conflict("STALE_QUESTION","Use the current question");
        if(question.getAttempt()==null)throw conflict("ANSWER_REQUIRED","Submit or reveal before continuing");
        question.acknowledge();session.advance();return state(session);
    }

    @Transactional
    public SessionResponse complete(long id){
        AspectSession session=session(id);List<AspectQuestion> queue=queue(session);
        session.finish(queue.stream().anyMatch(q->q.getAttempt()==null),now());
        queue.stream().filter(q->q.getAttempt()!=null).forEach(AspectQuestion::acknowledge);
        session.hideCurrent();return state(session);
    }

    @Transactional
    public Results results(long id){
        AspectSession session=session(id);
        if(session.getStatus()==SessionStatus.IN_PROGRESS)throw conflict("SESSION_NOT_COMPLETED","End the session before requesting results");
        SessionResponse state=state(session);
        var items=queue(session).stream().map(q->new ReviewItem(q.getPosition(),q.getDirection(),prompt(q),
                q.getAttempt()==null?"SKIPPED":q.getAttempt().getOutcome().name(),feedback(q))).toList();
        Double accuracy=state.answeredQuestions()==0?null:Math.round(state.correctAnswers()*1000.0/state.answeredQuestions())/10.0;
        return new Results(state,accuracy,items);
    }

    @Transactional(readOnly=true)
    public List<Progress> progress(String namespace){
        if(namespace!=null)validation.namespace(namespace);
        Instant at=scheduler.now();
        return progress.findForNamespace(namespace).stream().map(p->new Progress(p.getPair().getId(),p.getDirection(),
                p.getTotalAttempts(),p.getCorrectAttempts(),p.getSuccessfulReviewCount(),p.getConsecutiveCorrect(),
                p.getRepetitionInterval(),p.getLastReviewedAt(),p.getNextReviewAt(),scheduler.category(p,at))).toList();
    }

    private SessionResponse state(AspectSession session){
        List<AspectQuestion> queue=queue(session);
        int typed=0,incorrect=0,revealed=0;
        for(AspectQuestion q:queue)if(q.getAttempt()!=null){
            if(q.getAttempt().getOutcome()==AspectOutcome.REVEALED)revealed++;
            else {typed++;if(q.getAttempt().getOutcome()==AspectOutcome.INCORRECT)incorrect++;}
        }
        Question current=session.getStatus()==SessionStatus.ABANDONED?null:queue.stream()
                .filter(q->q.getPosition()==session.getCurrentPosition()).findFirst()
                .map(q->new Question(q.getId(),q.getPosition(),q.getDirection(),prompt(q),q.getRevealedAt()!=null,feedback(q))).orElse(null);
        return new SessionResponse(session.getId(),json.read(session.getSettingsJson(),Settings.class),session.getStatus(),
                session.getTotalQuestions(),session.getCurrentPosition(),typed,session.getCorrectAnswers(),incorrect,revealed,
                session.getTotalQuestions()-typed-revealed,session.getStartedAt(),session.getCompletedAt(),current);
    }
    private Entry content(AspectQuestion question){return json.read(question.getSnapshotJson(),Entry.class);}
    private List<String> accepted(AspectQuestion question){
        Entry entry=content(question);return question.getDirection()==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE?entry.acceptedPerfective():entry.acceptedImperfective();
    }
    private Prompt prompt(AspectQuestion question){
        Entry e=content(question);boolean forward=question.getDirection()==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE;
        return new Prompt(forward?e.imperfective():e.perfective(),forward?e.russianImperfective():e.russianPerfective(),
                forward?e.russianPerfective():e.russianImperfective(),forward?"Напиши совершенный вид":"Напиши несовершенный вид");
    }
    private Feedback feedback(AspectQuestion question){
        AspectAttempt a=question.getAttempt();if(a==null)return null;
        String message=switch(a.getOutcome()){
            case CORRECT->"Верно";case INCORRECT->"Не совпало с ответами этой карточки";
            case REVEALED->"Ответ раскрыт. Самостоятельное воспроизведение не засчитано";
        };
        return new Feedback(a.getOutcome(),a.getUserAnswer(),message,accepted(question),content(question));
    }
    private AspectSession session(long id){return sessions.findForUpdate(id).orElseThrow(()->new AspectPairException(404,"SESSION_NOT_FOUND","Aspect session not found"));}
    private List<AspectQuestion> queue(AspectSession session){return questions.findBySessionIdOrderByPositionAsc(session.getId());}
    private AspectQuestion question(AspectSession session,long id){return queue(session).stream().filter(q->q.getId()==id)
            .findFirst().orElseThrow(()->conflict("STALE_QUESTION","Question does not belong to this session"));}
    private void requireCurrentActive(AspectSession session,AspectQuestion question){
        if(session.getStatus()!=SessionStatus.IN_PROGRESS)throw conflict("SESSION_CLOSED","Session has ended");
        if(question.getPosition()!=session.getCurrentPosition())throw conflict("STALE_QUESTION","Use the current question");
    }
    private Instant now(){return scheduler.now().truncatedTo(ChronoUnit.MICROS);}
    private AspectPairException invalid(String message){return new AspectPairException(400,"INVALID_REQUEST",message);}
    private AspectPairException conflict(String code,String message){return new AspectPairException(409,code,message);}
}
