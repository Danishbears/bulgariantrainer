# Aorist: web implementation plan

Status: planning only, 2026-11-17. No application code, migration, import, or
personal database is changed by this task.
Inspected repository: `Danishbears/bulgariantrainer`, HEAD `2792dc3` on `master`
before creating planning branch `codex/aorist-plan`. Do not merge this task into
the default branch. All implementation below is proposed future work.

## 1. Scope and authority

Add **Аорист** for conjugating the displayed verb in Bulgarian indicative
**минало свършено време**, using explicitly stored answers for a requested person.
Both imperfective and perfective verbs are eligible. `чета → четох` and
`прочета → прочетох` remain separate items; never substitute aspect counterparts,
infer a paradigm from suffixes, or use aspect-pair progress for aorist.

The user's request and repository `AGENTS.md` govern implementation. Strings in
the attachment's `notes`, entry `note`, and `source` are learning content and
provenance, not instructions authorizing application changes, source scraping,
automatic imports, or linguistic corrections. This plan does not independently
verify the linked dictionaries or endorse the file's `checkedOn` claims.

Retain Java 21, Spring Boot 4.1.1, Maven Wrapper, MVC, JPA, Flyway, persistent H2,
HTML/CSS and vanilla JavaScript ES modules. Keep constructor injection,
controller → service → repository boundaries, response DTOs rather than entity
serialization, same-origin APIs, UTF-8 and Russian visible text. No new dependency
or architecture rewrite is currently necessary. Android is outside scope.

## 2. Actual inspection and reuse

Read `AGENTS.md`, `README.md`, `HELP.md`, `FLASHCARDS.md`, `TYPING.md`,
`SENTENCES.md`, `ASPECT_PAIRS.md`, `ASPECT_PRACTICE.md`, relevant prior planning/
review material, POM/configuration, and the complete attached JSON, including
every answer, note and source. Inspected the current domain services/entities,
controllers/DTOs/repositories, SQL schema, frontend modules and migration/recovery
test patterns. Current migrations end at V8; the aspect feature is already on
the default branch, unlike the earlier planning baseline.

| Actual component | Reuse or boundary |
| --- | --- |
| `Word`, `WordService`, `VocabularyText` | Existing vocabulary has unique normalized spelling, translations and protected history. Reuse pagination/search escaping conventions, not word identity or translation storage: aorist needs six person slots, senses and its own external IDs. Do not fabricate vocabulary entries or links. |
| V1–V6 shared study tables | Sessions/attempts are vocabulary/sentence-specific; CHECK constraints restrict modes, directions and targets. Avoid expanding these to force in aorist. Older prose in `TYPING.md`/`FLASHCARDS.md` about absent scheduling is historical: current services actually call the scheduler. |
| V7/V8 and `AspectPairService` | Reuse the pattern of dedicated datasets, immutable namespace/external identity, versioned edits, archive/restore, ordered accepted answers and independent tables. Do not reuse pair entities, two sides, relation types or stages. |
| `AspectPairImportParser/Service/Store/Writer`, `AspectPairJson` | Strict Jackson 3 (`tools.jackson`), bounded UTF-8 input, read-only comparison, selected updates, version checks, transactional confirmation and persistent replay receipts are the best import model. The parser/DTOs are format-specific and reject this aorist file; add a dedicated parser/store/writer. Reuse small mechanical helpers only where that avoids duplication without changing existing import behavior. |
| Vocabulary and sentence imports | Separate TXT/CSV routes and preview stores; do not route aorist through them or alter their duplicate semantics. Their in-memory confirmation behavior is weaker than durable aspect receipts. |
| `AspectPracticeService`, `TypingService`, question repositories | Adapt durable queues, immutable snapshots, session-row locks, exact retries, explicit feedback acknowledgement and atomic attempt/progress writes. Add aorist entities/services; no aspect directions or translation grading. |
| `AspectQuestionPlanner` | Its bounded balancing algorithm is specifically two-direction/one-pair. A six-person planner needs its own implementation and tests. Reuse the deterministic-seed/random-generator testing approach, not the planner as-is. |
| `SpacedRepetitionScheduler` | Reuse injected UTC clock, `intervalFor`, `eligible`, `priority` and small new aorist-specific `record`/`category` overloads. Existing intervals are 1, 3, 7, then doubling to 365 days; failure is 1 day. NEW precedes DUE, then mastery/learning. MIXED currently permits future learning/mastered items too, ordered after due/new. |
| `AspectAnswerNormalizer` | Its NFC-first, stress-mark/case/whitespace behavior is appropriate. It is package-private and named for aspects: introduce a small aorist normalizer with the same tested rules, or extract only that pure helper after regression tests; leave vocabulary/sentence normalization unchanged. |
| `StatisticsService`, `AspectStatisticsService` | Legacy totals read shared study tables; aspect totals read separate tables. Add separate aorist statistics with typed/choice breakdown; do not mix them into either existing total or mastery. |
| `layout.js`, `styles.css`, aspect management/import/practice modules | Reuse shared navigation, green/blue cards, labels, status/error/focus treatment, URL/localStorage recovery, uncertain-request freezing and responsive patterns. `aspect-common.js` contains pair-specific error/content/DOM assumptions and is not a generic module to import wholesale. |
| `pronunciation.js` | Optional explicit Bulgarian playback already exists. Its target-selection helpers are mode-specific. Aorist can initially omit TTS; any later adapter must withhold answer playback until feedback/reveal and tolerate missing voices. |
| `AspectFeatureUpgradeTests`, recovery/browser scripts | Extend the temporary-file migration/restart/copy approach and optional real Chrome flow; never run against `data/bulgariantrainer`. No multiple-choice backend/UI exists to reuse. |

