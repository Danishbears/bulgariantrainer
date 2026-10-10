package com.example.bulgariantrainer.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TypingNormalizationTests {
    @Test void canonicalCyrillicWhitespaceAndCaseAreEquivalent() {
        assertThat(VocabularyText.key(" \t МОИ\u0306\u00a0  ДОМ \n")).isEqualTo(VocabularyText.key("мой дом"));
        assertThat(VocabularyText.key("Е\u0308ЛКА")).isEqualTo(VocabularyText.key("ёлка"));
        assertThat(VocabularyText.key("И\u0300")).isEqualTo(VocabularyText.key("ѝ"));
    }
    @Test void meaningfulSpellingAndConfusablesRemainDistinct() {
        assertThat(VocabularyText.key("къща")).isNotEqualTo(VocabularyText.key("каща"));
        assertThat(VocabularyText.key("мой")).isNotEqualTo(VocabularyText.key("мои"));
        assertThat(VocabularyText.key("ѝ")).isNotEqualTo(VocabularyText.key("и"));
        assertThat(VocabularyText.key("дом")).isNotEqualTo(VocabularyText.key("дoм"));
        assertThat(VocabularyText.key("ёлка")).isNotEqualTo(VocabularyText.key("елка"));
    }
}

