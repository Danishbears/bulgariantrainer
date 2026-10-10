package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.FlashcardDtos.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.exception.FlashcardException;
import com.example.bulgariantrainer.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
public class FlashcardService {
    private final StudySessionRepository sessions;
    private final FlashcardQuestionRepository questions;
    private final WordRepository words;
    private final StudyProgressRepository progress;
    private final StudyAttemptRepository attempts;
    private final SpacedRepetitionScheduler scheduler;

    public FlashcardService(StudySessionRepository sessions, FlashcardQuestionRepository questions,
                            WordRepository words, StudyProgressRepository progress, StudyAttemptRepository attempts,
                            SpacedRepetitionScheduler scheduler) {
        this.sessions = sessions; this.questions = questions; this.words = words;
        this.progress = progress; this.attempts = attempts;
        this.scheduler = scheduler;
    }

    public SessionResponse start(StartRequest request) {
        int size = request.size() == null ? 20 : request.size();
        FlashcardDirection direction = request.direction() == null ? FlashcardDirection.BG_TO_RU : request.direction();
        StudyScope scope = request.scope() == null ? StudyScope.MIXED : request.scope();
        Optional<StudySession> previous = sessions.findByFlashcardRequestId(request.requestId());
        if (previous.isPresent()) {
            StudySession session = session(previous.get().getId());
            if (session.getRequestedSize() != size || session.getFlashcardDirection() != direction || session.getStudyScope() != scope)
                throw conflict("START_REQUEST_CHANGED", "Use a new requestId for different session settings");
            return state(session);
        }
        List<Long> ids = words.findTrainableIds();
        var records = progress.findByTrainingModeAndWordIdIn(TrainingMode.FLASHCARD, ids).stream()
                .collect(java.util.stream.Collectors.toMap(p -> p.getWord().getId(), p -> p));
        var at = scheduler.now();
        ids = ids.stream().filter(id -> scheduler.eligible(scheduler.category(records.get(id), at), scope)).toList();
        List<Long> ordered = new ArrayList<>(ids);
        Collections.shuffle(ordered);
        ordered.sort(Comparator.comparingInt(id -> scheduler.priority(scheduler.category(records.get(id), at))));
        // Lock in stable order to avoid deadlocks between sessions; then randomize the card order.
        List<Word> selected = ordered.stream().limit(size).sorted()
                .map(words::findForUpdate).flatMap(Optional::stream)
                .filter(word -> !word.isArchived() && !word.getTranslations().isEmpty())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (selected.isEmpty()) throw conflict(scope == StudyScope.DUE ? "EMPTY_REVIEW_QUEUE" : scope == StudyScope.NEW ? "EMPTY_NEW_QUEUE" : "EMPTY_DICTIONARY", "No words are available for this study choice");
        Collections.shuffle(selected);
        StudySession session = new StudySession(TrainingMode.FLASHCARD);
        session.setStudyScope(scope);
        session.configureFlashcards(direction, request.requestId(), size, selected.size());
        sessions.saveAndFlush(session);
        boolean firstBulgarian = ThreadLocalRandom.current().nextBoolean();
        for (int i = 0; i < selected.size(); i++) {
            Word word = selected.get(i);
            TranslationDirection cardDirection = switch (direction) {
                case BG_TO_RU -> TranslationDirection.BG_TO_RU;
                case RU_TO_BG -> TranslationDirection.RU_TO_BG;
                case MIXED -> ((i % 2 == 0) == firstBulgarian) ? TranslationDirection.BG_TO_RU : TranslationDirection.RU_TO_BG;
            };
            List<String> translations = word.getTranslations().stream()
                    .sorted(Comparator.comparing(Translation::isPrimary).reversed().thenComparing(Translation::getId))
                    .map(Translation::getRussianText).toList();
            questions.save(new FlashcardQuestion(session, word, i + 1, cardDirection, translations));
        }
        questions.flush();
        return state(session);
    }

    /** All operations lock the session, including reads, to return a consistent server-owned state. */
    public SessionResponse current(long id) { return state(session(id)); }

    public SessionResponse reveal(long id, QuestionRequest request) {
        StudySession session = session(id);
        requireActive(session);
        FlashcardQuestion question = currentQuestion(session);
        if (question == null || !question.getId().equals(request.questionId()))
            throw conflict("STALE_QUESTION", "This is not the current question");
        question.reveal();
        return state(session);
    }

