package com.example.bulgariantrainer.controller;

import com.example.bulgariantrainer.dto.FlashcardDtos.*;
import com.example.bulgariantrainer.service.FlashcardService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/flashcards/sessions")
public class FlashcardController {
    private final FlashcardService flashcards;
    public FlashcardController(FlashcardService flashcards) { this.flashcards = flashcards; }

    @PostMapping
    public ResponseEntity<SessionResponse> start(@RequestBody @Valid StartRequest request) {
        SessionResponse response = flashcards.start(request);
        return ResponseEntity.created(URI.create("/api/flashcards/sessions/" + response.id() + "/question")).body(response);
    }
    @GetMapping("/{id}/question")
    public SessionResponse current(@PathVariable @Positive long id) { return flashcards.current(id); }
    @PostMapping("/{id}/reveal")
    public SessionResponse reveal(@PathVariable @Positive long id, @RequestBody @Valid QuestionRequest request) {
        return flashcards.reveal(id, request);
    }
    @PostMapping("/{id}/ratings")
    public SessionResponse rate(@PathVariable @Positive long id, @RequestBody @Valid RatingRequest request) {
        return flashcards.rate(id, request);
    }
    @PostMapping("/{id}/complete")
    public SessionResponse complete(@PathVariable @Positive long id) { return flashcards.complete(id); }
    @GetMapping("/{id}/results")
    public ResultsResponse results(@PathVariable @Positive long id) { return flashcards.results(id); }
}
