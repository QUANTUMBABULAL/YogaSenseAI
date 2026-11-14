# YogaSenseAI — Implementation Plan

Time budget assumes ~40 working hours across two devs. Dev A = vision pipeline, Dev B = wellness/data/UI. See `ARCHITECTURE.md` for contracts.

## Phase 2 — Camera and MediaPipe (Dev A, ~5 h)
- Bump stale AndroidX libs (`core-ktx`, `lifecycle`, `activity-compose`) to versions aligned with the Compose BOM; add CameraX + MediaPipe `tasks-vision`.
- Bundle `pose_landmarker_lite.task` in `app/src/main/assets`.
- Camera permission flow; `PreviewView` in Compose via `AndroidView`.
- `ImageAnalysis` → `PoseLandmarker` (LIVE_STREAM) → `PoseFrame` StateFlow.
- Compose `Canvas` skeleton overlay with correct scaling + front-camera mirroring.
- FPS/latency debug readout.
- **Exit:** M1 on device.

## Phase 3 — Five-asana recognition (Dev A, ~5 h)
- Geometry utils: 2D joint angle, segment tilt vs. vertical, normalized distances.
- Smoothing filter + visibility gating.
- Reference table per asana (Tadasana, Tree, Warrior II, Chair, Triangle) with documented angle ranges and cited sources in code comments.
- Rule-based recognizer + stable tracker: time-based (1.5 s window, ≥ 60% share, ≥ 4 frames over ≥ 0.7 s; exit below 40%) because device inference is 5–9 FPS.
- Unit tests with synthetic landmark sets.
- **Exit:** M2.

## Phase 4 — Alignment, stability, sway, symmetry, hold (Dev A, ~6 h)
- Alignment: per-joint deviation outside range → weighted penalty → 0–100.
- Stability: sway of hip-centre over rolling window, normalized by torso length → 0–100.
- Hold timer: runs only while stable + alignment above threshold; tolerates brief dropouts.
- Symmetry: left vs. right angle difference (Tadasana, Chair; Tree/Warrior II/Triangle compare across sides when both performed).
- `AsanaAttemptSummary` emitted at session end.
- **Exit:** M3.

## Phase 5 — Coaching and TTS (Dev A, ~4 h)
- Priority: safety > largest deviation > stability > hold encouragement.
- Cooldown, de-duplication, one cue at a time; plain cue phrases per joint/direction.
- Praise only when a tracked deviation improves past a threshold vs. the attempt baseline.
- `TextToSpeech` lifecycle wrapper; mute toggle; on-screen cue banner.
- **Exit:** M4.

## Phase 6 — Wellness check-ins and questionnaires (Dev B, ~6 h; starts in parallel with Phase 2)
- Room + KSP setup; entities: consent, check-in, questionnaire result, session, attempt.
- Onboarding: consent + physical-safety + disclaimer screens.
- Daily check-in (mood, stress, sleep hours/quality).
- PHQ-9 and GAD-7 with standard wording, scoring and severity bands; due every 14 days.
- Support messaging for item 9 > 0 or severe bands; unit tests for scoring.
- **Exit:** M5.

## Phase 7 — Dashboard and weekly report (Dev B, ~6 h)
- Labelled demo-data seeder (toggle in settings).
- Trends: alignment/stability/hold vs. mood/stress/sleep over time (Compose `Canvas` charts — no chart dependency unless needed).
- Correlation (Pearson/Spearman) with n-threshold and "association, not causation" caveat.
- Weekly report screen: practice minutes, best/worst asanas, score deltas, wellness trends, disclaimers.
- **Exit:** M6.

## Phase 8 — Device testing and demonstration (both, ~6 h)
- Profile on OPPO F15: FPS, thermal over 10 min, memory.
- Lighting/distance/clothing robustness checks; tune thresholds.
- Release build with R8; verify MediaPipe keep rules.
- Demo script: onboarding → check-in → practice 2–3 asanas with live coaching → dashboard → weekly report → concerning-response flow.
- **Exit:** M7.

## Status log (as delivered)

