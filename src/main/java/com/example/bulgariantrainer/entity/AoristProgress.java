package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Storage only in this milestone. Content management never updates learning counters. */
@Entity @Table(name="aorist_progress")
public class AoristProgress {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="verb_id",nullable=false) private AoristVerb verb;
    @Column(nullable=false,length=3) private String person;
    @Column(nullable=false) private int totalAttempts,correctAttempts,successfulReviewCount,consecutiveCorrect,repetitionInterval;
    private Instant lastReviewedAt,nextReviewAt;
    protected AoristProgress() {}
}
