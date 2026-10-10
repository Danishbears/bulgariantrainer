package com.example.bulgariantrainer.entity;
import jakarta.persistence.*;
@Entity
@Table(name="sentence_attempt_words")
public class SentenceAttemptWord {
    @EmbeddedId private SentenceAttemptWordId id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @MapsId("attemptId") @JoinColumn(name="attempt_id",nullable=false) private StudyAttempt attempt;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @MapsId("wordId") @JoinColumn(name="word_id",nullable=false) private Word word;
    protected SentenceAttemptWord() {}
    public SentenceAttemptWord(StudyAttempt attempt,Word word){this.attempt=attempt;this.word=word;this.id=new SentenceAttemptWordId(attempt.getId(),word.getId());}
}
