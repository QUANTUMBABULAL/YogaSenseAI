# Phase 7B — Active Profiles & Local Data Integration: Report

Date: 2026-11-14 · Build: debug · Fully local: no backend, cloud, networking, analytics or new runtime dependency.

**Verification status:**
- 256/256 unit tests pass. They include 35 tests that run real Room on real SQLite on the JVM.
- `assembleDebug` and `assembleDebugAndroidTest` succeed.
- **Nothing was run on a phone or emulator.** `adb devices` listed no device. The only AVD (`Medium_Phone_API_37.0`) was not booted: the PC had 1.9 GB of 15.6 GB RAM free, and the earlier session stopped the emulator for low memory.
- The UI has not been visually verified.
- No real legacy `wellness.db` was migrated: there was no device with data. The migration is verified against fixtures (JVM) and against a real Phase 7 database file written by `SqliteWellnessRepository` (an instrumented test that is written but not executed).

## 1. Audit summary (before changes)

7A was verified by:
- compile and KSP code generation;
- 7 JVM mapping tests;
- 10 instrumented Room tests that were written but never executed.

To raise confidence without a device, 7B adds JVM tests that run Room's generated DAOs, transactions, foreign keys and migrations on real SQLite. They use the bundled `androidx.sqlite` driver, as a test dependency only. Writing these tests found three problems in code that had never run:

| Finding | Fix |
|---|---|
| `db.withTransaction { … }` only works in Room's SupportSQLite mode | Multi-statement writes moved into DAO `@Transaction` methods (`insertWithSetup`, `updatePreferencesIfExists`, `importVerified`). These are standard Room and work in both modes. |
| `@Upsert` depends on parsing the constraint-error message | `user_preferences` and `app_metadata` are leaf tables (nothing references them), so they use `INSERT OR REPLACE`, which is equivalent there. |
| A `Migration` must also implement `migrate(SQLiteConnection)` for driver mode | `MIGRATION_1_2` implements both overloads from one shared SQL list. |

Existing stores found in the audit:
- SharedPreferences `practice_settings`: device-wide.
- `wellness.db`: device-wide, the live wellness store.
- `PracticeHistoryRepository`: returned `NoPracticeHistory`, and no session was saved.
- Home check-in tiles read `WellnessGraph` directly.

## 2. Architecture changes

```
MainActivity → YogaSenseApp (profile gate)
   ├─ Loading / NoProfiles → ProfileCreateScreen
   ├─ ChooseProfile         → ProfileChooserScreen (+ create, delete with confirmation)
   └─ Active(profile)       → ProfileSession(userId) ── LocalProfileSession + LocalViewModelStoreOwner
                               └─ MainShell (Scaffold + NavHost: Home/Explore/Progress/Wellness/Profile, practice, wellness routes)
AppGraph (singleton): YogaSenseDatabase, RoomProfileRepository, ActiveProfileManager,
                      LegacyWellnessMigrator, LegacyImportLifecycle, DeviceSettings
ProfileSession(userId): RoomPracticeHistoryRepository(userId), RoomWellnessRepository(userId),
                        loadPreferences/savePreferences(userId), recordPractice(userId), ViewModelStore
```

- **The NavHost exists only under an active profile.** No route can be reached without one; profile-scoped screens get their repositories from `LocalProfileSession`, which throws if no profile is active.
- **Room schema v2.** The `1 → 2` migration is additive:
  - `wellness_check_ins.reflection` (plaintext until 7D);
  - `wellness_assessments.band_label` and `.difficulty`;
  - `consent_records.decision` (GRANTED/DECLINED; existing rows become GRANTED);
  - a new `app_metadata` table.
  - These are needed to import Phase 7 data without loss. `app/schemas/…/2.json` is exported.

## 3. New and modified files

**New (main):**
- `app/AppGraph.kt` (`AppGraph`, `ProfileSession`, `LocalProfileSession`)
- `profile/ActiveProfileManager.kt`
- `data/RoomWellnessRepository.kt` (+ `WellnessMappers`)
- `data/LegacyWellnessMigration.kt`
- `data/LegacyImportLifecycle.kt`
- `ui/practice/PracticeRecording.kt` (`CorrectionLog`, `SessionRecordPolicy`)
- `ui/profile/ProfileGateScreens.kt`
- `ui/wellness/WellnessMigrationGate.kt`
- `ui/components/PracticeFormat.kt`
- `app/schemas/com.chinmai.yogasense.data.db.YogaSenseDatabase/2.json`

