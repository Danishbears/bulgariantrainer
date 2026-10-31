package com.example.bulgariantrainer.dto;

import com.example.bulgariantrainer.entity.AspectDirection;
import java.time.Instant;
import java.util.List;

public final class AspectStatisticsDtos {
    private AspectStatisticsDtos() {}
    public record Direction(AspectDirection direction, long answered, long correct, long incorrect,
                            long revealed, long dueForReview) {}
    public record Summary(long answered, long correct, long incorrect, long revealed,
                          long dueForReview, long dueDirections, Instant asOf, List<Direction> directions) {}
}
