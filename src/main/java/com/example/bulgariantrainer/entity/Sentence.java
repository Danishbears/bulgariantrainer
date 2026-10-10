package com.example.bulgariantrainer.entity;
import jakarta.persistence.*;
import java.util.*;
@Entity
@Table(name="sentences")
public class Sentence {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=4000) private String bulgarianText;
    @Column(nullable=false,length=4000) private String russianText;
    @Column(length=4000) private String normalizedRussian;
    @Column(length=4000) private String explanation;
    private Integer difficulty;
    @Column(length=255) private String topic;
    @Column(nullable=false) private boolean archived;
    @Column(nullable=false) private boolean approved;
    @OneToMany(mappedBy="sentence",cascade=CascadeType.ALL,orphanRemoval=true)
    private List<SentenceAlternative> alternatives=new ArrayList<>();
    protected Sentence() {}
    public Sentence(String bulgarianText,String russianText,String normalizedRussian,String explanation,Integer difficulty,String topic) {
        update(bulgarianText,russianText,normalizedRussian,explanation,difficulty,topic);
        this.approved=true;
    }
    public Sentence(String bulgarianText,String russianText,String explanation,Integer difficulty) {
        this(bulgarianText,russianText,null,explanation,difficulty,null);
    }
    public void update(String bg,String ru,String normalized,String explanation,Integer difficulty,String topic) {
        this.bulgarianText=bg; this.russianText=ru; this.normalizedRussian=normalized;
        this.explanation=explanation; this.difficulty=difficulty; this.topic=topic; this.approved=true;
    }
    public void setArchived(boolean archived) {this.archived=archived;}
    public void replaceAlternatives(List<String> texts,List<String> keys) {
        Set<String> desired=new HashSet<>(keys);
        alternatives.removeIf(existing->!desired.contains(existing.getNormalizedText()));
        for(int i=0;i<texts.size();i++) {
            String key=keys.get(i),text=texts.get(i);
            SentenceAlternative existing=alternatives.stream().filter(a->a.getNormalizedText().equals(key)).findFirst().orElse(null);
            if(existing==null)alternatives.add(new SentenceAlternative(this,text,key));
            else existing.updateText(text);
        }
    }
    public Long getId(){return id;} public String getBulgarianText(){return bulgarianText;}
    public String getRussianText(){return russianText;} public String getNormalizedRussian(){return normalizedRussian;}
    public String getExplanation(){return explanation;} public Integer getDifficulty(){return difficulty;}
    public String getTopic(){return topic;} public boolean isArchived(){return archived;}
    public boolean isApproved(){return approved;}
    public List<SentenceAlternative> getAlternatives(){return Collections.unmodifiableList(alternatives);}
}

