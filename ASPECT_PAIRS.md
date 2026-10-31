# Aspect-pair storage, management and JSON import

The interface is available at `/aspect-pairs.html` through shared navigation and
the homepage. Typed recall sessions and independent scheduling are documented in
[ASPECT_PRACTICE.md](ASPECT_PRACTICE.md).
Existing vocabulary/sentence screens, imports, progress and statistics are unchanged.

## Web interface

Open `/aspect-pairs.html` and use **Мои пары** to search Bulgarian/Russian text,
combine exact topic, stage, relationship, dataset and archive filters, and page
through real records. **Добавить пару** and **Изменить** provide separate accepted
answer lists (one form per line), both meanings, stage/topic, notes, examples and
provenance. IDs/namespace are fixed while editing. Archive/restore retains history.

**Импорт JSON** uses file selection, whole-document validation and a paginated
preview. New rows start selected; changed rows require explicit selection after
comparing **Было / Будет**. Invalid files cannot be confirmed. Confirmation freezes
its exact selected IDs/versions and UUID; after connectivity failure or refresh,
**Повторить подтверждение** retries that same request. Stale/expired previews need
a fresh upload. The original file is not modified.

Uncertain editor saves also freeze the submitted draft. **Проверить сохранение и
повторить** first reads current server content/version before either acknowledging
the save or safely resending. This handles an update whose response was lost and
prevents a second creation under the same external identity. Errors retain form
contents; conflicting edits offer an explicit reload of the current record.

## Data and identity

The original supplied file is preserved byte-for-byte at
`learning-materials/aspect-pairs/bulgarian_aspect_pairs_100.json` (100 entries,
212 accepted answers, 16 pairs of examples). It is learning material, not a backup,
and is never imported automatically. No running application depends on Downloads.

Use the explicit namespace `bulgarian-aspect-pairs-100` when importing this dataset.
Namespaces are case-sensitive, 1–100 lowercase ASCII letters/digits/dots/underscores/
hyphens, beginning with a letter or digit. The database assigns numeric internal
IDs; an external ID such as `bgasp-001` is unique only within its namespace.
Namespace and external ID are immutable after creation, including when archived.
Different senses may share verb spellings. No vocabulary words or links are created.

Flyway V7 adds datasets, pairs, ordered accepted answers, independent directional
progress storage and durable import receipts. No earlier migration changes.
Forms, meanings, accepted arrays, topic, studyStage, relationType, note, structured
provenance and optional examples are retained. Root/source/editorial metadata is
stored separately from entry content. `formatVersion=1` describes the import
schema; `contentRevision` is the SHA-256 of the most recently confirmed source
file's original bytes. It is not a semantic version or a claim that every row in
that file was selected. Each receipt retains that file's hash, metadata, selected
outcomes and committed summary. Manual edits do not change the last-import hash.

The numeric `version` in pair responses is an optimistic concurrency token, not
the file format/content version. Edit/archive/restore require that token. These
operations and reimport keep the pair's internal ID, creation time and progress;
archiving never deletes records. Reimport of an archived pair does not restore it.
No deletion endpoint exists. Stopped-file H2 backups include all new tables.

## Backup and migration limitations

There is no application-level full backup/restore feature to extend. The existing
procedure is an offline copy of the H2 file, not the JSON learning import:

1. Stop the application normally and ensure no other process is using this database.
2. Copy `data/bulgariantrainer.mv.db` to a safe backup location. For an overridden
   datasource URL, copy that database's `.mv.db` instead. Keep the application
   version, H2 version and Flyway schema version with the backup; keep source
   learning materials separately if their original files are also needed.
3. To restore, keep a copy of the current file first, stop the application, and copy
   the selected backup to the configured database path. Never overwrite an open
   database or mix files from different databases. Start only one app instance.
4. Prefer verifying an upgrade on a disposable **copy**, with an explicit datasource
   URL and separate loopback port, before replacing an actual installation.

The same file contains vocabulary, sentences, aspect pairs, accepted alternatives,
snapshots, sessions, submissions, progress, schedules and durable import receipts.
Uncommitted import previews are in memory and are not backed up; upload their
source file again. Browser drafts/last-session shortcuts are browser-local, so keep
the `?session=ID` URL when moving to another browser. Sessions themselves resume
from the database. Original JSON source bytes live in `learning-materials/`, not in
the backup file, although imported fields, source metadata and hashes are stored.

