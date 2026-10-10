package com.example.bulgariantrainer.controller;

import com.example.bulgariantrainer.dto.*;
import com.example.bulgariantrainer.service.WordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/words")
public class WordController {
    private final WordService words;

    public WordController(WordService words) {
        this.words = words;
    }

    @GetMapping
    public PageResponse<WordResponse> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) @Size(max = 100) String partOfSpeech,
                                           @RequestParam(defaultValue = "false") boolean archived) {
        return words.filter(null, partOfSpeech, archived, page, size);
    }

    @GetMapping("/search")
    public PageResponse<WordResponse> search(@RequestParam @NotBlank @Size(max = 255) String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) @Size(max = 100) String partOfSpeech,
                                             @RequestParam(defaultValue = "false") boolean archived) {
        return words.filter(q, partOfSpeech, archived, page, size);
    }

    @GetMapping("/parts-of-speech")
    public java.util.List<String> partsOfSpeech() { return words.partsOfSpeech(); }

    @PatchMapping("/{id}/archive")
    public WordResponse archive(@PathVariable @Positive long id, @RequestBody @Valid ArchiveWordRequest request) {
        return words.archive(id, request.archived());
    }

    @GetMapping("/{id}")
    public WordResponse get(@PathVariable @Positive long id) {
        return words.get(id);
    }

    @PostMapping
    public ResponseEntity<WordResponse> create(@RequestBody @Valid WordRequest request) {
        WordResponse word = words.create(request);
        return ResponseEntity.created(URI.create("/api/words/" + word.id())).body(word);
    }

    @PutMapping("/{id}")
    public WordResponse update(@PathVariable @Positive long id, @RequestBody @Valid WordRequest request) {
        return words.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive long id) {
        words.delete(id);
        return ResponseEntity.noContent().build();
    }
}