No full application backup/restore or learning JSON export endpoint currently
exists. The actual backup procedure is a stopped H2 file copy; see section 11.

## 3. Attachment: verified shape and findings

Read `C:\Users\Danish\Downloads\bulgarian_aorist_40.json` completely and parsed it
read-only with Node. SHA-256 of original bytes:
`AF92873C06A36C81973206CC24808D01E1E9CC2CDACCE66225F92F095C521E33`.

Root fields are exactly:

```text
format: "bulgariantrainer.aorist"
formatVersion: 1
datasetId: "bg-aorist-core-40"
datasetRevision: 1
createdOn: "2026-11-17"
language: "bg"
translationLanguage: "ru"
tense: "aorist"
entryForm: "first_person_singular_present_dictionary_form"
notes: string[5]
entries: object[40]
```

Every entry has `id`, `lemma`, `russian`, `aspect`, `tense`,
`reflexiveParticle`, `forms`, `note`, `source`, `studyGroup`.
`forms` has exactly `1sg`, `2sg`, `3sg`, `1pl`, `2pl`, `3pl`, each a nonempty
string array. `source` has `title`, `url`, `section`, `checkedOn`.
The attachment contains:

- 40 unique external IDs (`bgaor-001` through `bgaor-040`), 240 person slots and
  240 answer strings: presently one answer per slot, although arrays permit
  alternatives. Structural checks found no missing required strings/persons,
  invalid aspect/tense values or repeated external IDs.
- 11 imperfective and 29 perfective verbs. Sources are labelled БЕРОН (35) and
  Речник Читанка (5), all with `checkedOn=2026-11-17`.
- Groups: `1_ah` (2), `2_ih` (9), `3_ah_stem` (9), `4_oh` (13),
  `5_short_or_changed` (6), `6_yah` (1). These are labels, not CEFR levels,
  ordered study stages or executable conjugation rules.
- Two reflexive entries: `облека се` and `съблека се`; every stored answer
  includes final `се`. Other entries have explicit null `reflexiveParticle`.
  No `си` examples, stress-marked variants, or multi-answer slots are supplied;
  tests will need synthetic fixtures for these cases.
- All 40 entries have identical `2sg` and `3sg` arrays. Some answers also equal
  the displayed lemma (`започна`, `стана`, etc.); this is valid stored content,
  not an answer leak caused by serialization. No gender-specific aorist slots.
- No `studyStage`, `topic`, examples, explicit mood field or counterpart IDs.
  Do not invent these or infer them from aspect-pair data. Indicative is the
  feature's fixed scope, not a missing field to insert into the original file.

Concrete discrepancy: `bgaor-009` (`вземам`) has group `2_ih` but stores
`вземах/взема/...` and explains its distinction from `взема → взех`.
Preserve all fields; flag the group mismatch for content review. Do not change
it to another group or to the perfective paradigm during import.

## 4. Storage and migration

