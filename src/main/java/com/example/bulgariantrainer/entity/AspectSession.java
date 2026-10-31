package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="aspect_sessions")
public class AspectSession {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,updatable=false) private UUID requestId;
    @Lob @Column(nullable=false,updatable=false) private String settingsJson;
    @Column(nullable=false) private int totalQuestions;
    @Column(nullable=false) private int currentPosition=1;
    @Column(nullable=false) private int correctAnswers;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false,length=30)
    private SessionStatus status=SessionStatus.IN_PROGRESS;
    @Column(nullable=false,updatable=false) private Instant startedAt;
    private Instant completedAt;
    protected AspectSession() {}
    public AspectSession(UUID requestId,String settings,int total,Instant at){
        this.requestId=requestId;settingsJson=settings;totalQuestions=total;startedAt=at;
    }
    public void recordCorrect(){correctAnswers++;}
    public void advance(){currentPosition++;}
    public void finish(boolean early,Instant at){
        if(status!=SessionStatus.IN_PROGRESS)return;
        status=early?SessionStatus.ABANDONED:SessionStatus.COMPLETED;completedAt=at;
    }
    public void hideCurrent(){currentPosition=totalQuestions+1;}
    public Long getId(){return id;} public String getSettingsJson(){return settingsJson;}
    public int getTotalQuestions(){return totalQuestions;} public int getCurrentPosition(){return currentPosition;}
    public int getCorrectAnswers(){return correctAnswers;} public SessionStatus getStatus(){return status;}
    public Instant getStartedAt(){return startedAt;} public Instant getCompletedAt(){return completedAt;}
}
