package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.*;
import com.example.bulgariantrainer.exception.ImportException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ImportPreviewStore {
    public static final Duration LIFETIME = Duration.ofMinutes(30);
    private static final int MAX_PREVIEWS = 5;
    private final Map<UUID, PendingImport> pending = new LinkedHashMap<>();

    public synchronized void put(ImportPreview preview) {
        pending.values().removeIf(item -> !item.preview.expiresAt().isAfter(Instant.now()));
        while (pending.size() >= MAX_PREVIEWS) pending.remove(pending.keySet().iterator().next());
        pending.put(preview.importId(), new PendingImport(preview));
    }

    public synchronized PendingImport get(UUID id) {
        PendingImport item = pending.get(id);
        if (item == null || !item.preview.expiresAt().isAfter(Instant.now())) {
            pending.remove(id);
            throw new ImportException(410, "PREVIEW_EXPIRED", "Preview expired or application restarted. Upload again.");
        }
        return item;
    }

    static final class PendingImport {
        final ImportPreview preview;
        ImportConfirmRequest confirmedRequest;
        ImportSummary summary;

        PendingImport(ImportPreview preview) { this.preview = preview; }
    }
}
