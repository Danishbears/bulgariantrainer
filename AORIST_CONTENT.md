# Aorist content storage and management

This page documents the backend content APIs from [AORIST_PLAN.md](AORIST_PLAN.md).
Typed/multiple-choice sessions and independent exercise schedules are now
documented in [AORIST_PRACTICE.md](AORIST_PRACTICE.md). No aorist page is added yet.
Existing web sections and their imports remain separate.
Use Java 21 and the existing Maven Wrapper startup instructions in README.

## Content and identity

The supplied learning file is preserved under
`learning-materials/aorist/bulgarian_aorist_40.json`. It has 40 entries and 240
explicit answer strings. Nothing is imported at startup. Forms are stored exactly
as supplied, including alternative order, Cyrillic, particles, notes, source
metadata and dataset-defined teaching labels. No conjugations or aspect
counterparts are inferred. `чета` and `прочета` remain separate verbs.

Numeric database IDs are internal. External entry identity is the pair
`(datasetId, entry.id)`, including archived entries. Both are immutable on edit;
different external IDs may share a lemma or represent different senses.

- `formatVersion=1`: the JSON schema, not a source revision or SQL schema.
- `datasetRevision`: positive integer supplied by the material's author.
- `fileHash`: SHA-256 of original confirmed upload bytes, including formatting/BOM.
- Verb `contentHash` is internal and compares the ordered learning fields.
- Response `version`: optimistic database concurrency token.
- Flyway V9: additive SQL schema migration; V1–V8 are unchanged.

The new tables are `aorist_datasets`, `aorist_verbs`, `aorist_forms`,
`aorist_progress` and `aorist_import_receipts`. Progress is independently keyed
by internal verb/person/exercise type after V10 and is never updated by these
content operations. The practice backend owns its own attempt/session tables. Edits and
archive/restore preserve verb ID, creation timestamp, progress and receipt history;
there is no permanent-delete API. Later practice can reference these same IDs.

The source revision/hash on a dataset describe the last confirmed source upload,
not a guarantee that every source row was selected or that local edits match a
published release. Import receipts retain their own original metadata/hash/summary.
Manual entry edits do not change the last-import revision/hash.

## Management APIs

All routes are same-origin UTF-8 REST endpoints using response DTOs. Errors use
the existing `ApiError` envelope (`status`, `code`, `message`, `fieldErrors`,
`timestamp`). API identifiers/messages are English for frontend localization.

| Method / path | Behavior |
| --- | --- |
| `GET /api/aorist-verbs` | Active entries by default; zero-based `page=0`, `size=20` (1–100); ascending internal ID; existing `PageResponse` envelope. |
| `GET /api/aorist-verbs/{id}` | Full verb and six-person table, including archived entries. |
| `GET /api/aorist-verbs/datasets` | Internal ID, external `datasetId`, source revision, row version, file hash and original root metadata. |
| `GET /api/aorist-verbs/filters?datasetId=...` | Distinct stored group/aspect suggestions; dataset filter optional; includes archived material. |
| `POST /api/aorist-verbs` | Create, 201 plus Location. |
| `PUT /api/aorist-verbs/{id}` | Replace editable entry fields with current row version; unchanged content is a no-op. |
| `PATCH /api/aorist-verbs/{id}/archive` | `{ "version":0, "archived":true }`, or false to restore. |

Listing accepts combinable `datasetId`, `q`, `aspect=imperfective|perfective`,
`studyGroup`, `reflexiveParticle=none|се|си`, `archived=true|false`, `page`, `size`.
Search covers lemma, Russian meaning, external ID, group and all explicit forms,
ignoring case with NFC/Unicode whitespace normalization. `%`, `_`, `!` are literal
characters. Search does not remove accents. Group/dataset/particle filters are
exact. An out-of-range page is empty; invalid filters/sizes return 400.

Create body: `{ "datasetId":"bg-aorist-core-40", "entry":{...} }`.
For a new dataset also supply `metadata`, the complete validated root object
without `entries` (including format, revision, languages, dates and notes).
Its datasetId must match. Existing dataset metadata cannot be overwritten through
entry creation. Entry identity is reserved even in the archive (409
`DUPLICATE_AORIST_VERB`). Homographs with different IDs remain possible.

Edit body: `{ "datasetId":"bg-aorist-core-40", "version":0, "entry":{...} }`.
Do not pass metadata on edit; root metadata is managed through explicit import.
`entry` follows the supplied JSON exactly:

```json
{
  "id": "my-aorist-001",
  "lemma": "чета",
  "russian": "читать",
  "aspect": "imperfective",
  "tense": "aorist",
  "reflexiveParticle": null,
  "forms": {
    "1sg": ["четох"], "2sg": ["чете"], "3sg": ["чете"],
    "1pl": ["четохме"], "2pl": ["четохте"], "3pl": ["четоха"]
  },
  "note": "Учебная заметка",
  "source": {
    "title": "Название источника",
    "url": "https://example.org/verb",
    "section": "Минало свършено време",
    "checkedOn": "2026-11-17"
  },
  "studyGroup": "Моя учебная группа"
}
```

