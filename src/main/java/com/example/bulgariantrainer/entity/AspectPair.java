package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import com.example.bulgariantrainer.dto.AspectPairDtos.Entry;

@Entity
@Table(name="aspect_pairs", uniqueConstraints=@UniqueConstraint(columnNames={"dataset_id","external_id"}))
public class AspectPair {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="dataset_id", nullable=false)
    private AspectDataset dataset;
    @Column(nullable=false, length=100, updatable=false) private String externalId;
    @Column(nullable=false, length=255) private String imperfective, perfective;
    @Column(nullable=false, length=1000) private String russianImperfective, russianPerfective;
    @Column(nullable=false, length=255) private String topic;
    @Column(nullable=false) private int studyStage;
    @Column(nullable=false, length=30) private String relationType;
    @Column(nullable=false, length=4000) private String note;
    @Lob @Column(nullable=false) private String provenanceJson;
    @Lob private String examplesJson;
    @Column(nullable=false, length=64000) private String searchText;
    @Column(nullable=false, length=64) private String contentHash;
    @Column(nullable=false) private boolean archived;
    @Version private long version;
    @Column(nullable=false, updatable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    @OneToMany(mappedBy="pair", cascade=CascadeType.ALL, orphanRemoval=true)
    @OrderBy("side ASC, position ASC") private List<AspectPairAnswer> answers = new ArrayList<>();
    protected AspectPair() {}
    public AspectPair(AspectDataset dataset, String externalId) {
        this.dataset = dataset; this.externalId = externalId; createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
    public void update(Entry e, String provenance, String examples, String search, String hash) {
        imperfective=e.imperfective(); perfective=e.perfective();
        russianImperfective=e.russianImperfective(); russianPerfective=e.russianPerfective();
        topic=e.topic(); studyStage=e.studyStage(); relationType=e.relationType(); note=e.note();
        provenanceJson=provenance; examplesJson=examples; searchText=search; contentHash=hash;
        updatedAt=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
    public void setArchived(boolean value) { if(archived!=value) { archived=value; updatedAt=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS); } }
    public void clearAnswers() { answers.clear(); }
    public void addAnswer(String side, int position, String text, String key) {
        answers.add(new AspectPairAnswer(this,side,position,text,key));
    }
    public List<String> answers(String side) { return answers.stream().filter(a->a.getSide().equals(side)).map(AspectPairAnswer::getAnswerText).toList(); }
    public Long getId(){return id;} public AspectDataset getDataset(){return dataset;}
    public String getExternalId(){return externalId;} public String getImperfective(){return imperfective;}
    public String getPerfective(){return perfective;} public String getRussianImperfective(){return russianImperfective;}
    public String getRussianPerfective(){return russianPerfective;} public String getTopic(){return topic;}
    public int getStudyStage(){return studyStage;} public String getRelationType(){return relationType;}
    public String getNote(){return note;} public String getProvenanceJson(){return provenanceJson;}
    public String getExamplesJson(){return examplesJson;} public String getContentHash(){return contentHash;}
    public boolean isArchived(){return archived;} public long getVersion(){return version;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
