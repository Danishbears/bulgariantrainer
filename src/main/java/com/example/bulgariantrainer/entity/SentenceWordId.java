package com.example.bulgariantrainer.entity;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class SentenceWordId implements Serializable {
    private Long sentenceId;
    private Long wordId;

    protected SentenceWordId() {
    }

    public SentenceWordId(Long sentenceId, Long wordId) {
        this.sentenceId = sentenceId;
        this.wordId = wordId;
    }

    public Long getSentenceId() { return sentenceId; }
    public Long getWordId() { return wordId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SentenceWordId that)) return false;
        return Objects.equals(sentenceId, that.sentenceId) && Objects.equals(wordId, that.wordId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sentenceId, wordId);
    }
}
