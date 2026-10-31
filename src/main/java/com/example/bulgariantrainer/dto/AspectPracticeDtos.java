package com.example.bulgariantrainer.dto;

import com.example.bulgariantrainer.entity.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class AspectPracticeDtos {
    private AspectPracticeDtos() {}
    public record StartRequest(@NotNull UUID requestId, @Min(1) @Max(100) Integer size,
                               List<Integer> studyStages, String topic, AspectDirection direction,
                               StudyScope scope, String namespace, Boolean allowShorter) {}
    public record Settings(int size, List<Integer> studyStages, String topic, AspectDirection direction,
                           StudyScope scope, String namespace, boolean allowShorter) {}
    public record Availability(int eligiblePairs, int eligibleForward, int eligibleReverse,
                               int requestedSize, int offeredSize, boolean shorterRequired) {}
    public record QuestionRequest(@NotNull @Positive Long questionId) {}
    public record AnswerRequest(@NotNull @Positive Long questionId, @NotBlank @Size(max=4000) String answer) {}
    public record Prompt(String bulgarian, String russianMeaning, String targetRussianMeaning, String instruction) {}
    public record Feedback(AspectOutcome outcome, String userAnswer, String message,
                           List<String> acceptedAnswers, AspectPairDtos.Entry content) {}
    public record Question(long id, int position, AspectDirection direction, Prompt prompt,
                           boolean revealed, Feedback feedback) {}
    public record SessionResponse(long id, Settings settings, SessionStatus status, int totalQuestions,
                                  int currentPosition, int answeredQuestions, int correctAnswers,
                                  int incorrectAnswers, int revealedQuestions, int remainingQuestions,
                                  Instant startedAt, Instant completedAt, Question currentQuestion) {}
    public record ReviewItem(int position, AspectDirection direction, Prompt prompt, String outcome,
                             Feedback feedback) {}
    public record Results(SessionResponse session, Double accuracyPercent, List<ReviewItem> items) {}
    public record Progress(long pairId, AspectDirection direction, int totalAttempts, int correctAttempts,
                           int successfulReviewCount, int consecutiveCorrect, int intervalDays,
                           Instant lastReviewedAt, Instant nextReviewAt, LearningCategory category) {}
}
