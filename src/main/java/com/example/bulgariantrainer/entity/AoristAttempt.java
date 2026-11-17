package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="aorist_attempts")
public class AoristAttempt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="question_id",nullable=false,unique=true) private AoristQuestion question;
    @Column(length=4000) private String userAnswer;
    private UUID optionId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false,length=30) private AoristOutcome outcome;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    protected AoristAttempt() {}
    public AoristAttempt(AoristQuestion question,String answer,UUID optionId,AoristOutcome outcome,Instant at) {
        this.question=question; userAnswer=answer; this.optionId=optionId; this.outcome=outcome; createdAt=at;
    }
    public String getUserAnswer() { return userAnswer; }
    public UUID getOptionId() { return optionId; }
    public AoristOutcome getOutcome() { return outcome; }
    public Instant getCreatedAt() { return createdAt; }
}
