package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.*;
import com.example.bulgariantrainer.entity.Translation;
import com.example.bulgariantrainer.entity.Word;
import com.example.bulgariantrainer.exception.ImportException;
import com.example.bulgariantrainer.repository.WordRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Service
public class VocabularyImportService {
    private final VocabularyImportParser parser;
    private final WordRepository words;
    private final ImportPreviewStore store;
    private final VocabularyImportWriter writer;
    private final TransactionTemplate readTransaction;

    public VocabularyImportService(VocabularyImportParser parser, WordRepository words,
                                   ImportPreviewStore store, VocabularyImportWriter writer,
                                   PlatformTransactionManager transactions) {
        this.parser = parser;
        this.words = words;
        this.store = store;
        this.writer = writer;
        this.readTransaction = new TransactionTemplate(transactions);
        this.readTransaction.setReadOnly(true);
    }

    public ImportPreview preview(MultipartFile file) {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        // Retain only a display name; uploaded paths are never read or written on the server.
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        byte[] bytes;
        if (file.getSize() > VocabularyImportParser.MAX_BYTES) {
            throw new ImportException(413, "FILE_TOO_LARGE", "Maximum file size is 1 MiB");
        }
        try (var stream = file.getInputStream()) {
            bytes = stream.readNBytes(VocabularyImportParser.MAX_BYTES + 1);
        } catch (IOException exception) {
            throw new ImportException(400, "INVALID_UPLOAD", "Cannot read the uploaded file");
        }
        var parsed = parser.parse(filename, bytes);
        List<ImportEntry> entries = readTransaction.execute(status -> enrich(parsed));
        ImportPreview preview = new ImportPreview(UUID.randomUUID(), filename,
                Instant.now().plus(ImportPreviewStore.LIFETIME), List.copyOf(entries));
        store.put(preview);
        return preview;
    }

    private List<ImportEntry> enrich(List<VocabularyImportParser.ParsedEntry> parsed) {
        Map<String, Integer> firstOccurrences = new HashMap<>();
        Map<String, Optional<Word>> existing = new HashMap<>();
        List<ImportEntry> entries = new ArrayList<>();
        for (var row : parsed) {
            List<String> warnings = new ArrayList<>(row.warnings());
            Word match = null;
            Integer duplicate = null;
            if (!row.bulgarianText().isEmpty()) {
                String key = VocabularyText.key(row.bulgarianText());
                duplicate = firstOccurrences.putIfAbsent(key, row.rowId());
                match = existing.computeIfAbsent(key, words::findByNormalizedText).orElse(null);
                if (duplicate != null) warnings.add("DUPLICATE_IN_FILE");
                if (match != null) warnings.add("DUPLICATE_IN_DATABASE");
            }
            String status = row.status().equals("READY") && !warnings.isEmpty() ? "WARNING" : row.status();
            entries.add(new ImportEntry(row.rowId(), row.lineNumber(), row.endLineNumber(), row.sourceLine(),
                    row.bulgarianText(), row.russianText(), row.partOfSpeech(), status, List.copyOf(warnings),
                    match == null ? null : match.getId(),
                    match == null ? List.of() : match.getTranslations().stream().map(Translation::getRussianText).toList(),
                    duplicate, status.equals("READY")));
        }
        return entries;
    }

    public ImportSummary confirm(ImportConfirmRequest request) {
        ImportPreviewStore.PendingImport pending = store.get(request.importId());
        // Serialize confirmation of a preview, and cache only after the writer's transaction commits.
        synchronized (pending) {
            if (pending.summary != null) {
                if (pending.confirmedRequest.equals(request)) return pending.summary;
                throw new ImportException(409, "IMPORT_ALREADY_CONFIRMED", "This preview has already been confirmed");
            }
            ImportSummary summary = writer.save(pending.preview, request);
            pending.confirmedRequest = new ImportConfirmRequest(request.importId(), List.copyOf(request.entries()));
            pending.summary = summary;
            return summary;
        }
    }
}
