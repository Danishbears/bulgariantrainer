package com.example.bulgariantrainer.entity;
import jakarta.persistence.*;

@Entity @Table(name="aspect_pair_answers")
public class AspectPairAnswer {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="pair_id", nullable=false) private AspectPair pair;
    @Column(nullable=false,length=20) private String side;
    @Column(nullable=false) private int position;
    @Column(nullable=false,length=255) private String answerText;
    @Column(nullable=false,length=255) private String normalizedKey;
    protected AspectPairAnswer() {}
    public AspectPairAnswer(AspectPair pair,String side,int position,String text,String key) {
        this.pair=pair; this.side=side; this.position=position; answerText=text; normalizedKey=key;
    }
    public String getSide(){return side;} public String getAnswerText(){return answerText;}
}
