package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "study_attempts")
public class StudyAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private StudySession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id")
    private Word word;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sentence_id")
    private Sentence sentence;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private TranslationDirection direction;

    @Column(length = 4000)
    private String userAnswer;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private AttemptResult result;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(length=30)
    private EvaluationSource evaluationSource;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected StudyAttempt() {
    }

    public StudyAttempt(StudySession session, Word word, Sentence sentence,
                        TranslationDirection direction, String userAnswer, AttemptResult result) {
        if ((word == null) == (sentence == null)) {
            throw new IllegalArgumentException("An attempt must reference exactly one word or sentence");
        }
        this.session = session;
        this.word = word;
        this.sentence = sentence;
        this.direction = direction;
        this.userAnswer = userAnswer;
        this.result = result;
        this.createdAt = Instant.now();
    }

    public void finalizeEvaluation(AttemptResult result, EvaluationSource source) {
        if (this.result != AttemptResult.PENDING) throw new IllegalStateException("Attempt already evaluated");
        this.result = result; this.evaluationSource = source;
    }
    public void markAutomatic() { this.evaluationSource = EvaluationSource.AUTOMATIC; }
    public EvaluationSource getEvaluationSource() { return evaluationSource; }
    public Long getId() { return id; }
    public StudySession getSession() { return session; }
    public Word getWord() { return word; }
    public Sentence getSentence() { return sentence; }
    public TranslationDirection getDirection() { return direction; }
    public String getUserAnswer() { return userAnswer; }
    public AttemptResult getResult() { return result; }
    public Instant getCreatedAt() { return createdAt; }
}

