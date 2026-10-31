package com.example.bulgariantrainer.controller;

import com.example.bulgariantrainer.dto.AspectPairDtos.*;
import com.example.bulgariantrainer.dto.PageResponse;
import com.example.bulgariantrainer.service.AspectPairService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController @RequestMapping("/api/aspect-pairs")
public class AspectPairController {
    private final AspectPairService service;
    public AspectPairController(AspectPairService service){this.service=service;}
    @GetMapping public PageResponse<Response> list(@RequestParam(required=false) String namespace,
            @RequestParam(required=false) String q,@RequestParam(required=false) Integer studyStage,
            @RequestParam(required=false) String topic,@RequestParam(required=false) String relationType,
            @RequestParam(defaultValue="false") boolean archived,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size){return service.list(namespace,q,studyStage,topic,relationType,archived,page,size);}
    @GetMapping("/datasets") public List<Dataset> datasets(){return service.datasets();}
    @GetMapping("/{id}") public Response get(@PathVariable @Positive long id){return service.get(id);}
    @PostMapping public ResponseEntity<Response> create(@Valid @RequestBody Request request){
        Response response=service.create(request);return ResponseEntity.created(URI.create("/api/aspect-pairs/"+response.id())).body(response);
    }
    @PutMapping("/{id}") public Response update(@PathVariable @Positive long id,@Valid @RequestBody Request request){return service.update(id,request);}
    @PatchMapping("/{id}/archive") public Response archive(@PathVariable @Positive long id,@Valid @RequestBody ArchiveRequest request){return service.archive(id,request);}
}
