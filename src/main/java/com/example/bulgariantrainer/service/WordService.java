package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.exception.*;
import com.example.bulgariantrainer.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class WordService {
    private final WordRepository words;
    private final StudyProgressRepository progress;
    private final StudyAttemptRepository attempts;
    private final SentenceWordRepository sentenceWords;
    private final FlashcardQuestionRepository flashcards;
    private final TypingQuestionRepository typing;
    private final SentenceAttemptWordRepository sentenceUsage;

    public WordService(WordRepository words, StudyProgressRepository progress,
                       StudyAttemptRepository attempts, SentenceWordRepository sentenceWords,
                       FlashcardQuestionRepository flashcards, TypingQuestionRepository typing, SentenceAttemptWordRepository sentenceUsage) {
        this.sentenceUsage = sentenceUsage;
        this.typing = typing;
        this.words = words;
        this.progress = progress;
        this.attempts = attempts;
        this.sentenceWords = sentenceWords;
        this.flashcards = flashcards;
    }

    public PageResponse<WordResponse> list(int page, int size) {
        return filter(null, null, false, page, size);
    }

    public PageResponse<WordResponse> search(String query, int page, int size) {
        if (VocabularyText.clean(query).isEmpty()) {
            throw new InvalidVocabularyException("Search query must not be blank");
        }
        return filter(query, null, false, page, size);
    }

    public PageResponse<WordResponse> filter(String query, String partOfSpeech, boolean archived, int page, int size) {
        String part = VocabularyText.optional(partOfSpeech);
        String text = VocabularyText.optional(query);
        if (query != null && text == null) throw new InvalidVocabularyException("Search query must not be blank");
        return pageResponse(words.filter(text == null ? null : VocabularyText.searchPattern(text),
                part == null ? null : VocabularyText.key(part), archived, pageable(page, size)));
    }

    public List<String> partsOfSpeech() { return words.findPartsOfSpeech(); }

    @Transactional
    public WordResponse archive(long id, boolean archived) {
        Word word = words.findForUpdate(id).orElseThrow(() -> new WordNotFoundException(id));
        word.setArchived(archived);
        words.flush();
        return response(word);
    }

    public WordResponse get(long id) {
        return response(words.findById(id).orElseThrow(() -> new WordNotFoundException(id)));
    }

    @Transactional
    public WordResponse create(WordRequest request) {
        String text = validText(request.bulgarianText());
        String key = VocabularyText.key(text);
        if (words.existsByNormalizedText(key)) throw duplicate();
        List<TranslationValue> translations = translations(request);
        Word word = new Word(text, key, VocabularyText.optional(request.partOfSpeech()),
                VocabularyText.optional(request.grammaticalGender()), VocabularyText.optional(request.notes()));
        translations.forEach(value -> word.addTranslation(value.text(), value.key(), value.primary()));
        return response(words.saveAndFlush(word));
    }

    @Transactional
    public WordResponse update(long id, WordRequest request) {
        Word word = words.findForUpdate(id).orElseThrow(() -> new WordNotFoundException(id));
        String text = validText(request.bulgarianText());
        String key = VocabularyText.key(text);
        if (words.existsByNormalizedTextAndIdNot(key, id)) throw duplicate();
        List<TranslationValue> values = translations(request);
        word.updateDetails(text, key, VocabularyText.optional(request.partOfSpeech()),
                VocabularyText.optional(request.grammaticalGender()), VocabularyText.optional(request.notes()));
        Set<String> keys = new HashSet<>();
        values.forEach(value -> keys.add(value.key()));
        word.removeTranslationsExcept(keys);
        for (TranslationValue value : values) {
            Optional<Translation> existing = word.getTranslations().stream()
                    .filter(translation -> translation.getNormalizedText().equals(value.key())).findFirst();
            if (existing.isPresent()) existing.get().update(value.text(), value.primary());
            else word.addTranslation(value.text(), value.key(), value.primary());
        }
        words.flush();
        return response(word);
    }

    @Transactional
    public void delete(long id) {
        Word word = words.findForUpdate(id).orElseThrow(() -> new WordNotFoundException(id));
        if (progress.existsByWordId(id) || attempts.existsByWordId(id) || sentenceWords.existsByWordId(id)
                || flashcards.existsByWordId(id) || typing.existsByWordId(id) || sentenceUsage.countByWordId(id) > 0) {
            throw new VocabularyConflictException("WORD_IN_USE",
                    "This word is linked to sentences or study records and cannot be deleted");
        }
        words.delete(word);
        words.flush();
    }

    /** Append only; imports must never replace metadata, primary translations, or study records. */
    @Transactional
    public boolean addImportedTranslation(long id, String expectedBulgarianText, String russianText) {
        Word word = words.findForUpdate(id).orElseThrow(() -> new WordNotFoundException(id));
        if (!word.getNormalizedText().equals(VocabularyText.key(expectedBulgarianText))) {
            throw new VocabularyConflictException("IMPORT_TARGET_CHANGED",
                    "The target word does not match the confirmed Bulgarian text. Preview again.");
        }
        String text = validText(russianText);
        String key = VocabularyText.key(text);
        if (word.getTranslations().stream().anyMatch(item -> item.getNormalizedText().equals(key))) return false;
        if (word.getTranslations().size() >= 50) {
            throw new InvalidVocabularyException("A word may have at most 50 translations");
        }
        word.addTranslation(text, key, word.getTranslations().isEmpty());
        word.updateDetails(word.getBulgarianText(), word.getNormalizedText(), word.getPartOfSpeech(),
                word.getGrammaticalGender(), word.getNotes());
        words.flush();
        return true;
    }

    private List<TranslationValue> translations(WordRequest request) {
        long primaryCount = request.translations().stream().filter(item -> Boolean.TRUE.equals(item.isPrimary())).count();
        if (primaryCount > 1) {
            throw new InvalidVocabularyException("Only one translation may be primary");
        }
        Set<String> keys = new HashSet<>();
        List<TranslationValue> values = new ArrayList<>();
        for (int i = 0; i < request.translations().size(); i++) {
            TranslationRequest item = request.translations().get(i);
            String text = validText(item.russianText());
            String key = VocabularyText.key(text);
            if (!keys.add(key)) throw new InvalidVocabularyException("Duplicate Russian translations are not allowed");
            values.add(new TranslationValue(text, key, Boolean.TRUE.equals(item.isPrimary()) || (primaryCount == 0 && i == 0)));
        }
        return values;
    }

    private String validText(String text) {
        String cleaned = VocabularyText.clean(text);
        if (cleaned.isEmpty()) throw new InvalidVocabularyException("Vocabulary text must not be blank");
        return cleaned;
    }

    private VocabularyConflictException duplicate() {
        return new VocabularyConflictException("DUPLICATE_WORD", "A word with this Bulgarian text already exists");
    }

    private Pageable pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new InvalidVocabularyException("Page must be non-negative and size must be between 1 and 100");
        }
        return PageRequest.of(page, size, Sort.by("id").ascending());
    }

    private PageResponse<WordResponse> pageResponse(Page<Word> page) {
        return new PageResponse<>(page.getContent().stream().map(this::response).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private WordResponse response(Word word) {
        List<TranslationResponse> translations = word.getTranslations().stream()
                .sorted(Comparator.comparing(Translation::isPrimary).reversed().thenComparing(Translation::getId))
                .map(item -> new TranslationResponse(item.getId(), item.getRussianText(), item.isPrimary())).toList();
        return new WordResponse(word.getId(), word.getBulgarianText(), word.getPartOfSpeech(),
                word.getGrammaticalGender(), word.getNotes(), word.getCreatedAt(), word.getUpdatedAt(), translations, word.isArchived());
    }

    private record TranslationValue(String text, String key, boolean primary) {
    }
}