The previous project schema is V6. V7/V8 add aspect storage/practice without
rewriting previous migrations or legacy rows. On startup Flyway upgrades an older
compatible file, and Hibernate validates it. No data is seeded or auto-imported.
Tests cover a populated V6 upgrade and restoring both a V6 stopped-file copy and a
current stopped-file copy with all aspect data/history. This is not a guarantee for
arbitrary H2 engine versions, corrupt files or independently modified schemas.

There is no automatic downgrade, selective history merge, JSON database restore,
or browser-draft recovery from an H2 backup. Do not expect an older app to support
the upgraded schema. Rollback requires the matching pre-upgrade database backup
and compatible application/H2 version; post-backup learning is not in that older
file. Do not edit Flyway checksums, use automatic baselining, clear data or rebuild
the personal database to bypass a migration error.

## Management API

JSON endpoints return response records; entities are not serialized. Errors use
the existing `ApiError` envelope. English API identifiers/messages are intended for
clients to map to Russian; the web interface supplies those Russian messages.

- `GET /api/aspect-pairs?namespace=bulgarian-aspect-pairs-100&q=куп&studyStage=1&topic=Покупки&relationType=core&archived=false&page=0&size=20`
  returns `PageResponse`: `content`, `page`, `size`, `totalElements`, `totalPages`.
  All filters are optional and combine before pagination. `q` is a case-insensitive
  Unicode substring of forms, accepted variants, Russian meanings, topic or external
  ID. `%`, `_` and `!` are literal characters, not wildcards. Accents remain
  significant for search. Topic/relation/namespace filters are exact. Default is
  active pairs only, ascending internal ID; page is zero-based and size is 1–100.
- `GET /api/aspect-pairs/{internalId}` retrieves active or archived entries.
- `GET /api/aspect-pairs/datasets` lists numeric dataset IDs, namespace, row version,
  last-import content revision and original root metadata (without entries).
- `POST /api/aspect-pairs` creates a pair (201 plus Location). Body:
  `{ "namespace":"my-pairs", "entry":{...} }`. A new namespace is created only
  when this valid write commits. Duplicate external IDs return 409, even archived.
- `PUT /api/aspect-pairs/{internalId}` replaces all learning fields. Body:
  `{ "namespace":"my-pairs", "version":0, "entry":{...} }`.
  Unchanged content is a no-op. Missing optional examples clears examples.
- `PATCH /api/aspect-pairs/{internalId}/archive` uses
  `{ "version":0, "archived":true }`, or `false` to restore. Return the updated
  pair and version. Repeating with the current version is safe; a stale version
  requires reloading. A lost edit/archive response should be resolved by GET.

Responses contain `id`, `namespace`, `version`, `archived`, `entry`, `createdAt`
and `updatedAt`. `entry` uses the original file's field names, including `id` for
the **external** ID. Example create body:

```json
{
  "namespace": "my-pairs",
  "entry": {
    "id": "my-001",
    "imperfective": "купувам",
    "perfective": "купя",
    "russianImperfective": "покупать",
    "russianPerfective": "купить",
    "acceptedImperfective": ["купувам"],
    "acceptedPerfective": ["купя"],
    "topic": "Покупки",
    "studyStage": 1,
    "relationType": "core",
    "note": "",
    "provenance": {"kind": "added"}
  }
}
```

## Separate import API and review flow

1. Choose a UTF-8 `.json` file and explicit namespace. Upload multipart `file` and
   `namespace` to `POST /api/aspect-pair-imports/preview`. For example, from the
   project root in PowerShell (application already running):

   ```powershell
   curl.exe -sS -X POST http://127.0.0.1:8080/api/aspect-pair-imports/preview -F "namespace=bulgarian-aspect-pairs-100" -F "file=@learning-materials/aspect-pairs/bulgarian_aspect_pairs_100.json;type=application/json"
   ```

2. Preview validates the **entire** document without writing any database rows.
   Successful uploads return a preview with `importId`, namespace, filename,
   expiry, `valid`, `fileHash`, root `metadata`, `entries`, and `errors`.
   Structural/content problems return `valid:false` and path/code/message errors;
   no part of an invalid file can be confirmed. Upload/type/size problems use
   HTTP 400/413/415. A leading UTF-8 BOM is accepted.
3. Valid entries show ADD / UNCHANGED / UPDATE, external ID, existing internal ID,
   expected version, full `before` response and proposed `after` entry. Inspect
   both versions before choosing updates; none is saved by preview. Correct the
   source file and upload again if necessary. Entries are not edited in preview.
