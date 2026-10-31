# Typed aspect-pair practice backend

The backend supports Bulgarian imperfective/perfective recall using the pairs
managed in [ASPECT_PAIRS.md](ASPECT_PAIRS.md). It does not conjugate by person,
translate sentences, or generate additional acceptable forms. The Russian web
interface is at `/aspect-pairs.html`; all endpoints below are same-origin JSON APIs.

## Web practice and recovery

Use **Практика** to select size, namespace/topic, direction, queue and learning
stages. A fresh form selects only stage 1. Check availability before starting;
shortages offer an explicitly labelled shorter session without duplicate pairs.
Resumed sessions restore their saved configuration, including deliberately chosen
stages, for subsequent practice.

Questions display the source verb/meaning and explicitly request the target
Russian meaning. Hidden forms, examples and notes are rendered only from server
feedback after submission/reveal. Enter submits; after feedback Enter or **Дальше**
continues. Held Enter and pending actions cannot submit another outcome. The field
keeps a draft after connection loss. Exact pending requests and the last session
link are retained in localStorage when available; a `?session=ID` URL restores
authoritative state after refresh. Opening the page without the query offers
**Продолжить сохранённое занятие**. URL-based recovery still works without browser
storage (unsubmitted drafts then remain only on the current page).

Results separate mistakes, reveals and skipped items and offer a full question
review. Skipped items retain hidden answers. The **Как заниматься** view explains
aspect versus tense, citation forms, particles, meaning-specific relationships and
learning stages versus CEFR. No TTS is added to this section: no audio can expose a
hidden answer, and voice availability cannot block practice.

## Session selection

API base: `/api/aspect-pair-practice`.

`GET /availability` accepts `size`, repeated/comma-separated `studyStages`,
`topic`, `direction`, `scope`, and `namespace`. These filters have the same meaning
as session-start settings. It returns `eligiblePairs`, `eligibleForward`,
`eligibleReverse`, `requestedSize`, `offeredSize`, and `shorterRequired`.
Forward/reverse counts reflect the selected direction mode and queue; they can
overlap because a pair may be eligible in both directions.

`POST /sessions` starts a durable session and returns 201 with a Location header:

```json
{
  "requestId": "client-generated UUID",
  "size": 20,
  "studyStages": [1],
  "direction": "MIXED",
  "scope": "MIXED",
  "namespace": "bulgarian-aspect-pairs-100",
  "allowShorter": false
}
```

- `size`: 1–100, default 20.
- `studyStages`: distinct integers from 1–3, default **[1]**. Empty lists are
  invalid; stages 2 and 3 are included only when explicitly selected. Stage order
  is canonicalized so `[2,1]` and `[1,2]` mean the same thing.
- `direction`: `IMPERFECTIVE_TO_PERFECTIVE`, `PERFECTIVE_TO_IMPERFECTIVE`, or
  `MIXED` (default).
- `scope`: `NEW`, `DUE`, or `MIXED` (default). Eligibility and schedules are
  independent for each pair/direction. NEW has no attempts in that direction;
  DUE includes reviewed items at or beyond their due instant. MIXED prioritizes
  due, new, learning, then mastered items.
- `topic`: optional exact topic; `namespace`: optional explicit dataset namespace.
  Omitted filters include all topics/namespaces within the selected stages.
- `allowShorter`: defaults false. If fewer distinct eligible pairs exist than
  requested, start returns 409 `SHORTER_SESSION_REQUIRED`. Show the availability
  offer and explicitly retry with `allowShorter:true` and a new start UUID.
  Empty queues return 409 `EMPTY_ASPECT_PAIRS`, `EMPTY_NEW_QUEUE`, or
  `EMPTY_REVIEW_QUEUE` without creating a session.

Archived pairs cannot enter new sessions. Each pair appears at most once in a
session. Mixed direction chooses the most even **feasible** split; odd sizes use
a random extra direction when scheduler priorities tie. Eligibility can force a
larger imbalance (for example, only one direction is due). The planner first fixes
the nearest feasible direction quotas, then minimizes total scheduler priority
across distinct pairs; odd splits prefer due directions before random tie-breaking. No duplicates or
ineligible directions are used to fill a shortage. Order/tie randomization uses
an independent optional test seed `training.aspect-pairs.random-seed`.

## Questions, feedback and explicit next

