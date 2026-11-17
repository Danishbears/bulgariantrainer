# Aorist integration and review

## Launch and import

Use JDK 21 and the Maven Wrapper from the WEB repository root:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Open `http://127.0.0.1:8080/aorist.html`, or choose **Аорист** in navigation.
Under **Импорт и экспорт**, choose
`learning-materials/aorist/bulgarian_aorist_40.json`, validate the file, review
the preview and explicitly confirm the selected entries. Nothing imports on
startup. The supplied dataset contains 40 verbs and 240 person slots. Reimport
is idempotent; updates are initially unselected and require explicit selection.
Export is content-only and must not be used as a progress backup.

## Statistics

Open **Аорист → Статистика** (`/aorist.html#statistics`).
`GET /api/aorist-practice/statistics` returns a consistent read with `asOf`
and two `exercises`: `TYPED` and `MULTIPLE_CHOICE`. Optional `datasetId` filters
the external dataset namespace. Invalid namespaces return 400; an unknown valid
namespace returns zero counts. Each exercise includes all six persons in fixed
dictionary order, even when empty.

- `answered = correct + incorrect`: persisted independent submissions for that
  exercise type. Choice success is recognition, not typed-recall mastery.
- `revealed`: separate terminal outcomes, never credited as correct or included
  in answer accuracy. Skips have no attempt and do not enter these counts.
- `dueForReview`: active verb/person schedules whose due time has arrived,
  using the existing scheduler. Future schedules, new items and archives are
  excluded. The boundary is inclusive at `nextReviewAt`.
- `dueVerbs`: distinct active verbs represented by those due questions, within
  that exercise type. One verb may have several due persons.
- History includes submissions from unfinished/early-ended sessions and archived
  or edited entries. It counts attempts, not distinct questions across sessions.

The UI shows separate totals, accuracy and six-person tables for each type.
Existing vocabulary, typing, flashcard, sentence and aspect statistics are not
altered. A link from the general statistics page makes the separation explicit.
Refresh failures retain and label the last successful result; a changed filter
is not claimed to apply to that retained result.

## Practice and answer contract

Typed recall and **аз** are defaults. Selected/all six persons, aspect, teaching
group, size and new/due/mixed queues are available. Questions identify the lemma,
Russian meaning, aorist tense and person separately. No counterpart replacement
or suffix generation is used. See [AORIST_PRACTICE.md](AORIST_PRACTICE.md) for API
contracts, independent progress and scheduling.

Answers must match an explicitly stored variant for the exact verb/person.
Normalization uses NFC, optional combining stress removal, lowercase and
collapsed Unicode whitespace. It preserves и/й/ѝ, spelling and particles;
there is no fuzzy, substring or Latin-lookalike matching. The scope is an
isolated form without subject pronouns: `облякох се`. A sentence such as
`Аз се облякох` is outside this exercise; the UI never constructs
`Аз облякох се` by concatenating the person label with the answer.

Choices use stored forms, normalized deduplication and exactly one target
variant. Fewer options are stated explicitly; when choices are unavailable the
learner can select typed practice. A mismatch means wrong for this card, not
impossible Bulgarian. Revealing records one non-correct outcome, resets the
streak and schedules that person/type for one day, just like an unsuccessful
recall. Separate Next, durable snapshots and transactional idempotent submission
keep retries from recording extra attempts or schedule updates.

Before answer/reveal the practice API excludes target arrays, notes, source
metadata and tables. Multiple-choice options have no correct marker. A full
paradigm remains withheld while another unanswered question for the same verb
is queued. Management/export APIs intentionally allow studying content; this
local learning tool is not an examination security boundary.

## Format, sources and limitations

See [AORIST_CONTENT.md](AORIST_CONTENT.md) for the strict v1 format, import
validation, scoped external identity, metadata, transaction and stale-preview
rules. Format version, dataset revision and Flyway schema version are distinct.
`studyGroup` is a dataset teaching label, not CEFR or a universal suffix class.
No reviewed sentence dataset is supplied; there are no sentence-gap exercises.
Both imperfective and perfective verbs have independently stored aorist items;
aorist is a tense, and does not universally assert a completed result.

