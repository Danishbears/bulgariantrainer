package com.example.bulgariantrainer.dto;

import java.time.Instant;
import java.util.Map;

public record ApiError(int status, String code, String message,
                       Map<String, String> fieldErrors, Instant timestamp) {
}
