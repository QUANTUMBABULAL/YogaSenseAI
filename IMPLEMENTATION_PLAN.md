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
