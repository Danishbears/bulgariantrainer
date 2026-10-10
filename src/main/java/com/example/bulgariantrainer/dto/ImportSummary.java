package com.example.bulgariantrainer.dto;

import java.util.List;
import java.util.UUID;

public record ImportSummary(UUID importId, int createdWords, int addedTranslations,
                            int skippedEntries, List<ImportRowResult> entries) {
}
