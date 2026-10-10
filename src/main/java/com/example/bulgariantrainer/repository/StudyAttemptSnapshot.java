package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.*;
import java.time.Instant;

/** Scalar history row for statistics; no entity graph or answer key is loaded. */
public record StudyAttemptSnapshot(Long id, Long sessionId, TrainingMode trainingMode,
        TranslationDirection direction, AttemptResult result, EvaluationSource evaluationSource,
        Instant createdAt, Long wordId) {}
