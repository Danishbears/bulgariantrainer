package com.example.bulgariantrainer.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImportPreview(UUID importId, String filename, Instant expiresAt, List<ImportEntry> entries) {
}
