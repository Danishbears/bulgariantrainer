package com.example.bulgariantrainer.dto;

import java.util.List;

public record ImportEntry(int rowId, int lineNumber, int endLineNumber, String sourceLine,
                          String bulgarianText, String russianText, String partOfSpeech,
                          String status, List<String> warnings, Long existingWordId,
                          List<String> existingTranslations, Integer duplicateOfRowId,
                          boolean suggestedSelected) {
}
