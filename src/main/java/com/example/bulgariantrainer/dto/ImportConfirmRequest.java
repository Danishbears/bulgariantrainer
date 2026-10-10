package com.example.bulgariantrainer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record ImportConfirmRequest(@NotNull UUID importId,
                                   @NotNull @Size(max = 2000) List<@NotNull @Valid ImportConfirmEntry> entries) {
}
