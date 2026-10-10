package com.example.bulgariantrainer;

import com.example.bulgariantrainer.dto.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.repository.*;
import com.example.bulgariantrainer.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:import-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private WordService wordService;
    @Autowired private WordRepository words;
    @Autowired private StudyProgressRepository progress;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ImportPreviewStore store;

    @BeforeEach
    @AfterEach
    void cleanIsolatedDatabase() {
        for (String table : List.of("study_attempts", "study_sessions", "study_progress", "sentence_words", "sentences", "translations", "words")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @Test
    void previewRetainsEveryLineAndDetectsDuplicatesWithoutWrites() throws Exception {
        create("куче", "собака");
        ImportPreview preview = preview("personal.txt", "куче - пёс\nкотка - кошка\n КОТКА = кот\nлитър - \n\nзаголовок\nhi - здрасти");
        assertThat(preview.entries()).hasSize(7);
        assertThat(preview.entries().get(0).existingWordId()).isNotNull();
        assertThat(preview.entries().get(0).existingTranslations()).containsExactly("собака");
        assertThat(preview.entries().get(2).duplicateOfRowId()).isEqualTo(2);
        assertThat(preview.entries().get(3).status()).isEqualTo("INVALID");
        assertThat(preview.entries().get(4).status()).isEqualTo("EMPTY");
        assertThat(preview.entries().get(5).sourceLine()).isEqualTo("заголовок");
        assertThat(preview.entries().get(6).suggestedSelected()).isFalse();
        assertThat(words.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM translations", Long.class)).isEqualTo(1);
    }

    @Test
    void confirmsCorrectionsAndSelectionAndCachesExactRetries() throws Exception {
        ImportPreview preview = preview("words.txt", "литър - \nкотка - кошка\nдума - слово");
        var request = new ImportConfirmRequest(preview.importId(), List.of(
                row(1, true, ImportAction.CREATE, "литър", "литр", null),
                row(2, false, ImportAction.CREATE, null, null, null)));
        ImportSummary result = confirm(request);
        assertThat(result.createdWords()).isEqualTo(1);
        assertThat(result.skippedEntries()).isEqualTo(2);
        assertThat(result.entries().getFirst().sourceLine()).isEqualTo("литър - ");
        assertThat(words.count()).isEqualTo(1);
        assertThat(wordService.get(result.entries().getFirst().wordId()).translations().getFirst().russianText()).isEqualTo("литр");
        assertThat(confirm(request)).isEqualTo(result);
        assertThat(words.count()).isEqualTo(1);
        mvc.perform(post("/api/imports/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ImportConfirmRequest(preview.importId(), List.of()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_ALREADY_CONFIRMED"));
    }

    @Test
    void createsCsvWordsWithMetadataAndPreservesCommas() throws Exception {
        ImportPreview preview = preview("words.csv", "\uFEFFbulgarian,russian,partOfSpeech\nотивам,\"идти, ехать\",verb");
        var parsed = preview.entries().get(1);
        assertThat(parsed.russianText()).isEqualTo("идти, ехать");
        ImportSummary result = confirm(new ImportConfirmRequest(preview.importId(), List.of(
                new ImportConfirmEntry(parsed.rowId(), true, ImportAction.CREATE, parsed.bulgarianText(),
                        parsed.russianText(), parsed.partOfSpeech(), null))));
        var word = wordService.get(result.entries().get(1).wordId());
        assertThat(word.partOfSpeech()).isEqualTo("verb");
        assertThat(word.translations()).hasSize(1);
        assertThat(word.translations().getFirst().russianText()).isEqualTo("идти, ехать");
    }

    @Test
    void skipsDuplicatesIncludingWordsCreatedAfterPreviewAndEarlierInSameImport() throws Exception {
        ImportPreview preview = preview("words.txt", "куче - пёс\nкотка - кошка\n КОТКА - кот");
        create("куче", "собака");
        ImportSummary result = confirm(new ImportConfirmRequest(preview.importId(), List.of(
                row(1, true, ImportAction.CREATE, "куче", "пёс", null),
                row(2, true, ImportAction.CREATE, "котка", "кошка", null),
                row(3, true, ImportAction.CREATE, "КОТКА", "кот", null))));
        assertThat(result.createdWords()).isEqualTo(1);
        assertThat(result.skippedEntries()).isEqualTo(2);
        assertThat(words.count()).isEqualTo(2);
        assertThat(result.entries().getFirst().outcome()).isEqualTo("DUPLICATE_SKIPPED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM translations", Long.class)).isEqualTo(2);
    }

    @Test
    void explicitAppendPreservesMetadataPrimaryTranslationAndHistory() throws Exception {
        WordResponse original = wordService.create(new WordRequest("куче", "noun", "neuter", "Моя заметка",
                List.of(new TranslationRequest("собака", true))));
        progress.saveAndFlush(new StudyProgress(words.findById(original.id()).orElseThrow(), TrainingMode.TYPING));
        ImportPreview preview = preview("words.txt", "куче - пёс\nкуче - собака");
        ImportSummary result = confirm(new ImportConfirmRequest(preview.importId(), List.of(
                new ImportConfirmEntry(1, true, ImportAction.ADD_TRANSLATION, " КУЧЕ ", "пёс", "changed", original.id()),
                row(2, true, ImportAction.ADD_TRANSLATION, "куче", "собака", original.id()))));
        assertThat(result.addedTranslations()).isEqualTo(1);
        assertThat(result.skippedEntries()).isEqualTo(1);
        WordResponse updated = wordService.get(original.id());
        assertThat(updated.notes()).isEqualTo(original.notes());
        assertThat(updated.partOfSpeech()).isEqualTo("noun");
        assertThat(updated.grammaticalGender()).isEqualTo("neuter");
        assertThat(updated.translations()).hasSize(2);
        assertThat(updated.translations().getFirst().russianText()).isEqualTo("собака");
        assertThat(updated.translations().getFirst().isPrimary()).isTrue();
        assertThat(progress.findByWordIdAndTrainingMode(original.id(), TrainingMode.TYPING)).isPresent();
    }

    @Test
    void invalidSelectedRowPreventsAllWritesButCanBeCorrectedAndRetried() throws Exception {
        ImportPreview preview = preview("words.txt", "куче - собака\nлитър -");
        var invalid = new ImportConfirmRequest(preview.importId(), List.of(
                row(1, true, ImportAction.CREATE, "куче", "собака", null),
                row(2, true, ImportAction.CREATE, "литър", "", null)));
        mvc.perform(post("/api/imports/confirm").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMPORT_ROW"));
        assertThat(words.count()).isZero();
        assertThat(confirm(new ImportConfirmRequest(preview.importId(), List.of(
                row(1, true, ImportAction.CREATE, "куче", "собака", null),
                row(2, true, ImportAction.CREATE, "литър", "литр", null)))).createdWords()).isEqualTo(2);
    }

    @Test
    void wrongAppendTargetRollsBackPreviouslyProcessedRows() throws Exception {
        WordResponse target = create("котка", "кошка");
        ImportPreview preview = preview("words.txt", "куче - собака\nлитър - литр");
        var request = new ImportConfirmRequest(preview.importId(), List.of(
                row(1, true, ImportAction.CREATE, "куче", "собака", null),
                row(2, true, ImportAction.ADD_TRANSLATION, "литър", "литр", target.id())));
        mvc.perform(post("/api/imports/confirm").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_TARGET_CHANGED"));
        assertThat(words.count()).isEqualTo(1);
        assertThat(wordService.get(target.id()).translations()).hasSize(1);
        assertThat(words.existsByNormalizedText("куче")).isFalse();
    }

    @Test
    void rejectsForgedRepeatedAndExpiredPreviewRows() throws Exception {
        ImportPreview preview = preview("words.txt", "куче - собака");
        for (List<ImportConfirmEntry> rows : List.of(
                List.of(row(2, true, ImportAction.CREATE, "куче", "собака", null)),
                List.of(row(1, true, ImportAction.CREATE, "куче", "собака", null),
                        row(1, true, ImportAction.CREATE, "куче", "собака", null)))) {
            mvc.perform(post("/api/imports/confirm").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new ImportConfirmRequest(preview.importId(), rows))))
                    .andExpect(status().isBadRequest());
        }
        UUID expired = UUID.randomUUID();
        store.put(new ImportPreview(expired, "words.txt", Instant.now().minusSeconds(1), preview.entries()));
        mvc.perform(post("/api/imports/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ImportConfirmRequest(expired, List.of()))))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("PREVIEW_EXPIRED"));
        assertThat(words.count()).isZero();
    }

    @Test
    void handlesInvalidUploadsAndServesImportPage() throws Exception {
        mvc.perform(multipart("/api/imports/preview").file(new MockMultipartFile("file", "words.txt",
                        "text/plain", new byte[] {(byte) 0xFF})))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_UTF8"));
        mvc.perform(multipart("/api/imports/preview").file(new MockMultipartFile("file", "words.txt",
                        "text/plain", new byte[VocabularyImportParser.MAX_BYTES + 1])))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart("/api/imports/preview").file(new MockMultipartFile("file", "words.xlsx",
                        "application/octet-stream", new byte[0])))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/imports/preview")).andExpect(status().isBadRequest());
        assertThat(preview("empty.txt", "").entries().getFirst().status()).isEqualTo("EMPTY");
        mvc.perform(get("/import.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Подтвердить импорт")));
        mvc.perform(get("/js/import.js")).andExpect(status().isOk());
        mvc.perform(get("/css/import.css")).andExpect(status().isOk());
    }

    private WordResponse create(String bg, String ru) {
        return wordService.create(new WordRequest(bg, null, null, null, List.of(new TranslationRequest(ru, true))));
    }

    private ImportConfirmEntry row(int id, boolean selected, ImportAction action, String bg, String ru, Long target) {
        return new ImportConfirmEntry(id, selected, action, bg, ru, null, target);
    }

    private ImportPreview preview(String filename, String content) throws Exception {
        String result = mvc.perform(multipart("/api/imports/preview").file(new MockMultipartFile("file", filename,
                        "application/octet-stream", content.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readValue(result, ImportPreview.class);
    }

    private ImportSummary confirm(ImportConfirmRequest request) throws Exception {
        String result = mvc.perform(post("/api/imports/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readValue(result, ImportSummary.class);
    }
}
