# Phase 7B.5 — Performance & Personalised Onboarding: Report

Date: 2026-11-14 · Device: OPPO F15 (CPH2001, Android 11) · Fully local: no cloud, networking or new runtime dependency.

**Verification status:**
- **Baseline measurements were taken on the OPPO.**
- The phone then **disconnected** (`adb devices` empty) before the optimised build could be installed. **No after-measurements exist**, and the new onboarding UI has **not been seen on a device**.
- Unit tests: 277/277 pass (JVM, with real Room on SQLite).
- Instrumented tests: compiled, not executed.
- The phone still has the instrumented Phase 7B debug build. It was installed with `install -r`, so your profile and data are intact. Nothing was uninstalled.

## Part A — Performance

### 1. Baseline (Phase 7B debug build, measured on the OPPO)

Measured with `am start -W`, the `ActivityTaskManager` "Displayed" and "Fully drawn" lines, `Choreographer` skipped-frame warnings, and debug-only `YsPerf` markers. "Fully drawn" is reported when the profile gate resolves to a real screen. The phone was in use during the runs (Chrome in the foreground), so expect variance.

| Metric | Runs | Median |
|---|---|---|
| Cold start, first frame (`TotalTime`) | 3425, 3299, 3233, 2825, 2745 ms | **3233 ms** |
| Cold start, profile restored → Home ("Fully drawn") | 4746, 4586, 4460, 3820, 3766 ms | **4460 ms** |
| Hot resume (process and activity alive) | 158, 177, 150, 177, 148 ms | **158 ms** |

Breakdown of one cold start:

| Phase | Measurement |
|---|---|
| Before `Activity.onCreate` | +1629 ms after process start (+1790 ms in another run) |
| First frame ("Displayed") | +2713 ms, then **46 skipped frames** (~0.8 s main thread blocked) |
| Profile gate resolved | +3518 ms, then **55 skipped frames** (~0.9 s) |
| `AppGraph.get` (Room builder, SharedPreferences) | 115 ms |
| First composition and frame of the main shell plus Home | **1709 ms and 1972 ms** (two runs straight after an install) |

The app's compilation status was `status=run-from-apk`: the debug APK is not AOT-compiled and runs debuggable, so ART interprets or JITs it.

**Not measured (no device after the disconnect):**
- release or benchmark startup;
- navigation jank (`gfxinfo`);
- Progress rendering;
- camera initialisation time;
- pose inference latency and FPS;
- main-thread cost per practice frame;
- recomposition counts.

The logging for the practice metrics is in place (`YsPerf practice inferenceFps=… latencyMs=… recognitionMs=… snapshotMainMs=…` every 5 s), but it has never run.

### 2. What made it feel sluggish (evidence → cause)

1. **Debug, uncompiled build (dominant):**
   - 1.6–1.8 s pass before the Activity even starts.
   - The first composition of Compose, Material 3 and Navigation takes 1.7–2.0 s on the main thread.
   - Nothing in our composables is algorithmically heavy. This was checked: the figures are a few `drawLine` calls, there are no infinite animations on idle screens, and list keys are present.
   - The cost is interpreted, unoptimised framework code. A debuggable app also ignores baseline profiles.
2. **Two-stage startup:** the first frame shows a placeholder, the profile is restored about 0.8 s later, and then Home is composed. That gives two long main-thread stalls, each with dropped frames.
3. **Practice screen recomposes on every pose result (found in code, not measured):**
   - the root practice composable collected the pose frame, the recognition state and the assessment state directly;
   - every MediaPipe result (~15–30/s) therefore recomposed the whole screen (HUD, controls, guidance, skeleton);
   - `FramingGuidance.evaluate` also ran during composition.
4. **Duplicate database work:** Home subscribed to the same `practice_sessions` query 3 times (weekly summary, recent sessions, overview), and Progress subscribed to it twice more. Every write re-ran all of them.
5. **Not the cause:**
   - **Room:** queries run off the main thread on Room's executor. `AppGraph` creation is 115 ms.
   - **Camera and MediaPipe:** the frame pipeline was already correct (see §4).

