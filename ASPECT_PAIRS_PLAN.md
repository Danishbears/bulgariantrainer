# Verb Aspect Pairs: web implementation plan

Status: planning only, 2026-10-30. No application implementation is included.
Repository inspected: `Danishbears/bulgariantrainer`, local HEAD `de0a3e8`.
Planning branch: `codex/aspect-pairs-plan`; do not merge into the default branch.

## 1. Scope and authority

The requested feature is Bulgarian aspect-pair recall: show a Bulgarian citation
form and ask for its counterpart, for example `купувам → купя` and
`купя → купувам`. It is neither conjugation by person nor sentence translation.

The user's request and repository `AGENTS.md` govern this work. The attached guide
supplies feature/data guidance; its Android Room, platform file-picker, and Android
backup instructions do not apply. Do not access or change any Android repository.
Keep Java 21, Spring Boot 4.1.1, Maven wrapper, MVC, JPA, Flyway, file-mode H2,
HTML/CSS, and vanilla JavaScript ES modules. No new dependency is currently needed.
Use constructor injection and controller → service → repository boundaries, DTO
records, same-origin JSON, Russian UI messages, and UTF-8.

This document proposes future implementation. Do not apply migrations, import data,
or change application code during this planning task. Import must remain explicitly
initiated and confirmed by the user; nothing is seeded at startup.

## 2. Inspection findings and reusable components

Read `AGENTS.md`, `README.md`, `TYPING.md`, `FLASHCARDS.md`, `SENTENCES.md`,
`HELP.md`, the POM, application/test configuration, both supplied attachments,
and the relevant services, entities, repositories, controllers, DTOs, frontend
modules, migrations V1–V6, and existing test coverage.

| Existing component | Actual behavior and proposed reuse |
| --- | --- |
| `Word`, `Translation`, `WordService` | Words have unique normalized Bulgarian spelling, multiple Russian translations, archive state, and protected history. A pair must be its own entity; do not create two vocabulary words or use translations as aspect answers. |
| Flyway V1–V6 | H2 schema is migration-owned; Hibernate validates it. Add V7 (or the next unused version at implementation time); never edit applied SQL or recreate the database. |
| `TypingService`, `TypingQuestion`, `StudySessionRepository` | Persist ordered immutable questions, exact answer retry identity, feedback acknowledgement, start UUIDs, session-row locks, and atomic answer/progress writes. Reuse these state-machine conventions, not vocabulary-specific entities or direction enums. |
| `SentencePracticeService`, `SentenceQuestion` | Snapshot structured alternatives and explanation; own progress table demonstrates independent learning history. Do not reuse its pending/self-assessment grading: aspect answers are exact accepted-list membership. |
| `TypingQuestionPlanner` | Generic synchronized shuffle can be reused. Its translation directions and 30-word policy are unsuitable. Keep any aspect seed/configuration independent; avoid a broad planner refactor. |
| `VocabularyText` | NFC, Unicode whitespace collapse, and `Locale.ROOT` lowercase; accents remain significant. Preserve this behavior and add an aspect-specific answer normalizer. |
| `SpacedRepetitionScheduler` | Injected UTC `Clock`; success intervals 1, 3, 7, then doubling to 365 days; failure retries in 1 day. Category precedence is NEW, then DUE, then MASTERED/LEARNING. Reuse `now`, `intervalFor`, `eligible`, `priority`; add small aspect-specific `record`/`category` overloads. |
| Word/sentence import services and writers | Multipart bounded UTF-8 uploads, read-only preview, explicit confirmation, transactional writes, per-row summaries, and exact retry patterns. Existing parsers are TXT/CSV, not this JSON format. |
| `ImportPreviewStore`, sentence pending map | Five previews, 30-minute in-memory lifetime; lost on restart. Word DTOs make the existing store unsuitable as-is. Use an aspect-specific bounded preview store without building an unrelated generic framework. |
| `StatisticsService`, `StudyAttemptRepository` | Existing overall activity/accuracy uses all shared study attempts and sessions, while retention explicitly uses vocabulary schedules. Separate aspect history prevents accidental changes to those totals and word mastery. |
| `layout.js`, `styles.css`, homepage | Shared Russian navigation, skip link, focus treatment, mobile menu, cards and responsive layout. Add one section link and homepage card using these conventions. |
| `typing.js`, `sentence-import.js` | Explicit busy/pending state, exact retry, feedback focus, URL recovery and Russian error mapping. Adapt their patterns in a small new ES module; do not rewrite the frontend. |
| Existing JUnit/Boot tests | Typing tests cover concurrency, stale questions, snapshots, directional schedules and idempotency; imports cover rollback/forgery; temporary-file migration tests cover upgrades and reopen durability. Extend these patterns. |

