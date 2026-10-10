package com.example.bulgariantrainer.entity;
import jakarta.persistence.*;
@Entity
@Table(name="sentence_questions")
public class SentenceQuestion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="session_id",nullable=false) private StudySession session;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sentence_id",nullable=false) private Sentence sentence;
    @Column(nullable=false) private int position;
    @Column(nullable=false,length=4000) private String russianText;
    @Column(nullable=false,length=4000) private String bulgarianText;
    @Column(nullable=false,length=210000) private String alternativesJson;
    @Column(length=4000) private String explanation;
    @Column(nullable=false) private boolean acknowledged;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="attempt_id",unique=true) private StudyAttempt attempt;
    protected SentenceQuestion() {}
    public SentenceQuestion(StudySession session,Sentence sentence,int position,String alternativesJson){
        this.session=session;this.sentence=sentence;this.position=position;
        russianText=sentence.getRussianText();bulgarianText=sentence.getBulgarianText();
        this.alternativesJson=alternativesJson;explanation=sentence.getExplanation();
    }
    public void record(StudyAttempt attempt){this.attempt=attempt;}
    public void acknowledge(){acknowledged=true;}
    public Long getId(){return id;} public Sentence getSentence(){return sentence;} public int getPosition(){return position;}
    public String getRussianText(){return russianText;} public String getBulgarianText(){return bulgarianText;}
    public String getAlternativesJson(){return alternativesJson;} public String getExplanation(){return explanation;}
    public boolean isAcknowledged(){return acknowledged;} public StudyAttempt getAttempt(){return attempt;}
}