4. Explicitly select additions/unchanged entries/updates to save. Confirm with
   `POST /api/aspect-pair-imports/confirm`, JSON:

   ```json
   {
     "importId": "UUID returned by preview",
     "requestId": "new client-generated UUID",
     "entries": [
       {"externalId":"bgasp-001"},
       {"externalId":"bgasp-057","expectedVersion":2}
     ]
   }
   ```

   Omit or use null `expectedVersion` only for new rows; copy the exact version
   for existing rows. Unlisted entries are deselected. At least one selection is
   required. The server retrieves the parsed values from its own preview; clients
   cannot inject replacement fields. A confirmation may contain only known,
   unrepeated external IDs and the previewed versions.
5. The writer locks the dataset and selected existing pairs and checks versions
   before writing. A change after preview (including archive/restore, another
   import or creation of a selected new ID) returns 409 `STALE_IMPORT_PREVIEW`.
   Upload again and review changes. Concurrent first creation may return 409
   `DATA_CONFLICT`; re-preview instead of assuming a save occurred. All selected
   writes and their receipt commit in one transaction or roll back together.
6. Summary contains `created`, `updated`, `unchanged`, `deselected`, and per-entry
   CREATED / UPDATED / UNCHANGED / DESELECTED results with internal IDs. Reimport
   skips unchanged entries; selected updates preserve identity, archive state and
   progress. Omitted file entries are never removed.

Keep an uncertain confirmation's exact body and UUID until its result is known.
Retrying the same request returns the original committed summary, even after server
restart and preview expiry. A changed body for that UUID returns 409
`IMPORT_REQUEST_CHANGED`. A preview may be confirmed only once. Uncommitted previews
expire after 30 minutes/restart or eviction (five-preview cache), returning 410
`PREVIEW_EXPIRED`; upload again. No automatic imports or fixed-server-path import
endpoint are provided. TXT/CSV vocabulary and sentence imports remain separate.

## Validation rules

Required root: `format="bulgariantrainer.aspect-pairs"`, integer `formatVersion=1`,
and 1–1000 entries. Optional supplied root fields (`language`, `translationLanguage`,
`entryForm`, `createdOn`, `sources`, `editorialStatus`) are retained; when language/
entryForm are supplied they must match the supported Bulgarian/Russian citation
format. Upload limit is 1 MiB. Unknown fields, duplicate object keys, trailing
documents and malformed UTF-8/JSON are rejected, not discarded or coerced.

Entries require unique nonblank external IDs, both forms and Russian meanings,
topic, note (empty allowed), both accepted arrays, integer stage 1–3, relation
core/resultative/bounded, and provenance. IDs/namespaces are at most 100 characters;
forms/answers 255, Russian meanings 1000, topic 255, note/examples text 4000.
Each side has 1–50 nonblank string answers and no normalized duplicates; its primary
form must be a member. Validation uses NFC, removal of remaining combining acute/
grave stress marks, Unicode case folding to lowercase and whitespace collapse.
`се`, `си`, `й`, `ѝ`, spelling and punctuation remain significant. This normalizer
is independent of vocabulary/sentence normalization and does not implement practice.

Provenance is either `{kind:"added"}` or presentation with nonblank `sourceId`
and a positive integer `tableRow`. Imported source references must exist in root
`sources`. Optional examples, when non-null, require both imperfective/perfective
objects with nonblank `bg` and `ru` strings. Notes, source definitions and examples
are preserved rather than used to generate new cards or accepted synonyms.

Editing meaning does not silently reset learning credit; use a new external ID for
a genuinely new learning item. The educational content has not been independently
proofread; importing it is not a claim of linguistic verification.

## Verification

Run `.\mvnw.cmd clean verify` with JDK 21. Tests use the `test` profile and isolated
in-memory databases, plus temporary file databases for V6→V8 upgrade, constraints,
reopen and stopped-file copy checks. New integration coverage includes all 100
entries/fields, idempotency, explicit selection, namespaces, search/filter/page,
archive/restore, history preservation, invalid whole-file rejection, stale/forged
confirmations, concurrent confirm and durable receipts after preview-store loss.
Never run smoke imports against the personal database. Override the datasource
with a disposable H2 URL and use a separate port for live HTTP checks.

The final results and checklist are in [ASPECT_PAIRS_REVIEW.md](ASPECT_PAIRS_REVIEW.md).
The populated-upgrade test also verifies old vocabulary/flashcard/typing/sentence
data, then imports all 100 pairs and restores their history from a stopped-file
copy. Rejected malformed/version/duplicate-ID documents are tested against an
already populated dataset with attempts and progress, not just an empty database.
