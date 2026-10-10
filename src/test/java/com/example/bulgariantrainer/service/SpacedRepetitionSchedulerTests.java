package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.entity.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SpacedRepetitionSchedulerTests {
    private final Instant now = Instant.parse("2026-10-10T12:30:00Z");
    private final SpacedRepetitionScheduler scheduler = new SpacedRepetitionScheduler(
            Clock.fixed(now, ZoneOffset.UTC), 5, 28);
    private final Word word = new Word("къща", "къща", null, null, null);

    @Test void successfulReviewsUseOneThreeSevenThenDoubleAndBecomeDueAtExactInstant() {
        StudyProgress record = new StudyProgress(word, TrainingMode.FLASHCARD);
        assertThat(scheduler.category(record, now)).isEqualTo(LearningCategory.NEW);
        for (int expected : new int[] {1, 3, 7, 14, 28, 56}) {
            scheduler.record(record, true);
            assertThat(record.getRepetitionInterval()).isEqualTo(expected);
            assertThat(record.getLastReviewedAt()).isEqualTo(now);
            assertThat(record.getNextReviewAt()).isEqualTo(now.plus(Duration.ofDays(expected)));
        }
        assertThat(record.getSuccessfulReviewCount()).isEqualTo(6);
        assertThat(record.getTotalAttempts()).isEqualTo(6);
        assertThat(scheduler.category(record, now)).isEqualTo(LearningCategory.MASTERED);
        assertThat(scheduler.category(record, record.getNextReviewAt())).isEqualTo(LearningCategory.DUE);
        assertThat(scheduler.eligible(LearningCategory.MASTERED, StudyScope.MIXED)).isTrue();
        assertThat(scheduler.eligible(LearningCategory.MASTERED, StudyScope.DUE)).isFalse();
    }

    @Test void failureResetsStreakButPreservesLifetimeSuccessesAndSeparateModes() {
        StudyProgress bg = new StudyProgress(word, TrainingMode.TYPING_BG_TO_RU);
        StudyProgress ru = new StudyProgress(word, TrainingMode.TYPING_RU_TO_BG);
        scheduler.record(bg, true);
        scheduler.record(bg, true);
        scheduler.record(bg, false);
        assertThat(bg.getRepetitionInterval()).isEqualTo(1);
        assertThat(bg.getConsecutiveCorrect()).isZero();
        assertThat(bg.getSuccessfulReviewCount()).isEqualTo(2);
        assertThat(bg.getTotalAttempts()).isEqualTo(3);
        assertThat(scheduler.category(ru, now)).isEqualTo(LearningCategory.NEW);
        scheduler.record(bg, true);
        assertThat(bg.getRepetitionInterval()).isEqualTo(1);
        assertThat(ru.getTotalAttempts()).isZero();
    }

    @Test void sentencePartialAndIncorrectScheduleShortRetryWithoutSuccess() {
        Sentence sentence = new Sentence("Аз съм тук.", "Я здесь.", null, null);
        SentenceProgress record = new SentenceProgress(sentence);
        scheduler.record(record, AttemptResult.CORRECT);
        scheduler.record(record, AttemptResult.PARTIAL);
        assertThat(record.getSuccessfulReviewCount()).isEqualTo(1);
        assertThat(record.getTotalAttempts()).isEqualTo(2);
        assertThat(record.getRepetitionInterval()).isEqualTo(1);
        assertThat(record.getConsecutiveCorrect()).isZero();
        scheduler.record(record, AttemptResult.INCORRECT);
        assertThat(record.getTotalAttempts()).isEqualTo(3);
        assertThat(record.getNextReviewAt()).isEqualTo(now.plus(Duration.ofDays(1)));
    }
}
