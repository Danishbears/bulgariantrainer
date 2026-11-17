package com.example.bulgariantrainer.dto;

import com.example.bulgariantrainer.entity.AoristExerciseType;
import java.time.Instant;
import java.util.List;

public final class AoristStatisticsDtos {
    private AoristStatisticsDtos() {}
    public record Person(String person, long answered, long correct, long incorrect, long revealed, long dueForReview) {}
    public record Exercise(AoristExerciseType exerciseType, long answered, long correct, long incorrect,
                           long revealed, long dueForReview, long dueVerbs, List<Person> persons) {}
    public record Summary(Instant asOf, List<Exercise> exercises) {}
}
