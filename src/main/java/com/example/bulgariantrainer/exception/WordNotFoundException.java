package com.example.bulgariantrainer.exception;

public class WordNotFoundException extends RuntimeException {
    public WordNotFoundException(long id) {
        super("Word " + id + " was not found");
    }
}
