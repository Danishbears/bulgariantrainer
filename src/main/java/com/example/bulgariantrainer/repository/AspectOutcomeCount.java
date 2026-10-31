package com.example.bulgariantrainer.repository;

import com.example.bulgariantrainer.entity.AspectDirection;
import com.example.bulgariantrainer.entity.AspectOutcome;

public record AspectOutcomeCount(AspectDirection direction, AspectOutcome outcome, long count) {}
