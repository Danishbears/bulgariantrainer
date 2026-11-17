package com.example.bulgariantrainer.entity;

import com.example.bulgariantrainer.dto.AoristDtos.Entry;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Entity @Table(name="aorist_verbs", uniqueConstraints=@UniqueConstraint(columnNames={"dataset_id","external_id"}))
public class AoristVerb {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="dataset_id", nullable=false)
    private AoristDataset dataset;
    @Column(nullable=false, length=100, updatable=false) private String externalId;
    @Column(nullable=false, length=255) private String lemma;
    @Column(nullable=false, length=1000) private String russian;
    @Column(nullable=false, length=20) private String aspect, tense;
    @Column(length=2) private String reflexiveParticle;
    @Column(nullable=false, length=100) private String studyGroup;
    @Column(nullable=false, length=4000) private String note;
    @Lob @Column(nullable=false) private String sourceJson;
    @Column(nullable=false, length=100000) private String searchText;
    @Column(nullable=false, length=64) private String contentHash;
    @Column(nullable=false) private boolean archived;
    @Version private long version;
    @Column(nullable=false, updatable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    @OneToMany(mappedBy="verb", cascade=CascadeType.ALL, orphanRemoval=true)
    @OrderBy("person ASC, position ASC") private List<AoristForm> forms=new ArrayList<>();
    protected AoristVerb() {}
    public AoristVerb(AoristDataset dataset, String externalId) {
        this.dataset=dataset; this.externalId=externalId; createdAt=Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
    public void update(Entry e, String source, String search, String hash) {
        lemma=e.lemma(); russian=e.russian(); aspect=e.aspect(); tense=e.tense();
        reflexiveParticle=e.reflexiveParticle(); studyGroup=e.studyGroup(); note=e.note();
        sourceJson=source; searchText=search; contentHash=hash;
        updatedAt=Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
    public void setArchived(boolean value) {
        if(archived!=value) { archived=value; updatedAt=Instant.now().truncatedTo(ChronoUnit.MICROS); }
    }
    public void clearForms() { forms.clear(); }
    public void addForm(String person, int position, String text, String key) {
        forms.add(new AoristForm(this,person,position,text,key));
    }
    public List<String> forms(String person) { return forms.stream().filter(f->f.getPerson().equals(person)).map(AoristForm::getAnswerText).toList(); }
    public Long getId() { return id; }
    public AoristDataset getDataset() { return dataset; }
    public String getExternalId() { return externalId; }
    public String getLemma() { return lemma; }
    public String getRussian() { return russian; }
    public String getAspect() { return aspect; }
    public String getTense() { return tense; }
    public String getReflexiveParticle() { return reflexiveParticle; }
    public String getStudyGroup() { return studyGroup; }
    public String getNote() { return note; }
    public String getSourceJson() { return sourceJson; }
    public String getContentHash() { return contentHash; }
    public boolean isArchived() { return archived; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