### 3. Optimisations made

| Change | File(s) | Expected effect |
|---|---|---|
| `benchmark` build type: release code (R8, not debuggable, profileable), signed with the debug key so it upgrades in place on a test phone. Local testing only. | `app/build.gradle.kts` | Measure and use the app as users would get it. This removes the interpreter/debug overhead, the main cause. |
| App baseline profile (`HSPLcom/chinmai/yogasense/**`) plus the library profiles already shipped by Compose; `profileinstaller` was already present transitively. Packaged as `assets/dexopt/baseline.prof`. | `app/src/main/baseline-prof.txt` | AOT compilation of startup and UI code on install for release/benchmark builds. |
| Practice screen: skeleton reads the frame in the **draw phase**; HUD, guidance, timer and summary use `derivedStateOf` (invalidate only when the shown value changes); the assessment strip reads live values in **its own scope**; diagnostics stats are collected only when the diagnostics panel is shown. | `ui/practice/LiveTrackingScreen.kt`, `SkeletonOverlay.kt` | A pose result redraws the skeleton and updates the strip without recomposing the whole screen. |
| Shared practice-history upstreams (`shareIn`, `WhileSubscribed(5 s)`) per profile session; Home no longer subscribes to the unused `recentSessions`. | `data/ProfileRepositories.kt`, `ui/home/HomeUiState.kt` | 2 queries per data change instead of up to 5. Covered by a test. |
| Startup placeholder is a static wordmark instead of an infinite spinner. | `ui/navigation/YogaSenseApp.kt` | No animation work while the profile is restored. |
| Debug-only `PerfLog` markers and `reportFullyDrawn()`. | `perf/PerfLog.kt`, `MainActivity.kt`, `YogaSenseApp.kt`, `PracticeSession.kt`, `LiveTrackingScreen.kt` | Repeatable before/after measurements. Disabled in non-debuggable builds; logs timings only. |

### 4. Camera and MediaPipe (audited, unchanged)

The pipeline already meets the requirements, so it was **not changed**:
- analysis runs on its own single-thread executor, not the UI thread;
- one frame is in flight (`canAccept` gate) with `STRATEGY_KEEP_ONLY_LATEST`, so stale frames are dropped;
- bitmaps are reused, with a copy only when there is row padding or rotation;
- `ImageProxy` is closed in `finally`;
- on dispose, the analyzer is cleared and the camera unbound, then MediaPipe is closed on the analysis thread;
- after `close()`, no frames are accepted (new instrumented `PoseEngineLifecycleTest`).

The 33-landmark Pose Landmarker Lite, its CPU delegate and all its thresholds are untouched. Recognition still runs on `Dispatchers.Default`. Per-frame assessment stays on the main thread: moving it would mean re-threading `PracticeSession`. Its cost is now logged (`snapshotMainMs`) so the decision can be based on a measurement.

### 5. Before/after

| Metric | Before | After |
|---|---|---|
| Debug cold start, median | 3233 ms first frame / 4460 ms Home | **not measured** (phone disconnected) |
| Benchmark (release) cold start | — | **not measured** |
| Practice recompositions per pose result | whole screen (by code inspection) | skeleton redraw plus strip only (by code; not profiled) |
| Practice-history queries per data change (Home + Progress) | up to 5 | 2 (unit test) |

## Part B — Onboarding

**Screens** (`ui/onboarding/OnboardingScreen.kt`): Welcome, then Height, Weight, Experience, Body areas, Goals, Duration, Frequency, then Completion.

**Overall layout**
- One question fills the screen, under a large headline (32 sp, extra-bold) with a short explanation.
- A top bar holds Back, an animated coral progress bar with "n of 7", and Skip on optional questions.
- A full-width Continue sits at the bottom and is disabled until the answer is valid.
- Screens slide horizontally with a fade, in the direction of travel.
- Welcome and Completion are dark (Midnight gradient, Lime accents); the questions are on Warm White.