Add V9 management/import storage and V10 practice/history storage, or the next
unused versions if other migrations land first. Never edit V1–V8. Use JPA
entities, repositories and service transactions with Hibernate validation.

| Proposed table/entity | Fields and constraints |
| --- | --- |
| `aorist_datasets` / `AoristDataset` | Numeric internal ID; immutable unique namespace equal to root `datasetId`; original root metadata JSON without entries; last-confirmed source `dataset_revision`; SHA-256 last-import file hash; optimistic row version; timestamps. Revision, file hash, format version and DB row version are different concepts. |
| `aorist_verbs` / `AoristVerb` | Internal ID, dataset FK, immutable external ID, lemma, Russian meaning, aspect, fixed tense, nullable particle, study group, note, structured source JSON, search text, content hash, archived flag, optimistic version, timestamps; UNIQUE(dataset_id, external_id). No uniqueness on lemma: homographs/senses and different namespaces may coexist. |
| `aorist_forms` / `AoristForm` | Internal ID, verb FK, person enum, positive array position, answer text, normalized key; UNIQUE(verb_id, person, position) and UNIQUE(verb_id, person, normalized_key). Keep ordering; first answer is the display form, not the only accepted form. Each verb must have all six nonempty slots, enforced transactionally. |
| `aorist_progress` / `AoristProgress` | UNIQUE(verb_id, person); typed total/correct/success/streak counts, last/next review times and interval, following `AspectProgress` invariants. Missing row means NEW. No merging by spelling, gender or equal-looking 2sg/3sg answers. |
| `aorist_import_receipts` | Unique request UUID and import UUID, dataset FK, request hash, original file hash, root metadata, immutable selected outcomes/summary and timestamp. |
| `aorist_sessions` | Unique start UUID, canonical settings JSON, format/planner version, status, size/current position/correct counters and timestamps. |
| `aorist_questions` | Session/verb FKs, position, person, immutable learning snapshot/hash, acknowledgement and reveal timestamp. UNIQUE(session_id, position) and UNIQUE(session_id, verb_id) under the proposed once-per-verb policy. |
| `aorist_choices` | Question FK, opaque option UUID, persisted display order and answer text, internal correctness flag; unique option ID/order per question. Typed questions have no choices. Never serialize correctness before grading. |
| `aorist_attempts` | UNIQUE(question_id), original typed text OR selected option reference OR reveal, outcome CORRECT/INCORRECT/REVEALED, timestamp. CHECK the valid submission shape; no synthetic attempts for skipped questions. |

Add indexes for active dataset/group/aspect listings, person/due progress and
session/question history. Use foreign keys without history-deleting cascades;
only replace current form children during edits. Proposed bounds: IDs/namespace
100, lemma/forms 255, Russian meaning 1000, group 100, note 4000, source title/
section 1000, URL 2048, up to 50 alternatives/person. Bound root notes and entry
count (1–1000) with the existing 1 MiB upload limit. Validate values before writes.

Preserve source fields and root notes losslessly as content. Editing, archiving,
restoring and selected reimport retain internal verb IDs and all history. Existing
sessions use their original snapshots. A genuinely different sense needs a new
external ID; edits do not silently reset learning credit. No hard-delete API.

## 5. Management API

Use `AoristVerbController → AoristVerbService → repositories`. DTO records and
the existing `ApiError` envelope; specific codes mapped to Russian in the UI.

- `GET /api/aorist-verbs`: optional `datasetId`, `q`, `aspect`, `studyGroup`,
  `reflexiveParticle`, `archived=false`, `page=0`, `size=20` (1–100). Combine
  filters before pagination, sort by internal ID, return `PageResponse`.
  Search lemma, Russian, external ID and explicit forms, using escaped literal
  substring semantics. `reflexiveParticle=none` means null; `се`/`си` mean exact
  particle. Search normalization does not rewrite displayed text.
- `GET /api/aorist-verbs/{id}`: complete editable content, ID/version/archive
  metadata, all six arrays and provenance, including archived records.
- `GET /api/aorist-verbs/datasets` and `/filters`: real dataset/group suggestions.
- `POST /api/aorist-verbs`: `{datasetId, entry}` (201 + Location). Creating a
  namespace also requires its validated v1 root metadata; do not manufacture a
  source revision/date for a manually created dataset. UI can default to an
  existing imported dataset; a new dataset dialog collects required metadata.
