package com.example.bulgariantrainer.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="sentence_progress")
public class SentenceProgress {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sentence_id",nullable=false,unique=true) private Sentence sentence;
    @Column(nullable=false) private int totalAttempts,correctAttempts,partialAttempts,incorrectAttempts;
    private Instant lastReviewedAt;
    private Instant nextReviewAt;
    @Column(nullable=false) private int repetitionInterval;
    @Column(nullable=false) private int successfulReviewCount;
    @Column(nullable=false) private int consecutiveCorrect;
    protected SentenceProgress() {}
    public SentenceProgress(Sentence sentence){this.sentence=sentence;}
    public void record(AttemptResult result, Instant reviewedAt, int intervalDays){
        if(result!=AttemptResult.CORRECT && result!=AttemptResult.PARTIAL && result!=AttemptResult.INCORRECT)
            throw new IllegalArgumentException("Final evaluation required");
        totalAttempts++;
        if(result==AttemptResult.CORRECT){correctAttempts++;successfulReviewCount++;consecutiveCorrect++;}
        else {consecutiveCorrect=0;if(result==AttemptResult.PARTIAL)partialAttempts++;else incorrectAttempts++;}
        lastReviewedAt=reviewedAt;
        repetitionInterval=intervalDays;
        nextReviewAt=reviewedAt.plus(java.time.Duration.ofDays(intervalDays));
    }
    public int getTotalAttempts(){return totalAttempts;} public int getCorrectAttempts(){return correctAttempts;}
    public Sentence getSentence(){return sentence;}
    public int getPartialAttempts(){return partialAttempts;} public int getIncorrectAttempts(){return incorrectAttempts;}
    public int getConsecutiveCorrect(){return consecutiveCorrect;} public int getSuccessfulReviewCount(){return successfulReviewCount;}
    public Instant getLastReviewedAt(){return lastReviewedAt;} public Instant getNextReviewAt(){return nextReviewAt;}
    public int getRepetitionInterval(){return repetitionInterval;}
}
