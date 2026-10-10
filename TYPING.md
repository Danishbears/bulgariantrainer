# Typing practice

Open `/typing.html`. A standard session has 30 distinct active vocabulary words with
translations: exactly 15 Bulgarian-to-Russian and 15 Russian-to-Bulgarian questions,
randomly ordered. Archived words are excluded. For fewer than 30 eligible words,
the page offers an explicitly labelled shorter session. No words repeat; direction
counts differ by at most one (one word necessarily gives a single direction).

Enter one stored translation and press Enter or **Проверить**. Feedback remains
visible until **Следующий вопрос**; the last answer offers **Посмотреть результаты**.
The summary includes saved mistakes and accepted answers. **Завершить досрочно**
keeps existing attempts without awarding progress for unanswered questions.

## Persistence and evaluation

Flyway V4 adds typing session metadata and an ordered question table without changing
previous migrations or recreating data. Questions snapshot the Bulgarian text and all
Russian translations at start, so vocabulary edits do not invalidate an ongoing session.
Queued vocabulary is protected from hard deletion; archiving remains available.

BG-to-RU accepts any stored Russian translation. RU-to-BG displays Russian translations
and accepts only the Bulgarian entry. Comparison uses Unicode NFC, Unicode whitespace
collapse/trim, and locale-independent lowercase. It does not remove accents, normalize
lookalike alphabets, equate й/и or ъ/а, strip punctuation, or use fuzzy matching.
Each submission stores its original text and direction. Only submitted answers update
TYPING progress; FLASHCARD progress is independent. This milestone does not schedule
spaced repetition or assign mastery.

Session/word database locks and unique constraints prevent duplicate attempts and lost
progress updates. Repeating the same question ID and exact answer is idempotent, including
after completion; a different answer conflicts. Finished sessions reject new attempts.
Start requests use a client-generated UUID, also safe to retry. The browser retains an
uncertain request and retries exactly that request after a connectivity failure.

Question order, direction, attempts, feedback acknowledgement, and results survive a
server restart. Reload the `?session=ID` URL to recover; local storage also remembers the
last session. Before submission, the question API returns only the source prompt and
`feedback: null`. It never returns that question's accepted answers. Vocabulary APIs
remain accessible in this single-user app; this is a learning aid, not an exam-security system.

## REST API

All endpoints return JSON. `POST /api/typing/sessions` returns 201 and a Location header;
other successful requests return 200. Session responses include `id`, `status`,
`totalQuestions`, `answeredQuestions`, `correctAnswers`, and `currentQuestion`.

- `GET /api/typing/availability`: eligibleWords, offeredSize, shorterRequired.
- `POST /api/typing/sessions`: `{ "requestId": "UUID", "allowShorter": false }`.
  Fewer than 30 requires explicit `allowShorter: true`; otherwise 409
  `SHORTER_SESSION_REQUIRED`. Zero words returns 409 `EMPTY_DICTIONARY`.
- `GET /api/typing/sessions/{id}/question`: authoritative state. A submitted current
  question includes feedback with result, userAnswer, and acceptedAnswers.
- `POST /api/typing/sessions/{id}/answers`: `{ "questionId": 123, "answer": "собака" }`.
  Answers are required, maximum 4000 characters. Returns state with CORRECT/INCORRECT feedback.
- `POST /api/typing/sessions/{id}/next`: `{ "questionId": 123 }`. Acknowledges feedback;
  idempotent. Final submission completes the session; final acknowledgement opens results.
- `POST /api/typing/sessions/{id}/complete`: ends early (ABANDONED), or keeps COMPLETED.
- `GET /api/typing/sessions/{id}/results`: closed session state plus `mistakes`, each
  containing position, direction, prompt, original userAnswer, and acceptedAnswers.

Unknown sessions return 404; stale/foreign questions, conflicting answers, closed
sessions, or premature results return 409 with structured error codes. Malformed or
invalid requests return 400. No other session's question can be submitted.

For repeatable tests set `training.typing.random-seed=42`. Production omits the seed.
`TypingQuestionPlanner` also accepts any Java `RandomGenerator` for isolated tests.
Run all tests with Java 21 using `.\mvnw.cmd clean verify`.

The default file database uses H2 WRITE_DELAY=0 to flush committed writes without the
usual delayed-write window. Stop the application normally when possible. Custom
datasource URL overrides should retain WRITE_DELAY=0 for the same durability behavior.

