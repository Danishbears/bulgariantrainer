# Aorist practice backend

The backend supports typed recall and multiple choice of Bulgarian indicative
**минало свършено време**, using the content in [AORIST_CONTENT.md](AORIST_CONTENT.md).
It never derives a paradigm or swaps a verb for its aspect counterpart. Both
aspects are eligible; `чета/1pl → четохме` and `прочета/1pl → прочетохме` are
different learning targets. The Russian interface is at `/aorist.html`, documented
in [AORIST_WEB.md](AORIST_WEB.md). Import remains explicitly initiated and confirmed.

The current user request supersedes three earlier proposals in AORIST_PLAN:
default to **typed / 1sg**, allow different persons of a verb in one session,
and keep independent scheduled progress for **verb + person + exercise type**.
Recognition can advance its own schedule, never the typed-recall schedule.

## Answer and prompt scope

Every public question identifies the lemma, Russian meaning, `tense=aorist`,
`tenseLabel=Минало свършено време`, indicative mood and separate person code/label:

| Code | Label |
| --- | --- |
| 1sg | аз |
| 2sg | ти |
| 3sg | той/тя/то |
| 1pl | ние |
| 2pl | вие |
| 3pl | те |

The answer is an **isolated verb form without a subject pronoun**. For
`облека се/1sg`, enter `облякох се`. The person label is separate; do not
concatenate it into the incorrect sentence `Аз облякох се`. A full sentence uses
`Аз се облякох`, but that sentence is outside this isolated-answer contract and
is not accepted unless explicitly supplied as an accepted learning form.
No particle is removed, appended, reordered, or inferred by practice.

Typed validation uses only the selected question's snapshotted `forms[person]`:
Unicode NFC first; remove remaining combining acute/grave U+0301/U+0300; lowercase
with Locale.ROOT; trim/collapse Unicode whitespace; NFC again. `и`, `й`, `ѝ`
remain distinct, including canonically decomposed spellings. Preserve particles,
prefixes, punctuation and spelling. No fuzzy matches, substrings, lookalike
alphabet replacement, pronoun stripping or automatic grammatical correction.

For mismatch use **«Не совпало с ответами этой карточки»**. This evaluates the
stored question, not whether an unmatched form can exist elsewhere in Bulgarian.

## Session settings and selection

API base: `/api/aorist-practice`. `GET /availability` accepts:

- `exerciseType`: TYPED (default) or MULTIPLE_CHOICE.
- `persons`: repeated/comma-separated codes; default `[1sg]`. Select any nonempty,
  distinct subset or explicitly supply all six; gender is not another slot.
- `aspect`: omitted for both, or imperfective/perfective.
- `studyGroup`: optional exact dataset-defined label, not a suffix rule/CEFR stage.
- `datasetId`: optional exact external dataset identifier.
- `size`: 1–100, default 20.
- `scope`: NEW, DUE, MIXED (default), applied to the selected exercise's own rows.

Availability reports `eligibleQuestions` (verb/person slots), `eligibleVerbs`,
requested/offered size and `shorterRequired`. For choice it also reports
`reducedOptionQuestions`, `choiceUnavailableQuestions` and `typedAlternativeQuestions`
(eligible typed slots under the same queue/content filters). Viewing availability
does not record learning. It is advisory; start rechecks eligibility under locks.

`POST /sessions` creates a persistent session (201 plus Location):

```json
{
  "requestId": "client-generated UUID",
  "exerciseType": "TYPED",
  "persons": ["1pl"],
  "size": 20,
  "scope": "MIXED",
  "datasetId": "bg-aorist-core-40",
  "allowShorter": false
}
```

Use `persons:["1sg"]` for аз, a selected subset, or
`["1sg","2sg","3sg","1pl","2pl","3pl"]` for all. Person order is canonicalized:
reordering the same selection with an existing request UUID is safe. Other changed
settings require a new UUID (409 START_REQUEST_CHANGED). Equivalent retries return
the existing session's current state rather than restarting it.

Archived verbs are excluded from new sessions. Each internal verb/person occurs
at most once in a session, even when 2sg/3sg answers look identical; different
persons can occur for the same verb. The supplied dataset has 40 аз targets or
240 all-person targets, with at most 100 per session. Prioritize DUE, NEW,
LEARNING, MASTERED with random ties and randomized presentation order. MIXED
includes future learning/mastered items after due/new, matching the existing
scheduler; it is not restricted to due+new only. No gender/person inference task.

Insufficient eligible slots return 409 SHORTER_SESSION_REQUIRED unless the caller
explicitly accepts `allowShorter:true`; no duplication pads the queue. Zero
eligible slots return the appropriate empty-queue conflict. Starts and all reads
return only the current question, never a future queue or its snapshots.

## Multiple choice and disclosure boundaries