- `PUT /api/aorist-verbs/{id}`: `{datasetId, version, entry}`; complete replacement
  of learning fields. Dataset/external ID immutable; unchanged content is a no-op.
- `PATCH /api/aorist-verbs/{id}/archive`: `{version, archived}`; archive/restore.

Use dataset locks followed by verb locks in stable ID order for management/import;
stale versions return 409 and a reload action. Creation must reserve archived IDs
too. Reuse lost-save recovery by identity/version/content, including searching
all pages if recovery uses a paginated list. No controller accesses repositories.

## 6. Dedicated JSON import and export

Implement `/api/aorist-imports/preview` (multipart file) and `/confirm` (JSON),
separate from all existing formats. Namespace comes from explicit root
`datasetId`; show it for confirmation rather than silently renaming datasets.

1. Select a UTF-8 JSON file (BOM allowed). Bound bytes before parsing. Reject
   malformed UTF-8/JSON, duplicate object keys, trailing documents, unknown fields,
   wrong primitive types/coercions, unsupported format/version and empty entries.
2. Require the exact root contract in section 3, positive integral
   `datasetRevision`, valid ISO dates, Bulgarian/Russian language constants and
   this file's exact `entryForm`. Validate all entries, unique nonblank external
   IDs, allowed aspects, entry/root `tense=aorist`, all and only six person keys,
   nonempty string answer arrays without normalized duplicates, metadata/source
   structure and HTTP(S) source URL syntax. Never fetch source URLs at import.
   Particle is null/`се`/`си`; when present, require the lemma and each isolated
   accepted form to end in that separate particle token. Reject contradictory
   metadata; never append/remove/reorder particles to repair an answer.
3. Read-only repeatable-read comparison produces ADD/UNCHANGED/UPDATE, before/after
   content, internal IDs/versions, errors and group/revision warnings. Validate
   the entire document: errors prohibit all confirmation, including valid rows.
4. Require explicit confirmation with import UUID, request UUID and selected
   external IDs/expected versions. Additions may be selected initially; updates
   must start unselected. No preview editing that bypasses server validation.
5. Lock/recheck dataset and selected verbs against preview, then apply all selected
   changes plus the receipt transactionally. Stale edits/archive/import/create
   cause 409 and fresh preview. Reimport never duplicates, removes omitted verbs,
   restores archives or resets progress. Preserve IDs and creation timestamps.
6. Replay identical confirmed UUID/body from durable receipts before consulting
   the preview cache, including after restart; changed body conflicts. Catch
   concurrent first-creation/receipt races and resolve from a fresh transaction
   or report an explicit conflict. Uncommitted previews: five cached, 30 minutes,
   lost at restart, then 410 requiring re-upload.

`formatVersion` is parser schema, `datasetRevision` is supplied source revision,
file hash is exact bytes, verb content hash detects edits, and `version` controls
database concurrency. Retain metadata/revision/hash on each receipt. A dataset's
last-import revision does not imply every row was selected or that local edits
match that source release. Preview lower revisions as an explicit downgrade
warning requiring a separate confirmation flag; same-revision/different-hash
uploads also warn and require selected updates, not silent replacement. Higher
revision never authorizes automatic updates.

Add `GET /api/aorist-verbs/export?datasetId=...&archived=false` as a downloaded
UTF-8 JSON document with the same v1 contract and all six ordered arrays/source
fields. Export one dataset at a time in a read-only consistent transaction,
sorted by external ID; no page-size truncation. Preserve its source root metadata,
including revision/date/notes, and explain that this is a current edited material
snapshot under the last imported source revision, not a newly published revision.
Do not silently increment or reinterpret source revision. Use a safe filename and
`Content-Disposition: attachment`; show count/archive scope before downloading.
Offer `archived=true` for archived-only, or an explicit `archiveScope=all` for
both; reject contradictory scope parameters. Empty scopes return a clear error
rather than a non-importable zero-entry file. Re-import exported material must
round-trip learning content. V1 has no archive/progress/session fields: export
cannot restore those, and importing into another DB creates active entries.

During implementation preserve the original file byte-for-byte under
`learning-materials/aorist/bulgarian_aorist_40.json` with a small provenance README.
Do not depend on Downloads, mutate the attachment or auto-import at startup.

