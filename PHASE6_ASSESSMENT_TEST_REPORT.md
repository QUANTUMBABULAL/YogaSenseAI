# Phase 6 — Alignment, Stability and Hold Assessment: Test Report

Date: 2026-11-14 · Build: debug
**Status:** implemented and unit-tested with synthetic fixtures. **No device testing** this phase (no OPPO or emulator connected). **No real-person validation.** All thresholds are prototype heuristics — not clinically or scientifically validated.

## Architecture
```
PoseFrame + Recognition (existing)
   │  (only when recognition is CONFIRMED)
   ▼
AssessmentPipeline (ui/practice/PracticeSession.kt, pure Kotlin)
   ├─ LandmarkSmoother (own instance) → BodyFeatures (existing conventions)
   ├─ AsanaAlignmentAssessor + AsanaAssessmentProfiles → AlignmentResult
   ├─ StabilityAnalyzer → StabilityResult
   ├─ HoldTracker → HoldStatus (stable-hold time only)
   ├─ AssessmentCoach → CoachEvent (correction / improvement / stability decreased)
   └─ SessionStats → SessionSummary
        │ CoachEvents
        ▼
CoachingDecisionEngine (existing; modes, cooldowns, priority) → VoiceCoachManager (existing TTS)
```
Conventions reused from `BodyFeatures`: angles in aspect-corrected pixel space; distances ÷ torso length; a measurement is null if any of its landmarks is below 0.5 visibility/presence; analysis frames are never mirrored and L/R are anatomical; sided poses use the side reported by recognition.

Recognition confidence, landmark visibility, alignment score and stability score are separate fields and never combined.

## Assessment metrics and thresholds (prototype heuristics)
Metric score s = 1 inside [min, max], linear to 0 at `tolerance` beyond. Issue when s < 0.75 (only on an available metric with a defined correction).

| Asana | Metric | Measurement | Ideal | Tol | Weight |
|---|---|---|---|---|---|
| Tadasana | torso_upright | trunk tilt from vertical | 0–5° | 10° | 1.0 |
| | shoulders_level | shoulder line vs horizontal | 0–4° | 8° | 0.8 |
| | hips_level | hip line vs horizontal | 0–4° | 8° | 0.8 |
| | legs_straight | min(knee L, knee R) | 170–180° | 15° | 0.7 |
| | weight_centred | \|mid-shoulder x − mid-ankle x\| ÷ torso | 0–0.08 | 0.17 | 0.8 |
| Tree (side = lifted leg) | standing_leg | knee angle, supporting leg | 168–180° | 15° | 1.0 |
| | knee_open | lifted thigh angle from vertical | 35–90° | 25° | 0.8 |
| | foot_height | lifted ankle above standing ankle ÷ torso | 0.5–2.0 | 0.3 | 0.6 |
| | torso_upright | trunk tilt | 0–8° | 12° | 1.0 |
| | hips_level | hip line | 0–6° | 10° | 0.7 |
| Warrior II (side = front leg) | front_knee | front knee angle | 85–110° | 25° | 1.0 |
| | back_leg | back knee angle | 165–180° | 15° | 0.8 |
| | arms_level | max(shoulder→wrist angle from horizontal) | 0–10° | 20° | 0.8 |
| | arms_extended | min(elbow angles) | 160–180° | 25° | 0.6 |
| | torso_upright | trunk tilt | 0–8° | 12° | 1.0 |
| | shoulders_level | shoulder line | 0–6° | 10° | 0.5 |
| Chair (side view) | knee_bend | mean knee angle | 90–130° | 25° | 1.0 |
| | hips_back | mean shoulder–hip–knee angle | 75–120° | 25° | 0.9 |
| | torso_lean | trunk tilt | 15–40° | 15° | 0.9 |
| | arms_up | min(hip–shoulder–elbow) | 145–180° | 40° | 0.6 |
| Triangle (side = front leg) | side_bend | trunk tilt | 50–95° | 25° | 1.0 |
| | arm_line | wrist-to-wrist line from vertical | 0–12° | 20° | 0.9 |
| | legs_straight | min(knee angles) | 165–180° | 15° | 0.8 |
| | arms_extended | min(elbow angles) | 160–180° | 25° | 0.6 |

Not observable in 2D (documented, not scored): Warrior II and Triangle hip/chest orientation; Chair knee-over-toe position. Rationale for each metric is stored with the rule in `AsanaAssessmentProfiles.kt`.

Body types / camera: every metric is an angle or a torso-normalised distance → independent of camera distance and body size; line tilts use absolute angles → mirror-invariant. Thresholds are deliberately wide but **must be tuned with real people**.

## Scoring formulas
- **Alignment** = round(100 × Σ wᵢ·sᵢ / Σ wᵢ) over *available* metrics only.
  - **Unavailable (null)** when available metrics carry < 60 % of the profile weight, fewer than 2 metrics are available, no body, or no profile. Never 0.
  - Visibility reported separately = fraction of the 12 key joints reliable.
- **Stability**: window 3 s of reliable frames; points = mid-hip and mid-shoulder ÷ torso length; sway = mean of the two RMS distances from the window mean. Score = round(100 × clamp(1 − (sway − 0.02) / 0.12)). Null until ≥ 8 samples spanning ≥ 1.5 s; frames without a reliable torso skipped; gap > 1 s or pose change resets. Labels: ≥70 Steady, ≥40 Some sway, else Unsteady.
- **Stable hold**: counted only between consecutive frames where recognition is confirmed AND alignment is available AND stability is measured and ≥ 40 (configurable `HoldTracker.stabilityThreshold`). Non-qualifying frames pause; dropout > 1.5 s, leaving frame or pose change ends the segment; gaps > 0.5 s between frames are not counted. Hold milestones (10 s) now come from this timer.
- **Session**: practice duration (session clock, excludes pause) is reported separately from stable hold per pose. Mean alignment/stability per pose only from frames where the hold was counting and only with ≥ 10 samples, otherwise "not enough data".

