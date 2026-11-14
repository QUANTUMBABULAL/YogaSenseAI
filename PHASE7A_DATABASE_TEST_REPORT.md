# Phase 7A — Local Database Foundation: Test Report

Date: 2026-11-14 · Build: debug · Fully local: no backend, no cloud, no networking added.

## 1. Architecture audit (before changes)
| Existing persistence | What it stores | Scope | Status after 7A |
|---|---|---|---|
| SharedPreferences `practice_settings` (`CoachSettingsStore`) | Voice + visual practice settings | Device-wide | Unchanged; `UserPreferencesEntity` will replace it per profile in 7B |
| SQLite `wellness.db` (`SqliteWellnessRepository`, Phase 7) | Check-ins, PHQ-9/GAD-7 results, consent | Device-wide, no user | **Still the live wellness store** (Wellness UI); to be imported into Room per profile in 7B |
| `PracticeHistoryRepository` + `NoPracticeHistory` | Nothing (interface only) | — | Now has a Room implementation (not yet wired to the UI) |
| `PracticeSession` / `AssessmentPipeline` / `SessionStats` / `SessionSummary` | In-memory per session | — | Unchanged; a `CompletedPracticeSession` model + recorder is ready for 7B wiring |
| Room / KSP | — | — | Added |

Duplication avoided: no second practice-history interface (the existing one is implemented); no second wellness repository (only Room entities/DAOs; a profile-scoped Room implementation of the existing `WellnessRepository` interface replaces the SQLite one in 7B, so two live wellness stores never coexist).

## 2. Dependency changes
| Change | Version | Why |
|---|---|---|
| `com.google.devtools.ksp` plugin | 2.3.13 (2026-10-30) | Room code generation. KSP ≥2.3 is no longer tied to the Kotlin version; **verified to work with AGP 9.5.1 built-in Kotlin** (the risk noted since Phase 1). 2.3.14 skipped (released 1 day earlier). |
| `androidx.room:room-runtime`, `room-ktx`, `room-compiler` (ksp) | 2.8.4 (2025-11-19) | Local persistence |
| `androidx.room:room-testing` (androidTest) | 2.8.4 | `MigrationTestHelper` |
No AGP, Kotlin, Gradle or SDK changes. KSP args: `room.schemaLocation=app/schemas`, `room.generateKotlin=true`; schemas are added to androidTest assets.

