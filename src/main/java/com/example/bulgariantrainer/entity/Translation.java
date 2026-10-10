package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "translations", uniqueConstraints =
        @UniqueConstraint(name = "uk_translations_word_text", columnNames = {"word_id", "normalized_text"}))
public class Translation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    @Column(nullable = false, length = 255)
    private String russianText;

    @Column(nullable = false, length = 255)
    private String normalizedText;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    protected Translation() {
    }

    Translation(Word word, String russianText, String normalizedText, boolean primary) {
        this.word = word;
        this.normalizedText = normalizedText;
        update(russianText, primary);
    }

    public void update(String russianText, boolean primary) {
        this.russianText = russianText;
        this.primary = primary;
    }

    public Long getId() { return id; }
    public Word getWord() { return word; }
    public String getRussianText() { return russianText; }
    public String getNormalizedText() { return normalizedText; }
    public boolean isPrimary() { return primary; }
}