For a choice question, include one displayed accepted answer and up to three
normalized-distinct distractors from explicitly stored forms of **other persons
of the same verb**. Exclude every member of the target accepted set, not just the
displayed variant. Equal 2sg/3sg forms cannot become ambiguous duplicates or wrong
alternatives. No invented spelling or aspect-counterpart substitution.

Four options are usual. Two or three are explicitly announced in `choiceNotice`;
the caller may instead start typed practice. Fewer than two excludes that slot
from choice; if no usable choices remain return CHOICE_OPTIONS_UNAVAILABLE and
use availability to offer typed practice. Exercise type never changes implicitly.

Current public options contain **only `{optionId,text}`**. UUIDs are random and
do not encode correctness. There is no correct option identifier/index/flag,
accepted set, note, source URL, full paradigm or future-question data before
submission/reveal. The accepted candidate is necessarily visible among options,
with its identity unmarked. A lemma can naturally coincide with an answer; its
display is not replaced or hidden. Typed questions have an empty options list.

Only the current person's accepted answers appear after submission or reveal.
Full content and provenance are snapshotted privately. A separate guarded
`GET /sessions/{id}/questions/{questionId}/explanation` route returns
`available`, `note`, `forms`, `source`, `notice`. It rejects unsubmitted/unrevealed
questions (409 ANSWER_REQUIRED). For an answered/revealed question with another
unanswered queued person of that verb, it returns available=false and null
note/forms/source; otherwise it returns the original full content fields.
The table is expandable in the UI. Early-skipped persons continue to withhold
the full explanation, even in closed sessions. Reading explanations never changes
progress. Result review items now include questionId for this guarded lookup.
Management APIs remain the separate place to inspect full learning material.
Early results keep skipped answers hidden. Choice feedback explains that a distractor is wrong for this
question and may be a valid Bulgarian form in another context.

## Actions, results and recovery

All child routes are `/sessions/{id}/...`:

| Method / route | Body / behavior |
| --- | --- |
| GET `/question` | Authoritative current state, settings, counters, status, prompt/options and nullable feedback. |
| POST `/answers` | Typed: `{questionId,answer}`; choice: `{questionId,optionId}`. Exactly one modality-appropriate payload, maximum typed text 4000 characters. |
| POST `/reveal` | `{questionId}`; finalizes as REVEALED, never correct. Cannot subsequently earn credit by typing/selecting. |
| POST `/next` | `{questionId}`; acknowledge feedback and advance separately. Requires a saved submission/reveal. Safe to retry. |
| POST `/complete` | Ends early as ABANDONED, or preserves COMPLETED; idempotent. Skips unanswered slots without attempts/schedule updates. |
| GET `/results` | Closed session, modality-specific accuracy, items and incorrect-only `mistakes`. Reveals/skips have distinct outcomes. Returns 409 while active. |

Final submission/reveal marks the session completed but retains final feedback
until Next. Results accuracy uses correct/(correct+incorrect), excluding reveal
and skip, and is null with no submitted answers. `correctAnswers` always belongs
to the session's explicit exercise type. `independentRecallCorrect` is typed
correctness only and is zero for every choice session. Do not merge these modes
into one typed mastery/accuracy number.

`GET /sessions?status=IN_PROGRESS&page=0&size=20` lists safe resumable summaries
(zero-based, size 1–100) without prompts/answers. `?session=ID` clients should load
`/sessions/ID/question` after refresh. Sessions do not depend on browser storage.
Future UI should persist the session URL, draft and exact uncertain request;
after a network failure retry the same body/UUID rather than creating another
session or changing a possibly committed answer.

Identical original typed text/option-ID retries return current state, including
after Next/completion. Changed submissions conflict even if their normalized text
would match. Wrong-modality, missing/both fields, unknown/foreign options and
blank answers are rejected without attempts. Foreign/future questions return
STALE_QUESTION; closed sessions reject new outcomes. Reads never award progress.
Reveal retries and acknowledged Next/complete retries are safe. Answer-vs-reveal
races have exactly one winning outcome.

## Independent progress and scheduling

`GET /progress?datasetId=...&exerciseType=TYPED&person=1sg` returns stored rows;
filters are optional. Missing row means NEW. Each row is uniquely keyed by
**internal verb ID, person and exercise type**. 2sg and 3sg remain separate even
when their strings match; all namespaces and other learning modes are independent.

Reuse SpacedRepetitionScheduler and its injected UTC clock: correct intervals
1, 3, 7 days, then doubling up to 365. Incorrect/revealed outcomes schedule a
one-day retry and reset the streak. The next correct after failure restarts at
one day. Reveal increments that exercise row's total attempts but not correct or
successful-review counts. Reveals are excluded from answer accuracy and never
award independent-recall credit. This matches the existing aspect reveal policy.

Choice success can meet mastery criteria only for its recognition row (existing
defaults: five consecutive successes and interval at least 28 days). It never
advances typed recall or another person. No existing vocabulary/sentence/aspect
schedule or existing global/aspect statistics calculation changes.

