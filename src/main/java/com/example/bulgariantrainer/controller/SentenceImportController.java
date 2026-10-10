package com.example.bulgariantrainer.controller;
import com.example.bulgariantrainer.dto.SentenceImportDtos.*;
import com.example.bulgariantrainer.service.SentenceImportService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/api/sentence-imports")
public class SentenceImportController {
 private final SentenceImportService service;
 public SentenceImportController(SentenceImportService service){this.service=service;}
 @PostMapping(value="/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public Preview preview(@RequestParam("file") MultipartFile file){return service.preview(file);}
 @PostMapping("/preview-initial") public Preview previewInitial(){return service.previewInitial();}
 @PostMapping("/confirm") public Summary confirm(@Valid @RequestBody ConfirmRequest request){return service.confirm(request);}
}