- `GET /sessions/{id}/question`: authoritative session state/current question.
- `POST /sessions/{id}/answers`: `{ "questionId":123, "answer":"купя" }`.
- `POST /sessions/{id}/reveal`: `{ "questionId":123 }` (Show answer).
- `POST /sessions/{id}/next`: `{ "questionId":123 }` acknowledges feedback.
- `POST /sessions/{id}/complete`: finish early or keep the already closed status.
- `GET /sessions/{id}/results`: results for a closed session (409 while active).
- `GET /progress?namespace=...`: stored directional progress, counts, interval,
  last/next review instants and scheduler category; no answer content. Only
  existing progress rows are returned; an absent row means NEW.
- `GET /statistics?namespace=...`: separate aspect statistics. `answered` equals
  typed correct + incorrect outcomes; `correct`, `incorrect` and `revealed` are
  counted from the unique durable attempts. `dueForReview` counts distinct active
  pairs with at least one due direction; `dueDirections` counts their due pair/
  direction schedules. Two due directions on one pair therefore mean one due pair.
  `directions` reports the same outcomes and due schedules per actual direction;
  `asOf` is the UTC scheduling clock instant. Omit namespace for all datasets.

The **Статистика** view within the aspect section uses that separate API across
all namespaces/stages/topics. Historical answer totals include in-progress/early
sessions and archived pairs. Reveals and skipped questions are excluded from
`answered`/`correct`/`incorrect`; reveals have their own counter. Archived pairs
remain in history but not in due queues. A failed refresh retains the last
confirmed values and displays an error; initial unavailable values remain dashes.
No aspect values are silently added to `/api/statistics`, word counts, retention,
activity streaks or existing mode totals.

Before submission/reveal, a question contains only its internal question ID,
position, actual direction, `revealed:false`, `feedback:null`, and a prompt:
source Bulgarian form, Russian source meaning, target Russian meaning and Russian
instruction. Target meaning helps distinguish resultative/bounded entries.
It does **not** expose hidden Bulgarian forms/accepted arrays, notes, examples,
provenance, external IDs, raw snapshots or the rest of the queue. This is a
learning feature in a single-user application: separate management APIs remain
available, but practice responses do not reveal unsubmitted answers.

Submission checks only the target side of that question's immutable snapshot:
forward uses `acceptedPerfective`, reverse uses `acceptedImperfective`.
Normalization is NFC first, remove remaining combining acute/grave stress marks
(U+0301/U+0300), `Locale.ROOT` lowercase, Unicode whitespace collapse/trim, final
NFC. Composed `ѝ` and `й` and canonically equivalent decomposed inputs remain
distinct from `и`. Do not remove all Unicode marks, particles `се`/`си`, spelling,
or punctuation. No Latin lookalike substitutions, transliteration, substrings,
fuzzy matches, or automatic synonyms are accepted. Precomposed grave letters
other than `ѝ` retain the literal NFC-first behavior documented in the plan.

Submitted feedback includes outcome `CORRECT`/`INCORRECT`, original `userAnswer`,
target `acceptedAnswers` and full saved `content`: both Bulgarian forms, both
Russian meanings, both accepted arrays, note, examples and other learning fields.
Mismatch message is exactly **«Не совпало с ответами этой карточки»**; it makes
no claim that an unmatched Bulgarian form cannot exist.

Feedback remains current until **Next**; GET/reload never advances it. Final
submission/reveal marks the session COMPLETED but retains final feedback until
Next (or complete) acknowledges it. Next before an outcome returns 409
`ANSWER_REQUIRED`. Early completion is ABANDONED if questions remain, retains all
committed outcomes and does not create attempts/schedule changes for unanswered
items. Their results are SKIPPED with prompt and `feedback:null`; their hidden
Bulgarian answers stay hidden because they were neither submitted nor revealed.

Session state includes settings, status, total questions, current position,
`answeredQuestions` (typed submissions only), `correctAnswers`, `incorrectAnswers`,
`revealedQuestions`, `remainingQuestions` (neither typed nor revealed), timestamps
and `currentQuestion`. Results include per-question outcomes/feedback and typed
accuracy `correctAnswers / answeredQuestions` (null when no typed answers).

## Reveal and independent scheduling

Show answer is a terminal `REVEALED` outcome with a persisted reveal timestamp,
null user answer, full feedback, and no independent-recall credit. A revealed
question cannot subsequently receive a scored answer, even if the typed answer
is correct. Revealing an already typed question conflicts; its feedback is
already visible. Exact reveal retries do not record another outcome.

Reveal **counts as failed recall for scheduling** in that direction: increment
its total attempts, reset consecutive-correct to zero, and schedule a one-day
retry. Correct/lifetime-success counters never increase. Reveals are reported
separately and excluded from typed accuracy. The reverse direction is untouched.

