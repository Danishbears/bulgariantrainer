# Bulgarian Trainer

A single-user local application for learning Bulgarian vocabulary as a Russian speaker.
The backend provides vocabulary CRUD and search, multiple Russian translations, controlled
Flyway migrations, sentence management, persistent training sessions, spaced repetition,
and learning statistics. The Russian interface connects the dashboard, two-step imports,
three training modes, and statistics to the local REST API. Authentication is not implemented.

## Interface

See [FLASHCARDS.md](FLASHCARDS.md), [TYPING.md](TYPING.md), and [SENTENCES.md](SENTENCES.md) for training usage, API contracts, persistence, and verification.

The Russian interface uses shared navigation, responsive white cards, green and blue accents,
system fonts, and vanilla ES modules. No third-party frontend libraries or remote assets are required.

- `/`: dashboard with live active/archive vocabulary counts from `/api/words` and connectivity
  from `/api/health`. Missing counts display a dash with an error and retry, never a fabricated zero.
- `/vocabulary.html`: real vocabulary search, filters, pagination, editing, archive and restore.
- `/import.html`: real document preview, corrections, confirmation, and import summary.
- `/flashcards.html`: recognition cards with persistent sessions, ratings, progress, and session results.
- `/typing.html`: bidirectional typed recall, balanced persistent sessions, feedback, and mistake review.
- `/sentences.html`: sentence practice and management with approved alternatives, self-assessment, linked vocabulary, and archive.
- `/sentence-import.html`: review and confirm the supplied sentence CSV or another matching file.
  If confirmation loses its response, edits stay locked until the exact request is retried.
- `/statistics.html`: live vocabulary, retention, accuracy, activity, and mistake-review metrics.
  No sample vocabulary, invented statistics, or simulated training results are displayed.

Navigation highlights the current page. On mobile, use the **Меню** button; Escape closes it.
A keyboard skip link leads to the main content. Focus indicators, labelled forms, live status
messages, empty states, and reduced-motion support are included. Import previews scroll within
their table instead of widening the mobile page.

## Requirements

- JDK 21 (set JAVA_HOME to its installation directory).
- An internet connection for the first Maven dependency download.
- A modern browser supporting JavaScript ES modules.

Spring Boot 4.1.1 is retained from the Initializr project. The existing Maven wrapper
provides Maven; a separate Maven installation is unnecessary. Maven compiles with
Java release 21 and uses UTF-8 for source files and resources.

## Start locally

Run commands from the project root so the relative database path stays consistent.

Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd --version
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```sh
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw --version
./mvnw spring-boot:run
```

Open http://127.0.0.1:8080/ in your browser. The page automatically requests
`GET /api/health` and shows a Russian success or failure message. The button retries
the check; failed requests time out after five seconds. Stop the application with Ctrl+C.
The server binds to loopback only. If port 8080 is occupied, use
`spring-boot:run -Dspring-boot.run.arguments=--server.port=8081` and open port 8081.

Health response (HTTP 200, JSON):

```json
{"status":"UP","application":"bulgariantrainer"}
```

This endpoint reports application liveness, not a database readiness probe.

## Compile, test, and package

```powershell
.\mvnw.cmd clean verify
java -jar target/bulgariantrainer-0.0.1-SNAPSHOT.jar
```

On macOS/Linux replace `.\mvnw.cmd` with `./mvnw`. Tests cover startup and static resources,
vocabulary CRUD and import, all three training modes, review scheduling, statistics,
database constraints, idempotent submissions, and migration persistence. The `test` profile
uses a separate in-memory H2 database, never the personal file database.

Vocabulary manager browser smoke test (use an isolated database):

```powershell
java -jar target/bulgariantrainer-0.0.1-SNAPSHOT.jar --server.port=18082 --spring.datasource.url=jdbc:h2:file:./target/vocabulary-smoke/vocabulary
```

