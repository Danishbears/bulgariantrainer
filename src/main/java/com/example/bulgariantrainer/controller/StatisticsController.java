package com.example.bulgariantrainer.controller;

import com.example.bulgariantrainer.dto.StatisticsDtos.*;
import com.example.bulgariantrainer.service.StatisticsService;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {
    private final StatisticsService service;

    public StatisticsController(StatisticsService service) { this.service = service; }

    @GetMapping
    public Overview overview() { return service.overview(); }

    @GetMapping("/words/{wordId}/mistakes")
    public WordMistakes wordMistakes(@PathVariable @Positive long wordId) {
        return service.wordMistakes(wordId);
    }
}
