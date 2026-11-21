package com.example.bulgariantrainer.controller;

import com.example.bulgariantrainer.dto.AoristDtos.*;
import com.example.bulgariantrainer.dto.PageResponse;
import com.example.bulgariantrainer.service.AoristVerbService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController @RequestMapping("/api/aorist-verbs")
public class AoristVerbController {
    private final AoristVerbService service;
    public AoristVerbController(AoristVerbService service) { this.service=service; }
    @GetMapping
    public PageResponse<Response> list(@RequestParam(required=false) String datasetId,@RequestParam(required=false) String q,
            @RequestParam(required=false) String aspect,@RequestParam(required=false) String studyGroup,
            @RequestParam(required=false) String reflexiveParticle,@RequestParam(defaultValue="false") boolean archived,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(datasetId,q,aspect,studyGroup,reflexiveParticle,archived,page,size);
    }
    @GetMapping("/datasets") public List<Dataset> datasets() { return service.datasets(); }
    @GetMapping("/filters") public Filters filters(@RequestParam(required=false) String datasetId) { return service.filters(datasetId); }
    @GetMapping("/export") public ResponseEntity<byte[]> export(@RequestParam String datasetId,
            @RequestParam(required=false) Boolean archived,@RequestParam(required=false) String archiveScope) {
        Export exported=service.export(datasetId,archived,archiveScope);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(exported.filename()).build().toString())
                .body(exported.json().getBytes(StandardCharsets.UTF_8));
    }
    @GetMapping("/{id}") public Response get(@PathVariable @Positive long id) { return service.get(id); }
    @PostMapping public ResponseEntity<Response> create(@Valid @RequestBody Request request) {
        Response response=service.create(request);
        return ResponseEntity.created(URI.create("/api/aorist-verbs/"+response.id())).body(response);
    }
    @PutMapping("/{id}") public Response update(@PathVariable @Positive long id,@Valid @RequestBody Request request) { return service.update(id,request); }
    @PatchMapping("/{id}/archive") public Response archive(@PathVariable @Positive long id,@Valid @RequestBody ArchiveRequest request) { return service.archive(id,request); }
}
