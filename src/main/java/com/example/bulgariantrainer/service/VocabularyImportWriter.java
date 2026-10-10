package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.*;
import com.example.bulgariantrainer.exception.ImportException;
import com.example.bulgariantrainer.repository.WordRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class VocabularyImportWriter {
    private final WordService wordService;
    private final WordRepository words;
    private final Validator validator;

    public VocabularyImportWriter(WordService wordService, WordRepository words, Validator validator) {
        this.wordService = wordService;
        this.words = words;
        this.validator = validator;
    }

    @Transactional
    public ImportSummary save(ImportPreview preview, ImportConfirmRequest request) {
        Map<Integer, ImportConfirmEntry> corrections = new HashMap<>();
        Set<Integer> knownRows = new HashSet<>();
        preview.entries().forEach(row -> knownRows.add(row.rowId()));
        // Validate the complete selection before any writes. Transaction rollback is a second safeguard.
        for (ImportConfirmEntry item : request.entries()) {
            if (!knownRows.contains(item.rowId()) || corrections.putIfAbsent(item.rowId(), item) != null) {
                throw new ImportException(400, "INVALID_IMPORT_ROW", "Unknown or repeated preview row");
            }
            if (!Boolean.TRUE.equals(item.selected())) continue;
            if (item.action() == null) throw invalid(item.rowId());
            if (item.action() == ImportAction.SKIP) continue;
            WordRequest word = wordRequest(item);
            if (!validator.validate(word).isEmpty() || VocabularyText.clean(word.bulgarianText()).isEmpty()
                    || VocabularyText.clean(item.russianText()).isEmpty()
                    || containsControl(item.bulgarianText()) || containsControl(item.russianText())
                    || (item.partOfSpeech() != null && containsControl(item.partOfSpeech()))) throw invalid(item.rowId());
            if (item.action() == ImportAction.ADD_TRANSLATION
                    && (item.existingWordId() == null || item.existingWordId() < 1)) throw invalid(item.rowId());
        }
        int created = 0, added = 0, skipped = 0;
        List<ImportRowResult> results = new ArrayList<>();
        for (ImportEntry source : preview.entries()) {
            ImportConfirmEntry item = corrections.get(source.rowId());
            String outcome;
            Long wordId = null;
            if (item == null || !Boolean.TRUE.equals(item.selected()) || item.action() == ImportAction.SKIP) {
                outcome = "DESELECTED";
                skipped++;
            } else if (item.action() == ImportAction.ADD_TRANSLATION) {
                wordId = item.existingWordId();
                if (wordService.addImportedTranslation(wordId, item.bulgarianText(), item.russianText())) {
                    outcome = "TRANSLATION_ADDED";
                    added++;
                } else {
                    outcome = "TRANSLATION_EXISTS_SKIPPED";
                    skipped++;
                }
            } else {
                var existing = words.findByNormalizedText(VocabularyText.key(item.bulgarianText()));
                if (existing.isPresent()) {
                    outcome = "DUPLICATE_SKIPPED";
                    wordId = existing.get().getId();
                    skipped++;
                } else {
                    WordResponse word = wordService.create(wordRequest(item));
                    wordId = word.id();
                    outcome = "CREATED";
                    created++;
                }
            }
            results.add(new ImportRowResult(source.rowId(), source.lineNumber(), source.sourceLine(), outcome, wordId));
        }
        return new ImportSummary(preview.importId(), created, added, skipped, List.copyOf(results));
    }

    private WordRequest wordRequest(ImportConfirmEntry item) {
        return new WordRequest(item.bulgarianText(), item.partOfSpeech(), null, null,
                List.of(new TranslationRequest(item.russianText(), true)));
    }

    private boolean containsControl(String text) {
        return text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r');
    }

    private ImportException invalid(int rowId) {
        return new ImportException(400, "INVALID_IMPORT_ROW", "Correct or deselect row " + rowId + " before confirming");
    }
}
