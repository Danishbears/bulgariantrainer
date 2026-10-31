package com.example.bulgariantrainer.controller;

import com.example.bulgariantrainer.dto.AspectPairDtos.*;
import com.example.bulgariantrainer.service.AspectPairImportService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/aspect-pair-imports")
public class AspectPairImportController {
    private final AspectPairImportService service;
    public AspectPairImportController(AspectPairImportService service){this.service=service;}
    @PostMapping(value="/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Preview preview(@RequestParam String namespace,@RequestParam MultipartFile file){return service.preview(namespace,file);}
    @PostMapping("/confirm") public Summary confirm(@Valid @RequestBody ConfirmRequest request){return service.confirm(request);}
}