**Per screen**
- **Welcome:** a Tree-pose figure inside a lime radial glow, "Let's make yoga yours.", and "Let's begin". Optional mode also shows "Not now".
- **Height:** large 64 sp numeric field, cm or ft·in toggle (switching converts the value), range 100–250 cm with inline validation. The decimal keyboard opens automatically and the screen is `imePadding`ed. Optional.
- **Weight:** kg or lb toggle, range 25–300 kg (55–661 lb). "We don't calculate anything from your weight or judge it." No BMI is calculated or shown. Optional.
- **Experience:** three animated selection cards (level bars, check badge). This step is required, and new profiles must choose explicitly.
- **Body areas:**
  - a tappable front-view body map with 13 hotspots, plus chips for all 8 areas and "No particular area" (which is exclusive);
  - each selected area can optionally be marked "Discomfort" or "Mobility & flexibility";
  - the required safety note is shown verbatim;
  - optional.
- **Goals:** a 2-column grid of 8 icon tiles with multi-select. Optional.
- **Duration:** 5, 10, 15, 20 or 30 minutes as animated circles, with a large readout and a friendly caption. Optional.
- **Frequency:** four cards: 2–3 days, 4–5 days, every day, flexible. "No streak pressure." Optional.
- **Completion:** "Your journey starts here.", a summary of the answers given, a note that suggestions are not a medical assessment, and "Explore my YogaSenseAI".

**Feedback, input and accessibility**
- Selections give haptic feedback (`TextHandleMove`) and animate colour, scale and the check mark.
- Back never loses answers, and switching units converts the value instead of clearing it.
- Every selectable element exposes `selected` / `stateDescription` and a role, questions are headings, and touch targets are 40–64 dp.
- The flow is edge-to-edge, with status-bar icons switching between dark and light screens.
- No idle or infinite animations, to respect Part A.

**Modes** (`OnboardingController`, a pure state machine):

| Mode | When | Behaviour |
|---|---|---|
| **Required** | New profiles | Shown by the profile gate before Home; Back on Welcome returns to the profile chooser. Interrupted onboarding resumes at the stored step with stored answers. Home opens automatically once completion is stored, which only happens on the final CTA. |
| **Optional** | Existing profiles | Opened from the Home "Personalise" card or Profile → "Finish personalising". Can be left at any time ("Not now", Back). |
| **Edit** | One answer from Profile | Save, then return. Doesn't change onboarding progress. |

## Part C — Data architecture

**Room v3** (version 2 → 3, explicit `MIGRATION_2_3`, **create-only**, so no existing row is touched):

| Table | Key | Columns |
|---|---|---|
| `profile_personalisation` | `user_id` (FK → profiles, CASCADE) | height_cm?, weight_kg?, height_unit, weight_unit, weekly_frequency?, no_particular_area, onboarding_required, onboarding_status (NOT_STARTED/IN_PROGRESS/COMPLETED), onboarding_step, onboarding_version, prompt_dismissed, updated_at |
| `profile_goals` | (`user_id`, `goal`) | one row per goal |
| `profile_body_areas` | (`user_id`, `area`) | intent? (DISCOMFORT / MOBILITY) |

**No duplicated fields:**
- Experience is written to the **existing** `user_profiles.experience_level`.
- Session length is written to the **existing** `user_preferences.preferred_practice_minutes`.
- The create-profile screen no longer asks for experience, because onboarding asks it.

**Other storage rules**
- Every query is scoped by `user_id`. Each step is saved in one `@Transaction` (`PersonalisationDao.saveStep`), which refuses to write for a deleted profile.
- Skip never clears or overwrites a stored value.
- Values are stored in kg and cm; pound entries are kept at full precision, so they read back exactly.
- No date of birth, location or medical history is collected, and nothing is logged.
- The schema is exported to `app/schemas/…/3.json`, and the migration SQL is copied verbatim from it.