## Corrective coaching
- `AssessmentCoach`: only the primary issue (highest severity × weight); it must persist 2 s on reliable metrics with full-body framing; re-emitted at most every 15 s (engine cooldown 12 s on top). Correction ids are structured: `WARRIOR_II.front_knee.high`, `TREE.torso_upright.high`, `CHAIR.torso_lean.low`, etc.
- **Improvement**: only for a correction that was given, when that metric is measured with score ≥ 0.85 continuously for 1.5 s → `AlignmentImproved` → "That's better. Keep it there." (engine also requires the correction within 60 s).
- **StabilityDecreased**: stability fell from ≥ 70 to < 45 → "Steady yourself. Find a fixed point and breathe." (once until it recovers).
- Framing/safety outrank corrections (existing priorities); Guided/Minimal speak corrections (Corrections toggle respected); Off is silent. Scores are never spoken.

## Live UI
- Assessment strip (shown only while a pose is confirmed): **Alignment** (score or "—" + "joints unclear"/visibility %), **Stability** (label + score or "measuring"), **Steady hold** timer (counting/paused).
- Guidance line priority: … framing → **one current correction** → "Holding <pose> · N s steady" → reason the hold isn't counting.
- The reference illustration remains a separate labelled drawing; no target skeleton is drawn.

## Session summary
Practice time; per recognised pose: steady hold total + longest, mean alignment (or "not enough data"), stability (or "not enough data"), corrections given (with counts), measured improvements; disclaimer that values are prototype single-camera estimates.

## Files
**Added (main)** — `coaching/assessment/`: `AssessmentModels.kt`, `AsanaAssessmentProfiles.kt`, `AsanaAlignmentAssessor.kt`, `StabilityAnalyzer.kt`, `HoldTracker.kt`, `AssessmentCoach.kt`, `SessionStats.kt`.
**Added (tests)** — `coaching/assessment/AssessmentFixtures.kt`, `AlignmentAssessorTest.kt` (20), `StabilityHoldCoachTest.kt` (24).
**Modified** — `asana/BodyFeatures.kt` (additive helpers only: shoulder/hip line tilt, lateral lean, thigh-from-vertical, mid points; recognition untouched); `coaching/CoachingModels.kt` (`StabilityDecreased` event; placeholder assessor interface removed); `coaching/CoachingContent.kt`; `coaching/CoachingDecisionEngine.kt` (one branch); `ui/practice/PracticeSession.kt` (`AssessmentPipeline`, summary); `ui/practice/PracticeGuidance.kt`; `ui/practice/PracticeHud.kt` (assessment strip, summary dialog); `ui/practice/LiveTrackingScreen.kt` (wiring); `test/.../CoachingEventSourceTest.kt` (call signature only; expectations unchanged except the new "steady" wording); `IMPLEMENTATION_PLAN.md`.
**Unchanged** — MediaPipe/CameraX pipeline, recognition engine, TTS manager, voice modes, Gradle.

## Test results
- `testDebugUnitTest`: **165 / 165 pass** (121 previous + 44 new).
- Coverage: correct pose for all five asanas (both sides where sided); incorrect variants (hitched shoulder, leaning Tree, straight Warrior knee, upright Chair, shallow Triangle); wrong-side detection; joint-angle calculations; occluded knee (no knee correction); low visibility (score null, no issues); too few metrics; unknown asana; mirroring invariance; camera distance and leg-length invariance; stability warm-up / jitter / sway / distance invariance / skipped frames / gap reset / reset; hold counting, pause, resume, segmenting, leaving frame, paused-app gaps; correction persistence, one-at-a-time, duplicate suppression, framing and unavailability suppression; improvement only after measured sustained improvement, not without prior correction, not from an occluded metric; stability-decrease events; engine handling across Guided/Minimal/Off and the Corrections toggle; full pipeline (no assessment without confirmed recognition, hold milestones from stable hold, pose-change reset, summary unavailable values, single correction counted, bad framing suppresses corrections).
- Synthetic fixtures validate logic only; they do not demonstrate real-person accuracy.

## Build
- `assembleDebug`: **success**.

## Device testing
- **Not performed.** No OPPO F15 or emulator was connected (the emulator had been stopped earlier because the PC was low on memory).
- Unverified: HUD strip layout at 360 dp, assessment CPU cost on the OPPO, spoken corrections in a real session, all real-person behaviour.

## Known limitations
- Single 2D camera: no depth, so rotation-based cues (hips/chest opening) are not assessed; foreshortening can distort angles when the user is not square to the camera.
- Thresholds are prototype guesses; stability noise floor (0.02 torso) and sway range (0.12) are untuned.
- Stability warm-up (≈1.5 s) delays the stable-hold timer.
- Assessment uses its own light smoothing; at 5–9 FPS jitter may still cause borderline metrics to flicker (the 2 s persistence rule limits spoken flicker; the on-screen correction can change faster).
- Recognition gating means a poorly performed pose that is not recognised gets no assessment at all.

## Real-world validation still required
1. A teammate performs each pose (correct, deliberately incorrect) at 2–3 m with the documented camera view; log metric values (Diagnostics) and adjust ideal bands/tolerances.
2. Measure stability noise on a person standing still to set the noise floor; check sway scores during deliberate wobble.
3. Check corrections are understandable, not too frequent, and never contradictory; check improvement announcements after real corrections.
4. Out-of-frame, occlusion and transition scenarios on the OPPO F15.
