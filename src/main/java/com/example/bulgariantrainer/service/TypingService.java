package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.TypingDtos.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.exception.TypingException;
import com.example.bulgariantrainer.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional
public class TypingService {
    private final StudySessionRepository sessions;
    private final TypingQuestionRepository questions;
    private final WordRepository words;
    private final StudyAttemptRepository attempts;
    private final StudyProgressRepository progress;
    private final TypingQuestionPlanner planner;
    private final SpacedRepetitionScheduler scheduler;

    public TypingService(StudySessionRepository sessions, TypingQuestionRepository questions,
                         WordRepository words, StudyAttemptRepository attempts,
                         StudyProgressRepository progress, TypingQuestionPlanner planner, SpacedRepetitionScheduler scheduler) {
        this.sessions = sessions; this.questions = questions; this.words = words;
        this.attempts = attempts; this.progress = progress; this.planner = planner; this.scheduler = scheduler;
    }

    @Transactional(readOnly = true)
    public Availability availability(StudyScope requestedScope) {
        StudyScope scope = requestedScope == null ? StudyScope.MIXED : requestedScope;
        int count = eligibleIds(words.findTrainableIds(), scope).size();
        return new Availability(count, Math.min(30, count), count < 30);
    }

    public SessionResponse start(StartRequest request) {
        boolean allowShorter = Boolean.TRUE.equals(request.allowShorter());
        StudyScope scope = request.scope() == null ? StudyScope.MIXED : request.scope();
        var previous = sessions.findByTypingRequestId(request.requestId());
        if (previous.isPresent()) {
            StudySession session = session(previous.get().getId());
            if (session.getShorterAccepted() != allowShorter || session.getStudyScope() != scope)
                throw conflict("START_REQUEST_CHANGED", "Use a new requestId for different settings");
            return state(session);
        }
        List<Long> ids = eligibleIds(words.findTrainableIds(), scope);
        List<PlannedWord> planned = select(ids, scope);
        // Stable lock order also coordinates with vocabulary edits and other training modes.
        List<Word> selected = planned.stream().map(PlannedWord::id).sorted().map(words::findForUpdate)
                .flatMap(Optional::stream).filter(w -> !w.isArchived() && !w.getTranslations().isEmpty()).toList();
        if (selected.isEmpty()) throw conflict(scope == StudyScope.DUE ? "EMPTY_REVIEW_QUEUE" : scope == StudyScope.NEW ? "EMPTY_NEW_QUEUE" : "EMPTY_DICTIONARY", "No words are available for this study choice");
        if (selected.size() < 30 && !allowShorter)
            throw conflict("SHORTER_SESSION_REQUIRED", "Explicitly accept a shorter session before starting");
        Map<Long, Word> byId = new HashMap<>(); selected.forEach(w -> byId.put(w.getId(), w));
        planned = planner.shuffled(planned.stream().filter(p -> byId.containsKey(p.id())).toList());
        StudySession session = new StudySession(TrainingMode.TYPING);
        session.setStudyScope(scope);
        session.configureTyping(request.requestId(), allowShorter, selected.size());
        sessions.saveAndFlush(session);
        for (int i = 0; i < planned.size(); i++) {
            Word word = byId.get(planned.get(i).id());
            var translations = word.getTranslations().stream()
                    .sorted(Comparator.comparing(Translation::isPrimary).reversed().thenComparing(Translation::getId))
                    .map(Translation::getRussianText).toList();
            questions.save(new TypingQuestion(session, word, i + 1, planned.get(i).direction(), translations));
        }
        questions.flush();
        return state(session);
    }

    public SessionResponse current(long id) { return state(session(id)); }

