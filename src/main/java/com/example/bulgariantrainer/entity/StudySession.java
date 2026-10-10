package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "study_sessions")
public class StudySession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private TrainingMode trainingMode;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    private Instant completedAt;

    @Column(nullable = false)
    private int totalQuestions;

    @Column(nullable = false)
    private int correctAnswers;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private FlashcardDirection flashcardDirection;

    @Column(unique = true)
    private java.util.UUID flashcardRequestId;

    private Integer requestedSize;
    @Column(unique=true) private java.util.UUID sentenceRequestId;
    private Integer sentenceRequestedSize;
    public void configureSentences(java.util.UUID requestId,int requestedSize,int total){
        sentenceRequestId=requestId;sentenceRequestedSize=requestedSize;totalQuestions=total;
    }
    public java.util.UUID getSentenceRequestId(){return sentenceRequestId;}
    public Integer getSentenceRequestedSize(){return sentenceRequestedSize;}

    @Column(unique = true)
    private java.util.UUID typingRequestId;
    private Boolean shorterAccepted;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name="study_scope", nullable=false, length=20)
    private StudyScope studyScope = StudyScope.MIXED;
    public StudyScope getStudyScope() { return studyScope; }
    public void setStudyScope(StudyScope studyScope) { this.studyScope = studyScope; }

    public void configureTyping(java.util.UUID requestId, boolean allowShorter, int total) {
        typingRequestId = requestId; shorterAccepted = allowShorter; totalQuestions = total;
    }
    public java.util.UUID getTypingRequestId() { return typingRequestId; }
    public Boolean getShorterAccepted() { return shorterAccepted; }

    public void configureFlashcards(FlashcardDirection direction, java.util.UUID requestId, int requestedSize, int total) {
        this.flashcardDirection = direction;
        this.flashcardRequestId = requestId;
        this.requestedSize = requestedSize;
        this.totalQuestions = total;
    }

    public void recordCorrectAnswer() { correctAnswers++; }

    public void complete(boolean early) {
        if (status != SessionStatus.IN_PROGRESS) return;
        status = early ? SessionStatus.ABANDONED : SessionStatus.COMPLETED;
        completedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    public FlashcardDirection getFlashcardDirection() { return flashcardDirection; }
    public Integer getRequestedSize() { return requestedSize; }

    protected StudySession() {
    }

    public StudySession(TrainingMode trainingMode) {
        this.trainingMode = trainingMode;
        this.startedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    public Long getId() { return id; }
    public TrainingMode getTrainingMode() { return trainingMode; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getTotalQuestions() { return totalQuestions; }
    public int getCorrectAnswers() { return correctAnswers; }
    public SessionStatus getStatus() { return status; }
}

