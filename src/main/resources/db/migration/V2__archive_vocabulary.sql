ALTER TABLE words ADD COLUMN archived BOOLEAN DEFAULT FALSE NOT NULL;
CREATE INDEX idx_words_archive_part ON words(archived, part_of_speech, id);
