package com.example.bulgariantrainer.dto;

import com.example.bulgariantrainer.entity.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class StatisticsDtos {
    private StatisticsDtos() {}

    public enum GradingType { AUTOMATIC, SELF_ASSESSED, UNCLASSIFIED }

    public record Rate(long attempts, long correct, long partial, long incorrect, Double accuracyPercent) {}
    public record Performance(Rate total, Rate automatic, Rate selfAssessed, Rate unclassified) {}
    public record Vocabulary(long total, long active, long archived, long newItems,
                             long dueItems, long masteredItems) {}
    public record DailyPoint(LocalDate date, long reviewAttempts, long correctAnswers,
                             long completedSessions) {}
    public record DifficultWord(long wordId, String bulgarianText, boolean archived,
                                List<String> russianTranslations, long incorrectAttempts,
                                Instant lastIncorrectAt) {}
    public record RecentSession(long id, TrainingMode trainingMode, SessionStatus status,
                                Instant startedAt, Instant completedAt, int totalQuestions,
                                long answeredQuestions, long correctAnswers, Double accuracyPercent) {}
    public record Overview(Vocabulary vocabulary, long completedSessions, long reviewAttempts,
                           Performance overall, Performance flashcards, Performance typingBgToRu,
                           Performance typingRuToBg, Performance sentences, long consecutiveStudyDays,
                           long reviewsScheduledToday, List<DailyPoint> last7Days,
                           List<DailyPoint> last30Days, List<DifficultWord> difficultWords,
                           List<RecentSession> recentSessions) {}
    public record Mistake(long attemptId, TrainingMode trainingMode, TranslationDirection direction,
                          String userAnswer, Instant createdAt, GradingType gradingType) {}
    public record WordMistakes(long wordId, String bulgarianText, boolean archived,
                               List<String> currentRussianTranslations, long incorrectAttempts,
                               List<Mistake> recentMistakes) {}
}
