package com.example.bulgariantrainer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;

public final class AoristDtos {
    private AoristDtos() {}
    public record Source(String title, String url, String section, String checkedOn) {}
    public record Entry(String id, String lemma, String russian, String aspect, String tense,
                        String reflexiveParticle, Map<String,List<String>> forms, String note,
                        Source source, String studyGroup) {}
    public record Request(@NotBlank String datasetId, Long version, @NotNull JsonNode entry, JsonNode metadata) {}
    public record ArchiveRequest(@NotNull @PositiveOrZero Long version, @NotNull Boolean archived) {}
    public record Response(long id, String datasetId, long version, boolean archived, Entry entry,
                           Instant createdAt, Instant updatedAt) {}
    public record Dataset(long id, String datasetId, int datasetRevision, long version,
                          String fileHash, JsonNode metadata) {}
    public record Filters(List<String> studyGroups, List<String> aspects) {}
    public record ValidationError(String path, String code, String message) {}
    public record Warning(String path, String code, String message) {}
    public record PreviewEntry(String externalId, String classification, Long existingId,
                               Long expectedVersion, Response before, Entry after) {}
    public record Preview(UUID importId, String filename, Instant expiresAt, boolean valid,
                          String fileHash, JsonNode metadata, List<PreviewEntry> entries,
                          List<ValidationError> errors, List<Warning> warnings) {}
    public record Selection(@NotBlank String externalId, @PositiveOrZero Long expectedVersion) {}
    public record ConfirmRequest(@NotNull UUID importId, @NotNull UUID requestId,
                                 @NotEmpty @Size(max=1000) List<@NotNull @Valid Selection> entries,
                                 Boolean allowRevisionDowngrade) {}
    public record RowResult(String externalId, String outcome, Long id) {}
    public record Summary(UUID importId, String datasetId, int datasetRevision, String fileHash,
                          int created, int updated, int unchanged, int deselected, List<RowResult> entries) {}
    public record Export(String filename, String json) {}
}