Open `http://127.0.0.1:18082/vocabulary.html`. Create a Cyrillic word with two translations,
edit it and change the primary translation, search in each language, combine the part-of-speech
filter with pagination, archive the word, then find it in the archive and restore it.
Try adding the same Bulgarian spelling again to check the duplicate warning.
Stop the test server while the page is open: failed saves must retain the form, and failed
loads must offer retry. Restart the same test server and retry. Also check keyboard navigation
and a narrow viewport. These checks must not run against `data/bulgariantrainer`.

## Persistence

- JDBC URL: `jdbc:h2:file:./data/bulgariantrainer`
- File: `data/bulgariantrainer.mv.db` (created on first startup).
- Local user: `sa`; empty password. No authentication or H2 web console.
- Data survives restarts. Start only one application instance for this database.
- Stop the application before copying the database file for backup.
- Flyway applies versioned SQL migrations from `src/main/resources/db/migration`.
  Hibernate uses `ddl-auto=validate` in both normal and test configurations.
- Flyway cleaning and automatic baselining are disabled. Do not edit an applied migration;
  add a new versioned migration. Back up the stopped database before an upgrade.
- The previous milestone created no domain tables, so its empty H2 database upgrades
  directly with V1. An independently populated schema without Flyway history requires
  an explicit reviewed migration plan; do not solve this by recreating the database.
- Generated database files are ignored; existing vocabulary TXT files are preserved
  and are not imported automatically.

## Structure and conventions

```text
src/main/java/com/example/bulgariantrainer/
  BulgariantrainerApplication.java   # Existing application entry point
  controller/                       # REST request handling
  service/                          # Business logic
  repository/                       # Spring Data JPA repositories
  entity/                           # Vocabulary, sentence, and study entities
  dto/                              # API request/response records
  exception/                        # Exceptions and JSON REST handlers
src/main/resources/
  application.properties
  db/migration/                     # Versioned Flyway SQL migrations
  static/*.html                     # Dashboard, management, imports, practice, statistics
  static/css/*.css                  # Shared and page-specific styles
  static/js/*.js                    # Vanilla JavaScript ES modules
src/test/java/                      # JUnit 5 / Spring Boot integration tests
src/test/resources/application-test.properties
```

Domain features follow `controller -> service -> repository -> database`.
Controllers use validated request DTOs and response records. WordService owns transaction
boundaries, normalization, primary translation selection, and deletion policy. FlashcardService,
TypingService, and SentencePracticeService own durable queues, answers, and session progress.
SpacedRepetitionScheduler owns review timing; StatisticsService calculates live aggregates.
See [AGENTS.md](AGENTS.md) for contributor rules.

The POM uses Spring Web MVC, Spring Data JPA, H2, Flyway, validation, and test starters.
The H2 console is not included. The Windows wrapper includes a null-safe directory-target
check for ordinary Maven cache folders.


## Vocabulary API

All vocabulary endpoints use JSON and UTF-8. Open `/vocabulary.html` to manage imported
and manually created words. The page supports multiple editable translations, a primary
translation, optional metadata, bilingual search, part-of-speech filtering, pagination,
and reversible archiving. Apply filters with **Найти** (or Enter in search).
Failed loads offer retry; failed saves retain the form contents.

- `GET /api/words?page=0&size=20`: list active words, ordered by ascending ID.
- `GET /api/words/{id}`: retrieve one word and its translations.
- `POST /api/words`: create a word; returns 201 with a Location header.
- `PUT /api/words/{id}`: replace all editable fields and the complete translation list;
  returns 200. Omitted optional metadata is cleared. Word ID and createdAt are retained;
  updatedAt changes, including for translation-only edits.
- `DELETE /api/words/{id}`: returns 204 for an unused word, or 409 `WORD_IN_USE` if it
  has any study progress, study attempts, or sentence associations. The entire operation
  is rejected; no history or translations are removed on rejection.
- `GET /api/words/search?q=къща&page=0&size=20`: case-insensitive substring search of
  Bulgarian text and all Russian translations. Each matching word appears once.
  `%`, `_`, and `!` are literal search characters, not query operators.