**New (tests):**
- `test/…/data/room/JvmRoom.kt`
- `test/…/data/room/ProfileDataRoomTest.kt` (13 tests)
- `test/…/data/room/LegacyMigrationRoomTest.kt` (11)
- `test/…/data/room/ActiveProfileRoomTest.kt` (9)
- `test/…/data/room/SchemaMigrationTest.kt` (2)
- `test/…/profile/Phase7BLogicTest.kt` (15)
- `androidTest/…/data/Phase7BInstrumentedTest.kt` (3)

**Modified (main):**
- `data/db/`: `Entities.kt`, `Converters.kt`, `Daos.kt`, `YogaSenseDatabase.kt` (v2), `Mappers.kt` (overview, streak rule)
- `data/`:
  - `ProfileRepositories.kt`, `PracticeHistoryRepository.kt` (`overview()`)
  - `WellnessRepository.kt`: record id is now a `String`
  - `SqliteWellnessRepository.kt`: `WellnessGraph` removed, class kept for its test and as the schema reference
- `profile/ProfileModels.kt` (`ProfileSetup`, `PracticePreferences`)
- `coaching/CoachSettingsStore.kt`: now a read-only `LegacyPracticeSettings` plus `DeviceSettings`. `SettingsCodec` is unchanged.
- `wellness/WellnessModels.kt`, `wellness/QuestionnaireFlow.kt`
- `ui/navigation/YogaSenseApp.kt`, `ui/navigation/Routes.kt`
- `ui/home/HomeScreen.kt`, `ui/home/HomeUiState.kt`
- `ui/progress/ProgressScreen.kt`, `ui/profile/ProfileScreen.kt`
- `ui/practice/PracticeSession.kt`, `ui/practice/LiveTrackingScreen.kt`
- `ui/wellness/CheckInScreen.kt`, `QuestionnaireScreens.kt`, `WellnessHistoryScreen.kt`, `WellnessOverviewScreen.kt`
- `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`

**Modified (build):**
- `gradle/libs.versions.toml`: `sqlite = "2.6.2"` and `androidx-sqlite-bundled-jvm`
- `app/build.gradle.kts`:
  - `testImplementation(libs.androidx.sqlite.bundled.jvm)`
  - `testOptions.unitTests.isReturnDefaultValues = true`
- Both are test-only. The SQLite version is the same one Room 2.8.4 already uses. Nothing was added to the app at runtime, and no AGP, Kotlin, Gradle or SDK versions changed.
- Backups of the previous Gradle files are in the session scratchpad.

**Modified (tests, inputs only):** `WellnessDataTest.kt` and `SqliteWellnessRepositoryTest.kt`. Questionnaire record ids changed from `Long` literals to `String`; no expected result changed.

**Docs:** `IMPLEMENTATION_PLAN.md` and this report.

## 4. Profile lifecycle and active-profile storage

**Create**
- Name: 1–40 characters after trimming. Experience: Beginner, Intermediate or Advanced.
- The profile, its preferences and any one-time markers are written in **one transaction**.
- `ActiveProfileManager.create` is serialised with `Mutex.tryLock`, so repeated taps return `Ignored`. The button is also disabled while a create is running.
- A test fires five concurrent taps and checks that exactly one profile is created.

**List, select and switch**
- Profiles are listed in creation order.
- `select(id)` verifies that the profile exists and is ACTIVE before storing it.
- "Switch profile" on the Profile tab clears the active id and returns to the chooser.

**Delete**
- An explicit confirmation dialog explains that the deletion is permanent and lists what is deleted. It is available from the Profile tab and from the chooser.
- If the deleted profile is the active one, the active state is cleared first, so its screens leave before the rows are deleted. Room's CASCADE then removes everything the profile owns.

**Active id storage**
- SharedPreferences `profile_session`, key `active_user_id`. It holds only the opaque UUID: no name or other personal data.
- It is written with `commit()` on the IO dispatcher and excluded from backup.
- DataStore was not added, to avoid a new dependency.

**Restore**
- The gate shows Loading until the profile list is loaded. A stored id becomes Active only if it is in the list.
- An id that is not in the list is checked against the database: if the profile was deleted, the id is cleared and the chooser is shown; if it was just created, the gate stays Loading until the list catches up.

## 5. How data isolation is enforced

1. **Data layer:**
   - Every repository is constructed with a `userId`: `RoomPracticeHistoryRepository(db, userId)`, `RoomWellnessRepository(db, userId, scope)`.
   - Every query and write includes `user_id`, or joins to the owning parent row filtered by it.
   - Wellness consent is per profile and is checked **in the database** before each write.
