package com.example.bulgariantrainer.dto;

import com.example.bulgariantrainer.entity.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class AoristPracticeDtos {
    private AoristPracticeDtos() {}
    public record StartRequest(@NotNull UUID requestId,@Min(1) @Max(100) Integer size,
                               AoristExerciseType exerciseType,List<String> persons,String aspect,
                               String studyGroup,StudyScope scope,String datasetId,Boolean allowShorter) {}
    public record Settings(int size,AoristExerciseType exerciseType,List<String> persons,String aspect,
                           String studyGroup,StudyScope scope,String datasetId,boolean allowShorter) {}
    public record Availability(int eligibleQuestions,int eligibleVerbs,int requestedSize,int offeredSize,
                               boolean shorterRequired,int reducedOptionQuestions,int choiceUnavailableQuestions,
                               int typedAlternativeQuestions) {}
    public record QuestionRequest(@NotNull @Positive Long questionId) {}
    public record AnswerRequest(@NotNull @Positive Long questionId,@Size(max=4000) String answer,UUID optionId) {}
    public record Option(UUID optionId,String text) {}
    /** Private persistence contract. It must never be returned directly by a controller. */
    public record Snapshot(int version,AoristDtos.Entry content,List<Option> options) {}
    public record Prompt(String lemma,String russianMeaning,String tense,String tenseLabel,String moodLabel,
                         String person,String personLabel,String answerScope) {}
    public record Feedback(AoristOutcome outcome,String userAnswer,UUID selectedOptionId,String selectedOptionText,
                           String message,String explanation,List<String> acceptedAnswers) {}
    public record Question(long id,int position,Prompt prompt,List<Option> options,String choiceNotice,
                           boolean revealed,Feedback feedback) {}
    public record SessionResponse(long id,Settings settings,SessionStatus status,int totalQuestions,int currentPosition,
                                  int answeredQuestions,int correctAnswers,int independentRecallCorrect,
                                  int incorrectAnswers,int revealedQuestions,int remainingQuestions,
                                  Instant startedAt,Instant completedAt,Question currentQuestion) {}
    public record SessionSummary(long id,Settings settings,SessionStatus status,int totalQuestions,
                                 int currentPosition,Instant startedAt) {}
    public record ReviewItem(int position,Prompt prompt,String outcome,Feedback feedback) {}
    public record Results(SessionResponse session,Double accuracyPercent,List<ReviewItem> items,List<ReviewItem> mistakes) {}
    public record Progress(long verbId,String person,AoristExerciseType exerciseType,int totalAttempts,int correctAttempts,
                           int successfulReviewCount,int consecutiveCorrect,int intervalDays,
                           Instant lastReviewedAt,Instant nextReviewAt,LearningCategory category) {}
}