- Both list and search accept `partOfSpeech` (case-insensitive exact match) and
  `archived` (default `false`; `true` lists archived words only). Filters combine before pagination.
- `GET /api/words/parts-of-speech`: distinct stored parts of speech, including archived words.
- `PATCH /api/words/{id}/archive` with `{"archived":true}` archives a word;
  `{"archived":false}` restores it. The response is the updated word, including `archived`.
  Repeating either request is safe. Archiving preserves translations, sentence links,
  attempts, sessions, and progress even for words already used in training.

The page uses archiving rather than permanent deletion. Archived words remain retrievable
by ID and editable; their Bulgarian spelling stays reserved by duplicate prevention.
Import duplicate detection includes archived words and never restores them implicitly.
Flyway V2 adds the archive flag with a default of false, preserving all existing V1 data.

Pages are zero-based; size must be 1–100 (default 20). An out-of-range page is empty.
The page response contains `content`, `page`, `size`, `totalElements`, and `totalPages`.
Search requires a nonblank query of at most 255 characters. IDs must be positive.

POST/PUT example:

```json
{
  "bulgarianText": "къща",
  "partOfSpeech": "noun",
  "grammaticalGender": "feminine",
  "notes": "Женский род",
  "translations": [
    {"russianText": "дом", "isPrimary": true},
    {"russianText": "здание", "isPrimary": false}
  ]
}
```

`bulgarianText` and each `russianText` must be nonblank and at most 255 characters.
Each word needs 1–50 translations. Metadata limits are 100 characters for part of
speech/gender and 4000 for notes. Part of speech and gender are optional free text.
There must be at most one explicit primary translation; if none is specified, the
first becomes primary. The response always lists the primary translation first and
includes translation IDs. Translations with unchanged normalized text keep their IDs.

Display text is normalized to Unicode NFC with surrounding whitespace removed and
internal whitespace collapsed. Duplicate detection additionally ignores case using
Locale.ROOT and is enforced by a unique database key. Accents remain significant.
Duplicate Bulgarian words return 409 `DUPLICATE_WORD`; homographs cannot currently be
created as separate entries. Repeated Russian translations within one word return 400.
Database conflicts, including concurrent duplicates, return 409 `DATA_CONFLICT`.

Missing words return 404; malformed JSON and invalid input return 400. Errors have a
consistent shape: `status`, `code`, `message`, `fieldErrors`, and UTC `timestamp`.
Errors use English as an API contract; a future Russian frontend should map error codes
to localized UI messages. Internal exception details are not returned to clients.

## Persistence model

- Word owns its translations. Only translations cascade on word removal.
- SentenceWord uses `(sentenceId, wordId)` as its composite primary key, allowing
  sentences to contain multiple words and words to belong to multiple sentences.
- StudyProgress has a unique `(wordId, trainingMode)` pair. Modes are `FLASHCARD`,
  `TYPING`, and `SENTENCE`. Counters and repetition interval default to zero;
  ease factor defaults to 2.5, with a minimum of 1.3. Intervals are whole days.
- StudySession statuses are `IN_PROGRESS`, `COMPLETED`, and `ABANDONED`.
- StudyAttempt references a session and exactly one target: a word or a sentence.
  Direction is `BG_TO_RU` or `RU_TO_BG`; result is `CORRECT`, `INCORRECT`, or `SKIPPED`.
  An answer may be null for a skipped attempt or a future self-assessed flashcard.
- Optional sentence difficulty is an integer from 1 (easiest) to 5 (hardest).
- Foreign keys prevent accidental history deletion even outside the service. Indexes
  support relationship lookups, review due dates, session history, and normalized text.
  Substring search may scan text indexes; full-text search is unnecessary at local scale.
- Exactly one primary translation is maintained by the transactional vocabulary service.
  Counter bounds, valid enum values, target exclusivity, and unique progress are also
  enforced by database constraints. Timestamps use UTC instants.

