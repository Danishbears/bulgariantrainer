package com.example.bulgariantrainer.dto;

public record ImportRowResult(int rowId, int lineNumber, String sourceLine, String outcome, Long wordId) {
}
