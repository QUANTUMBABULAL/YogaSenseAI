# Phase 7 — Mental Wellness Companion: Test Report

Date: 2026-11-14 · Build: debug
YogaSense AI is a wellness companion, not a medical or diagnostic tool. Nothing here is clinically validated.

## Implemented screens (Wellness tab + routes)
| Screen | Route | Content |
|---|---|---|
| Wellness overview | `wellness` (5th bottom tab) | Today card (check in / edit), weekly reflection, 14-day mood / stress / sleep charts, PHQ-9 & GAD-7 cards (last result, "about every 2 weeks" note), History, Support resources, not-a-diagnosis note |
| Daily check-in | `wellness/checkin` | Mood 1–5, Stress 1–5, Sleep duration (½-h stepper, "Not recorded"), Sleep quality 1–5, optional reflection (≤500 chars); all optional; Save → "Saved for today" → Edit |
| PHQ-9 / GAD-7 | `wellness/questionnaire/{PHQ9\|GAD7}` | Intro (purpose, 2-week period, optional, not a diagnosis, consent) → one item per page with "Question n of N" + progress bar → (PHQ-9 optional unscored difficulty item) → review → consent → result. Exit confirmation; answers discarded on exit |
| Result | inline / `wellness/record/{id}` | Total / max, standard band, plain-language explanation, date, score ranges, neutral comparison with the previous result of the same type (only if one exists), support panel if item 9 was positive, not-a-diagnosis note |
| Support | `wellness/support` and inside the flow | Item-9 support content (below) |
| History | `wellness/history` | Questionnaire results and check-ins (newest first), empty states, **Delete all wellness data** |

Integration: Home "Daily check-in" tiles now show today's real values and open the check-in; Progress "Wellness overview" opens the Wellness tab; Profile has a "Wellness data" row → History (view/delete).

## Questionnaire source and version
- **PHQ-9**: official English form, phqscreeners.com `PHQ-9_English.pdf` (Pfizer, 2010) — stem, 9 items, 4 response labels, difficulty question reproduced verbatim (read directly from the PDF on 2026-11-14).
- **GAD-7**: official English form, phqscreeners.com `GAD-7_English.pdf` (v2, 2010) — stem, 7 items, 4 response labels verbatim. This official form has **no** difficulty question, so none is shown. (A Dartmouth-Hitchcock copy uses the labels "Not at all sure"/"Over half the days" and adds a difficulty item; the official phqscreeners labels were used instead.)
- Licence on both forms: "Developed by Drs. Robert L. Spitzer, Janet B.W. Williams, Kurt Kroenke and colleagues, with an educational grant from Pfizer Inc. No permission required to reproduce, translate, display or distribute." (shown on the intro screen).
- Bands: PHQ-9 0–4 minimal, 5–9 mild, 10–14 moderate, 15–19 moderately severe, 20–27 severe; GAD-7 0–4 minimal, 5–9 mild, 10–14 moderate, 15–21 severe (standard published cut-offs; GAD-7 cut-offs 5/10/15 confirmed on the Dartmouth-Hitchcock scoring sheet).

## Scoring implementation
- Response points 0/1/2/3 ("Not at all", "Several days", "More than half the days", "Nearly every day").
- Total = sum of all items; **only computed when every item is answered** (no prorating); invalid values rejected.
- PHQ-9 difficulty item is optional and **unscored**.
- Explanations describe the band, say "This is a screening score, not a diagnosis", and contain no treatment recommendations or diagnostic labels.
- Comparison wording is neutral ("3 points lower than your previous result on …") — never "improved/worse".
- Not administered daily: the overview notes the 2-week reference period; it remains optional and can be repeated.

## PHQ-9 item 9 safety behaviour
- Trigger: item 9 answered with anything other than "Not at all" (1, 2 or 3).
- The support screen appears **immediately after that answer**, before the questionnaire is finished — completion is never required.
- Content (`CrisisSupport`): compassionate acknowledgement; separate sections for **immediate danger** (call **112** or go to the nearest emergency department, stay with someone you trust) and **not immediate** (talk to someone you trust, seek a professional, **Tele-MANAS 14416 / 1-800-891-4416**, free, confidential, 24×7); explicit statement that the app cannot provide emergency care and **has not contacted anyone**.
- "Call" buttons only open the phone dialer (`ACTION_DIAL`); nothing is dialled or sent automatically.
- User chooses **Continue questionnaire** or **Stop here** (stop discards answers). The result screen repeats the support panel when item 9 was positive, and the record stores an `item9Positive` flag.
- Numbers verified 2026-11-14: Tele-MANAS (MoHFW / NIMHANS) 14416 or 1-800-891-4416, 24×7, free; 112 is India's national emergency number.

