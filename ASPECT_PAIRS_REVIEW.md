# Aspect-pair feature: review and final verification

Verified 2026-10-31 in the web repository `Danishbears/bulgariantrainer`.
Review branch: `codex/aspect-pairs`. No default-branch merge or remote push.
The Android repository and the user's personal database were not used or changed.

## Complete behavior

- Dedicated namespace/external identity, full learning-field preservation, JPA
  storage and new Flyway V7/V8 migrations. Existing migrations and dependencies
  are unchanged; no vocabulary words or implicit links are fabricated.
- Search/filter/page, retrieve, create/edit, archive/restore; separate strict JSON
  whole-document preview and explicitly selected transactional confirmation.
- Typed forward/reverse/mixed recall, stage/topic/size/queue configuration, shorter
  offers without duplicate pairs, immutable queues/snapshots and durable sessions.
- Exact server-side accepted-answer checks, protected unanswered content, terminal
  uncredited reveal, explicit Next, early completion, results and mistake review.
- Independent pair/direction schedules using the existing scheduler. Separate
  statistics API and UI: typed answered, correct, incorrect, reveals, distinct
  active due pairs and due directions. Existing mode statistics are not mixed.
- Russian interface, shared navigation/homepage links, multiple-answer editor,
  import comparisons, meaning-specific prompts, session/draft recovery and help.
  No new TTS or voice dependency is introduced.

## Open and import

Use JDK 21, start/restart from the project root with `.\mvnw.cmd spring-boot:run`,
then open `http://127.0.0.1:8080/aspect-pairs.html` or the **Видовые пары** navigation
link. Other pages/modes retain their routes. **Статистика** within this section is
separate from the existing global statistics page, which links to it explicitly.

Choose **Импорт JSON** → **Выбрать JSON**, select
`learning-materials/aspect-pairs/bulgarian_aspect_pairs_100.json`, retain explicit
namespace `bulgarian-aspect-pairs-100`, and click **Предпросмотр**. Review all 100
entries and select intended changes (updates are initially unselected), then
**Подтвердить выбранные записи**. No startup import is performed. Reimport does not
duplicate IDs or implicitly restore archived entries. Detailed contracts are in
[ASPECT_PAIRS.md](ASPECT_PAIRS.md) and [ASPECT_PRACTICE.md](ASPECT_PRACTICE.md).

## Confirmed issue fixed during final verification

A committed creation whose HTTP response was lost could report a false duplicate
warning during recovery if the exact external ID was beyond search page one.
Recovery now searches every matching active/archive page before deciding whether
to acknowledge or resend. A real-Chrome regression imports the 100 `bgasp-*` IDs,
creates external ID `bgasp`, deliberately loses the successful creation response,
and confirms it from page two without a second POST/duplicate record.

## Executed checks

Environment: Windows PowerShell, Maven Wrapper 3.9.16, Temurin JDK 21.0.13,
Node 24.22.1, installed Chrome 156.0.8078.79 in isolated headless mode.

| Command/check | Result |
| --- | --- |
| `.\mvnw.cmd --version` | Confirmed Wrapper Maven 3.9.16 and JDK 21.0.13 |
| `.\mvnw.cmd -B clean verify` | 165 tests; zero failures, errors or skipped; compilation and executable JAR packaging passed |
| `node --check` for all `aspect-*.js` modules | Passed |
| `node --test src/test/js/pronunciation.test.mjs` | All nine existing JavaScript regression tests passed |
| `.\scripts\verify-aspect-pairs.ps1` | Real Chrome test explicitly executed and passed; separate live HTTP legacy-mode checks and actual JVM restart/resume check passed |
| `git diff --check` | Passed; only normal Windows line-ending conversion notices |
| Original learning JSON SHA-256 | Still `A6354B52AEC47626A9EB32FFA9548BC79DDD67931B072364DA9E68409D80E2B6` |

The Maven test profile uses isolated in-memory H2 databases; durability/migration
tests use JUnit temporary file databases. The browser/restart script creates a
unique database under ignored `target/` with `WRITE_DELAY=0`, uses a separate
loopback port and stops only its own test server. No test opens or clears
`data/bulgariantrainer`.

