package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "study_progress", uniqueConstraints =
        @UniqueConstraint(name = "uk_study_progress_word_mode", columnNames = {"word_id", "training_mode"}))
public class StudyProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private TrainingMode trainingMode;

    @Column(nullable = false)
    private int totalAttempts;

    @Column(nullable = false)
    private int correctAttempts;

    @Column(nullable = false)
    private int successfulReviewCount;

    @Column(nullable = false)
    private int consecutiveCorrect;

    private Instant lastReviewedAt;
    private Instant nextReviewAt;

    /** Interval in whole days. */
    @Column(nullable = false)
    private int repetitionInterval;

    @Column(nullable = false)
    private double easeFactor = 2.5;

    protected StudyProgress() {
    }

    public StudyProgress(Word word, TrainingMode trainingMode) {
        this.word = word;
        this.trainingMode = trainingMode;
    }

    public Long getId() { return id; }
    /** Recognition is self-reported; viewing a card never invokes this method. */
    public void recordAnswer(boolean known, Instant reviewedAt, int intervalDays) {
        totalAttempts++;
        if (known) { correctAttempts++; successfulReviewCount++; consecutiveCorrect++; }
        else consecutiveCorrect = 0;
        lastReviewedAt = reviewedAt;
        repetitionInterval = intervalDays;
        nextReviewAt = reviewedAt.plus(java.time.Duration.ofDays(intervalDays));
    }
    public Word getWord() { return word; }
    public TrainingMode getTrainingMode() { return trainingMode; }
    public int getTotalAttempts() { return totalAttempts; }
    public int getCorrectAttempts() { return correctAttempts; }
    public int getSuccessfulReviewCount() { return successfulReviewCount; }
    public int getConsecutiveCorrect() { return consecutiveCorrect; }
    public Instant getLastReviewedAt() { return lastReviewedAt; }
    public Instant getNextReviewAt() { return nextReviewAt; }
    public int getRepetitionInterval() { return repetitionInterval; }
    public double getEaseFactor() { return easeFactor; }
}