Important constraints in actual code: `StudyAttempt` requires exactly one word or
sentence and uses `BG_TO_RU`/`RU_TO_BG`; `study_progress` requires a word; shared
session SQL permits only FLASHCARD/TYPING/SENTENCE. Reusing those records directly
would require invasive changes and misrepresent Bulgarian-to-Bulgarian recall.

Documentation drift: `TYPING.md` and `FLASHCARDS.md` describe earlier milestones
without scheduling/mastery. V6 and current services already schedule reviews;
typing now stores `TYPING_BG_TO_RU` and `TYPING_RU_TO_BG` progress separately.
Use current code as the implementation baseline and correct these statements when
updating feature documentation. No web structured backup/import-backup API exists;
README documents copying the stopped H2 file.

## 3. Entire supplied JSON: format and content audit

Inputs read in full:

- `C:\Users\Danish\Downloads\aspect_pairs_app_guide.md`.
- `C:\Users\Danish\Downloads\bulgarian_aspect_pairs_100.json`.
- JSON SHA-256: `A6354B52AEC47626A9EB32FFA9548BC79DDD67931B072364DA9E68409D80E2B6`.

Observed root: `format="bulgariantrainer.aspect-pairs"`, integer `formatVersion=1`,
`language="bg"`, `translationLanguage="ru"`, `createdOn="2026-10-30"`,
`entryForm="1st_person_singular_present_citation_form"`, `sources`,
`editorialStatus`, and `entries`. Preserve this extra root metadata, which the
guide's brief format description does not enumerate.

| Check | Actual data |
| --- | --- |
| Entries/identity | 100 unique external IDs, `bgasp-001` through `bgasp-100` |
| Provenance | 56 `presentation` entries referencing `lesson13`, table rows 1–56; 44 `added` entries |
| Stage/relation | 77 stage 1/core; 17 stage 2/resultative; 6 stage 3/bounded |
| Topics | 12: Быт 30, Восприятие 2, Движение 15, Действия 9, Досуг 3, Еда 3, Общение 13, Покупки 5, Работа 1, Самочувствие 1, Техника 3, Учёба 15 |
| Required study text | All IDs, canonical forms, Russian meanings and topics are nonblank strings |
| Accepted arrays | Both sides nonempty for all entries; every canonical form belongs to its own accepted array |
| Extra accepted answers | 12 imperfective arrays contain two answers; all perfective arrays contain one (112 imperfective answers, 100 perfective answers) |
| Examples | 16 entries have both imperfective and perfective BG/RU examples: 32 sentences, not independent training entries |
| Notes | Empty strings occur and are valid; notes cannot be treated as mandatory nonblank text |
| Vocabulary links | No internal vocabulary IDs are supplied |

The guide matches the data on these structural checks. Stage and relation happen
to correspond exactly in this file; retain them as separate concepts. `studyStage`
is not CEFR and `topic` is not part of speech. Russian semicolons are meaning text,
not delimiters for generating new cards. Provenance belongs to the edited entry,
not necessarily every form in its accepted arrays.

