# Phase 5.2 — Immersive Live Practice + Voice Coaching: Test Report

Date: 2026-11-14 · Build: debug · Verified on: Android emulator (API 37, density 480 ≈ 360 × 800 dp, i.e. OPPO F15 width).
**Not verified on the OPPO F15** — it was not connected via ADB during this phase.

## UI changes
- Old layout (camera inside a card + chips + two large cards) replaced by an immersive layout:
  - **Top HUD:** Close, selected asana (or "Free practice"), camera dot + recognition state ("No pose yet" / "Detecting…" / confirmed pose name — never an unconfirmed pose), coach badge (Coach / Speaking / Voice off / No voice), camera switch.
  - **Camera surface:** takes all space between HUD and controls. Kept `FIT_CENTER` with 4:3 preview + analysis streams → full sensor field of view, no crop, no distortion, skeleton mapping unchanged. Rationale: `FILL_CENTER` or a 16:9 stream would crop width (arms in Warrior II / Triangle) or head/feet. HUD and controls never overlap the image on portrait screens.
  - **Guidance panel:** one current instruction (priority: paused → camera error → camera starting → no body → framing → holding *pose · N s* → "Hold still while the camera detects your pose" → "move into the pose"), optional reference illustration, and a caption of the last spoken cue (hidden when it repeats the instruction).
  - **Bottom controls:** pause/resume, voice settings, visual settings, session timer (excludes paused time), End.
  - **End:** summary dialog — time and longest *confirmed* hold per pose, labelled "not an alignment score"; Keep practising / Done.
  - **Diagnostics** (off by default, Visual settings): FPS, latency, reliable landmarks, the existing recognition card.
- Framing guidance now gives direction: *Step slightly to your left/right* (user's own frame, valid for both lenses).
- Visual options: Live skeleton; **Target skeleton shown as unavailable** (no calibrated target landmarks exist — not faked); Visibility highlights (rings low-confidence joints; off = only reliable joints drawn); Reference illustration (separate, labelled, catalogue figure — not drawn on the camera); Diagnostics.
- Auto-pause when the app goes to the background; user resumes explicitly.

## Voice architecture
```
real framing + confirmed recognition ──► CoachingEventSource (pure: debounce, stable-only, hold milestones)
                                          │ CoachEvent
AlignmentAssessor (interface; no impl) ──►│
                                          ▼
                                 CoachingDecisionEngine (pure: modes, settings, priority,
                                          │ Cue        cooldowns, de-dup, framing suppression)
                                          ▼
                                 VoiceCoachManager (Android TextToSpeech; once per session)
```
- Modes: **Guided** (default) — intro + instruction, breathing/instruction tips every 20 s while holding, 10 s countdown, hold-goal encouragement, completion; **Minimal** — safety, framing, corrections, hold-goal encouragement, completion; **Off** — silent, on-screen guidance unchanged.
- Rules: pose announced only for confirmed recognition with confidence ≥ 0.65; framing changes debounced 1.5 s; cooldowns (framing 8 s, correction 12 s, safety 120 s, others 30 s per message); global 4 s gap for non-urgent cues; no encouragement/tips/countdown while framing is bad; "That's better" only after a matching correction in the last 60 s; per-session reset.
- TTS: device locale → US → UK → English fallback; urgent cues flush the queue, others queue behind at most one item; stop on pause/End/Off; `shutdown()` when the practice screen leaves.
- Settings: mode, encouragement, countdown, corrections, speech speed (0.8–1.2) — persisted locally (SharedPreferences, no new dependency).
- Content: intro + 3 instructions for each of the five asanas (`CoachingContent`), breathing cues from the catalogue, safety reminder at start, framing text shared with the on-screen guidance.
- **No alignment corrections are spoken today** — the assessment engine does not exist yet; `AlignmentAssessor` is the integration point.

## Files
**Added**
- `coaching/CoachingModels.kt`, `CoachingContent.kt`, `CoachingEventSource.kt`, `CoachingDecisionEngine.kt`, `VoiceCoachManager.kt`, `CoachSettingsStore.kt`
- `ui/practice/PracticeSession.kt` (session glue + `SessionClock`), `PracticeGuidance.kt`, `PracticeHud.kt`, `PracticeSettingsSheet.kt`
- Tests: `coaching/CoachingDecisionEngineTest.kt` (17), `coaching/CoachingEventSourceTest.kt` (7 + clock/guidance 5 + settings codec 2)

**Modified**
- `ui/practice/LiveTrackingScreen.kt` — new layout; permission flow, `CameraPreview`, recognition controller and debug logging unchanged; new optional `targetAsana` parameter.
- `ui/practice/SkeletonOverlay.kt` — optional `highlightWeak` (default keeps previous behaviour) + rings for weak key joints.
- `pose/FramingGuidance.kt` — `OFF_CENTER` split into `STEP_LEFT` / `STEP_RIGHT`; clearer messages.
- `ui/navigation/YogaSenseApp.kt` — passes `targetAsana`.
- `test/.../pose/FramingGuidanceTest.kt` — directional test added.
- `IMPLEMENTATION_PLAN.md`.

Unchanged: MediaPipe pipeline, CameraX binding (`CameraPreview.kt`, `PoseFrameAnalyzer.kt`), recognition engine, Gradle/dependencies.

## Test results
- `testDebugUnitTest`: **121 / 121 pass** (89 previous + 32 new).
- `assembleDebug`: **success**.

## Device verification (emulator, ≈360 dp width)
| Check | Result |
|---|---|
| Full-screen camera, no crop, HUD/controls off the image | ✅ screenshot |
| Selected asana in HUD; reference figure | ✅ (Tree Pose) |
| Spoken framing cue + caption | ✅ caption shown; duplicate caption suppressed |
| TTS audio actually produced | ✅ `dumpsys audio` showed an active `CONTENT_TYPE_SPEECH` player after End ("Session complete. Well done.") |
| Voice / Visual sheets | ✅ render; target skeleton disabled with explanation; reference disabled in free practice |
| Settings persistence across app restart | ✅ (diagnostics stayed on; prefs file contents checked) |
| Pause freezes timer; Resume continues | ✅ 0:57 held, then 1:02 |
| Background → foreground auto-pause | ✅ |
| End → summary → Done returns to asana detail | ✅ |
| System back from practice → previous screen | ✅ |
| Camera/inference stopped after leaving | ✅ 0 recognition frames |
| Camera permission denied state + Close | ✅ |
| Crashes | none |

Not verified: OPPO F15 (layout, FPS impact, TTS voice/language on ColorOS), real human poses (stable-recognition cues, hold countdown and encouragement in practice), "camera temporarily unavailable" error state, front-camera mirroring with the new layout.

Note: an emulator restored from a snapshot showed a blank (unrendered) app until the stale app process was force-stopped; a fresh process rendered correctly. Not observed after a clean start.

## Known limitations
- No alignment assessment → no spoken corrections or "improved" praise yet (interface ready).
- Target skeleton unavailable (no target landmark source).
- Pause stops timer and coaching only; camera and inference keep running (battery).
- Recognition does not use the selected asana; a different confirmed pose is named ("That looks like…").
- Hold milestones depend on recognition stability; a brief recognition drop restarts the hold.

## Remaining tasks
1. Verify on the OPPO F15 with real people (see test steps in the final summary).
2. Implement alignment assessment and plug it into `AlignmentAssessor`.
3. Optional: pause camera analysis while paused to save battery.
