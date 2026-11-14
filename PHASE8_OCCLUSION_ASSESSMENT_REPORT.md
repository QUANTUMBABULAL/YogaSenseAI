# Phase 8 — Occlusion-Aware AI Assessment: Report

Date: 2026-11-14 · No database change · No new dependency · Recognition unchanged · Fully local.

**Verification status**
- 306/306 unit tests pass: 287 existing tests (unchanged) plus 19 new **synthetic** tests.
- The debug build succeeds.
- **Nothing in this phase has been tested on a person or on the OPPO.** The device test matrix in §8 is a plan. Synthetic tests check the logic only; they say nothing about real-world accuracy.

## 1. Assessment architecture (before)

`CameraX → MediaPipe Pose Landmarker Lite (33 landmarks) → LiveRecognitionController (AsanaRecognizer: smoothing → BodyFeatures → AsanaClassifier → StableAsanaTracker)` and, for a confirmed pose, `AssessmentPipeline: LandmarkSmoother → BodyFeatures → AsanaAlignmentAssessor (profiles) → StabilityAnalyzer → HoldTracker → AssessmentCoach → CoachingDecisionEngine → TTS`.

## 2. Problems identified

| # | Problem | Effect |
|---|---|---|
| 1 | Reliability was a single binary gate: visibility **and** presence ≥ 0.5. There was no partial category, no position check, and no time dimension. | Low-confidence (0.5–0.65) and predicted off-frame coordinates were used as measurements. |
| 2 | Many metrics required both sides (`min2`/`max2`/`avg2`, midline torso). | One hidden arm killed "arms level"; a hidden far leg killed Chair's knee bend. |
| 3 | The trunk tilt needed both shoulders and both hips. | In a profile view (far side hidden) the torso metric, and every metric depending on it, was unavailable. |
| 4 | Shoulder- and hip-**level** lines were computed in profile views, where the two points overlap. | Meaningless angles could be scored and corrected. |
| 5 | Chair knee/hip angles were scored when facing the camera (foreshortened). | The same for Warrior II knee angles in profile. |
| 6 | Stability used only the midline. | In profile, stability was never measured, so **holds never counted**. |
| 7 | `AssessmentCoach` needed `FramingStatus.FULL_BODY`, which requires both knees, ankles and shoulders. | In profile views, corrections were silenced and the screen said "Keep your feet visible" even when nothing was cut off. |
| 8 | There was no way to ask for a camera change, and confidence wasn't separated from the score (`visibility` was a fraction of 12 fixed joints). | — |
| 9 | Recognition (`AsanaClassifier`) needs the midline torso and treats every feature as required. | **Left unchanged** (tuned from your device log); see Limitations. |

## 3. Files

**Created**
- `pose/LandmarkReliability.kt`: `LandmarkState`, `LandmarkReliability`, `ReliabilityTracker`, `CameraOrientation`, `OrientationDetector`, `OcclusionAwareFraming`.
- `test/…/coaching/assessment/OcclusionAssessmentTest.kt`: 19 tests.
- This report.

**Modified**
- `coaching/assessment/`:
  - `AsanaAssessmentProfiles.kt`: visibility profiles, variants, views, critical metrics.
  - `AsanaAlignmentAssessor.kt`: adaptive scoring, confidence, camera hints.
  - `AssessmentModels.kt`: reasons, coverage, confidence, `framingAccepted`.
  - `StabilityAnalyzer.kt`: single-side fallback.
  - `AssessmentCoach.kt`: camera-adjustment requests.
- `coaching/`: `CoachingModels.kt` (new `CameraAdjustmentNeeded` event) and `CoachingDecisionEngine.kt` (speaks it).
- `asana/BodyFeatures.kt`: additive single-side features, and `frame` exposed. Existing features, which recognition uses, are unchanged.
- `pose/LandmarkSmoother.kt`: opt-in reset on reliability change (assessment only).
- `ui/practice/`:
  - `PracticeSession.kt` (pipeline wiring);
  - `PracticeGuidance.kt` (camera hint; side-view framing);
  - `PracticeHud.kt` (posture score, confidence, metrics measured, metrics not measured).
- `test/…/asana/PoseFixtures.kt`: additive `hide()` and `presence`. The existing `occlude()` is unchanged.

## 4. Visibility and confidence model

**Landmark states** (`LandmarkReliability.classify`)

| State | Rule | Used as a measurement? |
|---|---|---|
| RELIABLE | visibility ≥ 0.65, presence ≥ 0.5, inside the image | **Yes**, the only state that is |
| PARTIAL | 0.5 ≤ visibility < 0.65 | No; reported as "low confidence" |
| OCCLUDED | presence ≥ 0.5 but visibility < 0.5 | No; reported as "hidden" |
| MISSING | presence < 0.5, predicted outside the image, or no body | No |

