package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.StatisticsDtos.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.exception.WordNotFoundException;
import com.example.bulgariantrainer.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StatisticsService {
    private static final List<TrainingMode> WORD_SCHEDULES = List.of(
            TrainingMode.FLASHCARD, TrainingMode.TYPING_BG_TO_RU, TrainingMode.TYPING_RU_TO_BG);
    private final WordRepository words;
    private final SentenceRepository sentences;
    private final StudyProgressRepository wordProgress;
    private final SentenceProgressRepository sentenceProgress;
    private final StudySessionRepository sessions;
    private final StudyAttemptRepository attempts;
    private final SpacedRepetitionScheduler scheduler;

    public StatisticsService(WordRepository words, SentenceRepository sentences,
            StudyProgressRepository wordProgress, SentenceProgressRepository sentenceProgress,
            StudySessionRepository sessions, StudyAttemptRepository attempts,
            SpacedRepetitionScheduler scheduler) {
        this.words = words;
        this.sentences = sentences;
        this.wordProgress = wordProgress;
        this.sentenceProgress = sentenceProgress;
        this.sessions = sessions;
        this.attempts = attempts;
        this.scheduler = scheduler;
    }

    public Overview overview() {
        Instant now = scheduler.now();
        LocalDate today = utcDate(now);
        List<Word> allWords = words.findAll();
        Map<Long, Word> wordById = allWords.stream().collect(Collectors.toMap(Word::getId, Function.identity()));
        Set<Long> trainable = new HashSet<>(words.findTrainableIds());
        List<StudyProgress> progressRows = wordProgress.findAll();
        Map<Long, Map<TrainingMode, StudyProgress>> progressByWord = new HashMap<>();
        Set<Long> studied = new HashSet<>();
        for (StudyProgress row : progressRows) {
            Long id = row.getWord().getId();
            progressByWord.computeIfAbsent(id, ignored -> new EnumMap<>(TrainingMode.class))
                    .put(row.getTrainingMode(), row);
            if (row.getTotalAttempts() > 0) studied.add(id);
        }

        List<StudyAttemptSnapshot> history = attempts.findStatisticsRows();
        MutablePerformance overall = new MutablePerformance();
        MutablePerformance flashcards = new MutablePerformance();
        MutablePerformance bgToRu = new MutablePerformance();
        MutablePerformance ruToBg = new MutablePerformance();
        MutablePerformance sentenceTranslation = new MutablePerformance();
        Map<Long, MutableRate> sessionRates = new HashMap<>();
        Map<LocalDate, MutableDaily> days = new HashMap<>();
        Set<LocalDate> studyDates = new HashSet<>();
        Map<Long, WrongCount> wrongWords = new HashMap<>();

        for (StudyAttemptSnapshot row : history) {
            if (!finalized(row.result())) continue;
            GradingType grading = grading(row.trainingMode(), row.evaluationSource());
            overall.add(row.result(), grading);
            switch (row.trainingMode()) {
                case FLASHCARD -> flashcards.add(row.result(), grading);
                case TYPING -> {
                    if (row.direction() == TranslationDirection.BG_TO_RU) bgToRu.add(row.result(), grading);
                    else ruToBg.add(row.result(), grading);
                }
                case SENTENCE -> sentenceTranslation.add(row.result(), grading);
                default -> { /* Direction-specific values are schedules, not session modes. */ }
            }
            sessionRates.computeIfAbsent(row.sessionId(), ignored -> new MutableRate()).add(row.result());
            LocalDate day = utcDate(row.createdAt());
            studyDates.add(day);
            days.computeIfAbsent(day, ignored -> new MutableDaily()).addAttempt(row.result());
            if (row.wordId() != null) studied.add(row.wordId());
            if (row.wordId() != null && row.result() == AttemptResult.INCORRECT) {
                wrongWords.computeIfAbsent(row.wordId(), ignored -> new WrongCount()).add(row.createdAt());
            }
        }

        long active = allWords.stream().filter(word -> !word.isArchived()).count();
        long newItems = 0, dueItems = 0, masteredItems = 0, scheduledToday = 0;
        for (Word word : allWords) {
            if (word.isArchived() || !trainable.contains(word.getId())) continue;
            if (!studied.contains(word.getId())) newItems++;
            Map<TrainingMode, StudyProgress> perMode = progressByWord.getOrDefault(word.getId(), Map.of());
            boolean due = false, mastered = true;
            for (TrainingMode mode : WORD_SCHEDULES) {
                StudyProgress row = perMode.get(mode);
                LearningCategory category = scheduler.category(row, now);
                due |= category == LearningCategory.DUE;
                mastered &= category == LearningCategory.MASTERED;
                if (row != null && isScheduledToday(row.getNextReviewAt(), today)) scheduledToday++;
            }
            if (due) dueItems++;
            if (mastered) masteredItems++;
        }
        Set<Long> activeSentences = new HashSet<>(sentences.findTrainableIds());
        for (SentenceProgress row : sentenceProgress.findAll()) {
            if (activeSentences.contains(row.getSentence().getId())
                    && isScheduledToday(row.getNextReviewAt(), today)) scheduledToday++;
        }

        List<StudySession> allSessions = sessions.findAll();
        long completed = 0;
        for (StudySession session : allSessions) {
            if (session.getStatus() == SessionStatus.COMPLETED) {
                completed++;
                if (session.getCompletedAt() != null) {
                    days.computeIfAbsent(utcDate(session.getCompletedAt()), ignored -> new MutableDaily()).completed++;
                }
            }
        }
        List<RecentSession> recent = allSessions.stream()
                .sorted(Comparator.comparing(StudySession::getStartedAt).reversed()
                        .thenComparing(StudySession::getId, Comparator.reverseOrder()))
                .limit(10)
                .map(session -> {
                    Rate rate = sessionRates.getOrDefault(session.getId(), new MutableRate()).freeze();
                    return new RecentSession(session.getId(), session.getTrainingMode(), session.getStatus(),
                            session.getStartedAt(), session.getCompletedAt(), session.getTotalQuestions(),
                            rate.attempts(), rate.correct(), rate.accuracyPercent());
                }).toList();
        List<DifficultWord> difficult = wrongWords.entrySet().stream()
                .filter(entry -> wordById.containsKey(entry.getKey()))
                .sorted(Comparator.<Map.Entry<Long, WrongCount>>comparingLong(entry -> entry.getValue().count).reversed()
                        .thenComparing(entry -> entry.getValue().last, Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(20)
                .map(entry -> {
                    Word word = wordById.get(entry.getKey());
                    return new DifficultWord(word.getId(), word.getBulgarianText(), word.isArchived(),
                            translations(word), entry.getValue().count, entry.getValue().last);
                }).toList();
        Vocabulary vocabulary = new Vocabulary(allWords.size(), active, allWords.size() - active,
                newItems, dueItems, masteredItems);
        return new Overview(vocabulary, completed, overall.total.attempts, overall.freeze(),
                flashcards.freeze(), bgToRu.freeze(), ruToBg.freeze(), sentenceTranslation.freeze(),
                streak(studyDates, today), scheduledToday, daily(days, today, 7), daily(days, today, 30),
                difficult, recent);
    }

    public WordMistakes wordMistakes(long wordId) {
        Word word = words.findById(wordId).orElseThrow(() -> new WordNotFoundException(wordId));
        List<Mistake> recent = attempts.findTop20ByWordIdAndResultOrderByCreatedAtDescIdDesc(
                wordId, AttemptResult.INCORRECT).stream()
                .map(row -> new Mistake(row.getId(), row.getSession().getTrainingMode(), row.getDirection(),
                        row.getUserAnswer(), row.getCreatedAt(),
                        grading(row.getSession().getTrainingMode(), row.getEvaluationSource())))
                .toList();
        return new WordMistakes(wordId, word.getBulgarianText(), word.isArchived(), translations(word),
                attempts.countByWordIdAndResult(wordId, AttemptResult.INCORRECT), recent);
    }

    private static List<String> translations(Word word) {
        return word.getTranslations().stream().sorted(Comparator.comparing(Translation::isPrimary).reversed()
                .thenComparing(Translation::getId)).map(Translation::getRussianText).toList();
    }

    private static boolean finalized(AttemptResult result) {
        return result == AttemptResult.CORRECT || result == AttemptResult.INCORRECT || result == AttemptResult.PARTIAL;
    }

    private static GradingType grading(TrainingMode mode, EvaluationSource source) {
        if (mode == TrainingMode.FLASHCARD) return GradingType.SELF_ASSESSED;
        if (mode == TrainingMode.TYPING) return GradingType.AUTOMATIC;
        if (source == EvaluationSource.AUTOMATIC) return GradingType.AUTOMATIC;
        if (source == EvaluationSource.SELF) return GradingType.SELF_ASSESSED;
        return GradingType.UNCLASSIFIED;
    }

    private static LocalDate utcDate(Instant instant) { return instant.atZone(ZoneOffset.UTC).toLocalDate(); }
    private static boolean isScheduledToday(Instant next, LocalDate today) {
        return next != null && utcDate(next).equals(today);
    }

    private static long streak(Set<LocalDate> dates, LocalDate today) {
        LocalDate day = dates.contains(today) ? today : today.minusDays(1);
        long count = 0;
        while (dates.contains(day)) { count++; day = day.minusDays(1); }
        return count;
    }

    private static List<DailyPoint> daily(Map<LocalDate, MutableDaily> days, LocalDate today, int size) {
        List<DailyPoint> result = new ArrayList<>(size);
        for (int offset = size - 1; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            MutableDaily day = days.getOrDefault(date, new MutableDaily());
            result.add(new DailyPoint(date, day.attempts, day.correct, day.completed));
        }
        return List.copyOf(result);
    }

    private static final class MutableRate {
        long attempts, correct, partial, incorrect;
        void add(AttemptResult result) {
            attempts++;
            switch (result) {
                case CORRECT -> correct++;
                case PARTIAL -> partial++;
                case INCORRECT -> incorrect++;
                default -> throw new IllegalArgumentException("Final result required");
            }
        }
        Rate freeze() {
            Double accuracy = attempts == 0 ? null : Math.round(correct * 1000.0 / attempts) / 10.0;
            return new Rate(attempts, correct, partial, incorrect, accuracy);
        }
    }
    private static final class MutablePerformance {
        final MutableRate total = new MutableRate();
        final EnumMap<GradingType, MutableRate> byGrading = new EnumMap<>(GradingType.class);
        void add(AttemptResult result, GradingType grading) {
            total.add(result);
            byGrading.computeIfAbsent(grading, ignored -> new MutableRate()).add(result);
        }
        Performance freeze() {
            return new Performance(total.freeze(), rate(GradingType.AUTOMATIC),
                    rate(GradingType.SELF_ASSESSED), rate(GradingType.UNCLASSIFIED));
        }
        private Rate rate(GradingType grading) {
            return byGrading.getOrDefault(grading, new MutableRate()).freeze();
        }
    }
    private static final class MutableDaily {
        long attempts, correct, completed;
        void addAttempt(AttemptResult result) { attempts++; if (result == AttemptResult.CORRECT) correct++; }
    }
    private static final class WrongCount {
        long count;
        Instant last;
        void add(Instant at) { count++; if (last == null || at.isAfter(last)) last = at; }
    }
}