The repository copy is byte-identical to the supplied JSON (SHA-256
`AF92873C06A36C81973206CC24808D01E1E9CC2CDACCE66225F92F095C521E33`).
Each entry retains its supplied source title, URL, section and checked date.
These are source claims from the learning material, not independent linguistic
verification by this project.

Exact editorial discrepancy retained: `bg-aorist-core-40` / `bgaor-009`, lemma
`вземам`, has teaching label `2_ih`, but its explicit аз form is `вземах`
and its note distinguishes it from `взема → взех`. Its supplied source is
[БЕРОН](https://beron.mon.bg/leksema/4bc27df5dfa88eb5c03fba3089bb25a1),
with checked date `2026-11-17`. This is evidence of an internal label/model
discrepancy in the supplied file, not a claim that the dictionary contradicts
the stored forms. Import reports `GROUP_REVIEW`; all forms and labels remain
unchanged. Dictionary pages have not been independently reviewed in this task.

## Backup and migration

There is no full application backup/restore API. Do not introduce a separate
backup format. Stop the application normally and ensure no other instance uses
the database, then copy `data/bulgariantrainer.mv.db` (or the actual configured
database file) to a safe location. Record the matching application, H2 and
Flyway versions; retain material files separately. Restore only while stopped,
keeping the current database copy first. The closed-file copy includes all
content, stable IDs, sessions, snapshot options/settings, attempts, import
receipts, progress and schedules across modes. Browser drafts and unconfirmed
in-memory previews are not database backups.

V8→V9/V10 and populated V9→V10 upgrades are tested on isolated file databases;
old content/progress and existing aorist IDs/counters/dates survive. Existing
migrations are not edited. Statistics require no new migration. Rollback needs
the matching older application and pre-upgrade backup; there is no automatic
downgrade, history merge or arbitrary H2-version conversion. Never clear,
baseline or reset a personal database to fix a migration.

## Verification record

Executed on 2026-11-17 with Temurin JDK 21.0.13, Maven Wrapper 3.9.16 and Node
24.22.1. Tests used the `test` profile with isolated memory or temporary file
databases; packaged-app tests used unique ignored `target/` database paths and
loopback ports 18087/18089/18090/18092. The personal database was not used.

| Executed check | Result |
| --- | --- |
| `.\mvnw.cmd -B -Dtest=AoristPracticeIntegrationTests,AoristPracticeRecoveryTests test` | 42 tests passed after correcting a test-clock assertion; zero failures/errors/skips. |
| `.\mvnw.cmd -B clean verify` | **251 tests passed**, zero failures/errors/skips; executable JAR packaged. |
| `node --check` for each of the six `src/main/resources/static/js/aorist*.js` modules | All passed. |
| `node --test src/test/js/pronunciation.test.mjs` | 9 tests passed; zero failures/skips. |
| `.\scripts\verify-aorist-content.ps1` | Both initial and restarted-JVM HTTP phases passed. |
| `.\scripts\verify-aorist-practice.ps1` | Both initial and restarted-JVM HTTP phases passed, including separate persisted statistics. |
| `.\scripts\verify-aspect-pairs.ps1` | Existing real Chrome browser and actual JVM-restart regression passed; legacy-mode isolation passed. |
| `git diff --check` | Passed. |
| Original and repository `Get-FileHash` | Identical SHA-256 as recorded above. |

The JVM integration suite includes these specific requirements:

| Requirement | Evidence |
| --- | --- |
| Previous schema, no data loss | `AoristContentPersistenceTests`: populated V8→V10, vocabulary/aspect progress unchanged, old backup upgrade; `AoristPracticeMigrationTests`: populated V9→V10 preserving progress IDs/counters/dates and new type uniqueness. |
| All material, reimport and export/import | `AoristContentIntegrationTests`: all 40 entries/240 person slots and metadata round-trip; repeat import, scoped IDs, alternatives, no auto-import. |
| Invalid files and explicit updates | Content integration: invalid JSON/UTF-8/version/duplicate IDs/person slots/metadata leave populated DB snapshots unchanged; selected updates only; stale previews, receipt replay/concurrency and SQL-failure transactional rollback. |
| Answer rules and all persons | Practice integration: full 240-question pool, required чета/прочета, взема/вземам, видя, изляза, reflexives, alternatives, stress/case/Unicode whitespace, и/й/ѝ and Latin-lookalike/subject rejection. |
| Choice uniqueness and privacy | Practice integration: exactly one accepted option, normalized alternatives excluded as distractors, reduced/unavailable pools; safe question/list/results and guarded explanation endpoints before answer/reveal. |
| Atomicity, retries and reveal | Practice integration: duplicate/concurrent starts/submissions/reveals, failed SQL rollback, zero correct credit after reveal, separate Next and early ending. |
| Independent progress and history | Practice integration: verb/person/type separation and shared scheduler due/mastery boundaries; edits/archive/reimport preserve IDs, snapshots and counters. Statistics tests retain archive history while excluding archives from due counts. |
| Durable recovery and backup | `AoristPracticeRecoveryTests`: both exercise types across closed/reopened contexts; same question order/options/snapshots/feedback/settings and exact retry; stopped-file copy reopened with attempts, archive state, progress, results and statistics. |
| Existing modes | Complete Maven suite includes vocabulary, flashcard, typed translation, sentences, scheduler and aspect-pair tests; existing aspect browser/HTTP restart smoke also passed. |

Actual final browser checks used the ChatGPT in-app browser against the packaged
JAR and a fresh disposable file database:

- Empty statistics showed two independent exercise types and all six persons.
- Uploaded and confirmed the original file through the UI: 40 created, then
  list reported 40. Opened settings for a two-question `1_ah` typed session.
- The prompt contained source/meaning/tense/person and no target table/note.
  Enter accepted stressed uppercase `СЛУ́ШАХ`; refresh restored feedback.
- Separate Next opened гледам. Stopping only the owned test JVM before submitting
  `гледах` produced a connection error and retained the exact input. Restarted
  the same file database, refreshed, retried the pending request and completed:
  exactly two correct answers and 100% typed accuracy, with persisted results.
- A separate two-question choice session showed four distinct unmarked stored
  forms. `гледа` for аз produced question-specific mismatch feedback. Revealing
  слушам then completing showed one incorrect, one revealed, zero correct;
  mistake/reveal review displayed the saved outcomes.
- Statistics showed typed 2 answered/2 correct and choice 1 answered/1 incorrect/
  1 revealed/0 correct. Filtering an unused dataset returned zeros. After another
  deliberate disconnect, a changed filter retained and labelled previous totals;
  restarting and clicking/pressing Enter on Update applied the requested filter.
  Clearing it restored the persisted totals across the restart.
- Inspected 320px and 360px statistics layouts had no horizontal page overflow
  (document widths 305px and 345px respectively, including browser scrollbar).
  Cyrillic, labels, Enter actions and rendered tables were checked; screenshots
  were visually inspected. No unexpected browser console errors were observed.
  Temporary viewport was reset, test tab closed and owned server stopped.

The earlier management/edit/export/import-comparison and broader UI checks remain
documented in [AORIST_WEB.md](AORIST_WEB.md); they are distinguished from the
final checks above. No physical phone, other browser engine, screen-reader or
actual Bulgarian voice-quality test is claimed. Missing-voice behavior is tested
automatically. A real personal installation upgrade remains a user-controlled
manual step after a stopped-file backup, not a destructive verification target.
Independent linguistic review remains outstanding, including `bgaor-009`'s label.

Review branch: `codex/aorist-final-verification`, containing the preceding content,
practice and web commits. No default-branch merge or Android changes were made.