    public SessionResponse answer(long id, AnswerRequest request) {
        StudySession session = session(id);
        TypingQuestion question = question(session, request.questionId());
        // Retries return the authoritative state without recording another attempt.
        if (question.getAttempt() != null) {
            if (!question.getAttempt().getUserAnswer().equals(request.answer()))
                throw conflict("ANSWER_CONFLICT", "A different answer is already saved");
            return state(session);
        }
        requireActive(session);
        requireCurrent(session, question);
        String normalized = VocabularyText.key(request.answer());
        if (normalized.isEmpty()) throw new TypingException(400, "INVALID_REQUEST", "Answer must not be blank");
        boolean correct = accepted(question).stream().map(VocabularyText::key).anyMatch(normalized::equals);
        Word word = words.findForUpdate(question.getWord().getId()).orElseThrow();
        TrainingMode mode = question.getDirection() == TranslationDirection.BG_TO_RU ? TrainingMode.TYPING_BG_TO_RU : TrainingMode.TYPING_RU_TO_BG;
        StudyProgress record = progress.findByWordIdAndTrainingMode(word.getId(), mode)
                .orElseGet(() -> new StudyProgress(word, mode));
        scheduler.record(record, correct);
        progress.save(record);
        question.record(attempts.save(new StudyAttempt(session, word, null, question.getDirection(),
                request.answer(), correct ? AttemptResult.CORRECT : AttemptResult.INCORRECT)));
        if (correct) session.recordCorrectAnswer();
        questions.flush();
        if (queue(session).stream().allMatch(q -> q.getAttempt() != null)) session.complete(false);
        return state(session);
    }

    public SessionResponse next(long id, QuestionRequest request) {
        StudySession session = session(id);
        TypingQuestion question = question(session, request.questionId());
        if (question.isAcknowledged()) return state(session);
        requireCurrent(session, question);
        if (question.getAttempt() == null) throw conflict("ANSWER_REQUIRED", "Submit an answer first");
        question.acknowledge();
        return state(session);
    }

    public SessionResponse complete(long id) {
        StudySession session = session(id);
        List<TypingQuestion> queue = queue(session);
        queue.stream().filter(q -> q.getAttempt() != null).forEach(TypingQuestion::acknowledge);
        session.complete(queue.stream().anyMatch(q -> q.getAttempt() == null));
        return state(session);
    }

    public ResultsResponse results(long id) {
        StudySession session = session(id);
        if (session.getStatus() == SessionStatus.IN_PROGRESS)
            throw conflict("SESSION_NOT_COMPLETED", "Complete the session first");
        var mistakes = queue(session).stream()
                .filter(q -> q.getAttempt() != null && q.getAttempt().getResult() == AttemptResult.INCORRECT)
                .map(q -> new Mistake(q.getPosition(), q.getDirection(), prompt(q),
                        q.getAttempt().getUserAnswer(), accepted(q))).toList();
        return new ResultsResponse(state(session), mistakes);
    }

    private StudySession session(long id) {
        return sessions.findForUpdate(id).filter(s -> s.getTrainingMode() == TrainingMode.TYPING && s.getTypingRequestId() != null)
                .orElseThrow(() -> new TypingException(404, "SESSION_NOT_FOUND", "Typing session not found"));
    }
    private List<TypingQuestion> queue(StudySession session) { return questions.findBySessionIdOrderByPositionAsc(session.getId()); }
    private TypingQuestion question(StudySession session, long id) {
        return queue(session).stream().filter(q -> q.getId() == id).findFirst()
                .orElseThrow(() -> conflict("STALE_QUESTION", "Question does not belong to this session"));
    }
    private void requireCurrent(StudySession session, TypingQuestion question) {
        var current = queue(session).stream().filter(q -> !q.isAcknowledged()).findFirst();
        if (current.isEmpty() || !current.get().getId().equals(question.getId()))
            throw conflict("STALE_QUESTION", "Use the current question");
    }
    private void requireActive(StudySession session) {
        if (session.getStatus() != SessionStatus.IN_PROGRESS) throw conflict("SESSION_CLOSED", "Session is closed");
    }
    private List<String> prompt(TypingQuestion q) {
        return q.getDirection() == TranslationDirection.BG_TO_RU ? List.of(q.getBulgarianText()) : q.getRussianTexts();
    }
    private List<String> accepted(TypingQuestion q) {
        return q.getDirection() == TranslationDirection.BG_TO_RU ? q.getRussianTexts() : List.of(q.getBulgarianText());
    }
    private SessionResponse state(StudySession session) {
        var queue = queue(session);
        int answered = (int) queue.stream().filter(q -> q.getAttempt() != null).count();
        // Keep saved feedback visible across refresh, including the final answer.
        var current = session.getStatus() == SessionStatus.ABANDONED ? Optional.<TypingQuestion>empty()
                : queue.stream().filter(q -> !q.isAcknowledged()).findFirst();
        QuestionResponse response = current.map(q -> new QuestionResponse(q.getId(), q.getPosition(), q.getDirection(), prompt(q),
                q.getAttempt() == null ? null : new Feedback(q.getAttempt().getResult(), q.getAttempt().getUserAnswer(), accepted(q))))
                .orElse(null);
        return new SessionResponse(session.getId(), session.getStatus(), session.getTotalQuestions(), answered,
                session.getCorrectAnswers(), response);
    }
    private TypingException conflict(String code, String message) { return new TypingException(409, code, message); }

