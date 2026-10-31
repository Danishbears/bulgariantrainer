package com.example.bulgariantrainer.controller;

import com.example.bulgariantrainer.dto.AspectPracticeDtos.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.service.AspectPracticeService;
import com.example.bulgariantrainer.service.AspectStatisticsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController @RequestMapping("/api/aspect-pair-practice")
public class AspectPracticeController {
    private final AspectPracticeService service;
    private final AspectStatisticsService statistics;
    public AspectPracticeController(AspectPracticeService service,AspectStatisticsService statistics){this.service=service;this.statistics=statistics;}
    @GetMapping("/statistics") public com.example.bulgariantrainer.dto.AspectStatisticsDtos.Summary statistics(@RequestParam(required=false) String namespace){return statistics.overview(namespace);}
    @GetMapping("/availability") public Availability availability(@RequestParam(required=false) Integer size,
            @RequestParam(required=false) List<Integer> studyStages,@RequestParam(required=false) String topic,
            @RequestParam(required=false) AspectDirection direction,@RequestParam(required=false) StudyScope scope,
            @RequestParam(required=false) String namespace){
        return service.availability(service.settings(size,studyStages,topic,direction,scope,namespace,null));
    }
    @PostMapping("/sessions") public ResponseEntity<SessionResponse> start(@Valid @RequestBody StartRequest request){
        var state=service.start(request);return ResponseEntity.created(URI.create("/api/aspect-pair-practice/sessions/"+state.id()+"/question")).body(state);
    }
    @GetMapping("/sessions/{id}/question") public SessionResponse current(@PathVariable @Positive long id){return service.current(id);}
    @PostMapping("/sessions/{id}/answers") public SessionResponse answer(@PathVariable @Positive long id,@Valid @RequestBody AnswerRequest request){return service.answer(id,request);}
    @PostMapping("/sessions/{id}/reveal") public SessionResponse reveal(@PathVariable @Positive long id,@Valid @RequestBody QuestionRequest request){return service.reveal(id,request);}
    @PostMapping("/sessions/{id}/next") public SessionResponse next(@PathVariable @Positive long id,@Valid @RequestBody QuestionRequest request){return service.next(id,request);}
    @PostMapping("/sessions/{id}/complete") public SessionResponse complete(@PathVariable @Positive long id){return service.complete(id);}
    @GetMapping("/sessions/{id}/results") public Results results(@PathVariable @Positive long id){return service.results(id);}
    @GetMapping("/progress") public List<Progress> progress(@RequestParam(required=false) String namespace){return service.progress(namespace);}
}