Notable entries to preserve exactly: 020 `обличам се/облека се`; 021
`опитвам се/опитам се` versus 076 `опитвам/опитам`; 043 reverse accepts
`чета` and `прочитам`; 010 reverse accepts `пея` and `изпявам`; 047
`сядам/седна` excludes `седя`; 050 `ставам/стана` excludes `стоя`; 066
`спомням си/спомня си` retains `си`; 086/087 distinguish return-object and
return-self. Neither spellings nor accepted answers should be inferred globally.

Missing from the attachments: the referenced TXT with relation definitions and the
original presentation contents. Structural compliance does not establish linguistic
correctness; the supplied editorial status explicitly disclaims independent review.
The linked presentation is provenance, not a dependency to fetch during import.

## 4. Storage and migration design

Use the same H2 database at `./data/bulgariantrainer`, retaining loopback binding,
`WRITE_DELAY=0`, Flyway cleaning disabled and `ddl-auto=validate`. Add dedicated
entities/repositories and tables under the existing base package:

| Table | Proposed contents and constraints |
| --- | --- |
| `aspect_datasets` | Internal identity; unique explicit dataset key; format/version; original root metadata JSON; latest content SHA-256; timestamps. Dataset revision is distinct from schema/app/format version. |
| `aspect_pairs` | Internal ID; dataset FK; immutable external ID; both canonical forms and Russian meanings; topic; stage CHECK 1–3; relation CHECK core/resultative/bounded; note; provenance JSON; optional examples JSON; content hash; row version; timestamps. UNIQUE(dataset_id, external_id). |
| `aspect_pair_answers` | Pair FK; side IMPERFECTIVE/PERFECTIVE; display text; normalized key; position. UNIQUE(pair_id, side, normalized_key), UNIQUE(pair_id, side, position); nonblank text. JPA-owned collection or simple child entity. |
| `aspect_progress` | Pair FK and actual direction; UNIQUE(pair_id, direction); attempt/correct counts, streak, lifetime successful reviews, last/next review instants, interval. Nonnegative/bounded counters and due index. No word/sentence progress FK. |
| `aspect_sessions` | Unique start UUID; canonical settings JSON/fingerprint; requested size; actual total; direction mode; study scope; stage/topic/dataset filters; status; correct count; start/end timestamps. |
| `aspect_questions` | Session/pair FKs; position; actual direction; complete immutable content snapshot (forms, accepted arrays, meanings, note, examples, relation, content hash); acknowledgement. UNIQUE(session_id, position), UNIQUE(session_id, pair_id). |
| `aspect_attempts` | Unique question FK; original user answer (nullable on reveal); outcome CORRECT/INCORRECT/REVEALED; server timestamp. Direction/pair/session come from the immutable question, never client input. |
| `aspect_import_receipts` | Unique confirm request UUID; canonical request hash; committed summary and dataset metadata/content hash. Store atomically with import so a lost response can be recovered after restart. |

Use VARCHAR limits aligned with validation: external/dataset IDs 100, forms and
answers 255, Russian meanings 1000, topic 255, note 4000; at most 50 accepted
answers per side. Bound metadata/snapshot JSON through the upload and DTO limits;
use H2 CLOB where appropriate, not newline-delimited encoding for structured data.
No global unique constraint on a verb spelling: different senses and reflexive
entries may share forms. Restrict deletion of referenced pairs/datasets; no hard
delete UI is required. Import never deletes entries omitted from a later file.

Questions store snapshots because reimport may update the live pair. Existing
questions/results remain evaluated against the saved accepted arrays, not the
latest import. Reimport preserves internal identity and progress (see revision
ambiguity below). Do not manufacture migration rows for this dataset.

Optional vocabulary linking is deferred from the first implementation: it is not
necessary for recall and the guide makes it optional. If added subsequently, use
explicit side-specific nullable word FKs with a user-selected real internal ID,
update hard-delete guards, and preserve links on reimport. No substring matching,
automatic word creation, invented IDs, or schedule propagation.

## 5. Separate JSON import and repeat-import semantics

