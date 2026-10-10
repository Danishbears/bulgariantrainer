package com.example.bulgariantrainer;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;

class MigrationPersistenceTests {
    @TempDir Path directory;

    @Test
    void fileDatabaseMigrationPreservesDataAcrossReopenAndEnforcesConstraints() throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("vocabulary").toAbsolutePath().toString().replace('\\', '/');
        Flyway initial = Flyway.configure().dataSource(url, "sa", "").target("1").cleanDisabled(true).load();
        assertThat(initial.migrate().migrationsExecuted).isEqualTo(1);
        Flyway flyway = Flyway.configure().dataSource(url, "sa", "").cleanDisabled(true).load();
        try (Connection db = DriverManager.getConnection(url, "sa", "")) {
            execute(db, "INSERT INTO words(id, bulgarian_text, normalized_text, created_at, updated_at) VALUES (1, 'Къща', 'къща', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            execute(db, "INSERT INTO translations(word_id, russian_text, normalized_text, is_primary) VALUES (1, 'Дом', 'дом', TRUE)");
            execute(db, "INSERT INTO study_progress(word_id, training_mode) VALUES (1, 'TYPING')");
            assertThatThrownBy(() -> execute(db, "INSERT INTO study_progress(word_id, training_mode) VALUES (1, 'TYPING')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(db, "INSERT INTO words(bulgarian_text, normalized_text, created_at, updated_at) VALUES ('КЪЩА', 'къща', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(db, "DELETE FROM words WHERE id = 1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(db, "INSERT INTO translations(word_id, russian_text, normalized_text, is_primary) VALUES (999, 'дом', 'дом', TRUE)"))
                    .isInstanceOf(SQLException.class);
            execute(db, "INSERT INTO study_sessions(id, training_mode, started_at, status) VALUES (1, 'TYPING', CURRENT_TIMESTAMP, 'IN_PROGRESS')");
            execute(db, "INSERT INTO study_attempts(session_id, word_id, direction, result, user_answer, created_at) VALUES (1, 1, 'BG_TO_RU', 'CORRECT', 'дом', CURRENT_TIMESTAMP)");
            assertThatThrownBy(() -> execute(db, "DELETE FROM study_sessions WHERE id = 1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(db, "INSERT INTO study_attempts(session_id, direction, result, created_at) VALUES (1, 'BG_TO_RU', 'CORRECT', CURRENT_TIMESTAMP)"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(db, "UPDATE study_progress SET correct_attempts = 1 WHERE word_id = 1"))
                    .isInstanceOf(SQLException.class);
        }
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(5);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
        try (Connection db = DriverManager.getConnection(url, "sa", "");
             var statement = db.createStatement();
             var result = statement.executeQuery("SELECT w.bulgarian_text, t.russian_text, w.archived FROM words w JOIN translations t ON t.word_id = w.id")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("Къща");
            assertThat(result.getString(2)).isEqualTo("Дом");
            assertThat(result.getBoolean(3)).isFalse();
        }
    }

    @Test
    void upgradingExistingProgressPreservesHistoryAndMakesItDue() throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("study-history").toAbsolutePath().toString().replace('\\', '/');
        Flyway.configure().dataSource(url, "sa", "").target("5").cleanDisabled(true).load().migrate();
        try (Connection db = DriverManager.getConnection(url, "sa", "")) {
            execute(db, "INSERT INTO words(id, bulgarian_text, normalized_text, created_at, updated_at, archived) VALUES (1, 'куче', 'куче', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)");
            execute(db, "INSERT INTO study_progress(word_id, training_mode, total_attempts, correct_attempts, consecutive_correct, last_reviewed_at) VALUES (1, 'TYPING', 3, 2, 1, CURRENT_TIMESTAMP)");
            execute(db, "INSERT INTO study_sessions(id, training_mode, started_at, status) VALUES (1, 'TYPING', CURRENT_TIMESTAMP, 'COMPLETED')");
            execute(db, "INSERT INTO study_attempts(session_id, word_id, direction, result, user_answer, created_at) VALUES (1, 1, 'BG_TO_RU', 'CORRECT', 'собака', CURRENT_TIMESTAMP)");
            execute(db, "INSERT INTO study_attempts(session_id, word_id, direction, result, user_answer, created_at) VALUES (1, 1, 'RU_TO_BG', 'INCORRECT', 'котка', CURRENT_TIMESTAMP)");
            execute(db, "INSERT INTO sentences(id, bulgarian_text, russian_text, normalized_russian, approved, archived) VALUES (1, 'Тук съм.', 'Я здесь.', 'я здесь.', TRUE, FALSE)");
            execute(db, "INSERT INTO sentence_progress(sentence_id, total_attempts, correct_attempts, partial_attempts, incorrect_attempts, last_reviewed_at) VALUES (1, 3, 1, 1, 1, CURRENT_TIMESTAMP)");
        }
        assertThat(Flyway.configure().dataSource(url, "sa", "").cleanDisabled(true).load().migrate().migrationsExecuted).isEqualTo(1);
        try (Connection db = DriverManager.getConnection(url, "sa", ""); var statement = db.createStatement()) {
            try (var row = statement.executeQuery("SELECT total_attempts, correct_attempts, successful_review_count, next_review_at FROM study_progress WHERE training_mode = 'TYPING'")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getInt(1)).isEqualTo(3);
                assertThat(row.getInt(2)).isEqualTo(2);
                assertThat(row.getInt(3)).isEqualTo(2);
                assertThat(row.getTimestamp(4)).isNotNull();
            }
            try (var row = statement.executeQuery("SELECT total_attempts, correct_attempts, partial_attempts, incorrect_attempts, successful_review_count, next_review_at FROM sentence_progress")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getInt(1)).isEqualTo(3);
                assertThat(row.getInt(2)).isEqualTo(1);
                assertThat(row.getInt(3)).isEqualTo(1);
                assertThat(row.getInt(4)).isEqualTo(1);
                assertThat(row.getInt(5)).isEqualTo(1);
                assertThat(row.getTimestamp(6)).isNotNull();
            }
            try (var row = statement.executeQuery("SELECT training_mode, total_attempts, correct_attempts, successful_review_count, next_review_at FROM study_progress WHERE training_mode LIKE 'TYPING_%' ORDER BY training_mode")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("TYPING_BG_TO_RU");
                assertThat(row.getInt(2)).isEqualTo(1);
                assertThat(row.getInt(3)).isEqualTo(1);
                assertThat(row.getInt(4)).isEqualTo(1);
                assertThat(row.getTimestamp(5)).isNotNull();
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("TYPING_RU_TO_BG");
                assertThat(row.getInt(2)).isEqualTo(1);
                assertThat(row.getInt(3)).isZero();
                assertThat(row.next()).isFalse();
            }
        }
    }

    private void execute(Connection db, String sql) throws SQLException {
        try (var statement = db.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}

