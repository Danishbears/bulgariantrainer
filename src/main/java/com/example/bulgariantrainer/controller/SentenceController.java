package com.example.bulgariantrainer.controller;
import com.example.bulgariantrainer.dto.*;
import com.example.bulgariantrainer.dto.SentenceDtos.*;
import com.example.bulgariantrainer.service.SentenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
@RestController @RequestMapping("/api/sentences")
public class SentenceController {
 private final SentenceService service;
 public SentenceController(SentenceService service){this.service=service;}
 @GetMapping public PageResponse<Response> list(@RequestParam(defaultValue="false") boolean archived,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(archived,page,size);}
 @GetMapping("/{id}") public Response get(@PathVariable @Positive long id){return service.get(id);}
 @PostMapping public ResponseEntity<Response> create(@Valid @RequestBody Request request){Response response=service.create(request);return ResponseEntity.created(URI.create("/api/sentences/"+response.id())).body(response);}
 @PutMapping("/{id}") public Response update(@PathVariable @Positive long id,@Valid @RequestBody Request request){return service.update(id,request);}
 @PatchMapping("/{id}/archive") public Response archive(@PathVariable @Positive long id,@RequestBody ArchiveRequest request){return service.archive(id,request.archived());}
}
