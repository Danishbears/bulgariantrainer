# Aorist web interface

Open `/aorist.html` through **Аорист** in shared navigation or the homepage card.
Start from the repository root using JDK 21 and `.\mvnw.cmd spring-boot:run`;
the normal URL is `http://127.0.0.1:8080/aorist.html`.
Storage/import contracts are in [AORIST_CONTENT.md](AORIST_CONTENT.md), practice
and scheduling in [AORIST_PRACTICE.md](AORIST_PRACTICE.md).

## Interface and learning scope

The page uses the existing shared CSS/navigation, responsive cards, Russian
labels, vanilla ES modules, labelled fieldsets, live status messages, skip link
and visible keyboard focus. It uses real REST data; no demonstration records,
automatic imports, generated conjugations or sentence-gap exercises are added.

Views are **Практика**, **Глаголы**, **Импорт и экспорт**, **Как заниматься**.
The help distinguishes aorist from aspect, dictionary forms, six person slots,
2sg/3sg syncretism, reflexive-particle position and aorist/imperfect spelling
overlap. It does not equate every aorist with an achieved result. Group labels
remain source-defined teaching labels, not CEFR or universal conjugation rules.
The learning material has not been independently linguistically reviewed.

## Management

**Глаголы** combines search, exact dataset/group/aspect/particle filters,
active/archive selection and pagination. Expand **Спряжение, пояснение и источник**
to see an actual six-row, two-column table with every accepted variant. This
layout wraps on narrow screens rather than requiring six horizontal columns.

Create/edit supplies lemma, Russian meaning, aspect, particle, group, six explicit
answer arrays (one alternative per line), note and source title/URL/section/date.
Forms are never filled by a suffix rule. External ID and dataset are fixed on
edit. For a new dataset, explicitly check **Создать новый набор** and supply its
revision/date/notes; an existing dataset's metadata is managed through import.
Source dates are user/source claims, not application verification.

Archive/restore preserves IDs and history. Failed requests retain editor content;
drafts and uncertain mutations are browser-local when storage is available.
**Проверить сохранение и повторить** reads current content/version/identity first,
including all matching active/archive pages for a lost creation response, before
acknowledging or resending. A stale edit retains text and offers explicit reload.
Conflicting actions remain disabled while a mutation's result is uncertain.

## Import and export

In **Импорт и экспорт**, choose
`learning-materials/aorist/bulgarian_aorist_40.json` and click **Предпросмотр**.
Nothing is saved by upload. The complete document is validated and errors are
shown in Russian with human-readable field paths. Original root notes are
identified as notes from the supplied file, not application instructions.

The preview shows dataset/revision/format metadata, source warnings, counts and
ten-row pages. Additions initially selected; unchanged rows may be explicitly
selected in bulk. Updates are unselected and show complete **Было / Будет**
comparisons; select each intended update. A source revision downgrade needs an
additional checkbox. Only **Подтвердить выбранные записи** writes selected data.
Success reports added/updated/unchanged/deselected outcomes. Reimport never
duplicates identities, removes omitted manual entries or restores archives.

If confirmation loses its response, the exact UUID/body is retained across
refresh and the selection stays frozen. **Повторить подтверждение** safely checks
the committed result. Expired/stale previews require a new upload; a fully invalid
document cannot be partially confirmed. Vocabulary CSV/TXT, sentence CSV,
aspect-pair JSON and database backup procedures remain separate.

Export chooses a dataset and active/archived/all scope, downloads the complete
supported v1 JSON and reports the entry count. It contains content only: no
internal IDs, progress, sessions or archive-state restoration. Revision/date
remain last-source metadata even after local edits; it is an edited material
snapshot rather than a newly published source revision or full backup.

## Practice and recovery

Default configuration is **Ввод ответа / Только аз**. Choose selected/all six
persons, aspect, group, dataset, size and NEW/DUE/MIXED queue. Availability must
be checked after changing options. Shortage produces a clearly labelled shorter
start button; no duplicate verb/person slots pad a session. Choice availability
explains reduced/insufficient options and offers an explicit switch to typed
recall. The application never changes modality silently.

Each question displays lemma/Russian meaning, aorist tense and a **separate person
label**. Enter an isolated form such as `облякох се`; never concatenate the person
label to produce a sentence. **Проверить** or Enter submits typed text/one selected
radio option. Arrow keys select options; checking is a separate action. Correctness
is unmarked until server feedback. **Показать ответ** finalizes as a reveal without
correct credit and schedules a one-day retry in that exercise type.

Feedback shows the exact submitted text/selected option, primary accepted form,
alternatives, a short explanation and **Дальше**. Enter continues from the focused
Next control; held Enter and pending actions are guarded. Reveals, errors and
skips are distinguished in results/mistake review, and choice accuracy is labelled
as recognition rather than independent typed recall.

