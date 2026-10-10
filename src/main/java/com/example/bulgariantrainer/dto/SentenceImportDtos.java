package com.example.bulgariantrainer.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
public final class SentenceImportDtos {
 private SentenceImportDtos(){}
 public record Entry(int rowId,int lineNumber,int endLineNumber,String sourceLine,String sourceId,
   String russianText,String bulgarianText,List<String> alternativeTranslations,
   String explanation,Integer difficulty,String topic,String status,List<String> warnings,Long existingSentenceId) {}
 public record Preview(UUID importId,String filename,Instant expiresAt,List<Entry> entries) {}
 public record ConfirmEntry(@Positive int rowId,boolean selected,@Valid SentenceDtos.Request sentence) {}
 public record ConfirmRequest(@NotNull UUID importId,@NotNull List<@Valid ConfirmEntry> entries) {}
 public record RowResult(int rowId,String outcome,Long sentenceId) {}
 public record Summary(UUID importId,int created,int duplicatesSkipped,int deselected,List<RowResult> entries) {}
}
