package com.example.bulgariantrainer;

import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:statistics-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatisticsIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired WordRepository words;
    @Autowired TranslationRepository translations;
    @Autowired SentenceRepository sentences;
    @Autowired StudyProgressRepository wordProgress;
    @Autowired SentenceProgressRepository sentenceProgress;
    @Autowired StudySessionRepository sessions;
    @Autowired StudyAttemptRepository attempts;
    @Autowired FlashcardQuestionRepository flashcardQuestions;

    @BeforeEach void clean() {
        flashcardQuestions.deleteAllInBatch();
        attempts.deleteAllInBatch();
        sentenceProgress.deleteAllInBatch();
        wordProgress.deleteAllInBatch();
        sessions.deleteAllInBatch();
        translations.deleteAllInBatch();
        words.deleteAllInBatch();
        sentences.deleteAllInBatch();
    }

    @Test void emptyDatabaseHasZeroCountsAndUndefinedAccuracy() throws Exception {
        JsonNode stats = overview();
        assertThat(stats.at("/vocabulary/total").asLong()).isZero();
        assertThat(stats.at("/reviewAttempts").asLong()).isZero();
        assertThat(stats.at("/completedSessions").asLong()).isZero();
        assertThat(stats.at("/overall/total/accuracyPercent").isNull()).isTrue();
        assertThat(stats.at("/flashcards/total/accuracyPercent").isNull()).isTrue();
        assertThat(stats.at("/last7Days").size()).isEqualTo(7);
        assertThat(stats.at("/last30Days").size()).isEqualTo(30);
        assertThat(stats.at("/difficultWords").size()).isZero();
        assertThat(stats.at("/recentSessions").size()).isZero();
        mvc.perform(get("/api/statistics/words/999/mistakes")).andExpect(status().isNotFound());
    }

    @Test void calculatesRealRetentionAccuracySourcesActivityAndMistakeReview() throws Exception {
        Instant now = Instant.now();
        Word practiced = word("куче", "собака");
        word("котка", "кошка");
        Word mastered = word("дом", "дом");
        Word archived = word("архив", "архив"); archived.setArchived(true); words.saveAndFlush(archived);
        StudyProgress due = new StudyProgress(practiced, TrainingMode.FLASHCARD);
        due.recordAnswer(true, now.minus(Duration.ofHours(25)), 1);
        wordProgress.saveAndFlush(due);
        for (TrainingMode mode : List.of(TrainingMode.FLASHCARD, TrainingMode.TYPING_BG_TO_RU, TrainingMode.TYPING_RU_TO_BG)) {
            StudyProgress record = new StudyProgress(mastered, mode);
            for (int interval : List.of(1, 3, 7, 14, 28)) record.recordAnswer(true, now, interval);
            wordProgress.saveAndFlush(record);
        }
        Sentence sentence = sentences.saveAndFlush(new Sentence("Кучето е вкъщи.", "Собака дома.", null, 1));
        SentenceProgress scheduled = new SentenceProgress(sentence);
        scheduled.record(AttemptResult.CORRECT, now.minus(Duration.ofHours(25)), 1);
        sentenceProgress.saveAndFlush(scheduled);

        StudySession flash = session(TrainingMode.FLASHCARD, true);
        StudySession typing = session(TrainingMode.TYPING, true);
        StudySession sentenceSession = session(TrainingMode.SENTENCE, false);
        StudyAttempt flashAnswer = attempt(flash, practiced, null, TranslationDirection.BG_TO_RU, "KNOW", AttemptResult.CORRECT);
        StudyAttempt wrong = attempt(typing, practiced, null, TranslationDirection.BG_TO_RU, "кот", AttemptResult.INCORRECT);
        attempt(typing, practiced, null, TranslationDirection.RU_TO_BG, "куче", AttemptResult.CORRECT);
        StudyAttempt automatic = attempt(sentenceSession, null, sentence, TranslationDirection.RU_TO_BG, "Кучето е вкъщи.", AttemptResult.CORRECT);
        automatic.markAutomatic(); attempts.saveAndFlush(automatic);
        StudyAttempt self = attempt(sentenceSession, null, sentence, TranslationDirection.RU_TO_BG, "Кучето вкъщи.", AttemptResult.PENDING);
        self.finalizeEvaluation(AttemptResult.PARTIAL, EvaluationSource.SELF); attempts.saveAndFlush(self);
        attempt(sentenceSession, null, sentence, TranslationDirection.RU_TO_BG, "неоценено", AttemptResult.PENDING);
        jdbc.update("UPDATE study_attempts SET created_at = ? WHERE id = ?",
                Timestamp.from(now.minus(Duration.ofDays(1))), wrong.getId());

        JsonNode stats = overview();
        assertThat(stats.at("/vocabulary/total").asLong()).isEqualTo(4);
        assertThat(stats.at("/vocabulary/active").asLong()).isEqualTo(3);
        assertThat(stats.at("/vocabulary/archived").asLong()).isEqualTo(1);
        assertThat(stats.at("/vocabulary/newItems").asLong()).isEqualTo(1);
        assertThat(stats.at("/vocabulary/dueItems").asLong()).isEqualTo(1);
        assertThat(stats.at("/vocabulary/masteredItems").asLong()).isEqualTo(1);
        assertThat(stats.at("/reviewsScheduledToday").asLong()).isEqualTo(2);
        assertThat(stats.at("/completedSessions").asLong()).isEqualTo(2);
        assertThat(stats.at("/reviewAttempts").asLong()).isEqualTo(5);
        assertThat(stats.at("/overall/total/correct").asLong()).isEqualTo(3);
        assertThat(stats.at("/overall/total/partial").asLong()).isEqualTo(1);
        assertThat(stats.at("/overall/total/accuracyPercent").asDouble()).isEqualTo(60.0);
        assertThat(stats.at("/overall/automatic/attempts").asLong()).isEqualTo(3);
        assertThat(stats.at("/overall/selfAssessed/attempts").asLong()).isEqualTo(2);
        assertThat(stats.at("/flashcards/total/accuracyPercent").asDouble()).isEqualTo(100.0);
        assertThat(stats.at("/typingBgToRu/total/accuracyPercent").asDouble()).isZero();
        assertThat(stats.at("/typingRuToBg/total/accuracyPercent").asDouble()).isEqualTo(100.0);
        assertThat(stats.at("/sentences/total/accuracyPercent").asDouble()).isEqualTo(50.0);
        assertThat(stats.at("/consecutiveStudyDays").asLong()).isEqualTo(2);
        assertThat(stats.at("/last7Days/5/reviewAttempts").asLong()).isEqualTo(1);
        assertThat(stats.at("/last7Days/6/reviewAttempts").asLong()).isEqualTo(4);
        assertThat(stats.at("/difficultWords").size()).isEqualTo(1);
        assertThat(stats.at("/difficultWords/0/wordId").asLong()).isEqualTo(practiced.getId());
        assertThat(stats.at("/recentSessions").size()).isEqualTo(3);
        JsonNode mistakes = json.readTree(mvc.perform(get("/api/statistics/words/{id}/mistakes", practiced.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(mistakes.at("/incorrectAttempts").asLong()).isEqualTo(1);
        assertThat(mistakes.at("/recentMistakes/0/userAnswer").asText()).isEqualTo("кот");
        assertThat(mistakes.at("/currentRussianTranslations/0").asText()).isEqualTo("собака");
        assertThat(flashAnswer.getId()).isNotNull();
    }

    @Test void repeatedFlashcardRatingCountsOnceInStatistics() throws Exception {
        word("къща", "дом");
        String start = json.writeValueAsString(Map.of("requestId", UUID.randomUUID(), "size", 1));
        JsonNode session = json.readTree(mvc.perform(post("/api/flashcards/sessions")
                .contentType(MediaType.APPLICATION_JSON).content(start)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long id = session.at("/id").asLong(), questionId = session.at("/currentQuestion/id").asLong();
        String question = json.writeValueAsString(Map.of("questionId", questionId));
        mvc.perform(post("/api/flashcards/sessions/{id}/reveal", id).contentType(MediaType.APPLICATION_JSON)
                .content(question)).andExpect(status().isOk());
        String rating = json.writeValueAsString(Map.of("questionId", questionId, "known", false));
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/flashcards/sessions/{id}/ratings", id)
                .contentType(MediaType.APPLICATION_JSON).content(rating)).andExpect(status().isOk());
        JsonNode stats = overview();
        assertThat(stats.at("/reviewAttempts").asLong()).isEqualTo(1);
        assertThat(stats.at("/overall/selfAssessed/attempts").asLong()).isEqualTo(1);
        assertThat(stats.at("/difficultWords/0/incorrectAttempts").asLong()).isEqualTo(1);
        assertThat(stats.at("/completedSessions").asLong()).isEqualTo(1);
    }

    @Test void difficultWordsAreLimitedToTwentyAndRankedByIncorrectAttempts() throws Exception {
        StudySession session = sessions.saveAndFlush(new StudySession(TrainingMode.TYPING));
        List<Word> vocabulary = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            Word word = word("дума " + i, "слово " + i);
            vocabulary.add(word);
            attempt(session, word, null, TranslationDirection.BG_TO_RU, "ошибка", AttemptResult.INCORRECT);
        }
        Word hardest = vocabulary.get(21);
        attempt(session, hardest, null, TranslationDirection.BG_TO_RU, "другая ошибка", AttemptResult.INCORRECT);
        JsonNode stats = overview();
        assertThat(stats.at("/difficultWords").size()).isEqualTo(20);
        assertThat(stats.at("/difficultWords/0/wordId").asLong()).isEqualTo(hardest.getId());
        assertThat(stats.at("/difficultWords/0/incorrectAttempts").asLong()).isEqualTo(2);
        assertThat(stats.at("/overall/total/attempts").asLong()).isEqualTo(23);
    }

    private JsonNode overview() throws Exception {
        return json.readTree(mvc.perform(get("/api/statistics")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
    private Word word(String bulgarian, String russian) {
        Word word = new Word(bulgarian, bulgarian, null, null, null);
        word.addTranslation(russian, russian, true);
        return words.saveAndFlush(word);
    }
    private StudySession session(TrainingMode mode, boolean complete) {
        StudySession session = new StudySession(mode);
        if (complete) session.complete(false); else session.complete(true);
        return sessions.saveAndFlush(session);
    }
    private StudyAttempt attempt(StudySession session, Word word, Sentence sentence,
            TranslationDirection direction, String answer, AttemptResult result) {
        return attempts.saveAndFlush(new StudyAttempt(session, word, sentence, direction, answer, result));
    }
}