2. **Session boundary:**
   - Each active profile gets a `ProfileSession`.
   - On switch or delete, its coroutine scope is cancelled and its `ViewModelStore` is cleared. That store is the NavHost's ViewModelStore owner, so every ViewModel created under the old profile, and its state, is destroyed.
   - The new profile gets a new session, NavController and ViewModels. Screen rotation keeps the session, because it is held in `AppGraph` and closed only when the active id actually changes.
3. **Pending writes:**
   - `savePreferences` and `recordPractice` capture the `userId` when they are called and run in the app scope. A write still in flight therefore always lands in the profile that produced it.
   - If that profile was deleted meanwhile, the foreign key rejects the write (tested).
   - A practice session is bound to the profile that was active when practice opened.
4. **Navigation:** profile-scoped routes are not part of the composition without an active profile.

## 6. Practice history integration

- `PracticeSession` builds a `CompletedPracticeSession` when the practice screen is disposed (`release`) and hands it to `ProfileSession.recordPractice` **once** (`AtomicBoolean`).
- The DAO `insertCompletedSessionIfAbsent` is also idempotent on `session_id`. A repeated callback writes nothing, which is tested.
- **What is saved:**
  - wall-clock start and end;
  - **active** duration (excluding pauses);
  - completion status;
  - each recognised asana with its mean alignment and stability (**null when there were not enough samples; never 0**);
  - stable-hold total and longest hold;
  - correction events, with their structured id (`ASANA.metric.direction`) and time;
  - whether an improvement was measured (from `AssessmentCoach`'s sustained-improvement rule).
- No frames or landmarks are saved.

**Completion policy (`SessionRecordPolicy`):**

| How it ended | ≥1 counted stable hold | Saved as |
|---|---|---|
| End → Done on the summary | yes | COMPLETED |
| End → Done | no | ENDED_EARLY |
| Close/back without End (also after "Continue") | yes | ENDED_EARLY |
| Close/back without End | no | ABANDONED (stored, never counted) |
| < 10 s active and no pose recognised | — | not saved |

**Home**
- Shows the greeting with the profile name, and the profile's initials on the profile button.
- This-week tiles (sessions, minutes, streak) and a week strip.
- Recent activity: completed count, all-session count, last session's duration, and the five most recent sessions with date, duration, asanas and completion tag.
- Empty states when there is no history.

**Progress**
- All-time Completed / All sessions / Total minutes, plus this week's sessions, minutes and day streak, with the rules stated on screen.
- Per-asana history: number of sessions, total and longest steady hold, last practised, and average alignment and stability.
- Missing scores read **"Not measured"**; a real 0 shows as "0/100".
- No charts were added.

**Date rules:**
- Device time zone, local calendar days, Monday-first week.
- COMPLETED and ENDED_EARLY sessions with duration > 0 count.
- Streak: consecutive days ending today, or ending yesterday if there has been no practice yet today. It breaks once a whole day passes without practice.

## 7. Preferences migration

**Storage**
- Voice and visual settings live in Room `user_preferences`, per profile.
- The practice screen loads the **active** profile's settings, and changes are saved to that profile id only.
- Guided, Minimal and Off map 1:1, as do all toggles and the speech rate.
- `targetSkeletonEnabled` and `preferredPracticeMinutes` are kept as they are, since the practice screen doesn't edit them.
- The developer "diagnostics" toggle is device-level: it is not personal and not in the schema. It lives in SharedPreferences `device_settings`, falling back to the old value.

**One-time import**
- When the **first** profile on the device is created and the old `practice_settings` file contains saved keys, that profile starts with those settings.
- A marker, `legacy.practice_settings.imported_to = <userId>`, is written in the **same transaction** as the profile.
- Later profiles start with defaults, and existing Room preferences are never overwritten. Tests cover a later "launch" and a newer preference change.
- The old SharedPreferences file is **only read**. It is never written or deleted, and stays as a fallback.

## 8. Wellness migration strategy and verification

**Ownership is never guessed.**

| Device state when 7B first runs | Result |
|---|---|
| No `wellness.db`, or no records/consent in it | `NotNeeded`: Wellness uses Room directly |
| Legacy data, no profile yet (every pre-7B install) | `AwaitingFirstProfile`. The creation screen says the existing wellness data (with its count) will belong to this first profile. Creating it writes the owner marker in the same transaction, then imports. |
| Legacy data, profiles already exist, no valid owner (e.g. the owner was deleted before a successful import) | `NeedsOwner`. Nothing is imported. The Wellness tab explains this and offers an explicit, confirmed "These are mine — move to <name>". |
| Import failed (unreadable, unmappable or constraint conflict) | `Failed(reason)`. Nothing is imported, and the legacy data is untouched. "Try again" is offered. |

**Import (`LegacyWellnessMigrator` → `LegacyImportDao.importVerified`)**
1. Read `wellness.db` **read-only** with `SQLiteDatabase.OPEN_READONLY`. The file is never created or modified.
2. Map everything. If any record can't be mapped (unknown questionnaire type, unreadable answers, unrecognised consent value), **nothing** is imported, and the problems are listed by record id. Values are never shown.
3. In **one Room transaction**:
   - Insert, only where absent, using deterministic ids (`legacy7:<userId>:checkin:<day>`, `…:q:<id>`, `…:consent`).
   - Read everything back and verify:
     - counts;
     - every row, field by field;
     - the displayed model against the Phase 7 model (`DailyCheckIn` / `QuestionnaireRecord`, including reflection, answers, band, difficulty and the item-9 flag);
     - the consent decision.
   - Write the completion marker.
   - Any mismatch or exception rolls back everything.
4. **Retry is safe:** the ids are deterministic, the transaction is atomic, and the completion is remembered.

**Gating and the legacy file**
- Wellness tab, check-in, questionnaires, history and record screens, and the Home check-in tiles use the Room repository **only** when the status is `NotNeeded` or `Completed`. Before that, no store is writable.
- The crisis support screen is never gated.
- **The legacy file is retained** after a verified import.
- It is erased only when the import has completed **and** its owner later explicitly deletes their wellness data ("Delete all") or their profile. Otherwise "delete" would leave a plaintext copy behind.

**Migrated on this machine?** No: there was no device, so no real user data was migrated. The behaviour is verified with:
- JVM fixtures through real Room: all fields, sparse rows, a real 0 hours of sleep, PHQ-9 item 9 and difficulty, GAD-7, consent;
- idempotent retry;
- an unmappable record (nothing imported; succeeds after the data is fixed);
- a mid-import unique conflict (full rollback; the existing row is kept);
- a forced verification failure (rollback, no marker);
- ambiguous ownership (nothing assigned, then an explicit assignment);
- erasure rules.

An instrumented test that migrates a real Phase 7 database file and checks it is unchanged is written but not executed.

## 9. Tests executed and exact results

`./gradlew :app:testDebugUnitTest --rerun` → **256 tests, 0 failures, 0 errors, 0 skipped** (206 before 7B + 50 new).

New suites:
- `ProfileDataRoomTest` (13, real Room):
  - create, list and defaults; setup markers are atomic;
  - A can't modify B's preferences, read B's sessions, or read B's wellness;
  - consent is per profile; B's "delete all" leaves A untouched;
  - completed session persisted, and a duplicate callback is ignored;
  - null scores stay null (0 stays 0);
  - abandoned sessions are stored but not counted;
  - a late write for a deleted profile is rejected;
  - FKs enforced; wellness round trip and one check-in per day;
  - a declined consent is remembered; cascade delete.
- `ActiveProfileRoomTest` (9):
  - no profiles → create → active and persisted;
  - restore after a restart;
  - the stored id of a deleted profile is cleared;
  - list and switch (unknown select rejected);
  - deleting the active profile returns to selection or creation;
  - deleting another profile keeps the active one;
  - five repeated taps → one profile;
  - an invalid name creates nothing;
  - the first-profile flag reaches the lifecycle.
- `LegacyMigrationRoomTest` (11): as described in §8, plus the first-profile flow through `ActiveProfileManager` (wellness and settings imported once; second profile gets defaults; newer settings not overwritten on the next "launch") and a device without legacy data.
- `SchemaMigrationTest` (2): a v1 database built from `1.json` with data → opened by the current app → `MIGRATION_1_2` runs, Room validates the v2 schema, every row is preserved, and the new columns are null or default (no invented values). A missing migration refuses to open and keeps the data.
- `Phase7BLogicTest` (15):
  - gate resolver;
  - completion policy table, trivial sessions, null scores and time conversion, correction-improvement marking;
  - legacy settings import (only if saved); preference round trip keeping the fields practice doesn't edit; all voice modes;
  - streak through yesterday; overview counts; "Not measured" vs 0;
  - wellness mappers; legacy mapper ids, unmappable records without values, and verification detecting any difference.

Build: `assembleDebug` and `assembleDebugAndroidTest` **succeed**.

The migration SQL was also cross-checked with Python's sqlite3: v1 plus the migration gives a schema identical to a fresh v2 (columns, types, NOT NULL, defaults, PKs, FKs and indexes).

## 10. Tests not executed, and why

The following are **compiled but not run**, because no Android device or emulator was available (see the top of this report):
- `Phase7BInstrumentedTest` (3): v1→v2 with `MigrationTestHelper` on framework SQLite; a real Phase 7 `wellness.db` imported into Room with the legacy file unchanged and no duplicates on retry; isolation on framework SQLite.
- The 7A instrumented tests: `YogaSenseDatabaseTest` (7), `YogaSenseDatabaseFileTest` (3) and `SqliteWellnessRepositoryTest` (3).

No manual UI pass was done: screens, dialogs and layout at 360 dp have not been seen on a device.

JVM tests use Room's **bundled** SQLite driver, not the Android framework SQLite the phone uses. They exercise the same generated DAOs, SQL, transactions, foreign keys and migration, but not framework-specific behaviour.

## 11. Known limitations and remaining risks

- **Unverified on device** (UI, navigation, real migration). Highest-priority next step.
- **Rotation:** practice screen state is not retained across a configuration change (existing behaviour). Rotating mid-practice now saves the first part as a separate session (usually ENDED_EARLY or ABANDONED) and starts a new one.
- **Process death:** if Android kills the backgrounded app during practice, that session is not saved. Practice auto-pauses when backgrounded, but recording happens only on exit.
- **Plaintext:** reflections are stored as plaintext in `yogasense.db` (as they were in `wellness.db`). Phase 7D encrypts them into `reflection_ciphertext`. The verified legacy copy also stays until the owner deletes it or 7D removes it.
- **No access control between profiles:** anyone with the phone can open any profile. This is stated in the UI; PIN and biometrics belong to 7C.
- **Brief transitional emissions:** after a deletion, the chooser can show the deleted profile for one frame until the list refreshes.
- **Weekly totals:** the week is computed with the device time zone at read time. Sessions keep UTC timestamps.
- **JVM driver mode:** writes after reads fail on a *file* database in TRUNCATE journal mode. This doesn't affect the phone, which uses framework SQLite with one connection (WAL by default). The JVM file test uses WAL.

## 12. How to verify on the OPPO F15

Use `install -r` (upgrade). **Do not uninstall** the app: that would delete the Phase 7 `wellness.db` this phase is meant to migrate.

```
adb devices                                  # B65TRKDQGYYDCI9H must be listed
adb -s B65TRKDQGYYDCI9H shell run-as com.chinmai.yogasense ls -l databases   # before: wellness.db present?
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s B65TRKDQGYYDCI9H install -r app/build/outputs/apk/debug/app-debug.apk
adb -s B65TRKDQGYYDCI9H install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s B65TRKDQGYYDCI9H shell am instrument -w -e class com.chinmai.yogasense.data.Phase7BInstrumentedTest,com.chinmai.yogasense.data.db.YogaSenseDatabaseTest,com.chinmai.yogasense.data.db.YogaSenseDatabaseFileTest,com.chinmai.yogasense.data.SqliteWellnessRepositoryTest com.chinmai.yogasense.test/androidx.test.runner.AndroidJUnitRunner
```
Expected result: `OK (16 tests)`. The tests use throwaway database names and don't touch the app's real data.

**Manual pass**

1. Launch:
   ```
   adb -s B65TRKDQGYYDCI9H shell am start -n com.chinmai.yogasense/.MainActivity
   ```
   The "Welcome" creation screen appears. If Phase 7 data exists, it shows "Your existing data moves to this profile (N)".
2. Create a profile. The Wellness tab shows the previous check-ins and questionnaire results, and consent is not asked again if it was granted before.
3. Run `adb -s B65TRKDQGYYDCI9H shell run-as com.chinmai.yogasense ls -l databases`. Both `yogasense.db` and the retained `wellness.db` should be listed.
4. Practise for at least one steady hold, then End → Done. Home "Recent activity" and Progress per-asana history should show the session; missing scores read "Not measured".
5. Open the practice voice or visual settings, change one, leave and reopen practice. The change should be kept for this profile only.
6. Profile → Switch profile → Add profile. The new profile should have no history, default settings, and a consent prompt on its first check-in. Switch back: the first profile's data should return.
7. Restart the app and check it reopens the last active profile:
   ```
   adb -s B65TRKDQGYYDCI9H shell am force-stop com.chinmai.yogasense
   adb -s B65TRKDQGYYDCI9H shell am start -n com.chinmai.yogasense/.MainActivity
   ```
8. Delete the active profile after confirming. The app should return to the chooser, or to the creation screen if it was the last profile.
