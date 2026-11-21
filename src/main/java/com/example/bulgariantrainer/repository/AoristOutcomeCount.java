package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.*;

public record AoristOutcomeCount(AoristExerciseType exerciseType, String person, AoristOutcome outcome, long count) {}
