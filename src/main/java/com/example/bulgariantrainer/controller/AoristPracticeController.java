package com.example.bulgariantrainer.controller;

import com.example.bulgariantrainer.dto.AoristPracticeDtos.*;
import com.example.bulgariantrainer.dto.PageResponse;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.service.AoristPracticeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController @RequestMapping("/api/aorist-practice")
public class AoristPracticeController {
    private final AoristPracticeService service;
    public AoristPracticeController(AoristPracticeService service) { this.service=service; }
    @GetMapping("/availability") public Availability availability(@RequestParam(required=false) Integer size,
            @RequestParam(required=false) AoristExerciseType exerciseType,@RequestParam(required=false) List<String> persons,
            @RequestParam(required=false) String aspect,@RequestParam(required=false) String studyGroup,
            @RequestParam(required=false) StudyScope scope,@RequestParam(required=false) String datasetId) {
        return service.availability(service.settings(size,exerciseType,persons,aspect,studyGroup,scope,datasetId,null));
    }
    @PostMapping("/sessions") public ResponseEntity<SessionResponse> start(@Valid @RequestBody StartRequest request) {
        SessionResponse state=service.start(request);
        return ResponseEntity.created(URI.create("/api/aorist-practice/sessions/"+state.id()+"/question")).body(state);
    }
    @GetMapping("/sessions") public PageResponse<SessionSummary> sessions(@RequestParam(defaultValue="IN_PROGRESS") SessionStatus status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.sessions(status,page,size); }
    @GetMapping("/sessions/{id}/question") public SessionResponse current(@PathVariable @Positive long id) { return service.current(id); }
    @PostMapping("/sessions/{id}/answers") public SessionResponse answer(@PathVariable @Positive long id,@Valid @RequestBody AnswerRequest request) { return service.answer(id,request); }
    @PostMapping("/sessions/{id}/reveal") public SessionResponse reveal(@PathVariable @Positive long id,@Valid @RequestBody QuestionRequest request) { return service.reveal(id,request); }
    @PostMapping("/sessions/{id}/next") public SessionResponse next(@PathVariable @Positive long id,@Valid @RequestBody QuestionRequest request) { return service.next(id,request); }
    @PostMapping("/sessions/{id}/complete") public SessionResponse complete(@PathVariable @Positive long id) { return service.complete(id); }
    @GetMapping("/sessions/{id}/results") public Results results(@PathVariable @Positive long id) { return service.results(id); }
    @GetMapping("/progress") public List<Progress> progress(@RequestParam(required=false) String datasetId,
            @RequestParam(required=false) AoristExerciseType exerciseType,@RequestParam(required=false) String person) {
        return service.progress(datasetId,exerciseType,person);
    }
}