Stale edits/archive return 409 `STALE_AORIST_VERB`; reload before changing content.
After a lost save response, GET the record and compare identity, content/version
before resending. For an uncertain create, search its dataset/external ID across
all result pages, including archived records. Repeating archive with the current
version is safe; an obsolete version is still a conflict.

## Dedicated JSON import

1. Upload multipart `file` to `POST /api/aorist-imports/preview`:

   ```powershell
   curl.exe -sS http://127.0.0.1:8080/api/aorist-imports/preview -F "file=@learning-materials/aorist/bulgarian_aorist_40.json;type=application/json"
   ```

2. Preview validates the entire document without any database writes. Response:
   `importId`, filename, expiry, `valid`, file hash, root `metadata`, entries,
   errors and warnings. Entries classify ADD / UNCHANGED / UPDATE and include
   external ID, existing internal ID/version and complete before/after content.
   A document with any error cannot be partially confirmed. Errors contain JSON
   paths/codes/messages. Correct the file and upload again.
3. Explicitly select entries; updates must be selected by the caller, never
   implicitly applied. POST `/api/aorist-imports/confirm`:

   ```json
   {
     "importId": "preview UUID",
     "requestId": "new client-generated UUID",
     "entries": [
       {"externalId":"bgaor-001","expectedVersion":0},
       {"externalId":"bgaor-040","expectedVersion":null}
     ],
     "allowRevisionDowngrade": false
   }
   ```

   Use the exact expectedVersion from preview, null/omitted only for ADD.
   Omitted rows are deselected. At least one selection is required. Clients do
   not resupply content; the writer uses its validated server-side preview.
4. Confirmation rechecks dataset identity/version and selected entry versions,
   locks in stable order, then applies all selected writes and a receipt in one
   transaction. Any failure rolls everything back. Changes since preview return
   409 `STALE_IMPORT_PREVIEW`; upload/review again. A concurrent first-creation
   conflict likewise requires a fresh preview unless the same request won.
5. Summary reports created/updated/unchanged/deselected counts and per-entry
   outcomes/internal IDs. Reimport creates no duplicates. Selected updates keep
   internal IDs, archive state and progress. Omitted entries are never removed.

Retain the exact uncertain confirmation body/UUID. Exact replay returns the saved
summary even after restart, eviction or preview expiry. Changing its body returns
409 `IMPORT_REQUEST_CHANGED`. A preview cannot be confirmed under another UUID
after it has committed. Uncommitted previews are in memory (five, 30 minutes),
lost on restart/eviction, then 410 `PREVIEW_EXPIRED` requiring another upload.

A lower source revision produces `REVISION_DOWNGRADE` and needs explicit
`allowRevisionDowngrade:true`. Same revision with different file bytes produces
`SAME_REVISION_DIFFERENT_CONTENT`; it may be formatting, selected edits or changed
material. It does not authorize automatic updates. Higher revision also requires
selection. `GROUP_REVIEW` flags the supplied `вземам` label discrepancy without
changing or blocking it; other group strings are not matched to suffix rules.

## Validation contract

Required root fields: `format="bulgariantrainer.aorist"`, integer `formatVersion=1`,
lowercase ASCII datasetId (1–100 letters/digits/dots/underscores/hyphens, starting
with letter/digit), positive integer datasetRevision, ISO createdOn, `language=bg`,
`translationLanguage=ru`, `tense=aorist`,
`entryForm=first_person_singular_present_dictionary_form`, notes string array,
and 1–1000 entries. Maximum upload is 1 MiB; `.json`, strict UTF-8, optional BOM.
Unknown fields, duplicate object keys, trailing documents and type coercions are
rejected. Different JSON formats must use their own import routes.

Entries require unique nonblank external IDs (100 characters; no edge whitespace),
lemma (255), Russian meaning (1000), aspect imperfective/perfective, tense aorist,
studyGroup (100), note string (4000; empty allowed), source object, and all and only
six person arrays. Each array has 1–50 nonblank strings of up to 255 characters,
without normalized duplicates. Alternative order and original display text remain.
No requirement equates the lemma with an aorist form. 2sg/3sg equality is valid.

`reflexiveParticle` may be omitted/null or `се`/`си`. When present, both lemma and
all accepted isolated answers must retain that separate final token. No particle
is appended, removed or reordered. `source` requires title/section (1000), an
absolute HTTP(S) URL without credentials (2048), and ISO checkedOn. Dates/URLs are
syntax checks only, not external verification. Root notes allow up to 50 strings
of 4000 characters. Unknown source fields are rejected rather than discarded.

