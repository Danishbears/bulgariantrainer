package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Each verb/person/exercise has its own schedule; content management never resets it. */
@Entity @Table(name="aorist_progress")
public class AoristProgress {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="verb_id",nullable=false) private AoristVerb verb;
    @Column(nullable=false,length=3) private String person;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable=false,length=30) private AoristExerciseType exerciseType=AoristExerciseType.TYPED;
    @Column(nullable=false) private int totalAttempts,correctAttempts,successfulReviewCount,consecutiveCorrect,repetitionInterval;
    private Instant lastReviewedAt,nextReviewAt;
    protected AoristProgress() {}
    public AoristProgress(AoristVerb verb,String person,AoristExerciseType exerciseType) {
        this.verb=verb; this.person=person; this.exerciseType=exerciseType;
    }
    public void record(boolean correct,Instant at,int interval) {
        totalAttempts++;
        if(correct) { correctAttempts++; successfulReviewCount++; consecutiveCorrect++; } else consecutiveCorrect=0;
        lastReviewedAt=at; repetitionInterval=interval; nextReviewAt=at.plus(java.time.Duration.ofDays(interval));
    }
    public Long getId() { return id; }
    public AoristVerb getVerb() { return verb; }
    public String getPerson() { return person; }
    public AoristExerciseType getExerciseType() { return exerciseType; }
    public int getTotalAttempts() { return totalAttempts; }
    public int getCorrectAttempts() { return correctAttempts; }
    public int getSuccessfulReviewCount() { return successfulReviewCount; }
    public int getConsecutiveCorrect() { return consecutiveCorrect; }
    public int getRepetitionInterval() { return repetitionInterval; }
    public Instant getLastReviewedAt() { return lastReviewedAt; }
    public Instant getNextReviewAt() { return nextReviewAt; }
}