The existing `SpacedRepetitionScheduler` supplies the UTC clock, categories,
priority, mastery thresholds and interval algorithm. Successful reviews use 1,
3, 7 days, then double to a 365-day cap. Incorrect/revealed reviews use one day;
the next correct review after failure restarts at one day. Mastery follows the
existing streak/interval configuration; it is not awarded by viewing/revealing.
All aspect progress remains in `aspect_progress` keyed by pair/direction.
Vocabulary/flashcard/sentence schedules and existing `/api/statistics` totals,
activity and retention are unchanged.

## Persistence, concurrency and recovery

Flyway V8 adds separate `aspect_sessions`, `aspect_questions` and `aspect_attempts`.
It does not change previously applied migrations or reuse shared study attempts.
Question snapshots, order, direction, acknowledgements, reveal state, original
submissions/outcomes, current position and status all persist in H2. Editing,
reimporting or archiving a pair after start cannot change an ongoing question's
answer lists/feedback. Existing pair IDs/FKs and progress remain intact.

Save the session ID on the client and reload `/sessions/{id}/question` after a
refresh/restart. Keep the exact pending start/answer/reveal/next request until its
result is known. A repeated start UUID and equivalent complete settings returns
that session's current state; changed settings return 409 `START_REQUEST_CHANGED`.
Concurrent start UUID races restore the winning session from a fresh transaction.

Session-row locks serialize all actions/reads. Pair-row locks serialize progress
updates across sessions and coordinate snapshot selection with archive/edit.
Stable pair-ID lock order avoids conflicting start orders. One attempt per
question is enforced in the database. Attempt, directional progress, correct
count, reveal state and completion commit atomically. Exact original-answer
retries are safe even after completion; a changed answer returns 409
`ANSWER_CONFLICT` even if it would normalize identically. After reveal, answers
return 409 `ANSWER_REVEALED`. Repeated acknowledged Next/complete are safe.
Foreign/future questions return 409 `STALE_QUESTION`; closed sessions reject new
outcomes. Unknown session IDs return 404. Malformed/invalid requests return 400.

## Verification

Integration tests use the `test` profile and isolated H2. Coverage includes all
required positive/negative answer cases, Cyrillic normalization and distinctions,
default stages/filter/archive/shortages, no answer leaks (including early results),
immutable snapshots, explicit Next, start/answer/reveal retries and races,
independent schedules, unchanged legacy statistics and actual context restarts on
a temporary file database. Planner tests exhaust small eligibility pools to check
the optimal feasible balance and uniqueness; scheduler tests cover intervals,
mastery, exact due boundaries and failure resets.

Run `.\mvnw.cmd clean verify` with JDK 21. For HTTP checks use a separate loopback
port and an explicit disposable database under ignored `target/` with
`WRITE_DELAY=0`; never run smoke imports/practice against the personal database.
The web interface uses shared styles/navigation and vanilla ES modules. Its
phone layout uses wrapping cards/forms rather than wide tables.

### Browser test

The optional real-Chrome test uses Node's built-in test runner and CDP/WebSocket
support, without installing frontend dependencies. On Windows, after setting
`JAVA_HOME` to JDK 21 and running the Maven Wrapper build, use:

```powershell
.\scripts\verify-aspect-pairs.ps1
```

The script creates a fresh disposable file database under `target/`, starts a
hidden test server on loopback port 18087, runs real headless Chrome, stops and
restarts the JVM on that same disposable file, and verifies session recovery,
exact retries, schedules and separate statistics over HTTP. It stops its own
server afterwards. Use `-Port` for another free non-default port and `CHROME_PATH`
for an alternative installed Chrome executable. JDK 21, Node with built-in fetch/
WebSocket support (verified with Node 24), and installed Chrome are required.

For the browser-only part against a separately started **fresh disposable** server:

```powershell
$env:ASPECT_BROWSER_BASE_URL = 'http://127.0.0.1:18087'
node --test src/test/js/aspect-pairs-browser.test.mjs
```

The test requires an empty aspect dataset and never uses the personal database.
It checks import → list → practice → submit → refresh → explicit resume → results,
plus editing multiple answers, archive/restore, reimport selection, invalid-file
preview, lost creation responses with paginated recovery, network failures during
import/answer/statistics, held Enter, mistake/reveal review, contextual meaning,
unavailable localStorage, separate counters, legacy-mode HTTP exercises, and
overflow at 320/360 px. It saves `target/aspect-pairs-phone.png` and
`target/aspect-statistics-phone.png`. Without `ASPECT_BROWSER_BASE_URL` the
optional test is skipped; that is not browser verification.

See [ASPECT_PAIRS_REVIEW.md](ASPECT_PAIRS_REVIEW.md) for the final executed checks
and remaining manual verification. Test artifacts/logs are generated under ignored
`target/`; none belongs in the review commit.