## 3. Database
- File: `yogasense.db` (app-private). **Version 1.** Exported schema: `app/schemas/com.chinmai.yogasense.data.db.YogaSenseDatabase/1.json`.
- Foreign keys enforced (`PRAGMA foreign_keys = ON` on open, in addition to Room's FK handling).

| Table | Key | Owner / FK (on delete) | Indexes | Notes |
|---|---|---|---|---|
| user_profiles | user_id (UUID) | — | status | display_name, experience_level, created/updated_at, status |
| user_preferences | user_id | → user_profiles (CASCADE) | PK | voice mode, encouragement, countdown, corrections, speech rate, live/target skeleton, visual corrections, reference figure, preferred minutes |
| practice_sessions | session_id | → user_profiles (CASCADE) | (user_id, started_at) | started/ended_at, duration_ms (active time, excl. pauses), completion |
| pose_results | result_id | → practice_sessions (CASCADE) | unique (session_id, asana) | alignment_score **nullable**, stability_score **nullable**, stable_hold_total_ms, longest_hold_ms |
| practice_corrections | correction_id | → practice_sessions (CASCADE) | session_id | asana, correction_type (e.g. `WARRIOR_II.front_knee.high`), timestamp, improvement_observed |
| wellness_check_ins | check_in_id | → user_profiles (CASCADE) | **unique (user_id, epoch_day)** | mood, stress, sleep_hours, sleep_quality (all nullable), reflection_ciphertext (BLOB, encrypted only) |
| wellness_assessments | assessment_id | → user_profiles (CASCADE) | (user_id, completed_at) | instrument, instrument_version, completed_at, total_score, safety_flag (nullable) |
| assessment_responses | response_id | → wellness_assessments (CASCADE) | unique (assessment_id, question_number) | question_number (1-based), response_value |
| consent_records | consent_id | → user_profiles (CASCADE) | (user_id, consent_type) | consent_type, consent_version, granted_at, withdrawn_at |

Cascade policy (intentional): deleting a profile erases every record it owns (right to erasure); deleting a session erases its results and corrections. IDs are immutable (ON UPDATE NO ACTION). Enums are stored by **name** through explicit converters.

Not stored anywhere: camera video, frames, raw MediaPipe landmarks, PINs or passwords.

## 4. DAOs and repositories
- `ProfileDao`, `PreferencesDao`, `PracticeDao`, `WellnessDao`. Every user-specific query takes `userId`; child tables are read via a JOIN to the parent row filtered by `userId` (e.g. `poseResults(userId, sessionId)`); scoped update/delete (`finishSession`, `deleteSession`, `deleteCheckIn`, `withdrawConsent`) include `userId` in the WHERE clause. Observables are `Flow`. Multi-row writes are `@Transaction` (`insertCompletedSession`, `insertAssessmentWithResponses`, `upsertCheckInForDay`).
- `ProfileRepository` / `RoomProfileRepository`: create (validated name, default preferences in one transaction), get, observe, update preferences, delete (cascade).
- `RoomPracticeHistoryRepository(db, userId)`: implements the **existing** `PracticeHistoryRepository` (weekly summary from real sessions; recent pose records) and new `PracticeSessionRecorder.record(CompletedPracticeSession)`.
- Pure helpers: `Mappers` (entity ↔ domain, nullable scores passed through), `PracticeStats.weekly` (Monday-first week, streak, minutes; abandoned/zero-length sessions excluded).
- UI is not wired to Room yet (requires an active profile).

## 5. Migration strategy
- Explicit `Migration` objects in `YogaSenseDatabase.MIGRATIONS` (empty for v1); **no `fallbackToDestructiveMigration`**.
- A missing migration makes Room throw on open; the file is not modified (covered by `missingMigrationThrowsAndKeepsData`).
- Each schema change: bump `VERSION`, add a `Migration(n, n+1)`, commit the new exported JSON, add a `MigrationTestHelper` test.

## 6. Privacy preparation (encryption in Phase 7D)
- Sensitive columns: `wellness_check_ins` (reflection, mood/stress/sleep), `wellness_assessments.total_score`/`safety_flag`, `assessment_responses.response_value`.
- `reflection_ciphertext` is a BLOB intended only for ciphertext; there is no plaintext reflection column. Until 7D nothing writes reflections to Room (the Phase 7 SQLite store still holds them).
- No logging in the new database code; DAOs/repositories never log values.
- **Storage vs encryption:** Room/SQLite stores data in the app-private sandbox (protected by Android's per-app isolation and device encryption) but the file is readable by the app process and by anyone with root/backup access. Phase 7D plan: generate a non-exportable AES-256-GCM key in the Android Keystore (`KeyGenParameterSpec`, `PURPOSE_ENCRYPT|DECRYPT`), encrypt sensitive values field-by-field before insert (IV stored with ciphertext), decrypt after read; optionally require user authentication for the key. Whole-database encryption (e.g. SQLCipher) is an alternative that would add a dependency; to be decided in 7D.
- `wellness.db` is excluded from backup (Phase 7). `yogasense.db` will be added to the backup/transfer exclusions once it holds wellness data (7B).

## 7. Test results
- **Unit tests (`testDebugUnitTest`): 206 / 206 pass** (199 previous + 7 new: enum converter round-trips by name, session → rows mapping with ownership and **null scores preserved**, preferences round-trip and defaults, recent-record join, display-name validation, weekly totals/streak/day-index with abandoned sessions excluded, empty-history → no summary).
- **Instrumented tests written (`androidTest`), not yet executed** — no device was connected at the end of this phase (OPPO disconnected; the emulator had been stopped earlier for low memory and was not restarted):
  - `YogaSenseDatabaseTest` (in-memory Room, 7 tests): profile + default preferences CRUD and invalid name; FK violations for orphan session and orphan pose result; session persistence with nullable scores, corrections, weekly/recent via repository; multi-profile isolation (scoped read/update/delete return nothing for another user); one check-in per user per day with id kept on edit; scoped assessment responses + duplicate question rejected; consent grant/withdraw with history; profile delete cascades only that user's records.
  - `YogaSenseDatabaseFileTest` (file DB, 3 tests): persistence across close/reopen; v1 schema validated against the exported JSON with `MigrationTestHelper`; missing migration throws and the data remains on disk.
- `assembleDebugAndroidTest` compiles them successfully.

## 8. Build
- `assembleDebug`: **success**; KSP generates Room code with no warnings.

## 9. Known limitations
- Room is not yet used by any screen; practice sessions are not yet recorded at session end; preferences still come from SharedPreferences.
- Two databases exist until 7B (`wellness.db` live, `yogasense.db` new); the import is not implemented yet.
- `PracticeStats.weekly` is called with the device time zone; tested for IST.
- Instrumented tests pending execution on a device.

## 10. Next steps (Phase 7B)
1. Local profile selection/creation (no credentials in the DB) and an "active profile" holder.
2. Wire `RoomPracticeHistoryRepository` into Home/Progress and record `CompletedPracticeSession` at session end (from `AssessmentPipeline` + a correction event log).
3. Move practice preferences from SharedPreferences into `user_preferences`.
4. Implement a profile-scoped Room `WellnessRepository`, import existing `wellness.db` rows into the chosen profile once (with consent), then retire `SqliteWellnessRepository`; extend backup exclusions to `yogasense.db`.
5. Run the instrumented tests on the OPPO.
