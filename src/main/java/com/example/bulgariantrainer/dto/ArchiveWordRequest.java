package com.example.bulgariantrainer.dto;

import jakarta.validation.constraints.NotNull;

public record ArchiveWordRequest(@NotNull Boolean archived) {}
