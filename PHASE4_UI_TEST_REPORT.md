# Phase 4 — UI/UX Redesign: Test Report

Date: 2026-11-13 · Build: debug · Tested on: Android emulator (Medium_Phone_API_37.0, 1080×2400).
**Not tested on the OPPO F15** — it was not connected via ADB during this phase.

## Files created
- `catalog/AsanaCatalog.kt` — `AsanaInfo`, `Difficulty`, `Category`, `CameraView`, `PoseFigure`, `AsanaCatalog` (5 asanas), `AsanaFilter`.
- `data/PracticeHistoryRepository.kt` — `WeeklySummary`, `SessionRecord`, repository interface, `NoPracticeHistory` (empty).
- `ui/components/AsanaVisuals.kt` — `AsanaFigure`, `AsanaVisual` (Phase 5 slot), `AsanaCard`.
- `ui/components/BottomNavigationBar.kt`
- `ui/explore/ExploreScreen.kt`, `ui/explore/ExploreViewModel.kt`
- `ui/asanadetail/AsanaDetailScreen.kt`
- Tests: `catalog/AsanaCatalogTest.kt` (8), `ui/explore/ExploreViewModelTest.kt` (3).

## Files modified
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — added `androidx.navigation:navigation-compose:2.9.6`.
- `MainActivity.kt` — minimal; hosts `YogaSenseApp`.
- `ui/theme/Color.kt`, `Type.kt`, `Dimens.kt`, `Theme.kt` — new design system; light + `PracticeTheme`; `SystemBarIcons`.
- `ui/components/Components.kt`, `YsIcons.kt` — component library and additional icons.
- `ui/navigation/YogaSenseApp.kt` — Navigation Compose graph.
- `ui/home/HomeScreen.kt`, `HomeUiState.kt` (+ `HomeViewModel`), `ui/progress/ProgressScreen.kt` (+ `ProgressViewModel`), `ui/profile/ProfileScreen.kt` — redesigned.
- `ui/practice/LiveTrackingScreen.kt` — **only** an optional `onClose` parameter shown in the header. Camera, permission, overlay and recognition code unchanged.
- `res/values/themes.xml`, `res/values-v31/themes.xml` — warm-white window/splash background.
- `test/.../ui/home/HomeStateFactoryTest.kt` — updated for new Home state.
- `IMPLEMENTATION_PLAN.md` — status log.

## Screens
| Screen | Content | Data |
|---|---|---|
| Home | Brand + profile shortcut, greeting, tagline; Today's practice (rotating suggestion, difficulty, hold, Start practice, "How to do this pose"); check-in tiles (Coming soon); This week; Explore carousel; Recent activity | Empty states — no fabricated numbers |
| Explore | Search, difficulty chips, category chips, result count, adaptive grid, no-results state with Clear filters | Catalogue |
| Asana detail | Back, illustration (labelled *Static illustration*), names, tags, hold/camera/sides facts, summary, steps, focus points, safety + disclaimer, sticky Start practice | Catalogue |
| Progress | Practice overview (— values + empty state), Weekly activity, Asana performance (No sessions yet), Wellness overview + Weekly report entries (Coming soon) | Repository (empty) |
| Profile | Guest/local profile; **Available now**: camera permission (live, request/settings), privacy; **Coming in future updates**: practice, voice, visual, notifications; About | Live permission state |
| Live Practice | Existing `LiveTrackingScreen` in dark `PracticeTheme`, full screen, Close action | Unchanged pipeline |

## Navigation behaviour (verified on emulator)
- 4 bottom tabs; selection state, per-tab saved state and scroll restored (`saveState`/`restoreState`).
- Back from any tab → Home (scroll position restored). Back on Home → exits app.
- Asana detail and Live Practice are full-screen (bottom bar hidden).
- Practice → **Close** or system back → returns to the previous screen (asana detail).
- Leaving Practice stops the pipeline: AsanaDebug frames 9 while open → **0** in 4 s after Close.
- Status-bar icons: dark on light screens, light on Practice.

## Functional checks (emulator)
| Check | Result |
|---|---|
| Explore initial | 5 of 5 |
| Search "trikon" | 1 — Triangle Pose |
| Difficulty Intermediate | 1 — Triangle Pose |
| Intermediate + Balance | 0 → "No poses match" + Clear filters |
| Clear filters | 5 of 5 |
| Category Strength | 2 — Warrior II, Chair Pose |
| All five detail pages | Open; steps, Start practice, Back present |
| Start practice (detail) | Opens live camera; Camera on, inference + recognition running |
| Crashes | None (`FATAL EXCEPTION` absent) |

## Build and unit tests
- `assembleDebug`: **success**.
- `testDebugUnitTest`: **67 / 67 pass** (previous 56 + 11 new). Recognition engine tests unchanged and passing.

## Issues found and fixed during testing
- Explore grid span lambda compile error (fixed).
- Chair figure torso collapsed to a hairline in side view; arms overlapped head (fixed: spine stroke + arm keyframes).
- Emulator package installer was wedged (installs hung); resolved by cold-booting the emulator. Not an app issue.

## Known limitations
- No OPPO F15 verification of the new UI this phase.
- Illustrations are static stylised figures (clearly labelled), not photos or animations.
- Check-ins, session recording, Room, weekly report, preferences, notifications: entry points only (Coming soon).
- Practice screen retains its Phase 2/3 layout (in the new dark palette); full redesign is a later phase.
- No separate practice-preparation flow; Start practice opens the live camera directly and does not yet pass the chosen asana.
- Accessibility: contrast chosen for AA (Coral buttons use Midnight text); not yet audited with TalkBack or large font scales.

## Recommended next steps (Phase 5)
1. Implement the animation player inside `AsanaVisual` (all callers update automatically); extend `PoseFigure` to keyframe sequences.
2. Add a practice-preparation step that passes the selected asana into the live screen.
3. Re-test the new UI and the camera flow on the OPPO F15.
