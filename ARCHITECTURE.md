# YogaSenseAI — Architecture

Single-module (`:app`) native Android app. Kotlin + Jetpack Compose, fully on-device, no backend.

## 1. Toolchain (verified Phase 1)

| Item | Value |
|---|---|
| AGP | 9.5.1 (built-in Kotlin; no `kotlin-android` plugin) |
| Kotlin / Compose plugin | 2.2.10 |
| Compose BOM | 2026.02.01 (Material 3) |
| Gradle wrapper | 9.6.0, configuration cache on |
| JDK | 25 (Android Studio JBR), daemon toolchain 25 |
| Java source/target | 11 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 24 |
| Target device | OPPO F15, Android 11 (API 30), 8 GB RAM |

Planned dependencies (add only when the phase needs them): CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`), MediaPipe `tasks-vision`, Room + KSP, `lifecycle-viewmodel-compose`, `navigation-compose`. TTS uses platform `android.speech.tts.TextToSpeech` (no dependency).

## 2. Major components

| Component | Responsibility |
|---|---|
| Camera | CameraX `Preview` + `ImageAnalysis` (back camera default, front via Flip; `STRATEGY_KEEP_ONLY_LATEST`, RGBA_8888, ~640x480, 4:3, FIT_CENTER preview). |
| Pose | MediaPipe Pose Landmarker **Lite**, `LIVE_STREAM` mode, CPU delegate first (GPU optional after device test). Emits 33 landmarks. |
| Smoothing | One-Euro (or EMA) filter per landmark; visibility gating. |
| Recognition | Rule-based classifier over joint angles + relative positions → per-frame label + confidence; temporal voting window for stable label. |
| Scoring | Geometry engine (joint-angle deviation vs. reference ranges), stability (normalized sway), hold timer, left/right symmetry. |
| Coaching | Prioritized correction selection, cooldown/debounce, praise on genuine improvement, TTS + on-screen cue. |
| Wellness | Daily check-in (mood, stress, sleep); PHQ-9 / GAD-7 every 14 days; safety messaging. |
| Analytics | Practice-vs-wellness trends, correlation with caveats, weekly report. |
| Persistence | Room: sessions, asana attempts, check-ins, questionnaire results, consent. |

## 3. Camera-to-coaching data flow

```
CameraX ImageAnalysis (analysis thread)
  → MediaPipe PoseLandmarker.detectAsync (LIVE_STREAM)
  → result listener → PoseFrame (normalized landmarks, timestamp)
  → LandmarkSmoother → SmoothedPose
  → AsanaRecognizer (per-frame) → StableAsanaTracker (N-frame vote)
        └─ only when STABLE → AssessmentEngine
              ├─ AlignmentScorer (angles vs. reference ranges)
              ├─ StabilityScorer (hip/shoulder-centre sway / torso length)
              ├─ HoldTimer
              └─ SymmetryScorer (where applicable)
  → AssessmentFrame (StateFlow, throttled ~10 Hz to UI)
  → Coach (priority + cooldown) → TTS / on-screen cue
  → Session end → AsanaAttempt summary → Room
Wellness check-ins → Room → Analytics → Dashboard / Weekly report
```

Threading: camera + inference on a single background executor; scoring on `Dispatchers.Default`; UI consumes `StateFlow`; skeleton overlay drawn on a Compose `Canvas` from the latest frame only. Frames are never written to disk.

## 4. Package ownership (two developers)

Root: `com.chinmai.yogasense`

| Package | Owner | Contents |
|---|---|---|
| `camera` | Dev A | CameraX setup, analyzer, permission handling |
| `pose` | Dev A | MediaPipe wrapper, landmark model, smoothing |
| `asana` | Dev A | Reference definitions, recognizer, stable tracker |
| `assessment` | Dev A | Geometry math, alignment/stability/hold/symmetry scorers |
| `coaching` | Dev A | Feedback prioritization, TTS wrapper |
| `wellness` | Dev B | Check-ins, PHQ-9/GAD-7 definitions + scoring, safety messaging |
| `analytics` | Dev B | Trends, correlation, weekly report builder, mock-data seeder |
| `data` | Dev B | Room DB, entities, DAOs, repositories |
| `ui` | Shared | `ui/theme` (exists), `ui/practice`, `ui/wellness`, `ui/dashboard`, `ui/onboarding`, navigation |

Contract boundary: Dev A produces `AsanaAttemptSummary`; Dev B persists and analyzes it. Changes to `pose`/`assessment` contracts require both devs to agree.

## 5. Shared data contracts (pose contract implemented in Phase 2: `pose/PoseModels.kt`)

```kotlin
// pose
data class Landmark(val x: Float, val y: Float, val z: Float, val visibility: Float, val presence: Float) // normalized, upright, un-mirrored image coords; see pose/PoseModels.kt
data class PoseFrame(val landmarks: List<Landmark>, val timestampMs: Long, val imageWidth: Int, val imageHeight: Int)