## 7. Typed and multiple-choice practice

New `AoristPracticeService` with `AoristQuestionPlanner`, separate mode enum
`TYPED`/`MULTIPLE_CHOICE` and six concrete person values. Proposed defaults:
20 questions, typed, all six persons, both aspects, all study groups, MIXED queue.
Filters include dataset, group, aspect, selected nonempty person list, size 1–100,
queue NEW/DUE/MIXED and explicit `allowShorter`.

Question unit: one verb + one person. Proposed first release uses each verb only
once per session, so revealing its full paradigm cannot spoil a later question
for that verb. Consequently the supplied dataset offers at most 40 questions per
session, not 240. Across sessions choose eligible persons with scheduler priority
and balance selected persons as evenly as feasible without violating scope or
verb uniqueness. Use a tested six-person assignment (small capacity/min-cost
matching or bounded assignment), random tie-breaking and frozen output; avoid a
greedy rule that strands person-exclusive eligible items. Report achieved person
counts; impossible exact balance is not an error. Return a shorter offer before
start; never pad with repeated verbs. Availability is advisory, rechecked on start.

Example prompt:

```text
чета — читать
Аорист · минало свършено време · изъявительное наклонение
Напиши форму для «аз» (1-е лицо, единственное число).
```

Person labels: `аз`, `ти`, `той/тя/то`, `ние`, `вие`, `те`. Use verb Russian
meaning for disambiguation; do not invent person/tense translations. The answer
field requests just the stored form, without pronoun, auxiliary or sentence.

Typed evaluation is server-side membership in the requested snapshot person
array only: NFC first, remove remaining combining U+0301/U+0300, lowercase with
Locale.ROOT, trim/collapse Unicode whitespace. NFC before mark removal preserves
`й` and `ѝ`, including decomposed equivalents. Preserve `и/й/ѝ`, particles,
prefixes, spelling and punctuation; no fuzzy matching, substrings, Latin lookalike
substitution, pronoun stripping or clitic reordering. Wrong-answer text:
**«Не совпало с ответами этой карточки»**, without a claim that the form cannot
exist. Never accept an aspect counterpart merely because it is related.

Multiple choice uses the same requested person and accepted snapshot. Persist
up to four randomized, normalized-distinct options: exactly one target accepted
answer, plus distractors drawn only from explicitly stored aorist forms of other
persons of that same verb. Exclude *every* target alternative from distractors,
not just the displayed correct option. In this dataset 2sg/3sg equality therefore
creates no duplicate/ambiguous choice. Never generate wrong endings, use an
unverified form, or include source labels that identify correctness. If fewer
than four distinct choices exist, show 2–3 with a clear count; with fewer than two,
mark that verb/person unavailable for choice practice and offer typed practice.
Do not silently change modality or borrow unrelated aspect counterparts.

Only `{optionId, text}` for the current choice question is public; send no
correctness flag, accepted array, correct index, explanatory note, paradigm,
source link or other questions before submission/reveal. The correct candidate
is necessarily visible among choice options: hidden-answer policy means its
identity is unmarked, not that all candidate text is secret. Opaque random IDs
must not encode correctness. Typed questions send no choices or target forms.
Legitimate lemma/answer identity is retained; do not hide the requested lemma.

After grading/reveal show selected/original response, accepted alternatives,
correct person form, all six forms, note, aspect, group and provenance from the
snapshot; then explicit **Дальше**. Early-result skipped items retain no feedback.
Reveal is terminal, never correct and never followed by a credited answer.

## 8. Progress and statistics policy

Canonical scheduled progress is keyed by **internal verb ID + person**, isolated
from vocabulary, flashcards, sentences and aspect pairs. Equal 2sg/3sg strings
still have different counters/schedules. Proposed conservative modality policy:

- Typed CORRECT/INCORRECT and typed REVEALED update this row using the existing
  interval algorithm. Reveal counts as failed recall for scheduling: total +1,
  correct unchanged, streak reset, next review in one day. Skips do not update.
- Choice attempts are saved and graded, with separate recognition statistics,
  but do not advance or reset typed-recall scheduling/mastery. This avoids a
  guessed option earning independent-recall credit. Choice NEW/DUE filters refer
  to the canonical typed queue and the UI labels this explicitly. A choice-only
  learner may still have NEW typed items after successful choice sessions.
