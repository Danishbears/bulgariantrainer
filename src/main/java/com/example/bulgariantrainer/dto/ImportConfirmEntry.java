package com.example.bulgariantrainer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ImportConfirmEntry(@NotNull @Positive Integer rowId, @NotNull Boolean selected,
                                 ImportAction action, String bulgarianText, String russianText,
                                 String partOfSpeech, Long existingWordId) {
}
