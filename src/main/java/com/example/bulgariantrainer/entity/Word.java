package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "words")
public class Word {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String bulgarianText;

    @Column(nullable = false, unique = true, length = 255)
    private String normalizedText;

    @Column(length = 100)
    private String partOfSpeech;

    @Column(length = 100)
    private String grammaticalGender;

    @Column(length = 4000)
    private String notes;

    @Column(nullable = false)
    private boolean archived;

    public boolean isArchived() { return archived; }

    public void setArchived(boolean archived) {
        this.archived = archived;
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @BatchSize(size = 100)
    private List<Translation> translations = new ArrayList<>();

    protected Word() {
    }

    public Word(String bulgarianText, String normalizedText, String partOfSpeech,
                String grammaticalGender, String notes) {
        updateDetails(bulgarianText, normalizedText, partOfSpeech, grammaticalGender, notes);
    }

    public void updateDetails(String bulgarianText, String normalizedText, String partOfSpeech,
                              String grammaticalGender, String notes) {
        this.bulgarianText = bulgarianText;
        this.normalizedText = normalizedText;
        this.partOfSpeech = partOfSpeech;
        this.grammaticalGender = grammaticalGender;
        this.notes = notes;
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public void addTranslation(String russianText, String normalizedText, boolean primary) {
        translations.add(new Translation(this, russianText, normalizedText, primary));
    }

    public void removeTranslationsExcept(java.util.Set<String> normalizedTexts) {
        translations.removeIf(translation -> !normalizedTexts.contains(translation.getNormalizedText()));
    }

    @PrePersist
    void initializeTimestamps() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        updatedAt = createdAt;
    }

    public Long getId() { return id; }
    public String getBulgarianText() { return bulgarianText; }
    public String getNormalizedText() { return normalizedText; }
    public String getPartOfSpeech() { return partOfSpeech; }
    public String getGrammaticalGender() { return grammaticalGender; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<Translation> getTranslations() { return java.util.Collections.unmodifiableList(translations); }
}
