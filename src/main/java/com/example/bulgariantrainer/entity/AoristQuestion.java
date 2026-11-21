package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="aorist_questions")
public class AoristQuestion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="session_id",nullable=false) private AoristSession session;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="verb_id",nullable=false) private AoristVerb verb;
    @Column(nullable=false,length=3,updatable=false) private String person;
    @Column(nullable=false) private int position;
    @Lob @Column(nullable=false,updatable=false) private String snapshotJson;
    @Column(nullable=false,length=64,updatable=false) private String contentHash;
    @Column(nullable=false) private boolean acknowledged;
    private Instant revealedAt;
    @OneToOne(mappedBy="question",fetch=FetchType.LAZY) private AoristAttempt attempt;
    protected AoristQuestion() {}
    public AoristQuestion(AoristSession session,AoristVerb verb,String person,int position,String snapshot) {
        this.session=session; this.verb=verb; this.person=person; this.position=position;
        snapshotJson=snapshot; contentHash=verb.getContentHash();
    }
    public void record(AoristAttempt attempt) {
        this.attempt=attempt; if(attempt.getOutcome()==AoristOutcome.REVEALED) revealedAt=attempt.getCreatedAt();
    }
    public void acknowledge() { acknowledged=true; }
    public Long getId() { return id; }
    public AoristVerb getVerb() { return verb; }
    public String getPerson() { return person; }
    public int getPosition() { return position; }
    public String getSnapshotJson() { return snapshotJson; }
    public AoristAttempt getAttempt() { return attempt; }
    public boolean isAcknowledged() { return acknowledged; }
    public Instant getRevealedAt() { return revealedAt; }
}