The optional full note/source/paradigm is fetched only after submission/reveal
through the guarded explanation API. If another queued person of that verb is
unanswered, it remains withheld with a clear notice. It becomes available when
all queued questions of that verb are answered/revealed. Early-skipped questions
remain protected. The table is expandable, and explanations always use original
session snapshots rather than newly edited content.

Refresh/restart reloads authoritative state from `?session=ID`, including feedback
not yet acknowledged. Without a session query, open the browser's last session
or choose from the real server list of unfinished sessions. Browser storage keeps
the draft/selected option and exact uncertain request, not the session itself.
After a connection error, input remains and **Повторить запрос** resends the exact
body or restores conflicting server state. When storage is unavailable, the URL
and server list still resume the session; unsubmitted drafts then survive only on
the current page and a notice explains that limitation.

The existing Bulgarian TTS is reused through explicit **Прослушать / Медленно**
buttons and optional voice settings. Before feedback only the public lemma has
playback controls; target playback is created only from saved feedback. There is
no automatic playback, person/answer concatenation or non-Bulgarian fallback.
Changing question/view stops playback. Missing/unsupported voices affect audio
controls only and never disable answer/reveal/Next.

## Executed verification, 2026-11-17

The browser checks used the **ChatGPT in-app browser**, not a simulated DOM or
claimed physical device. The packaged JAR used JDK 21 on a separate loopback port
18092 and a unique disposable H2 file under `target/` with WRITE_DELAY=0. No test
opened the personal database. Browser imports and synthetic editing fixtures
were confined to that disposable database; the supplied source file is unchanged.

| Browser check | Observed result |
| --- | --- |
| Import supplied JSON | Preview showed 40 new records without saving; confirmation added 40. |
| List/search/table/edit | Search found чета/прочета; six-person table and source were inspected; edited group persisted. |
| Create/accepted alternatives | Explicitly synthetic test entry saved six person lists and two аз alternatives; table retained both. |
| Archive/restore | Entry disappeared from active results, appeared in archive, and was restored. |
| Update preview | Original reimport showed one update, 39 unchanged and no update selected; page-three before/after comparison inspected; explicit selection updated one only. |
| Invalid version | Russian version error; confirmation disabled; no entries imported. |
| Export | Download produced 41 content entries (40 originals plus a labelled synthetic fixture); the generated test file was checked and moved under target. |
| Filters/pagination | Perfective filter found 29; ten-row pagination advanced to page 2 of 3. |
| Typed flow | чета with selected аз/ние, size 2; Enter accepted stressed uppercase ЧЕТО́Х, then четохме. |
| Privacy | No target table/note before answer; after the first person the full paradigm remained withheld; after both, original note and all six rows were available. |
| Refresh/resume | Saved first feedback survived refresh; opening the page without a query and explicitly resuming recovered it; Enter advanced separately. |
| Connectivity/JVM restart | Stopped only the owned test server before submission; error retained четохме and froze conflicting actions; restarted same DB, refreshed, retried pending request; final results were exactly two correct answers. |
| Choice/results | Four distinct unmarked options; radio Enter submission of чете for аз was graded as wrong for that question; submitted/accepted forms, explanation and mistake review shown. |
| Audio | Explicit public-lemma playback entered speaking state; target controls were absent before feedback. Actual device audibility/voice quality was not assessed. |
| Phone layouts | 320px and 360px checks, including update comparisons and expanded tables; no horizontal page overflow in inspected views. Screenshots were visually inspected. |
| Browser errors | No unexpected browser console errors observed; the deliberate disconnected request showed the intended retry state. |
| Final packaged build | Fresh empty state verified; keyboard skip link focused main-content without switching away from help. Temporary viewport override was reset and the created browser tab closed. |

Automated checks: Maven Wrapper/JDK 21 `.\mvnw.cmd -B clean verify` passed
**249 tests**, zero failures/errors/skips, and packaged the executable JAR.
New integration coverage guards full explanation access before answering, across
unanswered/early-skipped persons, after all reveals and after content edits, without
changing schedules. The static navigation/page test now includes aorist. All five
`aorist*.js` modules passed `node --check`; all nine existing pronunciation
regressions passed. Applied migrations, POM and Maven Wrapper are unchanged.

Manual follow-up remains for physical phones, other browser engines, actual
screen-reader use and audio quality/voice availability on the learner's device.
Missing-voice behavior is covered by existing pronunciation unit tests; a real
missing-voice device was not available in this browser run. No independent
linguistic review or personal-database installation upgrade is claimed.
