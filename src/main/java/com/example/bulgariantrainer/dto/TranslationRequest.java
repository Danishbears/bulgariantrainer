package com.example.bulgariantrainer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TranslationRequest(
        @NotBlank @Size(max = 255) String russianText,
        Boolean isPrimary) {
}