- Do not silently aggregate typed and choice success percentages. Add
  `/api/aorist-practice/statistics` with per-modality answered/correct/incorrect/
  revealed, per-person breakdown, active due verb-person count and distinct due
  verbs (NEW excluded). Historical counts include archived items; due excludes
  them. Preserve existing `/api/statistics` and aspect statistics semantics.

This modality policy is a proposal, not a user-specified requirement; see section
12 before implementing schedules. A different policy needs explicit documentation
and tests rather than treating recognition as typed recall by accident.

## 9. Durable API, concurrency and recovery

API base `/api/aorist-practice`:

- `GET /availability` with canonical settings returns eligible verbs/slots,
  per-person counts, offered size, shorter-required and choice-unavailable counts.
- `POST /sessions`: request UUID + settings, 201/Location; identical canonical
  settings/UUID resume the existing session, changed settings conflict. Concurrent
  UUID collisions recover the committed winner from a fresh transaction.
- `GET /sessions?status=IN_PROGRESS&page=0&size=20`: resumable session summaries
  without queued answers, so recovery need not depend on one browser shortcut.
- `GET /sessions/{id}/question`: authoritative position/status/counters/settings,
  safe current prompt/options and feedback only when that question was finalized.
- `POST /sessions/{id}/answers`: `{questionId, answer}` for typed OR
  `{questionId, optionId}` for choice; reject both/neither and wrong modality,
  unknown options or an option belonging to another question. Score on server.
- `POST /sessions/{id}/reveal`, `/next`: `{questionId}`. Next acknowledges saved
  feedback; it cannot submit or reveal implicitly.
- `POST /sessions/{id}/complete`: idempotent early finish; keep attempts, mark
  unanswered items skipped in results without creating learning attempts.
- `GET /sessions/{id}/results`: closed-session counts, mode-labelled accuracy,
  mistake/reveal/skipped review. Results while active return 409.
- `GET /progress`: paginated/filterable verb/person progress and category.

Persist settings, exact chosen verb/person/order, immutable full content/source
snapshots, option text/IDs/order/correctness, original submission, outcome, reveal,
acknowledgement, current position and session status. Never regenerate choices,
reshuffle or evaluate against freshly edited forms after restart. Snapshots must
include distractors as well as accepted answers. Last feedback survives final
submission until Next, even if all questions are already finalized.

Serialize actions/reads by session-row lock. Lock target verbs in stable ID order
for queue snapshots and serialize progress creation/updates by the verb lock;
retain UNIQUE(question_id) attempts and UNIQUE(verb_id, person) progress as DB
backstops. Management takes dataset then verb locks; practice never takes a
dataset lock after a verb, avoiding an inverse lock order. Attempt, progress,
reveal state and counters commit together. Exact original answer/option retries
are safe after advancement/completion; a different payload conflicts, even if
normalization yields the same answer. Reveal-vs-answer races have one winner.
Read-only views do not update schedules. Archived verbs cannot enter new sessions;
already queued snapshots may finish and retain history.

Browser uses dedicated `bulgariantrainer.aorist.*` keys and `?session=ID`, preserving
unsubmitted drafts and the exact pending action/UUID/payload until resolved.
After uncertainty freeze conflicting actions and provide **Повторить запрос**.
Without localStorage, URL and server list still resume sessions; show a storage
notice rather than blocking practice. Protect double-clicks/held keys and ignore
stale async responses. Keep drafts after network errors and navigate explicitly.

## 10. Russian web interface

Add `/aorist.html`, shared navigation entry **Аорист** and homepage card, page CSS
on top of `styles.css`, and small `aorist-*.js` modules for management, import/
export, practice and statistics. Follow existing hash-view conventions.

- **Глаголы**: search/filter/page, archive/restore, details, six labelled person
  arrays editable one alternative per line, source/note/group/aspect/particle.
  Display numeric DB ID separately from external ID/dataset; preserve versions.
- **Импорт / экспорт**: file selection, root identity/revision/notes, validation
  errors with field paths, additions/unchanged/updates with before/after,
  explicit update selections, revision warnings, transactional summary/retry;
  dataset/archive-scope export controls and clear material-only export notice.
- **Практика**: typed/choice toggle, size, persons, aspect/group/dataset, queue,
  availability and explicit shorter offer, unfinished-session list. Do not offer
  aspect directions, study stages or conjugation-generation settings.
