package com.example.bulgariantrainer.exception;

public class VocabularyConflictException extends RuntimeException {
    private final String code;

    public VocabularyConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