// asana
enum class Asana { TADASANA, TREE, WARRIOR_II, CHAIR, TRIANGLE, UNKNOWN }
data class Recognition(val asana: Asana, val confidence: Float, val isStable: Boolean, /* + side, stability, pending, pendingProgress, frame (explanations) — see asana/AsanaModels.kt */)

// assessment
data class JointDeviation(val joint: String, val measuredDeg: Float, val minDeg: Float, val maxDeg: Float, val deviationDeg: Float)
data class AssessmentFrame(
    val recognition: Recognition,
    val alignmentScore: Int,          // 0–100
    val stabilityScore: Int,          // 0–100
    val symmetryScore: Int?,          // null if not applicable
    val holdSeconds: Float,
    val deviations: List<JointDeviation>, // sorted by severity — explainability source
)
data class AsanaAttemptSummary(
    val asana: Asana, val startedAt: Long, val holdSeconds: Float,
    val meanAlignment: Int, val meanStability: Int, val symmetry: Int?,
    val topIssues: List<String>,
)

// wellness
data class DailyCheckIn(val date: LocalDate, val mood: Int /*1–5*/, val stress: Int /*1–5*/, val sleepHours: Float, val sleepQuality: Int /*1–5*/)
data class QuestionnaireResult(val type: Type /*PHQ9, GAD7*/, val takenAt: Long, val itemScores: List<Int>, val total: Int, val severityBand: String, val item9Positive: Boolean)
```

`LocalDate` requires core library desugaring on API 24–25 (add with Room phase) or store as epoch day `Long`.

## 6. Performance and offline requirements

- 100% offline; model bundled in `assets/` (`pose_landmarker_lite.task`). No `INTERNET` permission.
- Target ≥ 15 FPS inference, ≤ 150 ms camera-to-overlay latency on OPPO F15.
- Drop frames (keep-only-latest); never queue. Reuse bitmaps/buffers; no per-frame allocations in hot paths where avoidable.
- UI state throttled to ~10 Hz; TTS cues ≥ 3 s apart, one at a time.
- Close `PoseLandmarker` and unbind camera on stop; handle rotation and front-camera mirroring in overlay mapping.
- Release build with R8 must keep MediaPipe classes (add keep rules if needed).

## 7. Safety and privacy constraints

- Camera frames processed in memory only; never stored or transmitted. Stated on consent screen.
- Explicit onboarding consent (camera use, local storage of wellness data) before first use; stored in Room/DataStore.
- Physical-safety guidance before practice: clear space, stop on pain/dizziness, consult a professional for injuries, pregnancy, or medical conditions.
- Persistent "wellness tool, not a medical diagnosis" disclaimer on questionnaire results, dashboard and report.
- PHQ-9 / GAD-7: standard item wording, 0–3 scale, standard totals and severity bands; offered every 14 days, never daily.
- PHQ-9 item 9 > 0 or severe band → non-dismissable-on-first-view support message with crisis resources (e.g. Tele-MANAS 14416 / 1-800-891-4416 for India) and advice to contact a professional. No automated diagnosis language.
- Correlations shown with "association, not causation" and small-sample warnings (n < 7 → no correlation claim).
- Mock data is clearly labelled "Demo data" in the UI and removable.
- `allowBackup` / data extraction rules should exclude wellness DB.

## 8. Milestone acceptance criteria

| # | Milestone | Accepted when (verified on OPPO F15) |
|---|---|---|
| M1 | Camera + pose | Live front preview with skeleton aligned to body, ≥ 15 FPS, no crash on rotate/background. |
| M2 | Recognition | All 5 asanas recognized; label only shown after stable window; UNKNOWN when out of pose. |
| M3 | Scoring | Alignment, stability, hold, symmetry shown live; each score explains its top deviations. |
| M4 | Coaching | At least one spoken + on-screen correction; no cue spam; praise only on measured improvement. |
| M5 | Wellness | Daily check-in saved; PHQ-9/GAD-7 scored correctly (unit tests); concerning response shows support message. |
| M6 | Dashboard/report | Trends render from Room (real or labelled demo data); weekly report generated; caveats visible. |
| M7 | Demo | Consent, safety, disclaimers present; 10-min session without crash; scripted demo rehearsed. |