| Requested verification | Evidence/result |
| --- | --- |
| All 100 entries and learning fields | `AspectPairIntegrationTests` round-trips all entries, 212 accepted answers, metadata and all examples; Chrome uploads/confirms all 100 |
| Repeat import and selected updates | Unchanged reimport creates zero records; preview old/new comparisons and explicit selection update in place; omitted/manual entries survive |
| Malformed JSON, unsupported versions, duplicate IDs | Parser/integration tests reject entire documents; a populated 100-entry dataset with receipt, attempt and progress remains unchanged after each rejected confirmation |
| Forward, reverse and mixed sessions | `AspectPracticeIntegrationTests`; exhaustive small-pool planner tests verify optimal feasible balance and no repeated pair |
| Accepted alternatives and exact answer rules | купувам/купя, прочета/чета and прочитам, изпея/изпявам, се/си, stress/case/Unicode whitespace, и/й/ѝ and decompositions; fragments, lookalikes and wrong meanings rejected |
| Hidden answers | Unsubmitted question and skipped-result APIs contain no target arrays, notes/examples or snapshots; fresh-JVM second question is checked again |
| Reveal has no recall credit | Correct counters unchanged; failed-recall scheduling/streak reset verified; post-reveal typed answer rejected |
| Retries, races and schedule updates | Start/submission/reveal/Next retries, concurrent identical starts/answers, answer-vs-reveal races and cross-session updates count once |
| Application restart | Fresh contexts on a temporary file plus an actual stopped/restarted packaged JVM recover unfinished order, current position, snapshots and feedback; exact resubmission remains idempotent |
| Independent progress/statistics | Separate directional rows and due boundaries; reveals/skips excluded from typed totals; archive retained in history but excluded from due; both directions of one due pair count once as a pair |
| Edit/archive preserve history | Stored progress/attempts and snapshots survive edits, archive, restore and selected reimport; restored-file data compared table-by-table |
| Existing modes | All existing JUnit mode tests passed. Live HTTP flashcard reveal/rating, typing recall and sentence automatic evaluation passed; legacy changes did not change aspect statistics |
| Previous schema and file restore | `AspectFeatureUpgradeTests` upgrades populated V6, compares all 13 legacy tables, resumes old flashcard/typing/sentence sessions, imports/practices new data, and restores current/old stopped-file copies |
| Browser usability | Import → list → practice → submit → reload → explicit resume → results; multiple-answer editing, archive, errors/retries, Enter/held Enter, contextual meaning, localStorage failure, all five views at 320/360 px without horizontal overflow |

Phone screenshots for practice and separate statistics were visually inspected.
Browser verification used actual headless Chrome; no interactive-browser or
physical-device verification is claimed. Existing non-failing H2/Flyway version
and Mockito dynamic-agent warnings remain; dependencies were not changed merely
to suppress those warnings.

## Backup, data limitations and manual follow-up

There is no full backup/restore API or portable backup format in this application;
none was added. The existing offline H2 file-copy procedure and downgrade/H2-version
limitations are documented in
[ASPECT_PAIRS.md](ASPECT_PAIRS.md#backup-and-migration-limitations). Tests restore
both a populated V6 copy and a current copy with pairs, accepted answers, sessions,
snapshots, attempts, progress, schedules and import receipts. Uncommitted previews
and browser-local drafts are not database backups.

The dataset remains an educational set: 56 edited presentation rows and 44 added
entries, not independently linguistically reviewed. Its accepted arrays are
card-specific and not an exhaustive Bulgarian dictionary. Meanings can be
resultative/bounded; entries are not universally interchangeable aspect partners.
Examples/notes need independent teacher review before any stronger quality claim.

Remaining manual verification:

- Optional human keyboard-only/screen-reader review and testing on physical phones
  and other browsers. Automated coverage is headless Chrome with simulated widths.
- Owner-controlled installation/backup handling with the actual database was not
  performed, in accordance with the no-personal-database test requirement. Read
  the backup/migration procedure before upgrading an installation.
- Independent Bulgarian linguistic proofreading of all entries and examples.

No code-level test failures or known implementation blockers remain in the
executed checks. Logs, temporary databases and screenshots stay ignored under
`target/`; only source, documentation, original learning material and reproducible
tests/scripts belong in review.
