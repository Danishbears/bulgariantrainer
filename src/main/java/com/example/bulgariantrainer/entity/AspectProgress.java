package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Independent stored learning history; pair management never modifies these rows. */
@Entity @Table(name="aspect_progress")
public class AspectProgress {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="pair_id",nullable=false) private AspectPair pair;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable=false,length=40) private AspectDirection direction;
    @Column(nullable=false) private int totalAttempts,correctAttempts,successfulReviewCount,consecutiveCorrect,repetitionInterval;
    private Instant lastReviewedAt,nextReviewAt;
    protected AspectProgress() {}
    public AspectProgress(AspectPair pair,AspectDirection direction){
        if(direction==AspectDirection.MIXED)throw new IllegalArgumentException("Progress needs an actual direction");
        this.pair=pair;this.direction=direction;
    }
    public void record(boolean correct,Instant at,int interval){
        totalAttempts++;
        if(correct){correctAttempts++;successfulReviewCount++;consecutiveCorrect++;}else consecutiveCorrect=0;
        lastReviewedAt=at;repetitionInterval=interval;nextReviewAt=at.plus(java.time.Duration.ofDays(interval));
    }
    public AspectPair getPair(){return pair;} public AspectDirection getDirection(){return direction;}
    public int getSuccessfulReviewCount(){return successfulReviewCount;} public int getConsecutiveCorrect(){return consecutiveCorrect;}
    public int getRepetitionInterval(){return repetitionInterval;} public Instant getLastReviewedAt(){return lastReviewedAt;}
    public Long getId(){return id;} public int getTotalAttempts(){return totalAttempts;}
    public int getCorrectAttempts(){return correctAttempts;} public Instant getNextReviewAt(){return nextReviewAt;}
}