- **Hidden joints are never reconstructed or carried forward.**
- The assessment smoother doesn't blend a joint that has just reappeared with MediaPipe's earlier prediction for it.

**Temporal tracking** (`ReliabilityTracker`): a joint unusable for under 2 s is a *transient* loss, and a camera hint is never given for it. Two seconds or more is *persistent*, and only then can a camera hint be given.

**Orientation** (`OrientationDetector`)
- Takes the wider of the shoulder and hip spans perpendicular to the trunk axis, divided by trunk length.
- ≥ 0.45 is FRONT, ≤ 0.20 is SIDE, anything between is ANGLED.
- If one whole side is visible and the other hidden, it is SIDE.
- Because the span is measured perpendicular to the trunk axis, a tilted-trunk front view such as Triangle is still recognised as FRONT.

**Asana visibility profiles.** Each metric lists, per variant, the joints it needs and the views where its 2D geometry is meaningful. Variants are tried in order.

| Asana (intended view) | Metric → requirements (✱ = critical) |
|---|---|
| Tadasana (front) | ✱ torso upright: midline any view, or the visible side's shoulder→hip line in profile · shoulders level / hips level / weight centred: front only · ✱ legs straight: both legs, or one visible leg ("one side only") |
| Tree (front) | ✱ standing leg: any view · knee opened out: front only · foot height: midline, or one-side trunk normalisation · ✱ torso upright: midline or side line · hips level: front only |
| Warrior II (front) | ✱ front knee and back leg: front only (foreshortened in profile) · arms level / arms extended: both arms, or **one visible arm** · ✱ torso upright: midline or side line · shoulders level: front only |
| Chair (**side**) | ✱ knee bend and ✱ hips back: side only, both legs or the visible leg · torso lean: side only, midline or side line · arms raised: any view, both arms or one |
| Triangle (front) | ✱ side bend: midline or one side line · arms in one line: front · ✱ legs straight and arms extended: both, or one visible |

**Confidence is reported separately from the posture score:**
- formula: coverage × joint quality × view factor × critical factor;
- joint quality maps the mean visibility of the measured joints from 0.65 → 0.6 up to 1.0 → 1.0;
- view factor is 1.0 in the intended orientation, otherwise 0.85;
- critical factor is 0.75 if a key metric is missing;
- levels: HIGH ≥ 0.7, MEDIUM ≥ 0.5, LOW below that, and "Not enough data" when the score is withheld.

## 5. Scoring changes

- Only **evaluated** metrics are scored: score = Σ(wᵢ·sᵢ) / Σwᵢ over them. This re-weighting is transparent and covered by a test.
- An unavailable metric carries a reason: *hidden*, *low confidence* or *not visible from this angle*. It is never shown or scored as 0 and never produces a correction.
- **Coverage** is evaluated weight divided by the profile's **total** weight. Metrics not measurable from the current view count as not covered.
- **The score is withheld below 60% coverage or with fewer than 2 metrics.** A test shows that perfect shoulder/arm metrics alone do not produce a score.
- Critical metrics don't veto the score: an existing test requires a Warrior II score when only the front knee is hidden (85% coverage remains). Instead, they lower confidence and trigger camera hints.
- **Live UI:**
  - The strip shows **Posture** (score), with "*n/m measured*", "*adjust camera*" or "*not enough visible*" underneath.
  - A new line reads "*Confidence High · 5/6 measured · some on one side only · Not measured: Front knee (hidden)*".
- **Voice coaching:**
  - Corrections come only from evaluated metrics when the score is available, so a hidden limb is never corrected.
  - Improvement is claimed only after a measured, sustained score ≥ 0.85 on that metric. A hidden metric has no score, so it can never "improve".
  - When key joints stay persistently hidden, out of frame, or unmeasurable from this angle, the user hears **one** camera instruction at most every 20 s:
    - "Turn sideways to the camera for this pose"
    - "Face the camera so your whole front is visible"
    - "Turn slightly so the camera can see your left knee"
    - "Step back so your whole body is in view" (only when a joint is actually off-frame)
- **Framing:** in a SIDE view, "show your feet/head" is accepted when the failing landmarks are inside the image (hidden behind the body, not cut off) and the near side is reliable. Front views keep the original guidance, and `FramingGuidance` is unchanged.
- **Stable hold:**
  - Profile views now get stability from the visible side.
  - A brief occlusion (≤ 1.5 s) pauses the hold without ending it; occluded time is not counted.
  - A longer occlusion ends the segment.
  - A one-side dropout during an established midline window is skipped, never a reset.

## 6. Tests and results

`./gradlew :app:testDebugUnitTest` gives **306 tests, 0 failures**. That includes all 287 existing tests: recognition, all five asanas' assessment cases, mirroring, scale, stability, hold, coach and pipeline, with no expected result changed.

