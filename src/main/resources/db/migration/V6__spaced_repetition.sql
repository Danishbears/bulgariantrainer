ALTER TABLE study_progress DROP CONSTRAINT ck_progress_mode;
ALTER TABLE study_progress ADD CONSTRAINT ck_progress_mode CHECK (training_mode IN ('FLASHCARD', 'TYPING', 'TYPING_BG_TO_RU', 'TYPING_RU_TO_BG', 'SENTENCE'));
ALTER TABLE study_progress ADD COLUMN successful_review_count INTEGER DEFAULT 0 NOT NULL;
UPDATE study_progress SET successful_review_count = correct_attempts,
    next_review_at = COALESCE(next_review_at, last_reviewed_at)
    WHERE total_attempts > 0;
ALTER TABLE study_progress ADD CONSTRAINT ck_progress_successes CHECK (successful_review_count BETWEEN 0 AND correct_attempts);
-- Earlier typing progress combined directions. Rebuild per-direction history only from
-- attempts whose direction is known; retain the original aggregate row unchanged.
INSERT INTO study_progress (word_id, training_mode, total_attempts, correct_attempts,
    consecutive_correct, last_reviewed_at, next_review_at, repetition_interval,
    ease_factor, successful_review_count)
SELECT a.word_id,
    CASE WHEN a.direction = 'BG_TO_RU' THEN 'TYPING_BG_TO_RU' ELSE 'TYPING_RU_TO_BG' END,
    COUNT(*), SUM(CASE WHEN a.result = 'CORRECT' THEN 1 ELSE 0 END),
    0, MAX(a.created_at), MAX(a.created_at), 0, 2.5,
    SUM(CASE WHEN a.result = 'CORRECT' THEN 1 ELSE 0 END)
FROM study_attempts a JOIN study_sessions s ON s.id = a.session_id
WHERE s.training_mode = 'TYPING' AND a.word_id IS NOT NULL
    AND a.result IN ('CORRECT', 'INCORRECT')
GROUP BY a.word_id, a.direction;

ALTER TABLE sentence_progress ADD COLUMN next_review_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE sentence_progress ADD COLUMN repetition_interval INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE sentence_progress ADD COLUMN successful_review_count INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE sentence_progress ADD COLUMN consecutive_correct INTEGER DEFAULT 0 NOT NULL;
UPDATE sentence_progress SET successful_review_count = correct_attempts,
    next_review_at = last_reviewed_at WHERE total_attempts > 0;
ALTER TABLE sentence_progress ADD CONSTRAINT ck_sentence_progress_schedule CHECK (
    repetition_interval >= 0 AND successful_review_count BETWEEN 0 AND correct_attempts
    AND consecutive_correct BETWEEN 0 AND correct_attempts);
CREATE INDEX idx_sentence_progress_due ON sentence_progress(next_review_at);

ALTER TABLE study_sessions ADD COLUMN study_scope VARCHAR(20) DEFAULT 'MIXED' NOT NULL;
ALTER TABLE study_sessions ADD CONSTRAINT ck_study_scope CHECK (study_scope IN ('DUE', 'NEW', 'MIXED'));
