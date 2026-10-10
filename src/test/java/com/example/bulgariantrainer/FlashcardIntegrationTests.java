package com.example.bulgariantrainer;

import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:flashcard-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FlashcardIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired WordRepository words;
    @Autowired TranslationRepository translations;
    @Autowired StudySessionRepository sessions;
    @Autowired FlashcardQuestionRepository questions;
    @Autowired StudyAttemptRepository attempts;
    @Autowired StudyProgressRepository progress;

    @BeforeEach
    void cleanIsolatedDatabase() {
        questions.deleteAllInBatch(); attempts.deleteAllInBatch(); progress.deleteAllInBatch();
        sessions.deleteAllInBatch(); translations.deleteAllInBatch(); words.deleteAllInBatch();
    }

    @Test
    void startsWithDefaultsExcludesArchiveAndDoesNotAwardProgressForViewing() throws Exception {
        word("куче", "собака"); word("котка", "кошка");
        Word archived = word("книга", "книга"); archived.setArchived(true); words.saveAndFlush(archived);
        JsonNode state = start(null, null);
        assertThat(state.get("totalQuestions").asInt()).isEqualTo(2);
        assertThat(state.get("direction").asText()).isEqualTo("BG_TO_RU");
        assertThat(state.get("answeredQuestions").asInt()).isZero();
        assertThat(state.get("currentQuestion").get("answer").size()).isZero();
        mvc.perform(get(path(state, "/question"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentQuestion.id").value(questionId(state)))
                .andExpect(jsonPath("$.currentQuestion.revealed").value(false));
        reveal(state);
        assertThat(attempts.count()).isZero(); assertThat(progress.count()).isZero();
        assertThat(questions.count()).isEqualTo(2);
        assertThat(questions.existsByWordId(archived.getId())).isFalse();
    }

    @Test
    void respectsAllDirectionsAndNeverRepeatsWordsWithinSession() throws Exception {
        for (int i = 0; i < 4; i++) word("дума " + i, "слово " + i);
        for (String direction : List.of("BG_TO_RU", "RU_TO_BG", "MIXED")) {
            JsonNode state = start(20, direction);
            long id = state.get("id").asLong();
            Set<Long> seen = new HashSet<>(); List<String> directions = new ArrayList<>();
            while (state.get("status").asText().equals("IN_PROGRESS")) {
                JsonNode question = state.get("currentQuestion");
                assertThat(seen.add(question.get("id").asLong())).isTrue();
                String actual = question.get("direction").asText(); directions.add(actual);
                assertThat(question.get("prompt").get(0).asText()).startsWith(actual.equals("BG_TO_RU") ? "дума" : "слово");
                if (!direction.equals("MIXED")) assertThat(actual).isEqualTo(direction);
                reveal(state); state = rate(state, false);
            }
            assertThat(seen).hasSize(4);
            if (direction.equals("MIXED")) assertThat(directions).containsOnly("BG_TO_RU", "RU_TO_BG").contains("BG_TO_RU", "RU_TO_BG");
            assertThat(state.get("status").asText()).isEqualTo("COMPLETED");
            assertThat(state.get("answeredQuestions").asInt()).isEqualTo(4);
            assertThat(questions.findBySessionIdOrderByPositionAsc(id).stream().map(q -> q.getWord().getId()).distinct()).hasSize(4);
        }
    }

    @Test
    void updatesOnlyFlashcardProgressAndResetsConsecutiveCorrectOnUnknown() throws Exception {
        Word word = word("къща", "дом");
        StudyProgress typing = new StudyProgress(word, TrainingMode.TYPING); typing.recordAnswer(true, java.time.Instant.now(), 1); progress.saveAndFlush(typing);
        JsonNode first = start(1, "BG_TO_RU"); reveal(first); rate(first, true);
        StudyProgress learned = progress.findByWordIdAndTrainingMode(word.getId(), TrainingMode.FLASHCARD).orElseThrow();
        assertThat(learned.getTotalAttempts()).isEqualTo(1); assertThat(learned.getCorrectAttempts()).isEqualTo(1);
        assertThat(learned.getConsecutiveCorrect()).isEqualTo(1); assertThat(learned.getLastReviewedAt()).isNotNull();
        assertThat(learned.getNextReviewAt()).isAfter(learned.getLastReviewedAt()); assertThat(learned.getRepetitionInterval()).isEqualTo(1);
        JsonNode second = start(1, "RU_TO_BG"); reveal(second); rate(second, false);
        learned = progress.findByWordIdAndTrainingMode(word.getId(), TrainingMode.FLASHCARD).orElseThrow();
        assertThat(learned.getTotalAttempts()).isEqualTo(2); assertThat(learned.getCorrectAttempts()).isEqualTo(1);
        assertThat(learned.getConsecutiveCorrect()).isZero();
        assertThat(progress.findByWordIdAndTrainingMode(word.getId(), TrainingMode.TYPING).orElseThrow().getTotalAttempts()).isEqualTo(1);
        assertThat(attempts.count()).isEqualTo(2);
    }

    @Test
    void refusesHiddenStaleAndForeignQuestionsAndDoesNotExposeEarlyResults() throws Exception {
        word("куче", "собака"); word("котка", "кошка");
        JsonNode first = start(2, "BG_TO_RU"); JsonNode other = start(1, "RU_TO_BG");
        postStatus(path(first, "/ratings"), Map.of("questionId", questionId(first), "known", true), 409, "ANSWER_HIDDEN");
        postStatus(path(first, "/ratings"), Map.of("questionId", questionId(other), "known", true), 409, "STALE_QUESTION");
        long future = questions.findBySessionIdOrderByPositionAsc(first.get("id").asLong()).get(1).getId();
        postStatus(path(first, "/reveal"), Map.of("questionId", future), 409, "STALE_QUESTION");
        mvc.perform(get(path(first, "/results"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_NOT_COMPLETED"));
        assertThat(attempts.count()).isZero(); assertThat(progress.count()).isZero();
    }

    @Test
    void ratingRetriesAreIdempotentEvenAfterCompletionAndChangedRatingsConflict() throws Exception {
        Word word = word("куче", "собака");
        JsonNode state = start(1, "BG_TO_RU"); reveal(state);
        JsonNode result = rate(state, true); JsonNode retry = rate(state, true);
        assertThat(retry).isEqualTo(result);
        postStatus(path(state, "/ratings"), Map.of("questionId", questionId(state), "known", false), 409, "RATING_CONFLICT");
        assertThat(attempts.count()).isEqualTo(1);
        assertThat(progress.findByWordIdAndTrainingMode(word.getId(), TrainingMode.FLASHCARD).orElseThrow().getTotalAttempts()).isEqualTo(1);
        mvc.perform(get(path(state, "/results"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.session.knownCount").value(1)).andExpect(jsonPath("$.items[0].result").value("CORRECT"));
    }

    @Test
    void concurrentRatingsCannotDoubleCountAndProgressIsSafeAcrossSessions() throws Exception {
        Word word = word("куче", "собака");
        JsonNode first = start(1, "BG_TO_RU"); reveal(first);
        raceRatings(first, first);
        assertThat(attempts.count()).isEqualTo(1);
        JsonNode second = start(1, "BG_TO_RU"); reveal(second);
        JsonNode third = start(1, "RU_TO_BG"); reveal(third);
        raceRatings(second, third);
        assertThat(attempts.count()).isEqualTo(3);
        StudyProgress record = progress.findByWordIdAndTrainingMode(word.getId(), TrainingMode.FLASHCARD).orElseThrow();
        assertThat(record.getTotalAttempts()).isEqualTo(3); assertThat(record.getCorrectAttempts()).isEqualTo(3);
        assertThat(record.getConsecutiveCorrect()).isEqualTo(3);
    }

    @Test
    void earlyCompletionCountsOnlyRatedCardsAndIsRepeatable() throws Exception {
        word("куче", "собака"); word("котка", "кошка"); word("книга", "книга");
        JsonNode state = start(3, "MIXED"); reveal(state); state = rate(state, false);
        JsonNode next = state; reveal(next);
        JsonNode done = postJson(path(state, "/complete"), Map.of(), 200);
        assertThat(done.get("status").asText()).isEqualTo("ABANDONED");
        assertThat(done.get("answeredQuestions").asInt()).isEqualTo(1);
        assertThat(done.get("remainingQuestions").asInt()).isEqualTo(2);
        assertThat(done.get("completedAt").isNull()).isFalse();
        assertThat(postJson(path(state, "/complete"), Map.of(), 200)).isEqualTo(done);
        postStatus(path(next, "/ratings"), Map.of("questionId", questionId(next), "known", true), 409, "SESSION_CLOSED");
        mvc.perform(get(path(state, "/results"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.session.unknownCount").value(1)).andExpect(jsonPath("$.items[1].result").value("SKIPPED"));
        assertThat(attempts.count()).isEqualTo(1); assertThat(progress.count()).isEqualTo(1);
    }

    @Test
    void completingWithoutRatingsDoesNotCreateProgress() throws Exception {
        word("куче", "собака");
        JsonNode state = start(1, "BG_TO_RU"); postJson(path(state, "/complete"), Map.of(), 200);
        assertThat(attempts.count()).isZero(); assertThat(progress.count()).isZero();
    }

    @Test
    void dueAndNewQueuesRespectScheduleAndRetryDoesNotScheduleTwice() throws Exception {
        Word word = word("куче", "собака");
        postStatus("/api/flashcards/sessions", Map.of("requestId", UUID.randomUUID(), "scope", "DUE"), 409, "EMPTY_REVIEW_QUEUE");
        JsonNode state = postJson("/api/flashcards/sessions", Map.of("requestId", UUID.randomUUID(), "scope", "NEW", "size", 1), 201);
        reveal(state);
        JsonNode rated = rate(state, true);
        StudyProgress saved = progress.findByWordIdAndTrainingMode(word.getId(), TrainingMode.FLASHCARD).orElseThrow();
        var dueAt = saved.getNextReviewAt();
        assertThat(saved.getSuccessfulReviewCount()).isEqualTo(1);
        assertThat(saved.getRepetitionInterval()).isEqualTo(1);
        assertThat(rate(state, true)).isEqualTo(rated);
        saved = progress.findByWordIdAndTrainingMode(word.getId(), TrainingMode.FLASHCARD).orElseThrow();
        assertThat(saved.getTotalAttempts()).isEqualTo(1);
        assertThat(saved.getNextReviewAt()).isEqualTo(dueAt);
        postStatus("/api/flashcards/sessions", Map.of("requestId", UUID.randomUUID(), "scope", "DUE"), 409, "EMPTY_REVIEW_QUEUE");
        postStatus("/api/flashcards/sessions", Map.of("requestId", UUID.randomUUID(), "scope", "NEW"), 409, "EMPTY_NEW_QUEUE");
        assertThat(postJson("/api/flashcards/sessions", Map.of("requestId", UUID.randomUUID(), "scope", "MIXED"), 201)
                .at("/currentQuestion/id").asLong()).isPositive();
    }

    @Test
    void startRetriesReuseQueueAndRejectChangedSettings() throws Exception {
        word("куче", "собака"); word("котка", "кошка");
        UUID requestId = UUID.randomUUID();
        Map<String, Object> request = Map.of("requestId", requestId, "size", 1, "direction", "MIXED");
        JsonNode state = postJson("/api/flashcards/sessions", request, 201);
        assertThat(postJson("/api/flashcards/sessions", request, 201)).isEqualTo(state);
        postStatus("/api/flashcards/sessions", Map.of("requestId", requestId, "size", 2), 409, "START_REQUEST_CHANGED");
        assertThat(sessions.count()).isEqualTo(1); assertThat(questions.count()).isEqualTo(1);
    }

    @Test
    void snapshotsSurviveVocabularyEditsAndSessionWordsCannotBeDeleted() throws Exception {
        Word word = word("къща", "дом"); word.addTranslation("здание", "здание", false); words.saveAndFlush(word);
        JsonNode state = start(1, "BG_TO_RU");
        mvc.perform(put("/api/words/{id}", word.getId()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("bulgarianText", "къщичка", "translations", List.of(Map.of("russianText", "домик")))))).andExpect(status().isOk());
        mvc.perform(delete("/api/words/{id}", word.getId())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORD_IN_USE"));
        mvc.perform(patch("/api/words/{id}/archive", word.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}")).andExpect(status().isOk());
        JsonNode revealed = reveal(state);
        assertThat(revealed.get("currentQuestion").get("prompt").get(0).asText()).isEqualTo("къща");
        assertThat(revealed.get("currentQuestion").get("answer").size()).isEqualTo(2);
        assertThat(revealed.get("currentQuestion").get("answer").get(0).asText()).isEqualTo("дом");
        rate(state, true);
    }

    @Test
    void emptyDictionaryAndInvalidRequestsDoNotCreateSessions() throws Exception {
        postStatus("/api/flashcards/sessions", Map.of("requestId", UUID.randomUUID()), 409, "EMPTY_DICTIONARY");
        for (int size : List.of(0, -1, 101)) postStatus("/api/flashcards/sessions", Map.of("requestId", UUID.randomUUID(), "size", size), 400, "VALIDATION_FAILED");
        postStatus("/api/flashcards/sessions", Map.of(), 400, "VALIDATION_FAILED");
        postStatus("/api/flashcards/sessions", Map.of("requestId", UUID.randomUUID(), "direction", "wrong"), 400, "INVALID_REQUEST");
        mvc.perform(get("/api/flashcards/sessions/999/question")).andExpect(status().isNotFound());
        assertThat(sessions.count()).isZero();
    }

    private void raceRatings(JsonNode first, JsonNode second) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2); CountDownLatch go = new CountDownLatch(1);
            List<Future<JsonNode>> results = new ArrayList<>();
            for (JsonNode state : List.of(first, second)) results.add(pool.submit(() -> {
                ready.countDown(); if (!go.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
                return rate(state, true);
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); go.countDown();
            for (Future<JsonNode> result : results) assertThat(result.get(15, TimeUnit.SECONDS).get("knownCount").asInt()).isEqualTo(1);
        }
    }
    private Word word(String bg, String ru) {
        Word word = new Word(bg, bg, null, null, null); word.addTranslation(ru, ru, true); return words.saveAndFlush(word);
    }
    private JsonNode start(Integer size, String direction) throws Exception {
        Map<String, Object> body = new HashMap<>(); body.put("requestId", UUID.randomUUID());
        if (size != null) body.put("size", size); if (direction != null) body.put("direction", direction);
        return postJson("/api/flashcards/sessions", body, 201);
    }
    private long questionId(JsonNode state) { return state.get("currentQuestion").get("id").asLong(); }
    private String path(JsonNode state, String suffix) { return "/api/flashcards/sessions/" + state.get("id").asLong() + suffix; }
    private JsonNode reveal(JsonNode state) throws Exception { return postJson(path(state, "/reveal"), Map.of("questionId", questionId(state)), 200); }
    private JsonNode rate(JsonNode state, boolean known) throws Exception { return postJson(path(state, "/ratings"), Map.of("questionId", questionId(state), "known", known), 200); }
    private JsonNode postJson(String url, Object body, int expected) throws Exception {
        return json.readTree(mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
    private void postStatus(String url, Object body, int expected, String code) throws Exception {
        assertThat(postJson(url, body, expected).get("code").asText()).isEqualTo(code);
    }
}