## Storage architecture
- `data/WellnessRepository` (interface, documented contract) → `data/SqliteWellnessRepository` using the Android framework `SQLiteOpenHelper` (`wellness.db`, app-private). **No new dependency**: Room would require KSP, whose compatibility with AGP 9.5.1's built-in Kotlin is an unresolved risk in this project.
- Tables: `check_ins` (PK `epoch_day` → one per local day; upsert = edit), `questionnaires` (answers, total, band, difficulty, item9 flag, timestamp), `meta` (consent).
- Schema version 1; `migrationStep` must be added per version; an unknown upgrade path **throws instead of dropping data**.
- Exposed as `StateFlow`s; writes on `Dispatchers.IO` behind a mutex; single app-wide instance (`WellnessGraph`).

## Privacy handling
- Consent dialog ("Keep this on your phone?") before the first save of a check-in or questionnaire; "Don't save" is honoured (questionnaire result still shown, marked "not saved"); writes without consent throw `ConsentRequiredException`.
- No network, no analytics, no logging of answers or reflections (no `Log` calls in wellness code).
- `wellness.db` excluded from cloud backup and device transfer (`data_extraction_rules.xml`, Android 12+) and from Auto Backup (`backup_rules.xml`, Android ≤ 11).
- No personal identifiers collected (no name, email or account).
- **Deletion**: History → "Delete all wellness data" (confirmation) removes every check-in, questionnaire result and the consent decision in one transaction. Uninstalling the app also removes the database.

## Test results
- Unit tests (`testDebugUnitTest`): **199 / 199 pass** (165 previous + 34 new): questionnaire structure, verbatim key items, response mapping, PHQ-9/GAD-7 scoring incl. incomplete/invalid answers, all band boundaries, non-diagnostic explanations, item-9 trigger, flow (decline, immediate support before completion, continue/stop from support, exit discards, GAD-7 without difficulty, back navigation, no submit while incomplete, record contents), check-in validation (empty rejected, every field optional, ranges, half-hour steps, reflection trim/limit, no defaults), repository contract via an explicitly labelled test double (consent, one-per-day edit, delete-all), trends (gaps, ≥3-entry averages, empty), neutral comparison, 2-week due logic, weekly reflection (empty / insufficient / averages / no invented values), overview empty state, local-day calculation, crisis content.
- Instrumented tests on **OPPO F15 (Android 11)** via `am instrument`: `SqliteWellnessRepositoryTest` **3 / 3 pass** — consent enforcement, one-per-day upsert, persistence across a new connection, delete-all, migration refusal.
- Unit tests check code logic only; they do not clinically validate anything.

## Build
- `assembleDebug` and `assembleDebugAndroidTest`: **success**.

## Device verification
- OPPO F15: debug APK installed; SQLite persistence verified (above).
- **Not verified on device:** the wellness screens visually (layout at 360 dp, large font sizes, TalkBack), the check-in/questionnaire/support flows by hand, dialer intents, Home tile integration. The phone was showing its notification shade, so no UI interaction or screenshots were taken.

## Known limitations
- Charts are simple single-series bar charts; tap shows a value, "Show values" gives a text table. No zoom or longer ranges than 14 days.
- Weekly reflection excludes practice sessions because practice history is not persisted yet.
- English only; translated official PHQ/GAD forms are available from phqscreeners.com but not integrated.
- Database is not encrypted beyond Android's app-private storage.
- ViewModels are not unit-tested (no coroutine-test dependency); their logic is in tested pure classes.
- Reminders/notifications not implemented (by design, no nudging).

## Remaining work
1. Manual device pass: check-in, both questionnaires (including item-9 path), results, history, delete-all, large fonts and TalkBack.
2. Optional: per-entry deletion, export, translations, practice-session persistence for the weekly reflection.
