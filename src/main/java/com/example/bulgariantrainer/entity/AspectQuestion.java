package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity @Table(name="aspect_questions")
public class AspectQuestion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="session_id",nullable=false) private AspectSession session;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="pair_id",nullable=false) private AspectPair pair;
    @Column(nullable=false) private int position;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false,length=40) private AspectDirection direction;
    @Lob @Column(nullable=false,updatable=false) private String snapshotJson;
    @Column(nullable=false,length=64,updatable=false) private String contentHash;
    @Column(nullable=false) private boolean acknowledged;
    private Instant revealedAt;
    @OneToOne(mappedBy="question",fetch=FetchType.LAZY) private AspectAttempt attempt;
    protected AspectQuestion() {}
    public AspectQuestion(AspectSession session,AspectPair pair,int position,AspectDirection direction,String snapshot){
        if(direction==AspectDirection.MIXED)throw new IllegalArgumentException("Question needs an actual direction");
        this.session=session;this.pair=pair;this.position=position;this.direction=direction;
        snapshotJson=snapshot;contentHash=pair.getContentHash();
    }
    public void record(AspectAttempt attempt){this.attempt=attempt;if(attempt.getOutcome()==AspectOutcome.REVEALED)revealedAt=attempt.getCreatedAt();}
    public void acknowledge(){acknowledged=true;}
    public Long getId(){return id;} public AspectPair getPair(){return pair;} public int getPosition(){return position;}
    public AspectDirection getDirection(){return direction;} public String getSnapshotJson(){return snapshotJson;}
    public AspectAttempt getAttempt(){return attempt;} public boolean isAcknowledged(){return acknowledged;}
    public Instant getRevealedAt(){return revealedAt;}
}
