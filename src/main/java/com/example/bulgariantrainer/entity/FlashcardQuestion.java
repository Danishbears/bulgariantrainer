package com.example.bulgariantrainer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.List;

/** Immutable vocabulary snapshots keep a running session stable when words are edited. */
@Entity
@Table(name = "flashcard_questions")
public class FlashcardQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private StudySession session;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;
    @Column(nullable = false)
    private int position;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private TranslationDirection direction;
    @Column(nullable = false, length = 255)
    private String bulgarianText;
    @Column(nullable = false, length = 14000)
    private String russianTexts;
    private Instant revealedAt;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", unique = true)
    private StudyAttempt attempt;

    protected FlashcardQuestion() {}

    public FlashcardQuestion(StudySession session, Word word, int position,
                             TranslationDirection direction, List<String> translations) {
        this.session = session;
        this.word = word;
        this.position = position;
        this.direction = direction;
        this.bulgarianText = word.getBulgarianText();
        // WordService normalizes whitespace; translations cannot contain newlines.
        this.russianTexts = String.join("\n", translations);
    }

    public void reveal() { if (revealedAt == null) revealedAt = Instant.now(); }
    public void record(StudyAttempt attempt) { this.attempt = attempt; }
    public Long getId() { return id; }
    public Word getWord() { return word; }
    public int getPosition() { return position; }
    public TranslationDirection getDirection() { return direction; }
    public String getBulgarianText() { return bulgarianText; }
    public List<String> getRussianTexts() { return List.of(russianTexts.split("\n")); }
    public boolean isRevealed() { return revealedAt != null; }
    public StudyAttempt getAttempt() { return attempt; }
}
