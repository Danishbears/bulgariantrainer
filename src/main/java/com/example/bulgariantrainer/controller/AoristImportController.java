package com.example.bulgariantrainer.controller;

import com.example.bulgariantrainer.dto.AoristDtos.*;
import com.example.bulgariantrainer.service.AoristImportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/aorist-imports")
public class AoristImportController {
    private final AoristImportService service;
    public AoristImportController(AoristImportService service) { this.service=service; }
    @PostMapping(value="/preview",consumes="multipart/form-data")
    public Preview preview(@RequestParam("file") MultipartFile file) { return service.preview(file); }
    @PostMapping("/confirm") public Summary confirm(@Valid @RequestBody ConfirmRequest request) { return service.confirm(request); }
}
