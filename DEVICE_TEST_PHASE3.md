# Phase 3.1 — On-device recognition test (OPPO F15)

## Setup
1. Install: `adb -s B65TRKDQGYYDCI9H install -r app/build/outputs/apk/debug/app-debug.apk`
2. Log recognition evidence while testing (separate terminal, keep running):
   `adb -s B65TRKDQGYYDCI9H logcat -s AsanaDebug:D > asana_log.txt`
   - `CONFIRMED <POSE> <SIDE>` lines = temporal confirmation events.
   - Once per second: frame result, closest template, every **off-range feature with its measured value and reference range**, low-confidence landmarks.
3. Phone upright at hip height, 2–3 m away, back camera, good front lighting, whole body in frame.
4. On screen, tap the recognition card to show per-feature evidence (✓ in range, ~ near, ✗ out).

## Checklist (hold each pose ~5 s; note time-to-confirm and what the card shows)

| # | Test | View | Expected | Result |
|---|---|---|---|---|
| 1 | Tadasana, arms by sides | Facing camera | Tadasana confirmed in ~1 s | |
| 2 | Tree, left foot lifted | Facing | Tree · Left foot lifted | |
| 3 | Tree, right foot lifted | Facing | Tree · Right foot lifted | |
| 4 | Warrior II, left knee bent | Facing, stance across frame | Warrior II · Left knee bent | |
| 5 | Warrior II, right knee bent | Facing | Warrior II · Right knee bent | |
| 6 | Chair | **Side-on** | Chair confirmed | |
| 7 | Triangle, left leg forward | Facing | Triangle · Left leg forward | |
| 8 | Triangle, right leg forward | Facing | Triangle · Right leg forward | |
| 9 | Transition 1→2→4→7→1 | Facing | Each confirmed in turn; no pose shown that was not performed | |
| 10 | Arms out sideways (T) standing | Facing | Not recognised | |
| 11 | Wide stance, both legs straight, arms out | Facing | Not recognised | |
| 12 | Tree with toes on floor | Facing | Not Tree | |
| 13 | Squat with upright trunk | Side-on | Not Chair | |
| 14 | Feet out of frame | Facing | Not recognised + "Keep your ankles in view" | |
| 15 | Dim light / partly behind a chair | Facing | Not recognised or correct; never a wrong confirmed pose | |
| 16 | Flip to front camera, repeat #1 | Facing | Resets, then Tadasana | |

## If a correct pose is NOT recognised
Copy the `AsanaDebug` lines for that period. The `off-range:` field names the feature, measured value
and reference range — tuning should widen only that range in `asana/AsanaReferences.kt`, then
re-run `./gradlew testDebugUnitTest` so look-alike rejection tests still pass.
