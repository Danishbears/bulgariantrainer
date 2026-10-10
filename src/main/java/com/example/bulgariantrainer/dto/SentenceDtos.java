package com.example.bulgariantrainer.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
public final class SentenceDtos {
 private SentenceDtos(){}
 public record Request(@NotBlank @Size(max=4000) String russianText,
    @NotBlank @Size(max=4000) String bulgarianText,
    @NotNull @Size(max=50) List<@NotBlank @Size(max=4000) String> alternativeTranslations,
    @Size(max=4000) String explanation,@Min(1) @Max(5) Integer difficulty,
    @Size(max=255) String topic,@NotNull List<@NotNull @Positive Long> wordIds) {}
 public record Response(long id,String russianText,String bulgarianText,List<String> alternativeTranslations,
    String explanation,Integer difficulty,String topic,List<Long> wordIds,boolean archived,boolean approved) {}
 public record ArchiveRequest(boolean archived) {}
 public record StartRequest(@NotNull UUID requestId,@Min(1) @Max(100) Integer size,
    com.example.bulgariantrainer.entity.StudyScope scope) {}
 public record AnswerRequest(@NotNull @Positive Long questionId,@NotBlank @Size(max=4000) String answer) {}
 public record QuestionRequest(@NotNull @Positive Long questionId) {}
 public record SelfRatingRequest(@NotNull @Positive Long questionId,@NotNull com.example.bulgariantrainer.entity.AttemptResult result) {}
 public record Feedback(com.example.bulgariantrainer.entity.AttemptResult result,
    com.example.bulgariantrainer.entity.EvaluationSource evaluationSource,String userAnswer,
    List<String> acceptedAnswers,String explanation,boolean needsSelfAssessment) {}
 public record QuestionResponse(long id,int position,String russianText,Feedback feedback) {}
 public record SessionResponse(long id,com.example.bulgariantrainer.entity.SessionStatus status,int totalQuestions,
    int answeredQuestions,int correctAnswers,int partialAnswers,QuestionResponse currentQuestion) {}
 public record ReviewItem(int position,String russianText,String userAnswer,
    List<String> acceptedAnswers,String explanation,
    com.example.bulgariantrainer.entity.AttemptResult result,
    com.example.bulgariantrainer.entity.EvaluationSource evaluationSource) {}
 public record ResultsResponse(SessionResponse session,List<ReviewItem> items) {}
}