    private record PlannedWord(Long id, TranslationDirection direction) {}

    private List<Long> eligibleIds(List<Long> ids, StudyScope scope) {
        if (scope == StudyScope.MIXED || ids.isEmpty()) return ids;
        var at = scheduler.now();
        Map<Long, StudyProgress> bg = records(ids, TrainingMode.TYPING_BG_TO_RU);
        Map<Long, StudyProgress> ru = records(ids, TrainingMode.TYPING_RU_TO_BG);
        return ids.stream().filter(id -> scheduler.eligible(scheduler.category(bg.get(id), at), scope)
                || scheduler.eligible(scheduler.category(ru.get(id), at), scope)).toList();
    }

    private Map<Long, StudyProgress> records(List<Long> ids, TrainingMode mode) {
        if (ids.isEmpty()) return Map.of();
        return progress.findByTrainingModeAndWordIdIn(mode, ids).stream()
                .collect(java.util.stream.Collectors.toMap(p -> p.getWord().getId(), p -> p));
    }

    private List<PlannedWord> select(List<Long> ids, StudyScope scope) {
        var at = scheduler.now();
        Map<Long, StudyProgress> bg = records(ids, TrainingMode.TYPING_BG_TO_RU);
        Map<Long, StudyProgress> ru = records(ids, TrainingMode.TYPING_RU_TO_BG);
        List<Long> bgIds = planner.shuffled(ids.stream().filter(id -> scheduler.eligible(scheduler.category(bg.get(id), at), scope)).toList());
        List<Long> ruIds = planner.shuffled(ids.stream().filter(id -> scheduler.eligible(scheduler.category(ru.get(id), at), scope)).toList());
        // Put direction-exclusive words first so a balanced assignment does not consume them in the wrong direction.
        bgIds.sort(Comparator.comparingInt((Long id) -> scheduler.priority(scheduler.category(bg.get(id), at)))
                .thenComparingInt(id -> ruIds.contains(id) ? 1 : 0)
                .thenComparingInt(id -> scheduler.category(ru.get(id), at) == LearningCategory.DUE ? 1 : 0));
        ruIds.sort(Comparator.comparingInt((Long id) -> scheduler.priority(scheduler.category(ru.get(id), at)))
                .thenComparingInt(id -> bgIds.contains(id) ? 1 : 0)
                .thenComparingInt(id -> scheduler.category(bg.get(id), at) == LearningCategory.DUE ? 1 : 0));
        List<PlannedWord> result = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        int bgCount = 0, ruCount = 0;
        while (result.size() < 30) {
            boolean preferBg = bgCount <= ruCount;
            TranslationDirection choice = preferBg ? TranslationDirection.BG_TO_RU : TranslationDirection.RU_TO_BG;
            List<Long> preferred = preferBg ? bgIds : ruIds;
            Long id = preferred.stream().filter(candidate -> !used.contains(candidate)).findFirst().orElse(null);
            if (id == null) {
                choice = preferBg ? TranslationDirection.RU_TO_BG : TranslationDirection.BG_TO_RU;
                List<Long> other = preferBg ? ruIds : bgIds;
                id = other.stream().filter(candidate -> !used.contains(candidate)).findFirst().orElse(null);
            }
            if (id == null) break;
            used.add(id); result.add(new PlannedWord(id, choice));
            if (choice == TranslationDirection.BG_TO_RU) bgCount++; else ruCount++;
        }
        return result;
    }
}

