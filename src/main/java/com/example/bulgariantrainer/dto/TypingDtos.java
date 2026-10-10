package com.example.bulgariantrainer.dto;
import com.example.bulgariantrainer.entity.*;
import jakarta.validation.constraints.*;
import java.util.*;
public final class TypingDtos {
    private TypingDtos() {}
    public record Availability(int eligibleWords, int offeredSize, boolean shorterRequired) {}
    public record StartRequest(@NotNull UUID requestId, Boolean allowShorter, StudyScope scope) {}
    public record QuestionRequest(@NotNull @Positive Long questionId) {}
    public record AnswerRequest(@NotNull @Positive Long questionId, @NotBlank @Size(max=4000) String answer) {}
    public record Feedback(AttemptResult result, String userAnswer, List<String> acceptedAnswers) {}
    public record QuestionResponse(long id, int position, TranslationDirection direction, List<String> prompt, Feedback feedback) {}
    public record SessionResponse(long id, SessionStatus status, int totalQuestions, int answeredQuestions,
                                  int correctAnswers, QuestionResponse currentQuestion) {}
    public record Mistake(int position, TranslationDirection direction, List<String> prompt, String userAnswer, List<String> acceptedAnswers) {}
    public record ResultsResponse(SessionResponse session, List<Mistake> mistakes) {}
}

