package com.example.bulgariantrainer.dto;

import com.example.bulgariantrainer.entity.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FlashcardDtos {
    private FlashcardDtos() {}

    public record StartRequest(@NotNull UUID requestId, @Min(1) @Max(100) Integer size,
                               FlashcardDirection direction, StudyScope scope) {}
    public record QuestionRequest(@NotNull @Positive Long questionId) {}
    public record RatingRequest(@NotNull @Positive Long questionId, @NotNull Boolean known) {}
    public record QuestionResponse(Long id, int position, TranslationDirection direction,
                                   List<String> prompt, boolean revealed, List<String> answer) {}
    public record SessionResponse(Long id, FlashcardDirection direction, SessionStatus status,
                                  int totalQuestions, int answeredQuestions, int knownCount,
                                  int unknownCount, int remainingQuestions, Instant startedAt,
                                  Instant completedAt, QuestionResponse currentQuestion) {}
    public record ResultItem(int position, String bulgarianText, List<String> russianTexts,
                             TranslationDirection direction, AttemptResult result) {}
    public record ResultsResponse(SessionResponse session, List<ResultItem> items) {}
}
