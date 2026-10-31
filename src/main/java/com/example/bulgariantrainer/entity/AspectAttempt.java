package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity @Table(name="aspect_attempts")
public class AspectAttempt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="question_id",nullable=false,unique=true) private AspectQuestion question;
    @Column(length=4000) private String userAnswer;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false,length=30) private AspectOutcome outcome;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    protected AspectAttempt() {}
    public AspectAttempt(AspectQuestion question,String answer,AspectOutcome outcome,Instant at){
        this.question=question;userAnswer=answer;this.outcome=outcome;createdAt=at;
    }
    public String getUserAnswer(){return userAnswer;} public AspectOutcome getOutcome(){return outcome;}
    public Instant getCreatedAt(){return createdAt;}
}