    public SessionResponse rate(long id, RatingRequest request) {
        StudySession session = session(id);
        FlashcardQuestion question = questions.findByIdAndSessionId(request.questionId(), id)
                .orElseThrow(() -> conflict("STALE_QUESTION", "Question does not belong to this session"));
        AttemptResult result = request.known() ? AttemptResult.CORRECT : AttemptResult.INCORRECT;
        // A replay, including after the last card completed the session, never writes another attempt.
        if (question.getAttempt() != null) {
            if (question.getAttempt().getResult() != result)
                throw conflict("RATING_CONFLICT", "This question already has a different rating");
            return state(session);
        }
        requireActive(session);
        FlashcardQuestion current = currentQuestion(session);
        if (current == null || !current.getId().equals(question.getId()))
            throw conflict("STALE_QUESTION", "Rate the current question first");
        if (!question.isRevealed()) throw conflict("ANSWER_HIDDEN", "Reveal the answer before rating");

        // Serializes progress creation and updates across different sessions for the same word.
        Word word = words.findForUpdate(question.getWord().getId()).orElseThrow();
        StudyProgress record = progress.findByWordIdAndTrainingMode(word.getId(), TrainingMode.FLASHCARD)
                .orElseGet(() -> new StudyProgress(word, TrainingMode.FLASHCARD));
        scheduler.record(record, request.known());
        progress.save(record);
        StudyAttempt attempt = attempts.save(new StudyAttempt(session, word, null, question.getDirection(),
                request.known() ? "KNOW" : "DONT_KNOW", result));
        question.record(attempt);
        if (request.known()) session.recordCorrectAnswer();
        questions.flush();
        if (answered(session) == session.getTotalQuestions()) session.complete(false);
        return state(session);
    }

    public SessionResponse complete(long id) {
        StudySession session = session(id);
        session.complete(answered(session) < session.getTotalQuestions());
        return state(session);
    }

    public ResultsResponse results(long id) {
        StudySession session = session(id);
        if (session.getStatus() == SessionStatus.IN_PROGRESS)
            throw conflict("SESSION_NOT_COMPLETED", "Complete the session before requesting results");
        List<ResultItem> items = questions.findBySessionIdOrderByPositionAsc(id).stream()
                .map(q -> new ResultItem(q.getPosition(), q.getBulgarianText(), q.getRussianTexts(), q.getDirection(),
                        q.getAttempt() == null ? AttemptResult.SKIPPED : q.getAttempt().getResult())).toList();
        return new ResultsResponse(state(session), items);
    }

    private StudySession session(long id) {
        return sessions.findForUpdate(id)
                .filter(s -> s.getTrainingMode() == TrainingMode.FLASHCARD && s.getFlashcardDirection() != null)
                .orElseThrow(() -> new FlashcardException(404, "SESSION_NOT_FOUND", "Flashcard session not found"));
    }

    private void requireActive(StudySession session) {
        if (session.getStatus() != SessionStatus.IN_PROGRESS)
            throw conflict("SESSION_CLOSED", "Session is already completed");
    }

    private FlashcardQuestion currentQuestion(StudySession session) {
        return questions.findFirstBySessionIdAndAttemptIsNullOrderByPositionAsc(session.getId()).orElse(null);
    }

    private int answered(StudySession session) {
        return Math.toIntExact(questions.countBySessionIdAndAttemptIsNotNull(session.getId()));
    }

    private SessionResponse state(StudySession session) {
        int answered = answered(session);
        FlashcardQuestion current = session.getStatus() == SessionStatus.IN_PROGRESS ? currentQuestion(session) : null;
        QuestionResponse question = null;
        if (current != null) {
            boolean bulgarian = current.getDirection() == TranslationDirection.BG_TO_RU;
            question = new QuestionResponse(current.getId(), current.getPosition(), current.getDirection(),
                    bulgarian ? List.of(current.getBulgarianText()) : current.getRussianTexts(), current.isRevealed(),
                    !current.isRevealed() ? List.of() : bulgarian ? current.getRussianTexts() : List.of(current.getBulgarianText()));
        }
        return new SessionResponse(session.getId(), session.getFlashcardDirection(), session.getStatus(),
                session.getTotalQuestions(), answered, session.getCorrectAnswers(), answered - session.getCorrectAnswers(),
                session.getTotalQuestions() - answered, session.getStartedAt(), session.getCompletedAt(), question);
    }

    private FlashcardException conflict(String code, String message) { return new FlashcardException(409, code, message); }
}