## Persistence, locks and migration

Flyway **V10__aorist_practice** is a Java migration because V9's two-column
progress uniqueness was unnamed. It discovers exactly that constraint by its
columns, adds exercise_type default TYPED to existing rows, replaces uniqueness
with `(verb_id,person,exercise_type)` and creates session/question/attempt tables.
Existing progress IDs, counters, dates, content and receipts are retained; no
applied SQL migration is edited. Keep Java migrations immutable after applying,
just like SQL migrations. Hibernate validates the resulting schema.

Questions persist selected verb/person/order, full original content/hash and
snapshot version plus ordered option UUIDs/text inside private snapshot JSON.
No correctness key is sent to clients; grading uses the snapshotted target array.
Edits, selected reimports or archives cannot change an ongoing session. Submitted
text/option, outcome, reveal timestamp, acknowledgement, position/status and
timestamps persist. Options are never regenerated on restart.

Session-row locks serialize action/read state. Stable verb locks coordinate
selection/content edits and serialize per-target progress writes across sessions.
Practice does not acquire a dataset lock after taking a verb lock; management
keeps its dataset→verb lock order. Unique start UUIDs, question position and
session/verb/person keys, one attempt per question and three-part progress keys
backstop application locking. Attempt, counters, progress, schedule and reveal
commit together or roll back together. A start UUID race resumes the winning
session in a fresh transaction after the failed insertion rolls back.

The existing offline H2 backup procedure includes all new tables. Content JSON
export still omits practice history, progress and sessions; it is not a backup.
Restore/upgrade only stopped compatible copies, and retain matching app/H2/schema
versions. There is no automatic downgrade or automatic learning import.

## Verification

Use JDK 21 and `.\mvnw.cmd clean verify`. All database tests use the test profile
with isolated memory databases or JUnit temporary files, never the personal DB.
Coverage includes every supplied verb/person answer, required negatives, reflexive
scope, Unicode distinctions, alternative arrays, syncretic forms, reduced choices,
privacy, settings/queues/shortages, retries/races, rollback, independent schedules,
editing/archiving, migration of populated V9 progress and repeated application
context restarts for both exercise types. Live HTTP/restart checks use a unique
disposable `target/` database and a separate loopback port.

On Windows, after building and setting JAVA_HOME to JDK 21, run
`.\scripts\verify-aorist-practice.ps1` (Node with built-in fetch/File/FormData is
required; verified with Node 24.22.1). It creates a unique `target/` database,
starts a hidden test server on port 18090 (`-Port` overrides), runs the HTTP
checks, stops/restarts its own JVM on the same disposable file and checks recovery,
then stops its server. It rejects normal port 8080 and never opens the personal DB.
The optional underlying Node test is skipped without AORIST_PRACTICE_BASE_URL;
that skip is not verification. `training.aorist.random-seed` is optional for
repeatable tests and should remain unset in normal use.

Executed 2026-11-17:

| Check | Result |
| --- | --- |
| Maven Wrapper 3.9.16, Temurin JDK 21.0.13: `.\mvnw.cmd -B clean verify` | 247 tests passed, zero failures/errors/skips; compilation and executable JAR packaging passed. |
| New practice tests | 37 integration cases (including all 240 supplied person answers), one populated V9 migration case, and two file-database recovery cases, one per exercise type; all passed. |
| `node --test src/test/js/pronunciation.test.mjs` | All nine existing JavaScript regressions passed. |
| `node --check src/test/js/aorist-practice-http.test.mjs` | Passed. |
| `.\scripts\verify-aorist-practice.ps1` | Initial live HTTP flow and fresh-JVM recovery both passed, zero skips. |
| Original learning file | SHA-256 remains `AF92873C06A36C81973206CC24808D01E1E9CC2CDACCE66225F92F095C521E33`. |
| Applied migrations and build stack | V1–V9, POM and Maven Wrapper unchanged. V10 is new; no default-branch merge. |

Live HTTP coverage includes importing all 40, default аз/typed settings, all-six
availability, explicit short offer, early skip, separate prompts, protected typed
answers/unmarked choices, stress/case/whitespace, exact retries and feedback until
Next. It then explicitly reimports changed forms, archives all verbs and restarts
the JVM: original content/options, both unfinished sessions and submissions remain;
wrong-answer/reveal results, one-day schedules, exactly-once progress and unchanged
legacy statistics are checked after restart. The test server is stopped afterwards.
Logs/state/disposable databases are under ignored `target/`.

No browser verification is claimed: this task adds backend behavior only.
The personal database and Android repository were not used or changed. Existing
non-failing H2/Flyway verified-version and Mockito dynamic-agent warnings remain;
dependencies were not changed to suppress them. The supplied material remains
learning content with provenance claims, not independently reviewed linguistics.