The 19 new synthetic tests:
- **Reliability (3):**
  - four-state classification, including low presence and off-frame;
  - transient vs persistent loss, with the timer restarting on recovery;
  - orientation for Tadasana, Triangle (tilted), Chair, a hidden far side, and a sideways-turned Warrior.
- **Assessment (11):**
  - fully visible: all five asanas have coverage 1.0, HIGH confidence and no hints;
  - one arm hidden: the visible arm is measured and flagged one-side;
  - one leg hidden: Tadasana stays available; with the Warrior front knee hidden, it isn't scored or corrected and confidence drops;
  - multiple missing: withheld, "Not enough data";
  - low confidence (0.55): nothing scored, no corrections;
  - adaptive score equals the re-weighted mean, and coverage equals evaluated/total;
  - a small subset can't produce a score;
  - side-view Chair with the far side hidden: scored via the visible side (≥ 90);
  - Tadasana in profile: level lines are "not visible from this angle", the score is withheld, and the user is asked to face the camera;
  - Warrior turned sideways: knee not scored, face-camera hint; Chair facing the camera: turn-sideways hint;
  - camera hint only after 2 s of persistence.
- **Coaching (3):**
  - no repeat of a hidden knee's correction and no unmeasured "improvement";
  - camera hint rate limit (2 in 30 s);
  - occlusion-aware framing (side only; not when cut off; not for other statuses).
- **Hold (2):** a brief occlusion pauses but keeps the segment; a long occlusion ends it.

Not executed: any real-camera test. There are no new instrumented tests, because this logic is pure Kotlin and tested on the JVM.

## 7. Build

`assembleDebug` and `assembleDebugAndroidTest` succeed.

## 8. OPPO F15 physical test matrix (to be performed; nothing here has been run yet)

Setup:
- Phone at hip height, 2.5–3 m away, portrait, back camera.
- Even lighting, plain background, fitted clothing.
- Debug build. Turn on **Visual settings → Diagnostics** to see FPS and landmark counts.
- Read the strip: Posture, Confidence, "n/m measured", "Not measured: …".
- Optional log: `adb -s B65TRKDQGYYDCI9H logcat -s YsPerf AsanaDebug`.

| # | Pose / orientation | Do | Expect (to verify) | Record |
|---|---|---|---|---|
| 1 | Warrior II, chest to camera (front) | Hold 20 s, each side | Recognised; 6/6 measured; Confidence High; knee correction if the knee is too straight | Recognised Y/N, metrics measured, confidence, correction sensible Y/N, hold counted |
| 2 | Warrior II, side-on (camera sees your side) | Hold 20 s | Knees "not visible from this angle"; Posture "—" with "adjust camera"; spoken "Face the camera…" at most every 20 s | Same, plus whether recognition happened at all |
| 3 | Tree, standing leg partly hidden (e.g. a chair in front of the shin) | Hold 20 s | Standing-leg metric "hidden"; no leg correction; after ≈ 2 s, "Turn slightly so the camera can see your … ankle/knee" | Same |
| 4 | Chair from the side (intended) | Hold 20 s | Knee/hip/torso measured (possibly "one side only"); stability measured; hold counts | Same |
| 5 | Chair facing the camera | Hold 15 s | Knee/hip "not visible from this angle"; "Turn sideways…" | Same |
| 6 | Triangle, chest to camera | Hold 20 s, each side | Side bend, arm line and legs measured; one hand may be "one side only" | Same |
| 7 | Tadasana, facing the camera | Hold 20 s | 5/5 measured, High | Same |
| 8 | Tadasana in profile | Hold 15 s | Level lines "not visible from this angle"; score withheld; "Face the camera…" | Same |
| 9 | Brief occlusion during any hold | Pass a hand in front of a knee for about 1 s | Hold pauses, then continues (not reset); no camera hint | Hold before/after |

For every row, also note false corrections, missed corrections, wrong hints, and the stability and FPS shown in diagnostics.

## 9. Remaining limitations

- **Recognition still requires the midline torso and every feature.** A side view in which MediaPipe reports the far shoulder or hip below 0.5 may not be recognised at all, so the assessment above never starts. Matrix rows 2, 4 and 8 will show how often this happens. Relaxing recognition would mean retuning the classifier against real data, which this phase deliberately doesn't do.
- Thresholds (0.65 / 0.5 visibility, 2 s persistence, 0.45 / 0.20 orientation, 60% coverage) are prototype values that need tuning from the matrix. MediaPipe also sometimes reports high visibility for a hidden far-side joint; the model can only use what MediaPipe reports.
- Orientation is 2D. "ANGLED" allows all metrics, as before, with a lower confidence factor.
- Per-frame assessment still runs on the main thread, as before (its cost is logged in debug builds).
- Coaching texts are English only.
- Synthetic tests say nothing about real-world accuracy.

## 10. Device test instructions

See the final message of this phase.
