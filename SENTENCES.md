# Sentence practice and import

Open `/sentences.html` to add, edit, link, archive, or practice approved sentences.
Open `/sentence-import.html` to review `data/bulgarian_sentences_100.csv` (or another file
with the same seven-column format) and choose which rows to save. Uploading is always a
preview; the source file is never changed and nothing is imported at application startup.
Use **Предпросмотр файла проекта** to read the supplied CSV directly from `data/` without
selecting it in the file picker; it still requires explicit confirmation to save.
The preview includes source row identifiers, original lines, warnings, duplicates, and
editable Russian/Bulgarian text, alternative translations, explanation, difficulty,
and topic. Confirming is transactional. Reimported Russian sentences are skipped, and
repeating an identical confirmation is safe while its preview remains cached (30 minutes).
The CSV parser handles UTF-8 BOM, quoted commas, escaped quotes, multiline fields, and
the JSON array in `alternativeTranslations`. File IDs are source references; the database
assigns sentence IDs. The supplied file has 100 rows, 20 alternative translations, and
difficulty values 1 and 2.

A session defaults to 10 distinct approved, active sentences and allows 1–100. Fewer
available sentences produce a shorter session. Sentences linked to words with review
history that is due or has no correct streak are selected first. This only prioritizes
saved sentences; the application does not generate examples. Each question shows the
Russian text and accepts a multiline Bulgarian response. The backend snapshots the
approved answers and explanation when the session starts. Before submission, those
answers are absent from the question response.

Automatic evaluation compares Unicode NFC text after trimming and collapsing Unicode
whitespace and using locale-independent lowercase. Punctuation and Bulgarian spelling
remain significant. Exact approved matches are marked `CORRECT` and `AUTOMATIC`.
Other answers receive `PENDING` with approved translations and an optional explanation.
The user then chooses correct, partially correct, or incorrect as a clearly labelled
self-assessment; string mismatch alone is not a grammar judgment. Responses, review
state, and queue order survive page refresh and server restart. Completing early keeps
submitted answers and marks unanswered questions as skipped. A saved pending response
is shown as unevaluated if the session is ended early.

Sentence counts live in `sentence_progress`, separate from word recognition and typing.
`sentence_attempt_words` records associated vocabulary usage without changing any word's
`study_progress` or mastery. Linked words and sentences remain protected from accidental
hard deletion. Flyway V5 adds these tables and fields while retaining prior data.

JSON API: `/api/sentences` supports paginated GET, GET by ID, POST, PUT, and
`PATCH /{id}/archive`. `/api/sentence-imports/preview` accepts multipart CSV and
`/confirm` accepts `{importId, entries:[{rowId, selected, sentence}]}`. A selected
`sentence` uses `russianText`, `bulgarianText`, `alternativeTranslations` (string array),
`explanation`, `difficulty`, `topic`, and `wordIds` (existing vocabulary IDs).
`POST /api/sentence-imports/preview-initial` previews the fixed project CSV from
`data/bulgarian_sentences_100.csv`; it does not save records.
`/api/sentence-practice/sessions` starts an idempotent session using a UUID `requestId`
and optional `size`; its child routes are `question`, `answers`, `self-rating`, `next`,
`complete`, and `results`. `self-rating` accepts a question ID and one of `CORRECT`,
`PARTIAL`, `INCORRECT`. Finished sessions reject new answers.

Use Java 21 and run `.\mvnw.cmd clean verify` to run all tests.