**Existing (Phase 7B) users**
- The migration creates **no** personalisation row for them. No row means onboarding is optional: they go straight to Home as before and see a dismissible "Personalise" card.
- Their experience level and settings are prefilled, and are changed only if they press Continue on that question.
- Practice and wellness history are untouched. A test runs the v2→v3 migration on real SQLite with practice, correction, wellness, assessment, consent and metadata rows.

## Part D — Personalisation integration

`profile/PracticeRecommender.kt` is pure, deterministic, and uses only the catalogue's own data plus the user's answers.

| Input | Effect |
|---|---|
| Experience | Orders poses. Beginners get Beginner poses first; Advanced users get Triangle first. Home's "Today's practice" is picked from the best-fitting poses (rotating by day). Nothing is removed. |
| Goals | Flexibility, Strength, Balance and Posture move poses in the matching catalogue category up the list, and the reason is shown. Stress, sleep, wellness and habit don't reorder poses. |
| Duration | Home "For you" shows an "N-minute session idea" with 2, 3, 4 or 5 poses (5, 10, 15, 20+ minutes) and the catalogue's hold ranges. |
| Body areas | Shown as "Extra attention: … Move gently and skip anything that causes pain." **Never excludes or prescribes a pose** (tested). |
| Profile tab | "Your personalisation" lists every answer, each tappable to edit. |

Every suggestion is labelled "General wellness suggestions, not medical advice." The pose recognition and assessment pipeline does not use any of this.

## Part F — Tests

**Executed:** `./gradlew :app:testDebugUnitTest` gives **277 tests, 0 failures** (256 existing + 21 new). `assembleDebug`, `assembleDebugAndroidTest` and `assembleBenchmark` succeed.

New tests:
- `PersonalisationRoomTest` (9, real Room/SQLite):
  - a new profile must onboard while an existing one must not;
  - the full flow persists every answer to its single source of truth;
  - skip keeps stored values and an interrupted flow resumes;
  - the optional flow never overwrites experience or minutes without an answer, and the prompt can be dismissed;
  - editing one answer keeps progress;
  - isolation between profiles, cascade delete, and no write for a deleted profile;
  - answers persist across an app restart (file DB);
  - history queries are shared (≤ 2 upstreams for 4 collectors).
- `SchemaMigrationTest` (+1): **v2→v3 on real SQLite** keeps all practice, wellness and metadata rows and doesn't force onboarding. The existing v1→v2→v3 path also passes.
- `BodyMetricsTest` (2): height/weight validation, units and round-trips.
- `OnboardingControllerTest` (7):
  - continue requires a valid answer; skip only on optional steps;
  - Back preserves answers; unit switching converts;
  - multi-select and exclusive "No particular area";
  - resume, and completion only on the final CTA;
  - a failed save stays on the step with an error;
  - optional and edit modes; gate rules and progress.
- `PracticeRecommenderTest` (3): experience orders without removing poses; goals reorder and body areas exclude nothing; plan size by duration.

**Written but not executed** (no device):
- `OnboardingUiTest`: walks every screen; the numeric field stays displayed with the keyboard open; Back keeps the answer; Skip; completion triggers exit, which is when the gate shows Home, and is stored.
- `PoseEngineLifecycleTest`: a closed engine rejects frames.
- The Phase 7A/7B instrumented tests.

## Known limitations

- **No after-measurements and no on-device UI check.** The device disconnected after the baseline.
- The baseline profile uses a wildcard rule rather than one generated by Macrobenchmark; a Macrobenchmark module was not added, to avoid a new module and dependencies.
- Per-frame assessment still runs on the main thread; its cost is logged but hasn't been measured yet.
- The body map is a simplified front view: "Upper back" and "Lower back" hotspots sit on the torso centre line. The chips are the accessible way to choose.
- Height and weight are stored only: no feature uses them yet, and none should judge them.
- The `benchmark` build type is for local testing only, and is signed with the debug key.

## Physical-device verification

See the final message of this phase.