The migration tests use temporary file databases to verify reopen durability, repeat
migration safety, Cyrillic round trips, and database constraints. API integration tests
use the isolated in-memory test database; they do not modify your vocabulary file.


## TXT and CSV import

Open http://127.0.0.1:8080/import.html or follow the homepage import link.
Choose a file, generate a preview, correct word pairs, select rows, and confirm.
Preview never writes vocabulary records. The actual document in
`data/BULGARIANFORME_original.txt` was inspected: it contains spaced hyphens, equals
signs, en dashes, tabular pairs, headings, blank/missing translations, repeated words,
slash alternatives, English text, and inline notes. It is never imported on startup.

Supported input:

- UTF-8 TXT or CSV, with or without a leading BOM; malformed UTF-8 is rejected.
- TXT pairs separated by `=`, hyphen/en dash/em dash with whitespace on at least one
  side, or tabs. Internal hyphens and multi-word expressions stay intact. Lines
  without a recognized separator remain visible as invalid records.
- Comma CSV with required `bulgarian` and `russian` headers and optional
  `partOfSpeech`, in any column order. Other headers/delimiters require correction;
  the importer does not silently infer a different format or discard extra columns.
- CSV supports quoted commas, doubled quotes, and multiline fields. Unclosed quotes
  preserve the remainder of the source as one invalid record with a line range.
- Commas, slash alternatives, and inline comments are never split into multiple
  translations. Review and edit them explicitly. Only one translation per imported
  record is created; more can be appended to an existing word on a subsequent import.
- Source text and physical line numbers are retained, including blank and invalid
  records. A terminal newline does not create an extra blank record. The initial BOM
  is treated as encoding metadata. Embedded CSV line endings are preserved in source.
- Latin/mixed-script text, multiple separators, lists, annotations, multiline values,
  and duplicates are warnings. This is conservative syntax inspection, not language
  detection or translation correction: Bulgarian/Russian direction is never swapped.
  Accents and unusual capitalization are preserved, and accents remain significant
  for duplicate detection just as in the vocabulary API.

Only unambiguous, nonduplicate rows are initially selected. Invalid rows can be
corrected and selected; unselected rows need not be valid. The page offers filtering,
25-row preview pages, selection controls, and a per-source-record result summary.
Duplicate warnings describe the uploaded preview; after edits, confirmation always
checks the current normalized Bulgarian text against the current database again.

### Preview endpoint

`POST /api/imports/preview`, multipart form field `file`.
Maximum file size is 1 MiB; maximum logical record count is 2000 (including headers
and blanks). Files are parsed in memory, not written into the vocabulary data folder.
Oversized files return 413; unsupported extensions return 415; invalid UTF-8 returns
400. There are no extra dependencies or schema changes for import.

Response fields: `importId`, `filename`, `expiresAt`, `entries`.
Each entry includes `rowId`, `lineNumber`, `endLineNumber`, `sourceLine`, proposed
`bulgarianText`, `russianText`, `partOfSpeech`, `status`, warning codes,
`existingWordId`, `existingTranslations`, `duplicateOfRowId`, and `suggestedSelected`.
Status is `READY`, `WARNING`, `INVALID`, `EMPTY`, or `HEADER`.

At most five previews are retained in memory for 30 minutes. Older previews are
expired/evicted; restarting the application clears them. Confirmation of an unknown
or expired preview returns 410 `PREVIEW_EXPIRED`, requiring a fresh upload.

### Confirmation endpoint

`POST /api/imports/confirm`, JSON. Use an importId and row IDs returned by preview:

```json
{
  "importId": "replace-with-preview-uuid",
  "entries": [
    {
      "rowId": 1,
      "selected": true,
      "action": "CREATE",
      "bulgarianText": "куче",
      "russianText": "собака",
      "partOfSpeech": null,
      "existingWordId": null
    }
  ]
}
```

