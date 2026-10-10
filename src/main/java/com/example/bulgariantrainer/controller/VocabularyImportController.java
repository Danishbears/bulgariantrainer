package com.example.bulgariantrainer.controller;

import com.example.bulgariantrainer.dto.*;
import com.example.bulgariantrainer.service.VocabularyImportService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
public class VocabularyImportController {
    private final VocabularyImportService imports;

    public VocabularyImportController(VocabularyImportService imports) {
        this.imports = imports;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreview preview(@RequestParam("file") MultipartFile file) {
        return imports.preview(file);
    }

    @PostMapping("/confirm")
    public ImportSummary confirm(@RequestBody @Valid ImportConfirmRequest request) {
        return imports.confirm(request);
    }
}