- Typed input with **Проверить**, **Показать ответ**, then **Дальше**; Enter submits
  then continues after feedback. Choice uses labelled radio group and an explicit
  check button; arrow keys select without submitting, Enter checks/continues.
  Show mode-specific results and review, never convert revealed answers to correct.
- **Статистика**: separate typed/recognition results and clearly labelled due
  verbs versus due person slots, with reload/error/empty states.
- **Как заниматься**: aorist is a tense, not perfective aspect; both aspects can
  form it; these are indicative forms; citation lemma is present first-person
  singular; 3sg covers той/тя/то without gender inflection; reflexive particles
  are meaningful and supplied answers use isolated postverbal position; group
  labels are aids rather than universal rules. Explain imperfect/imperfective
  terminology and that this drills forms, not full contextual tense selection.

Use textContent, `lang=bg`, labelled controls, fieldsets, visible focus,
aria-live status and accessible error focus. Cards/forms wrap at 320/360px with
no horizontal page scroll; do not use a mandatory wide six-column paradigm.
Pending/loading/empty/error states retain data and expose retry. No automatic
audio; optional explicit playback uses only already public text and never blocks
practice if a Bulgarian voice is absent.

## 11. Backup and migration compatibility

There is no full backup/restore API to extend. Do not introduce one as part of
this feature. Existing procedure: stop the application normally, ensure the DB
is not open elsewhere, copy `data/bulgariantrainer.mv.db` (or the actual overridden
database file) and retain matching application/H2/schema version information.
Restore only while stopped, keeping the current file first; start one instance.
The same file will include all aorist content, sessions, options, attempts,
progress and receipts. Preserve original learning files separately. JSON export
is learning material only, not backup/restore; browser drafts and uncommitted
in-memory previews are outside the database backup.

Flyway upgrades a compatible V8 backup in place with new additive migrations.
No automatic downgrade or arbitrary H2-version compatibility is promised.
Rollback needs the matching pre-upgrade backup/application; post-backup learning
will not exist in that older file. Never clear or rebaseline the user's DB to
make a migration pass. Test these paths only on isolated synthetic fixtures.

## 12. Correctness ambiguities and proposed decisions

| Open issue | Proposed decision / implementation consequence |
| --- | --- |
| `вземам` group conflicts with its stored model | Preserve `2_ih`; nonblocking warning and editorial follow-up. Groups never generate/validate suffixes. |
| Does size count verbs or all verb-person slots? | First-release proposal: one verb/person per question, one use of each verb/session, maximum 40 for this dataset. If future work requests multiple persons per verb, change uniqueness, prevent feedback from exposing future answers and retest scheduling/session selection. |
| Does recognition affect recall scheduling? | Proposed typed-only canonical schedule with separate choice attempt statistics (section 8). Confirm product policy before implementing that behavior; do not silently choose shared mastery or add a mode dimension contrary to the requested verb/person key. |
| How many options and which distractors? | Proposed 4, reduced to 2–3 when necessary, from same-verb explicit forms only. Exclude all target alternatives; no fabricated grammatical errors. A one-distinct-form custom paradigm is typed-only. |
| Revision handling after partial imports/manual edits | Source revision is provenance, not an immutable whole-DB content version. Keep hashes/receipts; selected updates and downgrade warnings are explicit. Export retains source revision with a visible edited-snapshot explanation. Publishing a new official revision is outside this plan. |
| Particle ordering | Match only explicit isolated answers; neither generate pronouns nor accept a reordering unless listed. Future `си` fixtures follow the same contract; this file supplies only `се`. |
| Stress and orthographic distinctions | Adopt existing aspect normalization; NFC-first keeps `ѝ` distinct from `и`. If accent-insensitive matching for precomposed grave letters is later requested, that needs a separate decision and tests. |
| Linguistic verification and permitted variation | The file has provenance labels/dates, not independent review. Do not certify correctness, broaden accepted alternatives from guesses, silently fix group/content errors, or treat a rejected answer as nonexistent Bulgarian. Source verification/licensing/editorial expansion is separate work. |

These are recorded proposals, not extra requirements attributed to the attachment.
No clarification is needed to deliver this planning-only task; schedule/repetition
policy should be settled when implementation is requested.

