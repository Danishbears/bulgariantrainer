package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "sentence_words")
public class SentenceWord {
    @EmbeddedId
    private SentenceWordId id;

    @MapsId("sentenceId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sentence_id", nullable = false)
    private Sentence sentence;

    @MapsId("wordId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    protected SentenceWord() {
    }

    public SentenceWord(Sentence sentence, Word word) {
        this.id = new SentenceWordId(sentence.getId(), word.getId());
        this.sentence = sentence;
        this.word = word;
    }

    public SentenceWordId getId() { return id; }
    public Sentence getSentence() { return sentence; }
    public Word getWord() { return word; }
}