Implement `AspectPairImportController`, `AspectPairImportService`,
`AspectPairImportParser`, a transactional writer, DTOs and a bounded preview store.
Use the existing Jackson 3 `tools.jackson` mapper. Keep word and sentence imports
unchanged. Browser upload uses an ordinary HTML file input and multipart form.

1. Select/create an explicit dataset namespace. Suggested key for this attachment:
   `bulgarian-aspect-pairs-100`. It is an application import choice, not a field
   claimed to exist in v1. Never derive identity from filename, date, or source URL.
2. Accept UTF-8 JSON, optional leading BOM, at most the existing 1 MiB upload limit;
   reject malformed UTF-8, malformed/trailing JSON, duplicate object keys, wrong
   types and unsupported format/version. Do not coerce string stages or versions.
3. Validate the whole file before any write: supported language/entryForm,
   nonempty entries (proposed maximum 1000), unique nonblank external IDs,
   required text/lengths, stages 1–3, supported relations, nonempty string answer
   arrays, canonical answer membership after aspect normalization, provenance
   kind/source references and positive table rows, and complete BG/RU example
   objects when provided. `note=""` and absent examples are valid. Validate
   normalized duplicate/empty answers explicitly. Unsupported/unknown fields
   should produce field-path errors rather than silently discarding content.
4. Preview is read-only. Return indexed entries and external IDs, validation
   details, dataset metadata, and classifications ADD / UPDATE / UNCHANGED.
   Show old/new field differences and counts; same-spelling different IDs are a
   review warning, never an automatic merge. A malformed/invalid file is blocked
   as a whole, including duplicate IDs even if a row would be deselected.
5. User selects rows and explicitly confirms updates. Initial import can select
   valid additions; changed existing entries require deliberate selection.
   Keep v1 preview content read-only for simplicity: corrections happen in the
   source file and require a new preview, rather than introducing an editor now.
6. Confirm carries `importId`, new UUID `requestId`, selected external IDs and
   previewed row versions. Server retrieves parsed content; client cannot supply
   arbitrary replacement data or forged IDs. Lock dataset and existing pairs in
   stable ID order, recheck versions and uniqueness, then commit all selected
   updates/additions and the receipt together. Stale data returns 409 and requires
   a fresh preview; never silently overwrite concurrent imports.
7. Same dataset/external ID and unchanged content is skipped; changed content is
   updated in place. Return per-entry internal IDs and CREATED / UPDATED /
   UNCHANGED / DESELECTED plus totals. Import does not reset progress, overwrite
   snapshots, modify vocabulary, or remove entries absent from the file.
8. Before checking preview expiry, consult the durable receipt for an exact
   confirm retry. Same UUID/body returns its saved result; different body returns
   409. Uncommitted previews expire after 30 minutes or restart (410). Persist
   frozen confirm body locally; keep controls locked after uncertain failures.
   Retry never doubles rows or changes the reported original summary.

Store original root/source/editorial metadata and per-import content hashes;
partial selection must not imply that the whole file was imported. Keep the
provided attachments untouched. A checked-in fixture under test resources may be
added at implementation time; production must not depend on a Downloads path.

## 6. Answer validation

Add `AspectAnswerNormalizer`, used on the server for both input and the selected
snapshot's accepted array. Do not change `VocabularyText` or existing typing tests.
Apply NFC first, remove remaining U+0301/U+0300 combining stress marks, lowercase
with `Locale.ROOT`, trim/collapse Unicode whitespace, then return NFC. Original
answer and display spellings remain stored verbatim. Reject normalization-empty
answers and answers longer than 4000 before evaluation.

Preserve `се`/`си`, particle order, punctuation and all other letters. No Latin
transliteration, lookalike replacement, stemming, substring/fuzzy acceptance,
automatic synonyms, or person conversion. NFC first preserves composed `ѝ` and
`й` (including their canonically decomposed input); never strip all Unicode marks
or turn `ѝ` into `и`. See the precomposed-accent ambiguity below.

