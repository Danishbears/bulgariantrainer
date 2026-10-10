package com.example.bulgariantrainer;

import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.repository.*;
import com.example.bulgariantrainer.service.TypingQuestionPlanner;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:typing-tests;DB_CLOSE_DELAY=-1", "training.typing.random-seed=42"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TypingIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired WordRepository words;
    @Autowired TranslationRepository translations;
    @Autowired StudySessionRepository sessions;
    @Autowired TypingQuestionRepository questions;
    @Autowired StudyAttemptRepository attempts;
    @Autowired StudyProgressRepository progress;

    @BeforeEach void clean() {
        questions.deleteAllInBatch(); attempts.deleteAllInBatch(); progress.deleteAllInBatch();
        sessions.deleteAllInBatch(); translations.deleteAllInBatch(); words.deleteAllInBatch();
    }
    @Test void exactDistributionUniqueWordsAndDurableOrderWithoutAnswerLeaks() throws Exception {
        for (int i = 0; i < 35; i++) word("дума " + i, "слово " + i);
        Word archived = word("архив", "архив"); archived.setArchived(true); words.saveAndFlush(archived);
        JsonNode state = start(false);
        var queue = questions.findBySessionIdOrderByPositionAsc(state.get("id").asLong());
        assertThat(queue).hasSize(30);
        assertThat(queue.stream().map(q -> q.getWord().getId())).doesNotHaveDuplicates();
        assertThat(queue.stream().filter(q -> q.getDirection() == TranslationDirection.BG_TO_RU)).hasSize(15);
        assertThat(queue.stream().filter(q -> q.getDirection() == TranslationDirection.RU_TO_BG)).hasSize(15);
        assertThat(questions.existsByWordId(archived.getId())).isFalse();
        assertThat(state.get("currentQuestion").get("feedback").isNull()).isTrue();
        assertThat(state.toString()).doesNotContain("acceptedAnswers", "russianTexts", "bulgarianText");
        assertThat(read(state)).isEqualTo(state);
        assertThat(progress.count()).isZero();
        mvc.perform(delete("/api/words/" + queue.getFirst().getWord().getId())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORD_IN_USE"));
    }
    @Test void deterministicRandomnessShufflesBothWordsAndDirections() {
        var a = new TypingQuestionPlanner(new Random(7));
        var b = new TypingQuestionPlanner(new Random(7));
        var c = new TypingQuestionPlanner(new Random(8));
        List<Integer> original = java.util.stream.IntStream.range(0, 40).boxed().toList();
        var shuffled = a.shuffled(original);
        assertThat(shuffled).containsExactlyInAnyOrderElementsOf(original).isNotEqualTo(original);
        assertThat(b.shuffled(original)).isEqualTo(shuffled);
        assertThat(c.shuffled(original)).isNotEqualTo(shuffled);
        var directions = a.directions(30);
        assertThat(b.directions(30)).isEqualTo(directions);
        assertThat(directions.subList(0, 15)).contains(TranslationDirection.BG_TO_RU, TranslationDirection.RU_TO_BG);
    }
    @Test void shorterRequiresExplicitConsentAndIsBalancedWithoutRepetition() throws Exception {
        mvc.perform(get("/api/typing/availability")).andExpect(jsonPath("$.offeredSize").value(0));
        mvc.perform(post("/api/typing/sessions").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("requestId", UUID.randomUUID(), "allowShorter", true))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMPTY_DICTIONARY"));
        for (int i = 0; i < 5; i++) word("дума " + i, "слово " + i);
        mvc.perform(get("/api/typing/availability")).andExpect(jsonPath("$.offeredSize").value(5))
                .andExpect(jsonPath("$.shorterRequired").value(true));
        mvc.perform(post("/api/typing/sessions").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("requestId", UUID.randomUUID()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SHORTER_SESSION_REQUIRED"));
        assertThat(sessions.count()).isZero();
        var state = start(true);
        var queue = questions.findBySessionIdOrderByPositionAsc(state.get("id").asLong());
        assertThat(queue).hasSize(5);
        assertThat(queue.stream().map(q -> q.getWord().getId())).doesNotHaveDuplicates();
        assertThat(queue.stream().filter(q -> q.getDirection() == TranslationDirection.BG_TO_RU).count()).isBetween(2L, 3L);
    }
    @Test void acceptsMultipleTranslationsAndUnicodeButKeepsModesIndependent() throws Exception {
        Word one = word("мой дом", "свой дом", "моё жилище");
        Word two = word("нейният дом", "её дом", "родной дом");
        progress.saveAndFlush(new StudyProgress(one, TrainingMode.FLASHCARD));
        var state = start(true);
        while (state.get("currentQuestion").isObject()) {
            var q = storedQuestion(state);
            String expected = q.getDirection() == TranslationDirection.BG_TO_RU ? q.getRussianTexts().getLast() : q.getBulgarianText();
            String answer = " \t" + java.text.Normalizer.normalize(expected.toUpperCase(Locale.ROOT), java.text.Normalizer.Form.NFD).replace(" ", "\u00a0  ") + "  ";
            state = answer(state, answer);
            assertThat(state.at("/currentQuestion/feedback/result").asText()).isEqualTo("CORRECT");
            assertThat(state.at("/currentQuestion/feedback/userAnswer").asText()).isEqualTo(answer);
            assertThat(read(state)).isEqualTo(state);
            state = next(state);
        }
        assertThat(state.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(state.get("correctAnswers").asInt()).isEqualTo(2);
        assertThat(progress.findByWordIdAndTrainingMode(one.getId(), TrainingMode.FLASHCARD).orElseThrow().getTotalAttempts()).isZero();
        assertThat(progress.findAll().stream().filter(p -> p.getWord().getId().equals(two.getId()) && p.getTrainingMode() != TrainingMode.FLASHCARD)
                .mapToInt(StudyProgress::getCorrectAttempts).sum()).isEqualTo(1);
        assertThat(attempts.findAll()).allSatisfy(a -> assertThat(a.getDirection()).isNotNull());
    }
    @Test void strictSpellingAndDirectionWithMistakeReview() throws Exception {
        word("къща", "дом"); word("мой", "мой");
        var state = start(true);
        int count = 0;
        while (state.get("currentQuestion").isObject()) {
            var q = storedQuestion(state);
            String wrong = q.getDirection() == TranslationDirection.BG_TO_RU ? q.getBulgarianText() + "x" : q.getBulgarianText().replace('ъ','а').replace('й','и');
            state = answer(state, wrong);
            assertThat(state.at("/currentQuestion/feedback/result").asText()).isEqualTo("INCORRECT");
            assertThat(state.at("/currentQuestion/feedback/acceptedAnswers").size()).isPositive();
            state = next(state); count++;
        }
        var result = json.readTree(mvc.perform(get(path(state, "results"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.get("mistakes").size()).isEqualTo(count);
        assertThat(progress.findAll()).allSatisfy(p -> { assertThat(p.getConsecutiveCorrect()).isZero(); assertThat(p.getCorrectAttempts()).isZero(); });
    }
    @Test void typingDirectionsHaveSeparateSchedulesAndDueQueueCanBeEmpty() throws Exception {
        word("куче", "собака"); word("котка", "кошка");
        mvc.perform(get("/api/typing/availability").param("scope", "DUE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligibleWords").value(0));
        mvc.perform(post("/api/typing/sessions").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("requestId", UUID.randomUUID(), "allowShorter", true, "scope", "DUE"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMPTY_REVIEW_QUEUE"));
        JsonNode state = start(true);
        while (state.get("currentQuestion").isObject()) {
            TypingQuestion question = storedQuestion(state);
            String accepted = question.getDirection() == TranslationDirection.BG_TO_RU
                    ? question.getRussianTexts().getFirst() : question.getBulgarianText();
            state = next(answer(state, accepted));
        }
        assertThat(progress.count()).isEqualTo(2);
        assertThat(progress.findAll().stream().map(StudyProgress::getTrainingMode).toList())
                .containsExactlyInAnyOrder(TrainingMode.TYPING_BG_TO_RU, TrainingMode.TYPING_RU_TO_BG);
        assertThat(progress.findAll()).allSatisfy(p -> {
            assertThat(p.getSuccessfulReviewCount()).isEqualTo(1);
            assertThat(p.getRepetitionInterval()).isEqualTo(1);
            assertThat(p.getNextReviewAt()).isAfter(p.getLastReviewedAt());
        });
        mvc.perform(get("/api/typing/availability").param("scope", "NEW"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligibleWords").value(2));
    }
    @Test void mixedTypingPrefersTheDirectionThatIsDue() throws Exception {
        Word dueWord = word("билет", "билет");
        word("куче", "собака"); word("котка", "кошка"); word("дом", "къща");
        StudyProgress due = new StudyProgress(dueWord, TrainingMode.TYPING_BG_TO_RU);
        due.recordAnswer(true, java.time.Instant.now().minus(java.time.Duration.ofDays(5)), 3);
        progress.saveAndFlush(due);
        JsonNode state = start(true);
        var selected = questions.findBySessionIdOrderByPositionAsc(state.get("id").asLong());
        assertThat(selected).filteredOn(q -> q.getWord().getId().equals(dueWord.getId()))
                .singleElement().extracting(TypingQuestion::getDirection).isEqualTo(TranslationDirection.BG_TO_RU);
        assertThat(selected.stream().map(q -> q.getWord().getId()).toList()).doesNotHaveDuplicates();
    }
    @Test void duplicateAnswersAndRefreshNeverDoubleCountAndEarlyEndRejectsNewAnswers() throws Exception {
        word("куче", "собака"); word("котка", "кошка");
        var state = start(true);
        long questionId = state.at("/currentQuestion/id").asLong();
        var answered = answer(state, "ошибка");
        assertThat(answer(state, "ошибка")).isEqualTo(answered);
        mvc.perform(post(path(state, "answers")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("questionId", questionId, "answer", "другое"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANSWER_CONFLICT"));
        assertThat(attempts.count()).isEqualTo(1);
        assertThat(progress.findAll().getFirst().getTotalAttempts()).isEqualTo(1);
        var next = next(answered);
        assertThat(read(next)).isEqualTo(next);
        mvc.perform(post(path(next, "complete"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ABANDONED"));
        mvc.perform(post(path(next, "answers")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("questionId", next.at("/currentQuestion/id").asLong(), "answer", "котка"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_CLOSED"));
        assertThat(attempts.count()).isEqualTo(1);
    }
    @Test void concurrentSubmissionIsIdempotent() throws Exception {
        word("куче", "собака");
        var state = start(true);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<JsonNode> task = () -> { start.await(); return answer(state, "ошибка"); };
            var a = pool.submit(task); var b = pool.submit(task); start.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(b.get(15, TimeUnit.SECONDS));
        }
        assertThat(attempts.count()).isEqualTo(1);
        assertThat(progress.findAll().getFirst().getTotalAttempts()).isEqualTo(1);
    }
    @Test void startRetriesAndSnapshotEditsAreSafe() throws Exception {
        Word word = word("куче", "собака");
        String body = json.writeValueAsString(Map.of("requestId", UUID.randomUUID(), "allowShorter", true));
        var first = json.readTree(mvc.perform(post("/api/typing/sessions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/typing/sessions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(first.get("id").asLong()));
        assertThat(sessions.count()).isEqualTo(1);
        mvc.perform(put("/api/words/" + word.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bulgarianText\":\"котка\",\"translations\":[{\"russianText\":\"кошка\"}]}"))
                .andExpect(status().isOk());
        assertThat(read(first)).isEqualTo(first);
        var q = storedQuestion(first);
        var answered = answer(first, q.getDirection() == TranslationDirection.BG_TO_RU ? "собака" : "куче");
        assertThat(answered.at("/currentQuestion/feedback/result").asText()).isEqualTo("CORRECT");
    }
    @Test void rejectsBlankForeignFutureQuestionsAndPrematureResults() throws Exception {
        word("куче", "собака"); word("котка", "кошка");
        var state = start(true); var other = start(true);
        mvc.perform(get(path(state, "results"))).andExpect(status().isConflict());
        mvc.perform(post(path(state, "next")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("questionId", state.at("/currentQuestion/id").asLong())))).andExpect(status().isConflict());
        for (long id : List.of(other.at("/currentQuestion/id").asLong(), questions.findBySessionIdOrderByPositionAsc(state.get("id").asLong()).getLast().getId()))
            mvc.perform(post(path(state, "answers")).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("questionId", id, "answer", "ошибка")))).andExpect(status().isConflict());
        mvc.perform(post(path(state, "answers")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("questionId", state.at("/currentQuestion/id").asLong(), "answer", "\u00a0"))))
                .andExpect(status().isBadRequest());
        assertThat(attempts.count()).isZero();
    }
    private Word word(String bg, String... ru) {
        Word word = new Word(bg, bg, null, null, null);
        for (int i = 0; i < ru.length; i++) word.addTranslation(ru[i], ru[i], i == 0);
        return words.saveAndFlush(word);
    }
    private JsonNode start(boolean shorter) throws Exception {
        return json.readTree(mvc.perform(post("/api/typing/sessions").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("requestId", UUID.randomUUID(), "allowShorter", shorter))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private String path(JsonNode state, String action) { return "/api/typing/sessions/" + state.get("id").asLong() + "/" + action; }
    private JsonNode read(JsonNode state) throws Exception {
        return json.readTree(mvc.perform(get(path(state, "question"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode answer(JsonNode state, String answer) throws Exception {
        return json.readTree(mvc.perform(post(path(state, "answers")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("questionId", state.at("/currentQuestion/id").asLong(), "answer", answer))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode next(JsonNode state) throws Exception {
        return json.readTree(mvc.perform(post(path(state, "next")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("questionId", state.at("/currentQuestion/id").asLong()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private TypingQuestion storedQuestion(JsonNode state) { return questions.findById(state.at("/currentQuestion/id").asLong()).orElseThrow(); }
}
