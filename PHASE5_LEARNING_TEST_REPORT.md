# Phase 5 — Animated Asana Learning: Test Report

Date: 2026-11-13 · Build: debug

## Status summary
| Area | Status |
|---|---|
| Learning screen, navigation, content for all 5 poses | Implemented |
| Reusable player (video / animated illustration / static) | Implemented |
| Human demonstration videos | **Missing — none bundled** (player ready; drop-in) |
| On-screen verification of the learning screen | **Not completed** (see Device testing) |

## New files
- `catalog/DemoFigures.kt` — lead-in keyframes for the animated illustration.
- `ui/learning/LessonContent.kt` — lesson data read from the catalogue (single source of truth).
- `ui/learning/PoseLearningScreen.kt` — the lesson screen.
- `ui/learning/player/Playback.kt` — `DemoPlayback` interface, `PlaybackState`, `FigureTimeline`, `FigurePlayback`, `NoPlayback`.
- `ui/learning/player/LessonMedia.kt` — `LessonMedia` (Video / AnimatedIllustration / StaticIllustration) + `LessonMediaResolver`.
- `ui/learning/player/VideoPlayback.kt` — Media3 ExoPlayer wrapper for `asset:///` MP4s.
- `ui/learning/player/AsanaAnimationPlayer.kt` — the single reusable player composable.
- `ui/navigation/Routes.kt` — route patterns/builders (moved out of `YogaSenseApp.kt`).
- Tests: `ui/learning/LessonContentTest.kt` (6), `ui/learning/player/PlayerTest.kt` (16).
- `PHASE5_LEARNING_TEST_REPORT.md`.

## Modified files
- `catalog/AsanaCatalog.kt` — added `breathing`, `commonMistakes`, `mediaKey`, `demoLeadIn`, `demoNote`; derived `demoAsset`, `demoSequence`. Content written for all five poses.
- `ui/asanadetail/AsanaDetailScreen.kt` — main CTA **Start learning**; secondary **Practice now**. Overview, difficulty, duration, steps, focus points and safety retained; static `AsanaVisual` retained.
- `ui/navigation/YogaSenseApp.kt` — `learn/{id}` route; `practice?asana={asana}` optional argument.
- `ui/practice/LiveTrackingScreen.kt` — optional `targetName` (header text "Practising: …"); display only.
- `ui/components/YsIcons.kt` — Pause, Replay, Fullscreen, Wind, Alert icons.
- `ui/explore/ExploreScreen.kt` — grid min cell 156→140 dp (2 columns on 360 dp phones); shorter search placeholder.
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — Media3.
- `IMPLEMENTATION_PLAN.md` — status log.

## Dependencies added
- `androidx.media3:media3-exoplayer:1.10.1` and `androidx.media3:media3-ui-compose:1.10.1` (May 2026; minSdk 23, Compose ≥ 1.7.6, Kotlin ≥ 2.0.20 — all satisfied). No Gradle/AGP/Kotlin/SDK changes.

## Player implementation
- One composable, `AsanaAnimationPlayer(media, fallback, autoPlay, loop, caption)`, for every asana and format.
- `LessonMediaResolver`: bundled video if `assets/asana/<key>.mp4` exists → otherwise animated illustration from catalogue keyframes → otherwise static figure. Asset lookup runs off the main thread with a loading state.
- Video: Media3 `ExoPlayer` + `ContentFrame`, muted, looping, auto-play; position polled every 250 ms while playing; errors switch automatically to the fallback and the label says "Video unavailable · animated illustration".
- Animated illustration: deterministic `FigureTimeline` (hold → eased transition → final hold), driven by the Compose frame clock only while playing; labelled **"Animated illustration · not a video"**.
- Controls: play/pause, replay, progress bar, `m:ss / m:ss`, full-screen dialog (inline surface hidden while full screen).
- Lifecycle: pauses on `ON_PAUSE`, resumes on `ON_RESUME` only if it was playing; `release()` on leaving composition or when media changes (ExoPlayer released).
- New formats: add a `LessonMedia` subtype + one renderer branch; the learning screen is unchanged.

## Animation assets
Expected location: `app/src/main/assets/asana/`

| Asana | Expected file | Present | Currently shown |
|---|---|---|---|
| Tadasana | `tadasana.mp4` | ✗ | Animated illustration |
| Tree Pose | `tree_pose.mp4` | ✗ | Animated illustration (left side) |
| Warrior II | `warrior_ii.mp4` | ✗ | Animated illustration (left side) |
| Chair Pose | `chair_pose.mp4` | ✗ | Animated illustration (side view) |
| Triangle Pose | `triangle_pose.mp4` | ✗ | Animated illustration (left side) |

No videos were downloaded or generated. Recommended encoding when adding them: H.264 MP4, ≤ 720p, ~1.5–2.5 Mbps, silent, 8–20 s, portrait or square. Adding a file switches that lesson to video automatically (covered by `bundledAssetsResolveConsistently`).

## User journey
Explore → Asana detail → **Start learning** → Lesson → **Skip lesson** or **Continue to practice** → `practice?asana={id}` → existing LiveTrackingScreen.
- Skip/Continue replace the lesson (`popUpTo(learn) inclusive`), so Back from practice returns to the asana detail page — never to Explore.
- The practice header shows "Practising: <pose>". **The recognition engine does not use this argument**; it still detects all five poses.

## Tests
- `testDebugUnitTest`: **89 / 89 pass** (67 existing + 22 new).
- New coverage: lesson content completeness for all five poses; each id opens its own lesson; unknown id; Skip/Continue routes carry the selected id and never target Explore; route builders/patterns; resolver (video present / missing / no keyframes / real assets folder); keyframes inside the unit box; timeline endpoints and interpolation; playback auto-play, pause/resume without jump, end/restart, loop, replay, release (ignores later commands), toggle, time format.
- Not unit-tested: ExoPlayer itself (needs a device or Robolectric; not added).
- `assembleDebug`: **success**.

## Device testing
- **OPPO F15 (Android 11):** partial. Build installed and launched; Explore and Home render. **Verified** that the selected asana reaches practice (header showed "Practising: Mountain Pose" after Detail → Practice now). **Found and fixed** a layout bug (Explore grid collapsed to one oversized column at 360 dp). Testing was stopped because the phone was in active use (another app came to the foreground); the learning screen itself was **not** visually verified on the phone, and the grid fix is not yet installed there.
- **Emulator (API 37, density set to 480 to mimic the OPPO width):** Explore 2-column grid and Tree detail (Start learning / Practice now) verified. The emulator was then closed externally before the lesson screen could be captured. Emulator display density was left at 480 when it shut down — reset with `adb -s emulator-5554 shell wm density reset`.
- Not verified on any device: auto-play, pause/replay/full-screen interaction, lesson Skip/Continue taps, video playback (no assets), camera hand-off after a lesson.

## Known limitations
- No human demonstration videos; animated illustrations are simple stick figures and show one side for sided poses.
- Recognition does not use the selected asana.
- Lesson progress indicator reflects scroll position, not completion tracking.
- Home "Today's practice" hold pill wraps to two lines at 360 dp (cosmetic).

## Next steps for Phase 6
1. Manually verify on the OPPO: Explore → Tree → Start learning → pause/replay/full screen → Skip → practice → Back.
2. Source licensed or self-recorded MP4s and place them in `assets/asana/`.
3. Practice preparation that uses the selected asana (e.g. target-pose feedback), then scoring, coaching/TTS and Room.
