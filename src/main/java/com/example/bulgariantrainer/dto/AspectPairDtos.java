package com.example.bulgariantrainer.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;

public final class AspectPairDtos {
    private AspectPairDtos() {}

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Provenance(String kind, String sourceId, Integer tableRow) {}
    public record Example(String bg, String ru) {}
    public record Examples(Example imperfective, Example perfective) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Entry(String id, String imperfective, String perfective,
                        String russianImperfective, String russianPerfective,
                        List<String> acceptedImperfective, List<String> acceptedPerfective,
                        String topic, Integer studyStage, String relationType, String note,
                        Provenance provenance, Examples examples) {}
    public record Request(@NotBlank String namespace, Long version, @NotNull Entry entry) {}
    public record ArchiveRequest(@NotNull Boolean archived, @NotNull @PositiveOrZero Long version) {}
    public record Response(long id, String namespace, long version, boolean archived,
                           Entry entry, Instant createdAt, Instant updatedAt) {}
    public record Dataset(long id, String namespace, long version, String contentRevision, JsonNode metadata) {}

    public record ValidationError(String path, String code, String message) {}
    public record PreviewEntry(int index, String externalId, String classification,
                               Long existingId, Long expectedVersion, Response before, Entry after) {}
    public record Preview(UUID importId, String namespace, String filename, Instant expiresAt,
                          boolean valid, String fileHash, JsonNode metadata,
                          List<PreviewEntry> entries, List<ValidationError> errors) {}
    public record Selection(@NotBlank String externalId, Long expectedVersion) {}
    public record ConfirmRequest(@NotNull UUID importId, @NotNull UUID requestId,
                                 @NotEmpty @Size(max=1000) List<@NotNull @jakarta.validation.Valid Selection> entries) {}
    public record RowResult(String externalId, String outcome, Long id) {}
    public record Summary(UUID importId, String namespace, String fileHash, int created,
                          int updated, int unchanged, int deselected, List<RowResult> entries) {}
}
