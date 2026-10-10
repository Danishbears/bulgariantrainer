package com.example.bulgariantrainer.controller;
import com.example.bulgariantrainer.dto.SentenceDtos.*;
import com.example.bulgariantrainer.service.SentencePracticeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
@RestController @RequestMapping("/api/sentence-practice")
public class SentencePracticeController {
 private final SentencePracticeService service;
 public SentencePracticeController(SentencePracticeService service){this.service=service;}
 @PostMapping("/sessions") public ResponseEntity<SessionResponse> start(@Valid @RequestBody StartRequest request){var state=service.start(request);return ResponseEntity.created(URI.create("/api/sentence-practice/sessions/"+state.id()+"/question")).body(state);}
 @GetMapping("/sessions/{id}/question") public SessionResponse current(@PathVariable @Positive long id){return service.current(id);}
 @PostMapping("/sessions/{id}/answers") public SessionResponse answer(@PathVariable @Positive long id,@Valid @RequestBody AnswerRequest request){return service.answer(id,request);}
 @PostMapping("/sessions/{id}/self-rating") public SessionResponse selfRate(@PathVariable @Positive long id,@Valid @RequestBody SelfRatingRequest request){return service.selfRate(id,request);}
 @PostMapping("/sessions/{id}/next") public SessionResponse next(@PathVariable @Positive long id,@Valid @RequestBody QuestionRequest request){return service.next(id,request);}
 @PostMapping("/sessions/{id}/complete") public SessionResponse complete(@PathVariable @Positive long id){return service.complete(id);}
 @GetMapping("/sessions/{id}/results") public ResultsResponse results(@PathVariable @Positive long id){return service.results(id);}
}
