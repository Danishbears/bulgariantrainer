package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.AoristPracticeDtos.*;
import com.example.bulgariantrainer.dto.AoristDtos.Entry;
import com.example.bulgariantrainer.dto.PageResponse;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.exception.AoristException;
import com.example.bulgariantrainer.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AoristPracticeService {
    private final AoristSessionRepository sessions;
    private final AoristQuestionRepository questions;
    private final AoristAttemptRepository attempts;
    private final AoristProgressRepository progress;
    private final AoristVerbRepository verbs;
    private final AoristVerbService content;
    private final AoristValidation validation;
    private final AoristJson json;
    private final AoristQuestionPlanner planner;
    private final SpacedRepetitionScheduler scheduler;
    private final TransactionTemplate transaction;
    private record Key(long verbId,String person,AoristExerciseType type) {}
    private record Pool(List<AoristQuestionPlanner.Candidate> candidates,int unavailable,int reduced,int typedAlternative) {}

    public AoristPracticeService(AoristSessionRepository sessions,AoristQuestionRepository questions,AoristAttemptRepository attempts,
            AoristProgressRepository progress,AoristVerbRepository verbs,AoristVerbService content,AoristValidation validation,
            AoristJson json,AoristQuestionPlanner planner,SpacedRepetitionScheduler scheduler,PlatformTransactionManager manager) {
        this.sessions=sessions; this.questions=questions; this.attempts=attempts; this.progress=progress; this.verbs=verbs;
        this.content=content; this.validation=validation; this.json=json; this.planner=planner; this.scheduler=scheduler;
        transaction=new TransactionTemplate(manager);
    }
    public Settings settings(Integer size,AoristExerciseType type,List<String> persons,String aspect,String group,
                             StudyScope scope,String datasetId,Boolean allowShorter) {
        int requested=size==null?20:size;
        if(requested<1 || requested>100) throw invalid("Size must be 1–100");
        List<String> selected=persons==null?List.of("1sg"):persons;
        if(selected.isEmpty() || selected.size()>6 || selected.stream().anyMatch(p->p==null || !AoristValidation.PERSONS.contains(p))
                || new HashSet<>(selected).size()!=selected.size()) throw invalid("Select distinct grammatical persons from the six supported codes");
        selected=AoristValidation.PERSONS.stream().filter(selected::contains).toList();
        if(aspect!=null && !AoristValidation.ASPECTS.contains(aspect)) throw invalid("Aspect must be imperfective or perfective");
        if(group!=null && (group.isBlank() || group.length()>100)) throw invalid("Study group must be nonblank and at most 100 characters");
        if(datasetId!=null) validation.datasetId(datasetId);
        return new Settings(requested,type==null?AoristExerciseType.TYPED:type,selected,aspect,group,
                scope==null?StudyScope.MIXED:scope,datasetId,Boolean.TRUE.equals(allowShorter));
    }
    @Transactional(readOnly=true)
    public Availability availability(Settings settings) {
        var records=verbs.findPracticeIds(settings.datasetId(),settings.aspect(),settings.studyGroup()).stream()
                .map(verbs::findById).flatMap(Optional::stream).toList();
        Pool pool=pool(records,settings); int eligible=pool.candidates().size();
        return new Availability(eligible,(int)pool.candidates().stream().map(AoristQuestionPlanner.Candidate::verbId).distinct().count(),
                settings.size(),Math.min(settings.size(),eligible),eligible<settings.size(),pool.reduced(),pool.unavailable(),pool.typedAlternative());
    }
    public SessionResponse start(StartRequest request) {
        if(request.requestId()==null) throw invalid("Start request UUID is required");
        Settings settings=settings(request.size(),request.exerciseType(),request.persons(),request.aspect(),request.studyGroup(),request.scope(),request.datasetId(),request.allowShorter());
        try { return transaction.execute(status->startInside(request.requestId(),settings)); }
        catch(DataIntegrityViolationException race) {
            return transaction.execute(status->{
                var previous=sessions.findIdByRequestId(request.requestId());
                if(previous.isEmpty()) throw race;
                return resume(previous.get(),settings);
            });
        }
    }
    private SessionResponse startInside(UUID requestId,Settings settings) {
        var previous=sessions.findIdByRequestId(requestId);
        if(previous.isPresent()) return resume(previous.get(),settings);
        Map<Long,AoristVerb> locked=new LinkedHashMap<>();
        for(Long id:verbs.findPracticeIds(settings.datasetId(),settings.aspect(),settings.studyGroup()))
            verbs.findForUpdate(id).filter(v->matches(v,settings)).ifPresent(v->locked.put(id,v));
        Pool pool=pool(new ArrayList<>(locked.values()),settings);
        if(pool.candidates().isEmpty()) {
            if(pool.unavailable()>0) throw conflict("CHOICE_OPTIONS_UNAVAILABLE","No distinct choice options; explicitly choose typed recall or change filters");
            throw conflict(settings.scope()==StudyScope.DUE?"EMPTY_REVIEW_QUEUE":settings.scope()==StudyScope.NEW?"EMPTY_NEW_QUEUE":"EMPTY_AORIST_QUEUE","No eligible aorist questions");
        }
        if(pool.candidates().size()<settings.size() && !settings.allowShorter()) throw conflict("SHORTER_SESSION_REQUIRED","Accept the shorter session offered by availability");
        var planned=planner.plan(pool.candidates(),settings.size());
        AoristSession session=sessions.saveAndFlush(new AoristSession(requestId,json.write(settings),settings.exerciseType(),planned.size(),now()));
        for(int i=0;i<planned.size();i++) {
            var item=planned.get(i);
            List<Option> options=settings.exerciseType()==AoristExerciseType.MULTIPLE_CHOICE?planner.choices(item.content(),item.person()):List.of();
            questions.save(new AoristQuestion(session,locked.get(item.verbId()),item.person(),i+1,json.write(new Snapshot(1,item.content(),options))));
        }
        questions.flush(); return state(session);
    }
    private boolean matches(AoristVerb v,Settings s) {
        return !v.isArchived() && (s.aspect()==null || s.aspect().equals(v.getAspect()))
                && (s.studyGroup()==null || s.studyGroup().equals(v.getStudyGroup()))
                && (s.datasetId()==null || s.datasetId().equals(v.getDataset().getNamespace()));
    }
    private Pool pool(List<AoristVerb> records,Settings settings) {
        Map<Key,AoristProgress> history=new HashMap<>();
        if(!records.isEmpty()) for(var p:progress.findByVerbIdIn(records.stream().map(AoristVerb::getId).toList()))
            history.put(new Key(p.getVerb().getId(),p.getPerson(),p.getExerciseType()),p);
        Instant at=scheduler.now(); List<AoristQuestionPlanner.Candidate> candidates=new ArrayList<>();
        int unavailable=0,reduced=0,typedAlternative=0;
        for(AoristVerb verb:records) {
            if(!matches(verb,settings)) continue;
            Entry entry=content.response(verb).entry();
            for(String person:settings.persons()) {
                var typed=scheduler.category(history.get(new Key(verb.getId(),person,AoristExerciseType.TYPED)),at);
                if(scheduler.eligible(typed,settings.scope())) typedAlternative++;
                var category=scheduler.category(history.get(new Key(verb.getId(),person,settings.exerciseType())),at);
                if(!scheduler.eligible(category,settings.scope())) continue;
                if(settings.exerciseType()==AoristExerciseType.MULTIPLE_CHOICE) {
                    int options=planner.optionCount(entry,person);
                    if(options<2) { unavailable++; continue; }
                    if(options<4) reduced++;
                }
                candidates.add(new AoristQuestionPlanner.Candidate(verb.getId(),person,scheduler.priority(category),entry));
            }
        }
        return new Pool(List.copyOf(candidates),unavailable,reduced,typedAlternative);
    }
    private SessionResponse resume(long id,Settings settings) {
        AoristSession session=session(id);
        if(!json.read(session.getSettingsJson(),Settings.class).equals(settings)) throw conflict("START_REQUEST_CHANGED","Use a new request UUID for changed settings");
        return state(session);
    }
    @Transactional public SessionResponse current(long id) { return state(session(id)); }
    @Transactional(readOnly=true)
    public PageResponse<SessionSummary> sessions(SessionStatus status,int page,int size) {
        if(page<0 || size<1 || size>100) throw invalid("Invalid pagination");
        Page<AoristSession> rows=sessions.findByStatus(status,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"startedAt","id")));
        return new PageResponse<>(rows.getContent().stream().map(s->new SessionSummary(s.getId(),json.read(s.getSettingsJson(),Settings.class),
                s.getStatus(),s.getTotalQuestions(),s.getCurrentPosition(),s.getStartedAt())).toList(),page,size,rows.getTotalElements(),rows.getTotalPages());
    }
    @Transactional
    public SessionResponse answer(long id,AnswerRequest request) {
        AoristSession session=session(id); AoristQuestion question=question(session,request.questionId());
        validateSubmission(session,request);
        AoristAttempt previous=question.getAttempt();
        if(previous!=null) {
            if(previous.getOutcome()==AoristOutcome.REVEALED) throw conflict("ANSWER_REVEALED","A revealed answer cannot earn recall credit");
            if(!Objects.equals(previous.getUserAnswer(),request.answer()) || !Objects.equals(previous.getOptionId(),request.optionId()))
                throw conflict("ANSWER_CONFLICT","A different submission is already saved");
            return state(session);
        }
        requireCurrentActive(session,question);
        String answer=request.answer();
        if(session.getExerciseType()==AoristExerciseType.MULTIPLE_CHOICE)
            answer=snapshot(question).options().stream().filter(o->o.optionId().equals(request.optionId())).findFirst()
                    .orElseThrow(()->invalid("Option does not belong to this question")).text();
        String key=AoristAnswerNormalizer.key(answer);
        boolean correct=accepted(question).stream().map(AoristAnswerNormalizer::key).anyMatch(key::equals);
        record(session,question,request.answer(),request.optionId(),correct?AoristOutcome.CORRECT:AoristOutcome.INCORRECT);
        return state(session);
    }
    private void validateSubmission(AoristSession s,AnswerRequest r) {
        if(s.getExerciseType()==AoristExerciseType.TYPED) {
            if(r.answer()==null || r.answer().length()>4000 || AoristAnswerNormalizer.key(r.answer()).isEmpty() || r.optionId()!=null)
                throw invalid("Typed recall requires a nonblank answer and no option ID");
        } else if(r.answer()!=null || r.optionId()==null) throw invalid("Multiple choice requires only an option ID");
    }
    @Transactional
    public SessionResponse reveal(long id,QuestionRequest request) {
        AoristSession session=session(id); AoristQuestion question=question(session,request.questionId());
        if(question.getAttempt()!=null) {
            if(question.getAttempt().getOutcome()==AoristOutcome.REVEALED) return state(session);
            throw conflict("ANSWER_ALREADY_SUBMITTED","Feedback is already saved");
        }
        requireCurrentActive(session,question); record(session,question,null,null,AoristOutcome.REVEALED); return state(session);
    }
    private void record(AoristSession session,AoristQuestion question,String answer,UUID optionId,AoristOutcome outcome) {
        AoristVerb verb=verbs.findForUpdate(question.getVerb().getId()).orElseThrow();
        AoristProgress row=progress.findByVerbIdAndPersonAndExerciseType(verb.getId(),question.getPerson(),session.getExerciseType())
                .orElseGet(()->new AoristProgress(verb,question.getPerson(),session.getExerciseType()));
        scheduler.record(row,outcome==AoristOutcome.CORRECT); progress.save(row);
        question.record(attempts.save(new AoristAttempt(question,answer,optionId,outcome,now())));
        if(outcome==AoristOutcome.CORRECT) session.recordCorrect();
        questions.flush();
        if(queue(session).stream().allMatch(q->q.getAttempt()!=null)) session.finish(false,now());
    }
    @Transactional
    public SessionResponse next(long id,QuestionRequest request) {
        AoristSession session=session(id); AoristQuestion question=question(session,request.questionId());
        if(question.isAcknowledged()) return state(session);
        if(session.getStatus()==SessionStatus.ABANDONED) throw conflict("SESSION_CLOSED","Session has ended");
        if(question.getPosition()!=session.getCurrentPosition()) throw conflict("STALE_QUESTION","Use the current question");
        if(question.getAttempt()==null) throw conflict("ANSWER_REQUIRED","Submit or reveal before continuing");
        question.acknowledge(); session.advance(); return state(session);
    }
    @Transactional
    public SessionResponse complete(long id) {
        AoristSession session=session(id); var queue=queue(session);
        session.finish(queue.stream().anyMatch(q->q.getAttempt()==null),now());
        queue.stream().filter(q->q.getAttempt()!=null).forEach(AoristQuestion::acknowledge);
        session.hideCurrent(); return state(session);
    }
    @Transactional
    public Results results(long id) {
        AoristSession session=session(id);
        if(session.getStatus()==SessionStatus.IN_PROGRESS) throw conflict("SESSION_NOT_COMPLETED","End the session before requesting results");
        SessionResponse state=state(session);
        var items=queue(session).stream().map(q->new ReviewItem(q.getPosition(),prompt(q),
                q.getAttempt()==null?"SKIPPED":q.getAttempt().getOutcome().name(),feedback(q))).toList();
        Double accuracy=state.answeredQuestions()==0?null:Math.round(state.correctAnswers()*1000.0/state.answeredQuestions())/10.0;
        return new Results(state,accuracy,items,items.stream().filter(i->i.outcome().equals("INCORRECT")).toList());
    }
    @Transactional(readOnly=true)
    public List<Progress> progress(String datasetId,AoristExerciseType type,String person) {
        if(datasetId!=null) validation.datasetId(datasetId);
        if(person!=null && !AoristValidation.PERSONS.contains(person)) throw invalid("Unsupported person");
        Instant at=scheduler.now();
        return progress.findForDataset(datasetId).stream().filter(p->(type==null || p.getExerciseType()==type) && (person==null || p.getPerson().equals(person)))
                .map(p->new Progress(p.getVerb().getId(),p.getPerson(),p.getExerciseType(),p.getTotalAttempts(),p.getCorrectAttempts(),
                        p.getSuccessfulReviewCount(),p.getConsecutiveCorrect(),p.getRepetitionInterval(),p.getLastReviewedAt(),p.getNextReviewAt(),scheduler.category(p,at))).toList();
    }
    private SessionResponse state(AoristSession session) {
        var queue=queue(session); int answered=0,incorrect=0,revealed=0;
        for(var q:queue) if(q.getAttempt()!=null) {
            if(q.getAttempt().getOutcome()==AoristOutcome.REVEALED) revealed++;
            else { answered++; if(q.getAttempt().getOutcome()==AoristOutcome.INCORRECT) incorrect++; }
        }
        Question current=session.getStatus()==SessionStatus.ABANDONED?null:queue.stream().filter(q->q.getPosition()==session.getCurrentPosition())
                .findFirst().map(q->{
                    List<Option> options=snapshot(q).options();
                    String notice=session.getExerciseType()==AoristExerciseType.TYPED?null:
                            options.size()<4?"Доступно вариантов: "+options.size()+". Можно выбрать ввод ответа в новом занятии.":"Выберите форму для указанного глагола, времени и лица.";
                    return new Question(q.getId(),q.getPosition(),prompt(q),options,notice,q.getRevealedAt()!=null,feedback(q));
                }).orElse(null);
        return new SessionResponse(session.getId(),json.read(session.getSettingsJson(),Settings.class),session.getStatus(),
                session.getTotalQuestions(),session.getCurrentPosition(),answered,session.getCorrectAnswers(),
                session.getExerciseType()==AoristExerciseType.TYPED?session.getCorrectAnswers():0,incorrect,revealed,
                session.getTotalQuestions()-answered-revealed,session.getStartedAt(),session.getCompletedAt(),current);
    }
    private Snapshot snapshot(AoristQuestion q) { return json.read(q.getSnapshotJson(),Snapshot.class); }
    private List<String> accepted(AoristQuestion q) { return snapshot(q).content().forms().get(q.getPerson()); }
    private Prompt prompt(AoristQuestion q) {
        Entry e=snapshot(q).content();
        String label=switch(q.getPerson()) { case "1sg"->"аз"; case "2sg"->"ти"; case "3sg"->"той/тя/то"; case "1pl"->"ние"; case "2pl"->"вие"; case "3pl"->"те"; default->throw new IllegalStateException("Unsupported stored person"); };
        return new Prompt(e.lemma(),e.russian(),"aorist","Минало свършено време","Изъявительное наклонение",q.getPerson(),label,
                "Изолированная форма без подлежащего. Сохраните частицы се/си; не добавляйте местоимение.");
    }
    private Feedback feedback(AoristQuestion q) {
        AoristAttempt a=q.getAttempt(); if(a==null) return null;
        String selected=a.getOptionId()==null?null:snapshot(q).options().stream().filter(o->o.optionId().equals(a.getOptionId())).findFirst().orElseThrow().text();
        String message=switch(a.getOutcome()) { case CORRECT->"Верно"; case INCORRECT->"Не совпало с ответами этой карточки"; case REVEALED->"Ответ раскрыт. Самостоятельное воспроизведение не засчитано"; };
        String explanation=a.getOptionId()==null?"Сравнение выполнено с сохранёнными ответами для указанного глагола и лица.":
                "Оценка относится к этому вопросу. Другой вариант может быть существующей болгарской формой; выбор не равен самостоятельному вводу.";
        // Notes, source URLs and other persons can expose later questions for this verb.
        return new Feedback(a.getOutcome(),a.getUserAnswer(),a.getOptionId(),selected,message,explanation,accepted(q));
    }
    private AoristSession session(long id) { return sessions.findForUpdate(id).orElseThrow(()->new AoristException(404,"SESSION_NOT_FOUND","Aorist session not found")); }
    private List<AoristQuestion> queue(AoristSession session) { return questions.findBySessionIdOrderByPositionAsc(session.getId()); }
    private AoristQuestion question(AoristSession session,long id) { return queue(session).stream().filter(q->q.getId()==id).findFirst().orElseThrow(()->conflict("STALE_QUESTION","Question does not belong to this session")); }
    private void requireCurrentActive(AoristSession session,AoristQuestion question) {
        if(session.getStatus()!=SessionStatus.IN_PROGRESS) throw conflict("SESSION_CLOSED","Session has ended");
        if(question.getPosition()!=session.getCurrentPosition()) throw conflict("STALE_QUESTION","Use the current question");
    }
    private Instant now() { return scheduler.now().truncatedTo(ChronoUnit.MICROS); }
    private AoristException invalid(String message) { return new AoristException(400,"INVALID_AORIST_PRACTICE",message); }
    private AoristException conflict(String code,String message) { return new AoristException(409,code,message); }
}