> Delivery order diverged from the original numbering above. "Phase N" in this log refers to the
> delivered milestone, not the section headings above.

- **Phase 2 — Camera + MediaPipe:** done; on-device (OPPO F15) 33/33 landmarks standing, 5.4–8.9 inference FPS.
- **Phase 3 / 3.1 — Recognition + live integration:** done; synthetic-fixture tests only. Real-person validation still pending (first device log contained no full-body frames).
- **Phase 4 — UI/UX redesign (2026-11-13):** done. Light brand theme (Midnight / Coral / Lime / Warm white) for Home,
  Explore, Asana detail, Progress, Profile; dark `PracticeTheme` for the existing live practice screen.
  Navigation Compose 2.9.6 with 4 bottom tabs + full-screen Asana detail and Live Practice routes.
  Structured `catalog/AsanaCatalog` (5 asanas) with working search/filters. `data/PracticeHistoryRepository`
  seam (empty implementation) for Room later. No fabricated history. See `PHASE4_UI_TEST_REPORT.md`.
- **Phase 5 — Animated learning (2026-11-13):** done, waiting for video assets. `PoseLearningScreen` (route
  `learn/{id}`) with one reusable `AsanaAnimationPlayer` (Media3 1.10.1 for bundled MP4; keyframe animated
  illustration fallback; static fallback). Catalogue extended with breathing, common mistakes, demo keyframes
  and `assets/asana/<key>.mp4` references. Detail → Start learning → Skip/Continue → `practice?asana={id}`.
  **No demonstration videos are bundled yet** — all five lessons currently show the labelled animated
  illustration. See `PHASE5_LEARNING_TEST_REPORT.md`.
- **Phase 5.2 — Immersive practice + voice coaching (2026-11-14):** done (emulator-verified; OPPO not yet).
  Full-screen camera between a floating top HUD and bottom controls (FIT_CENTER 4:3, no crop), single
  framing/recognition guidance line, pause/resume, session timer, end-of-session summary, voice + visual
  settings sheets (persisted in SharedPreferences), diagnostics panel. `coaching/` package: pure
  `CoachingEventSource` + `CoachingDecisionEngine`, Android `VoiceCoachManager` (TextToSpeech). Alignment
  corrections flow through `AlignmentAssessor`, which has no implementation yet. See `PHASE5_2_PRACTICE_TEST_REPORT.md`.
- **Phase 6 — Alignment, stability and hold assessment (2026-11-14):** implemented and unit-tested on synthetic
  fixtures; **not device-tested** (no device connected). `coaching/assessment/`: per-asana prototype heuristic
  profiles, explainable 0–100 alignment score (null when not enough reliable metrics), temporal stability
  (body-centre sway in torso lengths), stable-hold tracker, one-at-a-time corrections and measured
  improvement events wired into the existing coaching engine and TTS. Live HUD strip + richer session summary.
  See `PHASE6_ASSESSMENT_TEST_REPORT.md`. Real-person validation and threshold tuning still required.
- **Phase 7 — Mental wellness companion (2026-11-14):** implemented. Wellness tab: daily check-in (mood,
  stress, sleep duration/quality, optional reflection; one per day, editable), overview with 14-day trends and
  weekly reflection, PHQ-9 and GAD-7 (verbatim official phqscreeners.com wording, standard 0–3 scale and
  bands), results with neutral prior comparison, history, delete-all, PHQ-9 item 9 support (Tele-MANAS
  14416 / 1-800-891-4416, emergency 112). Local SQLite (framework, no new dependency), consent-gated writes,
  excluded from backup. SQLite persistence verified on the OPPO via instrumented tests; wellness UI not yet
  visually verified on device. See `PHASE7_WELLNESS_TEST_REPORT.md`.