For IMPERFECTIVE_TO_PERFECTIVE compare only acceptedPerfective; for
PERFECTIVE_TO_IMPERFECTIVE compare only acceptedImperfective, using full equality.
Never automatically union forms from other entries. On a mismatch say
`Не совпало с ответами этой карточки`, not that the word is nonexistent.

After a committed answer, show both forms, both Russian meanings, accepted target
answers, note and optional examples. Before answering/revealing, omit target forms,
accepted arrays, answer-bearing notes and examples from the question DTO.
Provide the target Russian meaning in the prompt, particularly for resultative and
bounded entries. This distinguishes, for example, `говоря → поговоря` from `кажа`.

## 7. Session, progress and scheduler behavior

Introduce `AspectDirection` with IMPERFECTIVE_TO_PERFECTIVE and
PERFECTIVE_TO_IMPERFECTIVE, and a separate session setting MIXED. Reuse `StudyScope`
NEW/DUE/MIXED and `SessionStatus` concepts; do not overload translation directions.

- Defaults: stage 1 only, all topics within the chosen dataset, mixed direction,
  mixed study scope, 20 questions; allow 1–100. Stages 2 and 3 require explicit
  selection with Russian meaning explanations. Return the actual shorter size
  before start; zero eligible entries returns 409 without creating a session.
- Eligibility is per (pair, actual direction). NEW means no finalized practice
  attempt; DUE uses that direction's due instant. MIXED scope prioritizes due,
  then new, learning and mastered, using the existing scheduler ordering.
- One pair appears at most once per session. Mixed direction balances as far as
  eligibility permits; never choose an ineligible reverse direction just to force
  equal counts. Shuffle ties; persist the chosen order/directions once. Expose
  available counts by direction and actual planned totals honestly.
- Start UUID plus normalized complete settings is idempotent; changed settings
  with the same UUID conflict. A unique-key race must reload the committed
  existing session in a fresh transaction and compare settings.
- State flow: unanswered → saved CORRECT/INCORRECT or REVEALED → feedback visible
  → explicit acknowledgement → next question. Last finalized outcome closes the
  session but preserves its feedback until acknowledged. No answer editing after
  commitment; one attempt per question.
- Provide a reveal action instead of a partial-answer hint in v1. Reveal creates
  a terminal REVEALED outcome, exposes feedback, awards no success, and cannot be
  followed by a scored answer. Repeating reveal is safe. Do not label it correct.
- Incorrect answers update only this direction's attempt count, reset its streak,
  and schedule the existing one-day retry; they never increase correct/lifetime
  success counts. Reveal likewise records a failed recall for scheduling, with
  its own outcome/count in results, so revealing cannot leave a mastered item
  untouched or earn credit. Reverse progress is unchanged.
- Lock the session first, then the pair row for progress updates; the pair lock
  serializes first-row creation and concurrent updates across sessions. Save
  outcome, progress, session counts and feedback state in one transaction.
- Retry identity uses the exact original answer, as existing typing does; a
  different original string conflicts even if normalization would match.
  Stale/future/foreign questions conflict; closed sessions reject new attempts.
- Early completion preserves attempts and marks the session ABANDONED if any
  questions remain. Show unattempted questions as SKIPPED in results without
  inventing attempts or modifying their schedules. Acknowledgement/completion
  and exact answer retries stay safe after completion.
- Store session ID in `?session=ID` and use a separate localStorage key
  `bulgariantrainer.aspect-pairs` for session and exact pending request. Restore
  authoritative queue, reveal state, feedback, counters and results after reload
  and server restart. Local storage is optional; URL recovery remains available.

Implement small scheduler overloads for `AspectProgress` using the existing
interval formula and mastery configuration (currently five successes, 28-day
interval plus a sufficient streak). Reuse classification thresholds exactly;
test direction independence and exact due boundaries. Do not copy vocabulary
history into pairs or write to word/sentence progress, even if optional links are
introduced later. No background worker is needed: existing scheduling is stored
review timing used when building queues.