- `CREATE`: create if the normalized Bulgarian word does not exist; otherwise skip.
  This applies to database duplicates, new duplicates since preview, and duplicates
  created earlier in the same confirmation. It never overwrites or appends implicitly.
- `SKIP` or `selected: false`: skip the record. Omitted preview rows are also skipped.
- `ADD_TRANSLATION`: explicitly append to `existingWordId`. The confirmed Bulgarian
  text must still match that word; stale/mismatched targets fail the entire import.
  Existing translations, primary choice, metadata, and training records are preserved.
  An already-present normalized translation is skipped. The 50-translation limit holds.
  The page offers this option for database matches from the preview. To append variants
  that were only duplicates inside a new file, import the first word and preview again.

Selected rows use the vocabulary API's text length and required-field rules. Row IDs
must belong to this preview and cannot repeat. All selected rows are validated before
writing, and all writes share one transaction. Any failure rolls back the complete
confirmation; correct the selection and retry. Unknown JSON or bad field values do
not result in partial success.

Summary fields are `importId`, `createdWords`, `addedTranslations`, `skippedEntries`,
and `entries` containing each source row's ID, line number, original text, outcome,
and affected word ID. Outcomes: `CREATED`, `TRANSLATION_ADDED`, `DUPLICATE_SKIPPED`,
`TRANSLATION_EXISTS_SKIPPED`, and `DESELECTED`. Skipped counts include headers/blanks.

An identical confirmation retry returns its cached committed result without writing
again. A different request for an already-confirmed preview returns 409
`IMPORT_ALREADY_CONFIRMED`. The browser freezes corrections after an uncertain network
failure and offers retry of the exact request. This retry cache has the same lifetime
as the preview; after expiry/restart, upload again and review duplicates.

Import tests cover the personal document's patterns, UTF-8/BOM, CSV quoting and malformed
input, selection/correction, stale duplicates, explicit append, preserved study progress,
preview expiry, safe retries, and rollback of earlier writes after a later conflict.

## Spaced repetition

The three training pages offer **Пора повторить** (due), **Новые** (new), and
**Смешанное занятие** (mixed). The choice is sent as `scope: "DUE" | "NEW" |
"MIXED"` when starting a session; omitting it defaults to mixed for older clients.
Typing availability accepts the same optional `scope` query parameter. If a due
or new queue is empty, session creation returns 409 `EMPTY_REVIEW_QUEUE` or
`EMPTY_NEW_QUEUE`. Mixed sessions select due items first, then new, learning,
and mastered items. Due items include mastered items whose date has arrived.
No item is permanently excluded. Sentence sessions additionally favor sentences
linked to vocabulary due for review within the same priority tier. Only active
words with translations and approved, unarchived sentences are eligible.

Each flashcard word, typing word and direction, and sentence has an independent
schedule. A flashcard rating counts only after the answer is shown; a typing
answer counts after submission; a sentence counts after automatic matching or
completed self-assessment. Seeing a question, an unanswered session, and a
pending sentence self-assessment do not change progress. Sentence-linked words
are tracked as usage but do not receive a successful vocabulary review.

The scheduling rule is deliberately simple and is **not the official Anki
algorithm**. Dates are UTC instants from the server clock. An accepted/correct
review increases the consecutive-correct streak and schedules the next review
after 1, 3, and 7 whole days for streaks one, two, and three. Every later
success doubles the prior interval, capped at 365 days (14, 28, 56, 112, 224,
365, ...). An incorrect answer or partially correct sentence resets the streak
to zero and schedules a one-day retry. Lifetime successful-review and total-
attempt counts never reset. Repeating an already saved HTTP answer/rating does
not add an attempt or move its due date. The backend selects and schedules all
questions; the browser never calculates dates.