- **Phase 7A — Local Room database foundation (2026-11-14):** Room 2.8.4 + KSP 2.3.13 (verified working with
  AGP 9.5.1 built-in Kotlin). `yogasense.db` v1: profiles, preferences, practice sessions, pose results,
  corrections, wellness check-ins/assessments/responses, consent records — all user-owned with cascading FKs,
  exported schema in `app/schemas/`, no destructive migration fallback. `RoomProfileRepository`,
  `RoomPracticeHistoryRepository` (existing interface) + `PracticeSessionRecorder`. Not yet wired into the UI
  (needs an active profile, Phase 7B). Phase 7 `wellness.db` remains the live wellness store until then.
  Instrumented Room tests written but **not yet run on a device**. See `PHASE7A_DATABASE_TEST_REPORT.md`.
- **Phase 7B — Active profiles & local data integration (2026-11-14):** implemented; **unit-tested on the JVM
  with real Room + SQLite, not yet run on a device or visually verified** (no device/emulator available).
  Profile gate (create / choose / switch / delete with confirmation) with `ActiveProfileManager` (active id in
  SharedPreferences, verified on restore); per-profile `ProfileSession` (repositories bound to the user id,
  own ViewModelStore cleared on switch). Home/Progress read Room history (completed/all sessions, weekly
  totals, streak, per-asana history; "Not measured" ≠ 0). Practice sessions recorded once at exit with a
  documented completion policy. Practice preferences per profile (one-time, marker-guarded import of the old
  SharedPreferences into the first profile; old file kept read-only). Room schema v2 (additive migration
  1→2). Phase 7 `wellness.db` imported transactionally + verified into the first profile (or an explicitly
  chosen profile); Wellness screens use Room only after the import is resolved; legacy file retained.
  256/256 unit tests; instrumented tests compiled, not executed. See `PHASE7B_PROFILE_INTEGRATION_REPORT.md`.
- **Phase 7B.5 — Performance & personalised onboarding (2026-11-14):** baseline measured on the OPPO
  (debug cold start median 3.2 s first frame / 4.5 s to Home; main cause: debuggable, un-compiled build +
  1.7–2.0 s first composition). Fixes: `benchmark` build type, app baseline profile, practice screen no longer
  recomposes per pose result, shared history queries, static startup placeholder. Full-screen 9-screen
  onboarding (required for new profiles, optional/resumable for existing ones), Room v3 (create-only
  migration 2→3; experience and minutes reuse existing columns), recommender driving Home "For you" and an
  editable Profile section. 277/277 unit tests; **after-measurements and on-device UI check pending** (phone
  disconnected). See `PHASE7B5_PERFORMANCE_ONBOARDING_REPORT.md`.
- **Next:** run the instrumented tests and a manual pass on the OPPO; then Phase 7C/7D (PIN/biometrics,
  Keystore encryption of reflections and other sensitive columns, removal of the legacy wellness.db copy).
- **Next (former Phase 6 note, superseded):** add licensed/self-recorded MP4s (no code change needed), practice preparation that uses
  the selected asana, then scoring/coaching/Room per the original plan.
- **Still open from the original plan (partly superseded by Phases 6–7B):** symmetry scoring,
  weekly report, authentication (7C), encryption (7D).

## Proposed minimal package structure

```
com.chinmai.yogasense
├── MainActivity.kt
├── camera/        # CameraX + analyzer
├── pose/          # MediaPipe wrapper, Landmark/PoseFrame, smoothing
├── asana/         # references, recognizer, stable tracker
├── assessment/    # geometry, scorers, AssessmentFrame
├── coaching/      # cue prioritization, TTS
├── wellness/      # check-in, PHQ-9/GAD-7, safety messages
├── analytics/     # trends, correlation, weekly report, demo data
├── data/          # Room DB, entities, DAOs, repositories
└── ui/
    ├── theme/     # existing
    ├── onboarding/
    ├── practice/
    ├── wellness/
    └── dashboard/
```

No DI framework; manual construction in a small `AppContainer` when needed.

## Risks to verify early
- ~~KSP compatibility with AGP 9.5.1 built-in Kotlin~~ — resolved in Phase 7A: KSP 2.3.13 (versioning decoupled from Kotlin) builds Room 2.8.4 successfully.
- MediaPipe `tasks-vision` compatibility with compileSdk 37 / AGP 9 packaging (verify at start of Phase 2).
- Physical device not yet connected via ADB.
