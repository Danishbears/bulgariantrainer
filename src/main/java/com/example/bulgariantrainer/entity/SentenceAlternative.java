package com.example.bulgariantrainer.entity;
import jakarta.persistence.*;
@Entity
@Table(name="sentence_alternatives")
public class SentenceAlternative {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sentence_id",nullable=false) private Sentence sentence;
    @Column(nullable=false,length=4000) private String bulgarianText;
    @Column(nullable=false,length=4000) private String normalizedText;
    protected SentenceAlternative() {}
    public SentenceAlternative(Sentence sentence,String text,String key){this.sentence=sentence;this.bulgarianText=text;this.normalizedText=key;}
    public void updateText(String text){this.bulgarianText=text;}
    public Long getId(){return id;} public String getBulgarianText(){return bulgarianText;}
    public String getNormalizedText(){return normalizedText;}
}