## 8. Proposed HTTP API

Use record DTOs, Bean Validation and the existing `ApiError` envelope/global
handler, adding aspect exception mapping. All paths are same-origin.

| Method/path | Contract |
| --- | --- |
| `GET /api/aspect-pairs/datasets` | Dataset choices and source metadata |
| `GET /api/aspect-pairs` | Paginated stored entries; dataset, stage and topic filters; no fabricated data |
| `GET /api/aspect-pairs/topics?datasetId=...` | Distinct topic filter choices |
| `GET /api/aspect-pairs/availability` | Dataset, stages, topic, direction, scope and size → eligible counts/offered size |
| `POST /api/aspect-pair-imports/preview` | Multipart file plus explicit dataset key; read-only preview with `importId`, expiry and differences |
| `POST /api/aspect-pair-imports/confirm` | UUID request ID, preview ID, selected external IDs and expected versions → saved summary |
| `POST /api/aspect-pair-practice/sessions` | UUID request ID and full settings → 201 plus Location and session state |
| `GET /api/aspect-pair-practice/sessions/{id}/question` | Authoritative current state, prompt and optional saved feedback |
| `POST /api/aspect-pair-practice/sessions/{id}/answers` | `{questionId, answer}` → state after exact evaluation |
| `POST /api/aspect-pair-practice/sessions/{id}/reveal` | `{questionId}` → terminal uncredited reveal feedback |
| `POST /api/aspect-pair-practice/sessions/{id}/next` | `{questionId}` → acknowledge feedback, return next state |
| `POST /api/aspect-pair-practice/sessions/{id}/complete` | End early or return already closed state |
| `GET /api/aspect-pair-practice/sessions/{id}/results` | Closed-session snapshots, answers, outcomes and direction totals |
| `GET /api/aspect-pairs/progress?datasetId=...` | Separate directional counts, due/learning/mastered counts, accuracy and reveal count |

Lists use existing zero-based pagination with size 1–100. Internal IDs must be
positive and distinct from external `bgasp-*` IDs. Validate all query settings as
strictly as start settings. Use 400 for malformed/invalid content, 404 for missing
session/dataset, 409 for conflicts/empty queues, 410 for expired uncommitted
previews, 413 for size limits, and 415 for unsupported file type. Unknown format
versions receive an explicit UNSUPPORTED_FORMAT_VERSION code. Frontend maps every
new code to Russian; never display English API errors or raw enum names.

## 9. UI and statistics

Add `/aspect-pairs.html`, its small ES module and page CSS only if shared CSS is
insufficient. Add `Видовые пары` to `layout.js` and the homepage practice grid.
Keep import within this section (link/panel), visibly separate from word/sentence
CSV imports. No frontend framework, database replacement or new build pipeline.

Show empty/import, loading, failure/retry, settings, question/feedback and results
states. Stage 1 is initially selected; stages 2/3 are deliberate choices. Provide
topic and direction filters, and independent new/due/mixed study choices.
Example prompt: `купувам — покупать. Напиши совершенный вид со значением
«купить»`. Input is Bulgarian; explanation and actions are Russian. Notes and
examples appear after evaluation/reveal, with `lang="bg"` for Bulgarian text.
Examples remain explanatory text, never sentence-translation questions.

Use Enter for answer submission, visible next/reveal/retry buttons, labels,
aria-live status, busy controls and predictable focus. Never let a double Enter
submit both an answer and the next question. On network uncertainty freeze edits
and retry the exact operation; retain typed answers/import confirmation across
reload. Render imported text with `textContent`, not HTML. Tables/cards must fit
a narrow viewport, with keyboard-accessible scrolling when needed.

