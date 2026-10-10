package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.entity.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Configuration
class StudyClockConfiguration {
    @Bean Clock studyClock() { return Clock.systemUTC(); }
}

/** Shared whole-day schedule. The server clock is the sole scheduling time source. */
@Service
public class SpacedRepetitionScheduler {
    private final Clock clock;
    private final int masterySuccesses;
    private final int masteryIntervalDays;

    public SpacedRepetitionScheduler(Clock clock,
            @Value("${training.srs.mastery-successes:5}") int masterySuccesses,
            @Value("${training.srs.mastery-interval-days:28}") int masteryIntervalDays) {
        if (masterySuccesses < 1 || masteryIntervalDays < 1) throw new IllegalArgumentException("Invalid mastery criteria");
        this.clock = clock;
        this.masterySuccesses = masterySuccesses;
        this.masteryIntervalDays = masteryIntervalDays;
    }

    public Instant now() { return clock.instant(); }

    public int intervalFor(boolean correct, int consecutiveBefore, int previousInterval) {
        if (!correct) return 1;
        return switch (consecutiveBefore) {
            case 0 -> 1;
            case 1 -> 3;
            case 2 -> 7;
            default -> Math.min(365, Math.max(7, previousInterval) * 2);
        };
    }

    public void record(StudyProgress progress, boolean correct) {
        progress.recordAnswer(correct, now(), intervalFor(correct, progress.getConsecutiveCorrect(), progress.getRepetitionInterval()));
    }

    public void record(SentenceProgress progress, AttemptResult result) {
        boolean correct = result == AttemptResult.CORRECT;
        progress.record(result, now(), intervalFor(correct, progress.getConsecutiveCorrect(), progress.getRepetitionInterval()));
    }

    public LearningCategory category(StudyProgress progress, Instant at) {
        return category(progress == null ? 0 : progress.getTotalAttempts(),
                progress == null ? null : progress.getNextReviewAt(),
                progress == null ? 0 : progress.getConsecutiveCorrect(),
                progress == null ? 0 : progress.getRepetitionInterval(), at);
    }

    public LearningCategory category(SentenceProgress progress, Instant at) {
        return category(progress == null ? 0 : progress.getTotalAttempts(),
                progress == null ? null : progress.getNextReviewAt(),
                progress == null ? 0 : progress.getConsecutiveCorrect(),
                progress == null ? 0 : progress.getRepetitionInterval(), at);
    }

    private LearningCategory category(int attempts, Instant next, int streak, int interval, Instant at) {
        if (attempts == 0) return LearningCategory.NEW;
        if (next == null || !next.isAfter(at)) return LearningCategory.DUE;
        if (streak >= masterySuccesses && interval >= masteryIntervalDays) return LearningCategory.MASTERED;
        return LearningCategory.LEARNING;
    }

    public boolean eligible(LearningCategory category, StudyScope scope) {
        return scope == StudyScope.MIXED || (scope == StudyScope.DUE && category == LearningCategory.DUE)
                || (scope == StudyScope.NEW && category == LearningCategory.NEW);
    }

    public int priority(LearningCategory category) {
        return switch (category) { case DUE -> 0; case NEW -> 1; case LEARNING -> 2; case MASTERED -> 3; };
    }
}
