# Flashcard sessions

Open `/flashcards.html`. Choose Bulgarian to Russian, Russian to Bulgarian, or mixed,
and a size from 1 to 100 (default 20). Only active words with translations are eligible.
Words are shuffled and never repeated within a session. If fewer are available, all
available words are used. Mixed sessions alternate directions with a random starting
direction, giving both directions when at least two words are available.

Reveal the answer with **Показать ответ** or Space; then choose **Не знаю** (1) or
**Знаю** (2). Russian prompts/answers include every translation, primary first.
The server controls the queue, reveal state, counts, completion, and results. The browser
does not increment progress locally. Rating buttons are disabled until reveal and while
a request is pending. Shortcuts ignore typing fields, modifier keys, and held-key repeats.

Use **Завершить досрочно** to finish early. Only rated cards affect progress. Remaining
cards appear as unrated in the summary and do not create attempts or count as mistakes.
A fully rated session completes automatically. Recognition ratings are self-reported;
neither viewing nor revealing a card awards mastery. No mastery thresholds or spaced
repetition scheduling are introduced here.

## JSON API

All endpoints are under `/api/flashcards/sessions`.

- `POST /`: start with `{"requestId":"<UUID>","size":20,"direction":"MIXED"}`.
  `requestId` is required. Size defaults to 20 and direction to `BG_TO_RU` when omitted.
  Direction values: `BG_TO_RU`, `RU_TO_BG`, `MIXED`. Returns 201 and a Location header.
  Retrying the same UUID/settings returns the existing session, including its current state.
  Changed settings for a used UUID return 409 `START_REQUEST_CHANGED`.
- `GET /{id}/question`: current session state, progress, and current question (null after completion).
- `POST /{id}/reveal`: `{"questionId":123}`. Reveals only the current card; repeatable.
- `POST /{id}/ratings`: `{"questionId":123,"known":true}`. Requires a revealed current card.
  An identical rating retry returns current state without another write, even after completion.
  A changed rating returns 409 `RATING_CONFLICT`; stale/foreign questions return 409 `STALE_QUESTION`.
- `POST /{id}/complete`: completes the session; safe to repeat. An early finish has status
  `ABANDONED`; rating every card yields `COMPLETED`.
- `GET /{id}/results`: completed session state and all immutable card snapshots with outcomes
  `CORRECT` (Know), `INCORRECT` (Don't Know), or `SKIPPED` (unrated). Returns 409 while active.

Session responses contain `id`, `direction`, `status`, `totalQuestions`, `answeredQuestions`,
`knownCount`, `unknownCount`, `remainingQuestions`, timestamps, and `currentQuestion`.
Questions contain `id`, `position`, actual `direction`, a list of `prompt` strings, `revealed`,
and an `answer` list that is empty until reveal. Empty dictionaries return 409 `EMPTY_DICTIONARY`
without creating a session; unknown IDs return 404 `SESSION_NOT_FOUND`.

## Persistence and retries

Flyway V3 adds session settings/request IDs and the durable `flashcard_questions` queue.
Unique constraints enforce one word and position per session, and one linked attempt per card.
Existing data and migrations are retained. Snapshots remain stable if a word is edited or
archived mid-session. Queued words cannot be permanently deleted; archiving is still available.

Session-row locks serialize reads, reveal, rating, and completion. Word-row locks serialize
progress updates across sessions. The attempt, FLASHCARD progress counters, question result,
and session counters are committed together. Progress for TYPING and SENTENCE is untouched.
Only ratings update total/correct/consecutive counts and last-reviewed time. Scheduling fields
retain their defaults. Early completion does not manufacture skipped study attempts.

The page keeps the session ID in the URL and saves the last session/pending request in localStorage
when available. A refresh or restart resumes server state. After an uncertain network failure,
controls stay locked and **Повторить запрос** resends the same request; the user cannot accidentally
send a different rating. Start, reveal, rating, and completion retries are safe. Distinct start UUIDs
create independent sessions, including across tabs. The browser's last-session shortcut is local
to that browser; the URL can retrieve any existing session. No automatic sample data is imported.

## Verification

Run `./mvnw clean verify` with JDK 21. Flashcard integration tests use their own in-memory test
database and cover defaults, archive exclusion, all directions, no duplicate queue entries,
viewing without credit, isolated progress, unknown ratings resetting streaks, concurrent retries,
concurrent progress updates across sessions, early completion, stable snapshots, validation,
empty dictionaries, and safe start retries.

For browser testing, start a separate instance with a disposable file database:

```powershell
java -jar target/bulgariantrainer-0.0.1-SNAPSHOT.jar --server.port=18082 --spring.datasource.url=jdbc:h2:file:./target/flashcard-smoke/vocabulary
```

Create test vocabulary using the vocabulary page. Check reveal/ratings and Space/1/2, reload
during a session, complete normally and early, and check the summary. Stop/restart this instance
while a request is pending and retry; counts must not double. Verify mobile layout and reduced
motion. Never run these mutations against the personal `data/bulgariantrainer` database.
