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
- **Next (former Phase 6 note, superseded):** add licensed/self-recorded MP4s (no code change needed), practice preparation that uses
  the selected asana, then scoring/coaching/Room per the original plan.
- **Still open from the original plan:** alignment/stability/hold/symmetry scoring, coaching + TTS, Room,
  check-ins, PHQ-9/GAD-7, dashboard data, weekly report.

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
- KSP compatibility with AGP 9.5.1 built-in Kotlin + Kotlin 2.2.10 (verify at start of Phase 6; Room needs KSP, so pin a KSP release matching the Kotlin version).
- MediaPipe `tasks-vision` compatibility with compileSdk 37 / AGP 9 packaging (verify at start of Phase 2).
- Physical device not yet connected via ADB.