Show directional progress and session results inside the new section. Typed
accuracy is correct/(correct + incorrect); show reveals separately and use an
undefined/dash accuracy when there are no typed answers. Success/streak/mastery
never includes reveals. Existing `/api/statistics`, activity streaks, vocabulary
counts, due totals and difficult-word rankings stay unchanged because aspect
sessions/attempts are separate. A later explicitly requested aggregate overview
can add a clearly labelled aspect block without blending vocabulary retention.
TTS is optional and omitted from initial scope; it is not an answer validator.

## 10. Correctness ambiguities and proposed decisions

| Ambiguity | Consequence and proposed resolution |
| --- | --- |
| No dataset ID or revision in v1 | `formatVersion` is a schema version, `createdOn` is a date, and `lesson13` describes only one source. Require an explicit stable import dataset key and store file/content hashes as revisions. Never infer a semantic version. A future file-format change can introduce declared dataset identity/version. |
| Updating a pair's meaning or accepted forms | Preserving progress may carry learning credit to changed content. For v1 preserve identity/history/schedules, warn prominently on changes, and keep snapshots immutable. If the meaning is a new learning item, require a new external ID. Do not silently reset or migrate credit. |
| Stress removal versus preserving `ѝ` | NFC composes decomposed `и` + grave to `ѝ`; deleting marks before NFC would corrupt it. Follow the specified NFC-first order and preserve `и/й/ѝ`. Other precomposed grave letters (e.g. `ѐ`) are not removed by literal combining-mark deletion; broader accent equivalence would need an explicit rule and tests. None of the supplied answers requires that extension. |
| Reveal/hint scheduling unspecified | Guide prohibits awarding correct credit but does not specify failure timing. Proposed v1 terminal reveal counts as failed recall for one-day retry and streak reset; track REVEALED separately and exclude it from typed accuracy. No partial hint feature. |
| Mixed direction and size unspecified | Proposed default 20 distinct pairs, one direction per pair, balanced where eligible. Do not force 30-word vocabulary typing rules or ask both sides of a pair in one session. |
| Relation definitions reference missing TXT | Preserve supplied values and notes. Explain core as basic pairing, resultative as a stated result, bounded as a limited action; do not claim these are complete linguistic definitions or infer acceptable synonyms. |
| Guide says full backup but gives Android workflow | Web uses a stopped-file H2 backup. The new tables are included in that same file automatically; no Android/structured backup feature is proposed. Test copy/reopen durability in a disposable database. |
| Optional vocabulary links | Defer them; JSON provides none. If later requested, explicit selection and FK guards are required, with separate per-side links and unchanged vocabulary progress. |
| Unknown-field/duplicate-answer handling unspecified | Proposed strict v1 validation rejects unknown keys and normalized duplicate answers with paths, rather than silently losing metadata or collapsing inconsistent input. Empty notes remain valid. |

These are reviewable design decisions, not claims that the attachments specify
them. Dataset identity, update policy, Unicode normalization and reveal semantics
must be fixed in the API/tests before implementation is considered complete.

## 11. Implementation order and meaningful verification

1. Add the v1 fixture, parser/normalizer and their focused tests; settle the above
   contracts. Preserve supplied source bytes and metadata.
2. Add the next migration, entities/repositories, constraints and upgrade/reopen
   tests. Update `MigrationPersistenceTests` assertions currently hard-coding
   migration counts (V1→latest currently expects five migrations).
3. Implement preview/confirm and durable receipts. Verify rollback and concurrent
   repeat import before any UI writes are connected.
4. Implement independent progress, scheduler overloads and the durable practice
   state machine/API, including snapshots, locks and exact retries.
5. Add the Russian section/import/progress UI, navigation and homepage entry;
   verify API failures and browser recovery with disposable data.
6. Update README and add `ASPECT_PAIRS.md` with import schema, namespace choice,
   API, defaults, retry behavior, normalization, independent statistics and web
   backup instructions. Correct stale scheduler statements in existing practice
   docs without changing those modes' behavior.

Required test coverage:

