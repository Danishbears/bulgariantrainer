package com.example.bulgariantrainer;

import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VocabularyIntegrationTests {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private WordRepository words;
    @Autowired private TranslationRepository translations;
    @Autowired private SentenceRepository sentences;
    @Autowired private SentenceWordRepository sentenceWords;
    @Autowired private StudyProgressRepository progress;
    @Autowired private StudySessionRepository sessions;
    @Autowired private StudyAttemptRepository attempts;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @Test
    void createsAndRetrievesMultipleCyrillicTranslations() throws Exception {
        JsonNode created = create("  Къща  ", "дом", "здание");
        long id = created.get("id").asLong();
        assertThat(created.get("bulgarianText").asText()).isEqualTo("Къща");
        assertThat(created.get("createdAt").asText()).isNotBlank();
        assertThat(created.get("updatedAt").asText()).isNotBlank();
        assertThat(created.get("translations").get(0).get("isPrimary").asBoolean()).isTrue();
        assertThat(created.get("translations").get(1).get("isPrimary").asBoolean()).isFalse();

        entityManager.flush();
        entityManager.clear();
        mvc.perform(get("/api/words/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").value(created.get("createdAt").asText()))
                .andExpect(jsonPath("$.bulgarianText").value("Къща"))
                .andExpect(jsonPath("$.translations", hasSize(2)))
                .andExpect(jsonPath("$.translations[0].russianText").value("дом"));
        assertThat(jdbc.queryForObject("select bulgarian_text from words where id = ?", String.class, id))
                .isEqualTo("Къща");
    }

    @Test
    void editReplacesTranslationsAndPreservesIdentityCreationTimeAndProgress() throws Exception {
        JsonNode created = create("къща", "дом", "строение");
        long id = created.get("id").asLong();
        long retainedTranslationId = created.get("translations").get(0).get("id").asLong();
        progress.saveAndFlush(new StudyProgress(words.findById(id).orElseThrow(), TrainingMode.TYPING));
        entityManager.clear();

        String body = json.writeValueAsString(Map.of(
                "bulgarianText", "къщичка", "partOfSpeech", "noun", "grammaticalGender", "feminine",
                "notes", "Уменьшительная форма",
                "translations", List.of(Map.of("russianText", "дом", "isPrimary", false),
                        Map.of("russianText", "домик", "isPrimary", true))));
        mvc.perform(put("/api/words/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.createdAt").value(created.get("createdAt").asText()))
                .andExpect(jsonPath("$.bulgarianText").value("къщичка"))
                .andExpect(jsonPath("$.notes").value("Уменьшительная форма"))
                .andExpect(jsonPath("$.translations", hasSize(2)))
                .andExpect(jsonPath("$.translations[0].russianText").value("домик"))
                .andExpect(jsonPath("$.translations[0].isPrimary").value(true))
                .andExpect(jsonPath("$.translations[1].id").value(retainedTranslationId));
        assertThat(translations.countByWordId(id)).isEqualTo(2);
        assertThat(progress.findByWordIdAndTrainingMode(id, TrainingMode.TYPING)).isPresent();
        assertThat(jdbc.queryForObject("select count(*) from translations where russian_text = 'строение'", Long.class))
                .isZero();
    }

    @Test
    void searchesBothLanguagesCaseInsensitivelyWithoutDuplicatePageRows() throws Exception {
        create("Добро утро", "доброе утро", "утреннее приветствие");
        create("котка", "кошка");
        for (String query : List.of("ДОБРО", "  УТРО  ", "УТР", "Приветствие")) {
            mvc.perform(get("/api/words/search").param("q", query).param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].bulgarianText").value("Добро утро"));
        }
        mvc.perform(get("/api/words/search").param("q", "несъществуващ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void paginatesWithStableOrdering() throws Exception {
        long first = create("един", "один").get("id").asLong();
        long second = create("два", "два").get("id").asLong();
        mvc.perform(get("/api/words").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(first))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/words").param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(second));
        mvc.perform(get("/api/words").param("page", "2").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!"})
    void searchTreatsSqlWildcardsAsLiteralText(String symbol) throws Exception {
        create("символ " + symbol, "знак");
        create("обикновена дума", "обычное слово");
        mvc.perform(get("/api/words/search").param("q", symbol))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].bulgarianText").value("символ " + symbol));
    }

    @Test
    void duplicateCreationUsesUnicodeCaseAndWhitespaceNormalization() throws Exception {
        create("Добър ден", "добрый день");
        mvc.perform(post("/api/words").contentType(MediaType.APPLICATION_JSON)
                        .content(body("  ДОБЪР\u00a0\u00a0ДЕН  ", "здравствуйте")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_WORD"));
        create("йога", "йога");
        mvc.perform(post("/api/words").contentType(MediaType.APPLICATION_JSON)
                        .content(body("и\u0306ога", "гимнастика")))
                .andExpect(status().isConflict());
        assertThat(words.count()).isEqualTo(2);
    }

    @Test
    void duplicateEditLeavesExistingDataUnchanged() throws Exception {
        create("дом", "дом");
        long id = create("къща", "здание").get("id").asLong();
        mvc.perform(put("/api/words/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(body("ДОМ", "строение")))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/words/{id}", id))
                .andExpect(jsonPath("$.bulgarianText").value("къща"))
                .andExpect(jsonPath("$.translations[0].russianText").value("здание"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"bulgarianText\":\" \",\"translations\":[{\"russianText\":\"дом\"}]}",
            "{\"bulgarianText\":\"къща\",\"translations\":[]}",
            "{\"bulgarianText\":\"къща\",\"translations\":[null]}",
            "{\"bulgarianText\":\"къща\",\"translations\":[{\"russianText\":\" \"}]}",
            "{\"bulgarianText\":\"къща\",\"translations\":[{\"russianText\":\"дом\",\"isPrimary\":true},{\"russianText\":\"здание\",\"isPrimary\":true}]}",
            "{\"bulgarianText\":\"къща\",\"translations\":[{\"russianText\":\"дом\"},{\"russianText\":\" ДОМ \"}]}",
            "{\"bulgarianText\":\"\\u00a0\",\"translations\":[{\"russianText\":\"дом\"}]}",
            "{invalid"
    })
    void rejectsInvalidRequests(String body) throws Exception {
        mvc.perform(post("/api/words").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").exists());
        assertThat(words.count()).isZero();
    }

    @Test
    void validatesTextLengthsAndPaginationAndSearchParameters() throws Exception {
        mvc.perform(post("/api/words").contentType(MediaType.APPLICATION_JSON).content(body("а".repeat(256), "дом")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.bulgarianText").exists());
        for (String size : List.of("0", "101", "oops")) {
            mvc.perform(get("/api/words").param("size", size)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/words").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/words").param("page", "2147483647")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/words/search")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/words/search").param("q", " ")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/words/search").param("q", "а".repeat(256))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/words/0")).andExpect(status().isBadRequest());
    }

    @Test
    void deletesUnusedWordAndOnlyItsTranslations() throws Exception {
        long id = create("къща", "дом", "здание").get("id").asLong();
        long other = create("котка", "кошка").get("id").asLong();
        mvc.perform(delete("/api/words/{id}", id)).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(words.existsById(id)).isFalse();
        assertThat(translations.countByWordId(id)).isZero();
        assertThat(translations.countByWordId(other)).isEqualTo(1);
        mvc.perform(get("/api/words/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/words/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(put("/api/words/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body("къща", "дом")))
                .andExpect(status().isNotFound());
    }

    @Test
    void protectsStudyProgressAndKeepsModesIndependent() throws Exception {
        long id = create("къща", "дом").get("id").asLong();
        Word word = words.findById(id).orElseThrow();
        for (TrainingMode mode : TrainingMode.values()) {
            progress.saveAndFlush(new StudyProgress(word, mode));
        }
        mvc.perform(delete("/api/words/{id}", id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORD_IN_USE"));
        assertThat(progress.count()).isEqualTo(TrainingMode.values().length);
        assertThat(translations.countByWordId(id)).isEqualTo(1);
        assertThat(progress.findByWordIdAndTrainingMode(id, TrainingMode.TYPING).orElseThrow().getEaseFactor())
                .isEqualTo(2.5);
    }

    @Test
    void protectsAttemptsAndTheirSessionEvenWithoutProgress() throws Exception {
        long id = create("къща", "дом").get("id").asLong();
        StudySession session = sessions.saveAndFlush(new StudySession(TrainingMode.TYPING));
        StudyAttempt attempt = attempts.saveAndFlush(new StudyAttempt(session, words.findById(id).orElseThrow(),
                null, TranslationDirection.BG_TO_RU, "дом", AttemptResult.CORRECT));
        mvc.perform(delete("/api/words/{id}", id)).andExpect(status().isConflict());
        assertThat(attempts.findById(attempt.getId()).orElseThrow().getUserAnswer()).isEqualTo("дом");
        assertThat(sessions.existsById(session.getId())).isTrue();
        assertThat(words.existsById(id)).isTrue();
    }

    @Test
    void sentenceCanLinkMultipleWordsAndProtectsSentenceBasedHistory() throws Exception {
        Word first = words.findById(create("къща", "дом").get("id").asLong()).orElseThrow();
        Word second = words.findById(create("котка", "кошка").get("id").asLong()).orElseThrow();
        Sentence sentence = sentences.saveAndFlush(new Sentence("В къщата има котка.", "В доме есть кошка.", null, 1));
        sentenceWords.saveAndFlush(new SentenceWord(sentence, first));
        sentenceWords.saveAndFlush(new SentenceWord(sentence, second));
        StudySession session = sessions.saveAndFlush(new StudySession(TrainingMode.SENTENCE));
        StudyAttempt attempt = attempts.saveAndFlush(new StudyAttempt(session, null, sentence,
                TranslationDirection.RU_TO_BG, "В къщата има котка.", AttemptResult.CORRECT));
        mvc.perform(delete("/api/words/{id}", first.getId())).andExpect(status().isConflict());
        assertThat(sentenceWords.count()).isEqualTo(2);
        assertThat(attempts.findById(attempt.getId()).orElseThrow().getSentence().getId()).isEqualTo(sentence.getId());
        assertThat(attempts.findById(attempt.getId()).orElseThrow().getWord()).isNull();
    }

    @Test
    void archivesAndRestoresWithoutLosingTranslationsOrStudyHistory() throws Exception {
        long id = create("къща", "дом", "здание").get("id").asLong();
        Word word = words.findById(id).orElseThrow();
        progress.saveAndFlush(new StudyProgress(word, TrainingMode.TYPING));
        StudySession session = sessions.saveAndFlush(new StudySession(TrainingMode.TYPING));
        StudyAttempt attempt = attempts.saveAndFlush(new StudyAttempt(session, word, null,
                TranslationDirection.BG_TO_RU, "дом", AttemptResult.CORRECT));
        Sentence sentence = sentences.saveAndFlush(new Sentence("Това е къща.", "Это дом.", null, 1));
        sentenceWords.saveAndFlush(new SentenceWord(sentence, word));
        for (int retry = 0; retry < 2; retry++) {
            mvc.perform(patch("/api/words/{id}/archive", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"archived\":true}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        }
        mvc.perform(get("/api/words")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/words/search").param("q", "дом"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/words/search").param("q", "дом").param("archived", "true"))
                .andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(get("/api/words/{id}", id)).andExpect(jsonPath("$.translations", hasSize(2)));
        mvc.perform(post("/api/words").contentType(MediaType.APPLICATION_JSON).content(body("КЪЩА", "дом")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_WORD"));
        mvc.perform(patch("/api/words/{id}/archive", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"archived\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false));
        entityManager.clear();
        assertThat(progress.findByWordIdAndTrainingMode(id, TrainingMode.TYPING)).isPresent();
        assertThat(attempts.findById(attempt.getId()).orElseThrow().getUserAnswer()).isEqualTo("дом");
        assertThat(sentenceWords.existsByWordId(id)).isTrue();
        assertThat(translations.countByWordId(id)).isEqualTo(2);
        mvc.perform(get("/api/words")).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void combinesBilingualSearchPartFilterAndPagination() throws Exception {
        for (String bg : List.of("къща", "дом")) {
            long id = create(bg, "дом", "домик").get("id").asLong();
            mvc.perform(put("/api/words/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("bulgarianText", bg, "partOfSpeech", "Существительное",
                            "translations", List.of(Map.of("russianText", "дом"), Map.of("russianText", "домик"))))))
                    .andExpect(status().isOk());
        }
        create("строя", "строить дом");
        mvc.perform(get("/api/words/search").param("q", "ДОМ").param("partOfSpeech", "существительное")
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/words").param("partOfSpeech", "СУЩЕСТВИТЕЛЬНОЕ"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/words/search").param("q", "къщ").param("partOfSpeech", "существительное"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/words/parts-of-speech"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0]").value("Существительное"));
        mvc.perform(get("/vocabulary.html")).andExpect(status().isOk());
    }

    @Test
    void validatesArchiveRequestsAndMissingWords() throws Exception {
        long id = create("котка", "кошка").get("id").asLong();
        for (String invalid : List.of("{}", "{\"archived\":null}", "{\"archived\":\"wrong\"}")) {
            mvc.perform(patch("/api/words/{id}/archive", id).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/words/999999/archive").contentType(MediaType.APPLICATION_JSON)
                .content("{\"archived\":true}")).andExpect(status().isNotFound());
    }

    private JsonNode create(String bulgarian, String... russian) throws Exception {
        String content = mvc.perform(post("/api/words").contentType(MediaType.APPLICATION_JSON).content(body(bulgarian, russian)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(content);
    }

    private String body(String bulgarian, String... russian) throws Exception {
        return json.writeValueAsString(Map.of("bulgarianText", bulgarian, "translations",
                java.util.Arrays.stream(russian).map(text -> Map.of("russianText", text)).toList()));
    }
}
