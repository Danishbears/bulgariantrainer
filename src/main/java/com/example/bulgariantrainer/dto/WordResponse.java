package com.example.bulgariantrainer.dto;

import java.time.Instant;
import java.util.List;

public record WordResponse(Long id, String bulgarianText, String partOfSpeech,
                           String grammaticalGender, String notes, Instant createdAt,
                           Instant updatedAt, List<TranslationResponse> translations, boolean archived) {
}
