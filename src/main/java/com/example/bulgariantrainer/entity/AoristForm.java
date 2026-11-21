package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;

@Entity @Table(name="aorist_forms")
public class AoristForm {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="verb_id",nullable=false) private AoristVerb verb;
    @Column(nullable=false,length=3) private String person;
    @Column(nullable=false) private int position;
    @Column(nullable=false,length=255) private String answerText, normalizedKey;
    protected AoristForm() {}
    public AoristForm(AoristVerb verb,String person,int position,String answer,String key) {
        this.verb=verb; this.person=person; this.position=position; answerText=answer; normalizedKey=key;
    }
    public String getPerson() { return person; }
    public String getAnswerText() { return answerText; }
}