## 13. Implementation sequence and verification

1. Preserve original material and document the v1 contract/decisions; add storage,
   additive migration(s), management APIs and tests for six arrays and history.
2. Add strict preview/confirm/receipt flow and material export; test round trips,
   invalid documents, concurrency and reimport before adding practice.
3. Implement person planner, snapshots, typed evaluation, terminal reveal,
   retries/recovery and canonical schedules; then persisted choice generation/
   evaluation and separate statistics.
4. Add Russian UI/navigation/help and documentation. Exercise full browser flows
   and network-failure/retry behavior on disposable data.

Meaningful test coverage:

- Import all 40 entries with every field/root metadata, 240 answers, exact ordered round-trip,
  alternative-answer fixture, two aspects, reflexives and group discrepancy.
  Reject malformed UTF-8/JSON, duplicate keys/IDs, unknown fields, trailing JSON,
  version coercions/unsupported format, invalid revisions/dates/URLs/particles,
  missing/extra persons, scalar/empty/blank/duplicate answer arrays. Compare DB
  before/after failures including progress and receipt counts.
- Reimport creates no duplicates; explicit selected update preserves internal IDs,
  archive/history/progress; stale preview, changed root revision, new-ID race,
  forged selections, expired cache and exact receipt replay after restart.
  Export all rows beyond a page; active/archive/all scopes, multi-answer order,
  edited-source-revision notice, no progress fields and re-import round-trip.
- Typed positives `чета/1sg → четох`, `прочета/1sg → прочетох`,
  `пиша → писах`, `взема → взех`, `вземам → вземах`,
  `вляза/1pl → влязохме`, `облека се → облякох се`, plus all supplied
  verb/person accepted values. Reject counterpart replacement, imperfect/participle
  answers where not listed, wrong person, omitted particles, fragments and Latin
  lookalikes. Test stress, case, Unicode whitespace, `и/й/ѝ`, decompositions and
  synthetic `си`/multiple-alternative cases. Do not reject valid syncretic forms.
- Choice options are distinct and have exactly one accepted candidate; 2sg/3sg
  syncretism and multiple target alternatives never create a second correct
  option. Test reduced/insufficient choices, forged/foreign option IDs, frozen
  option order/text after edits/restart and absence of answer-key leaks.
- Planner tests on constrained eligible pools: all/single person, unequal due
  pools, both aspects, groups, archive exclusion, maximum coverage, feasible
  balance, no repeated verb and explicit shortage. Deterministic seed tests.
- State-machine tests: normal/early completion, final feedback then Next, reveal
  no credit, exact/different retries, start/answer/reveal races, concurrent
  progress creation and updates, skipped no progress, direction/mode isolation,
  exact due boundaries and typed-vs-choice policy. No answer leak in question,
  settings, errors, resumable-session lists, skipped results or playback data.
- Temporary file/context and actual JVM restart tests: pending confirmation,
  queue/person/options/snapshots, answered-but-unacknowledged feedback, reveal
  and idempotent resubmission. Compare session/attempt/progress counts.
- Upgrade a populated V8 fixture containing vocabulary, sentences and aspect
  sessions/history; assert old tables unchanged and old modes still resume.
  Test stopped-file copy/restore before and after adding aorist, Flyway repeat
  validation, Cyrillic and constraints. No startup auto-import.
- Real browser flow: import → list/edit → export/reimport → typed and choice
  launch → submission → reload → explicit resume → results; malformed file,
  archive/restore, offline/lost-response retries, drafts, Enter/held keys/radio
  keyboard behavior, unavailable storage/voice, Russian text, 320/360px layouts,
  and unchanged existing pages/modes/statistics.

Future implementation acceptance commands: JDK 21, `.\mvnw.cmd --version`,
`.\mvnw.cmd clean verify`, existing Node regressions and new aorist tests,
then start the packaged app on a separate loopback port with a unique H2 database
under ignored `target/` and `WRITE_DELAY=0`. Verify affected REST APIs over HTTP
and browser flows; adapt `scripts/verify-aspect-pairs.ps1` into a separate aorist
script rather than replacing it. Report executed checks/skips honestly.

For this planning task, only repository/data inspection, read-only JSON structural
checks and documentation diff checks are applicable. No new backend/browser
behavior has been implemented or verified, and prior feature test results must
not be presented as tests of aorist.