Categories are assigned in this order: **NEW** when there are no attempts;
**DUE** when the next date is at or before the current server instant (or a
legacy reviewed record has no next date); **MASTERED** when the item is not
due, has at least five consecutive correct reviews, and its interval is at
least 28 days; otherwise **LEARNING**. Configure mastery with
`training.srs.mastery-successes` and `training.srs.mastery-interval-days` in
`application.properties` or environment-backed Spring properties. Mastery
changes a category, never removes the item from the mixed queue.

Flyway migration V6 adds scheduling fields while keeping existing attempts and
sessions. Existing reviewed records without a due date become due at their
last review instant; lifetime success counts are backfilled from correct
attempts. Historical typing progress was aggregated across both directions,
so the original `TYPING` record remains untouched. Recorded typing attempts
with a known direction seed separate `TYPING_BG_TO_RU` and
`TYPING_RU_TO_BG` records: their attempt/success totals are preserved, but
their unknown prior streak is set to zero and their last attempt becomes due
immediately. Aggregate history without individual attempts cannot be assigned
truthfully and remains only in the legacy record. New typing reviews update
the direction-specific records. Existing sentence
attempt counts remain in `sentence_progress` and are scheduled independently
from vocabulary.

## Learning statistics

Open `/statistics.html`. `GET /api/statistics` returns the complete dashboard;
`GET /api/statistics/words/{wordId}/mistakes` returns up to 20 recent incorrect
answers for a word, its current translations, and its lifetime incorrect count.
The page loads only real database records, supports refresh after connection
errors, and links recent sessions back to their saved results. No aggregate
statistics are stored.

Definitions used by the API and page:

- **Vocabulary size** counts all saved words, including archived words; active
  and archived counts are also returned. New, due, and mastered counts use only
  active words with at least one translation. **New** means no finalized word
  review or recorded progress in any word mode. Sentence usage does not make
  a word learned. **Due** means at least one current word-mode schedule has a
  next-review instant at or before the server's current UTC instant; a word is
  counted once even if multiple modes are due. **Mastered** means flashcard
  recognition and both typing directions all meet the configured mastery
  criteria and none is currently due. Legacy aggregate typing progress does
  not satisfy a direction-specific schedule.
- **Reviews scheduled for today** counts individual current schedule records
  with a next-review date on the current UTC calendar day, including approved,
  unarchived sentences. An archived word or sentence is excluded. Overdue
  records scheduled on earlier dates are not counted in this particular number.
- **Completed sessions** counts only sessions with status `COMPLETED`, once per
  saved session ID. **Review attempts** counts finalized attempt rows with
  result `CORRECT`, `INCORRECT`, or `PARTIAL`, including answered questions in
  abandoned sessions. Pending self-assessments and skipped questions are
  excluded. A retried submission reuses its existing attempt and does not
  increase any count.
- **Accuracy** is `CORRECT / finalized attempts × 100`, rounded to one decimal.
  `PARTIAL` contributes to the denominator but not the numerator. With no
  attempts, accuracy is `null` in JSON and shown as a dash, not 0%.
  Flashcards are self-rated recognition; typing is automatically graded;
  sentence attempts use their recorded `AUTOMATIC` or `SELF` source. Historical
  sentence attempts without a source are reported separately as unclassified.
  Overall and mode-specific rates include their own automatic/self-assessed
  breakdowns.
- **Consecutive study days** is the run of UTC calendar dates with at least one
  finalized attempt, ending today or yesterday. **Last 7/30 days** are rolling
  UTC calendar-day windows including today. Each point shows finalized
  attempts, correct answers, and sessions completed on that date. Zero values
  are actual days without matching records.
- **Words requiring attention** are the 20 saved words with the most
  `INCORRECT` word attempts across flashcards and typing, including archived
  words so history remains visible. Ties use most recent incorrect attempt,
  then word ID. The review panel shows the last 20 incorrect attempts and the
  word's **current** translations; earlier answer keys may differ if the word
  was edited. **Recent sessions** are the 10 latest by start time, including
  active and abandoned sessions; their answered/correct values are derived
  from finalized attempt rows.