- Parser: exact supplied 100 entries and metadata; 56/44 provenance; 77/17/6
  stages; all 16 example pairs and 212 answers preserved; invalid UTF-8/JSON,
  format/version/types, duplicate keys/IDs, missing text, empty/non-string arrays,
  canonical exclusion, invalid provenance/examples, bounds and unknown fields.
- Normalizer/evaluation: both купувам/купя directions; прочета accepts чета and
  прочитам; изпея accepts пея and изпявам; exact reflexivity including 020/021/076;
  си in 066; седя rejected for 047 and стоя rejected for 050; Unicode case,
  whitespace, stress, NFC/decomposition; distinguish и/й/ѝ, Latin lookalikes,
  ъ/а, fragments, wrong direction and answers from another entry.
- Import integration (`@ActiveProfiles("test")`, isolated H2): preview writes
  nothing; first confirm creates 100; second import creates zero; selective
  update preserves IDs/history; invalid file or stale/forged confirm rolls back
  all writes; concurrent imports do not duplicate; lost-response/restart exact
  retries return the original receipt, changed retries conflict.
- Practice integration: stage-1 default excludes stages 2/3; topic filters;
  direction-specific eligibility; mixed size/shortage/empty queues; persisted
  order; no answer leaks; mismatch, reveal, final feedback, next and early end;
  reload/restart at every state; snapshot correctness after reimport; stale,
  foreign and closed submissions; same/different start UUID payloads; concurrent
  answer/reveal/complete races; cross-session progress updates and reverse-side
  independence. Verify new mode leaves every legacy progress/statistics value
  unchanged, including accuracy, due counts and activity.
- Scheduler: injected fixed clock, 1/3/7/doubling cap, exact due instant,
  failure/reveal resets, mastery thresholds and independent schedules.
- Migration/persistence: upgrade a populated V6 disposable file database; preserve
  all existing rows/checksums; verify new FKs/uniqueness/counters, reopen a running
  aspect session, and copy a stopped file to restore pairs/history/receipts.
- Browser: import supplied file through preview/confirm; both recall directions,
  extra accepted forms, stage explanations, reveal/results, reload and server
  restart. Stop server before/after submission to test uncertain retry without
  duplicate credit. Check Cyrillic, Tab/Enter/Escape, focus/live messages,
  unavailable localStorage and narrow layouts. Never use personal database data.

Before implementation delivery, use JDK 21 and run `.\mvnw.cmd clean verify`.
Start the app with an explicit disposable database URL under `./target/` and
`WRITE_DELAY=0`, then verify affected endpoints over real HTTP and perform the
browser checks above. No tests or smoke imports may target `./data/bulgariantrainer`.
Do not install libraries or change the POM merely for this feature. Keep work on
a feature branch and do not merge into the default branch.

## 12. Planning-task verification record

Source inspection and the complete JSON structural audit were performed without
opening the personal H2 database or importing any learning entries. Application
code, configuration, supplied attachments and applied migrations are unchanged.

- Ran `.\mvnw.cmd clean verify`: BUILD SUCCESS, 103 tests, zero failures,
  errors or skipped tests. Maven compiled with release 21, but the installed
  runtime/compiler is Oracle JDK 25.0.2. JDK 21 was not found in the inspected
  Java installation locations; execution on JDK 21 remains unverified.
- Started the packaged application on loopback port 18084 with an explicit
  isolated in-memory H2 datasource. Real HTTP checks passed: `/api/health`
  returned UP, `/api/typing/availability` returned an empty eligible queue,
  `/api/statistics` returned successfully, and `/` returned HTTP 200. The smoke
  application was stopped afterwards. No data was imported.
- Git review confirmed that the only new workspace source/document file is
  `ASPECT_PAIRS_PLAN.md`, on `codex/aspect-pairs-plan`; no commit, push or merge
  was performed. Maven output and smoke logs are generated under ignored `target/`.
- The proposed new API/UI cannot be exercised until implementation. No new-feature
  tests or browser verification are claimed for this documentation-only change.