Answer uniqueness uses NFC first, remaining combining acute/grave stress mark
removal, Unicode whitespace collapse and locale-independent lowercase. This keeps
`и`, `й`, `ѝ` distinct, including their canonical decompositions, and retains
particles, punctuation and Latin/Cyrillic distinctions. It is content validation;
no practice answer endpoint is implemented here. Existing mode normalization is
unchanged. studyGroup is a teaching label, not a CEFR level or universal class.

## Content export and backups

`GET /api/aorist-verbs/export?datasetId=bg-aorist-core-40` downloads an active-only
v1 JSON material file; `archived=true` exports only archived entries, and
`archiveScope=all` exports both. Do not combine archiveScope with archived.
Export uses one consistent read, sorted external IDs, full arrays/metadata and
no pagination truncation. Empty scopes return 409 `EMPTY_AORIST_EXPORT`.
Over-limit content returns 413 rather than a truncated/non-importable document.

Export preserves the last imported source revision/date/notes. Local edits and
partial imports mean it is an edited material snapshot, not a newly published
source revision. No silent revision bump is performed. V1 has no archive/history
fields: export omits internal IDs, versions, progress, receipts and sessions;
importing into another database creates active entries. Existing archived entries
are not restored by reimport. A material export cannot restore learning progress.

There is no application-level full backup/restore API. Continue to stop the app
normally and copy the actual `.mv.db` file while no other instance uses it. That
file includes all aorist tables as well as existing modes. Keep application/H2/
schema versions and original material files with backups. Restore only while
stopped, keeping the current file first. Older V8 copies upgrade through V9/V10; there
is no automatic downgrade, arbitrary-engine compatibility or history merge.
Never reset/baseline/clear a user's database to resolve a migration failure.

The dataset is supplied learning material with source claims; it has not been
independently linguistically reviewed. Rejected forms are not declarations about
the entire Bulgarian language.

## Verification

Run `.\mvnw.cmd clean verify` using JDK 21. New integration tests use the `test`
profile and an isolated in-memory H2; persistence tests use temporary file H2.
They cover all 40 entries/240 forms, content round-trip, scoped identities,
alternatives/normalization, search/filter/page, create/edit/archive preservation,
whole-file invalid rejection on populated data, selected updates, stale versions,
revision warnings/downgrades, receipt replay/eviction/races, rollback after writes,
V8 upgrade and stopped-file copies. Never test against the personal database.

For live HTTP verification start the packaged app with a unique disposable
`jdbc:h2:file:./target/...;WRITE_DELAY=0` URL and a separate loopback port, then
exercise these APIs. This backend milestone introduces no new frontend/browser
flow; existing aspect/word/sentence import routes are not substitutes for it.

The repeatable Windows HTTP check is `.\scripts\verify-aorist-content.ps1` after
setting JAVA_HOME to JDK 21 and building. It requires Node with built-in fetch,
File and FormData support (verified with the installed Node 24). It creates its
own unique database under ignored `target/`, uses port 18089 by default (`-Port`
overrides it), runs the API flow, restarts its own JVM and verifies persisted
content and exact receipt replay, then stops its test server. It never uses the
normal port 8080 or the personal database. The underlying optional Node test is
skipped without `AORIST_HTTP_BASE_URL`; a skipped test is not HTTP verification.

Content-milestone checks executed on 2026-11-17 (current practice/build checks
are recorded separately in [AORIST_PRACTICE.md](AORIST_PRACTICE.md)):

- Maven Wrapper 3.9.16 / Temurin JDK 21.0.13: initial compilation passed;
  full `.\mvnw.cmd -B clean verify` passed all **207 tests**, no failures,
  errors or skips, and packaged the executable JAR. This includes 42 new
  aorist integration/persistence cases and existing mode regressions.
- `node --test src/test/js/pronunciation.test.mjs`: all nine tests passed.
- `node --check src/test/js/aorist-content-http.test.mjs`: passed.
- `.\scripts\verify-aorist-content.ps1`: initial live HTTP flow and fresh-JVM
  replay both passed, with no skips. Verified upload/preview/import of all 40,
  unchanged reimport, explicit update, editable alternatives, listing/filter/
  pagination, archive/restore, stale rejection, malformed/version/duplicate-ID
  rejection without changes, complete content export and unchanged legacy totals.
- Original JSON SHA-256 still matches the materials README. Its scoped Git
  attribute disables line-ending conversion so checkouts preserve those bytes.
- An initial sandboxed Maven test run stalled on Mockito's dynamic-attachment
  Windows pipe permission; it was stopped and the tests/build passed outside that
  sandbox. Existing non-failing Flyway/H2 version and Mockito agent warnings
  remain; no dependency changes were made to suppress them.

Logs and disposable databases are under ignored `target/`. No personal database
upgrade or interactive-browser verification was performed; this milestone has no
frontend changes. Source dictionary claims remain independently unverified.
