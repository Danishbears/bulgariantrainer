package com.example.bulgariantrainer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record WordRequest(
        @NotBlank @Size(max = 255) String bulgarianText,
        @Size(max = 100) String partOfSpeech,
        @Size(max = 100) String grammaticalGender,
        @Size(max = 4000) String notes,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid TranslationRequest> translations) {
}
