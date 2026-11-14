package com.chinmai.yogasense.catalog

import com.chinmai.yogasense.asana.Asana

enum class Difficulty(val label: String) { BEGINNER("Beginner"), INTERMEDIATE("Intermediate") }

enum class Category(val label: String) { STANDING("Standing"), BALANCE("Balance"), STRENGTH("Strength"), STRETCH("Stretch") }

/** Which way to face the phone for live recognition (matches the recognition engine's limits). */
enum class CameraView(val label: String) { FRONT("Face the camera"), SIDE("Side-on to the camera") }

/**
 * Stick-figure keyframe in a unit box (x right, y down; subject faces the viewer, so the
 * subject's LEFT side is at larger x). Used for illustrations and the animated illustration
 * fallback only — never for recognition.
 */
data class PoseFigure(
    val head: Pair<Float, Float>,
    val shoulderL: Pair<Float, Float>, val shoulderR: Pair<Float, Float>,
    val elbowL: Pair<Float, Float>, val elbowR: Pair<Float, Float>,
    val wristL: Pair<Float, Float>, val wristR: Pair<Float, Float>,
    val hipL: Pair<Float, Float>, val hipR: Pair<Float, Float>,
    val kneeL: Pair<Float, Float>, val kneeR: Pair<Float, Float>,
    val ankleL: Pair<Float, Float>, val ankleR: Pair<Float, Float>,
)

/** Catalogue entry. [asana] links the library to the live recognition engine. */
data class AsanaInfo(
    val asana: Asana,
    val name: String,
    val sanskrit: String,
    val difficulty: Difficulty,
    val categories: List<Category>,
    val holdSeconds: IntRange,
    val perSide: Boolean,
    val summary: String,
    val steps: List<String>,
    val focusPoints: List<String>,
    val safety: List<String>,
    val breathing: List<String>,
    val commonMistakes: List<String>,
    /** Base file name of the bundled demo video, e.g. "tree_pose" -> assets/asana/tree_pose.mp4. */
    val mediaKey: String,
    /** Lead-in keyframes for the animated illustration; [figure] is always the final frame. */
    val demoLeadIn: List<PoseFigure>,
    /** Note shown with demonstrations, e.g. which side is shown. */
    val demoNote: String?,
    val cameraView: CameraView,
    val figure: PoseFigure,
) {
    val id: String get() = asana.name
    val demoAsset: String get() = "asana/$mediaKey.mp4"
    val demoSequence: List<PoseFigure> get() = demoLeadIn + figure
    val primaryCategory: Category get() = categories.first()
    val holdLabel: String get() = "${holdSeconds.first}–${holdSeconds.last} s" + if (perSide) " / side" else ""
}

/**
 * Initial library: the five asanas the recognition engine supports.
 * Instruction text follows common beginner guidance; it is general wellness information,
 * not medical advice.
 */
object AsanaCatalog {
    private fun p(x: Float, y: Float) = x to y

    val all: List<AsanaInfo> = listOf(
        AsanaInfo(
            asana = Asana.TADASANA,
            name = "Mountain Pose", sanskrit = "Tadasana",
            difficulty = Difficulty.BEGINNER,
            categories = listOf(Category.STANDING),
            holdSeconds = 30..60, perSide = false,
            summary = "The foundation of standing poses. Builds postural awareness and a steady, grounded stance.",
            steps = listOf(
                "Stand with feet together or hip-width apart, weight spread evenly through both feet.",
                "Straighten the legs without locking the knees.",
                "Lengthen the spine and relax the shoulders down and back.",
                "Let the arms rest by your sides, palms facing in.",
                "Breathe slowly and hold steady.",
            ),
            focusPoints = listOf("Even weight in both feet", "Tall spine", "Relaxed shoulders"),
            safety = listOf("Keep a soft bend in the knees if they tend to hyperextend.", "Sit down if you feel light-headed."),
            breathing = listOf(
                "Breathe slowly through the nose.",
                "Inhale to lengthen up through the crown of the head.",
                "Exhale to relax the shoulders and settle into both feet.",
            ),
            commonMistakes = listOf(
                "Locking the knees straight.",
                "Leaning the weight into the heels or toes.",
                "Lifting or hunching the shoulders.",
            ),
            mediaKey = "tadasana",
            demoLeadIn = listOf(DemoFigures.RELAXED_STAND),
            demoNote = null,
            cameraView = CameraView.FRONT,
            figure = PoseFigure(
                head = p(.50f, .11f),
                shoulderL = p(.58f, .24f), shoulderR = p(.42f, .24f),
                elbowL = p(.60f, .38f), elbowR = p(.40f, .38f),
                wristL = p(.61f, .51f), wristR = p(.39f, .51f),
                hipL = p(.555f, .52f), hipR = p(.445f, .52f),
                kneeL = p(.555f, .72f), kneeR = p(.445f, .72f),
                ankleL = p(.555f, .92f), ankleR = p(.445f, .92f),
            ),
        ),
        AsanaInfo(
            asana = Asana.TREE,
            name = "Tree Pose", sanskrit = "Vrksasana",
            difficulty = Difficulty.BEGINNER,
            categories = listOf(Category.BALANCE, Category.STANDING),
            holdSeconds = 20..30, perSide = true,
            summary = "A single-leg balance that trains focus, ankle stability and hip mobility.",
            steps = listOf(
                "Begin in Mountain Pose and shift your weight onto one foot.",
                "Place the other foot on the inner calf or inner thigh — never on the knee.",
                "Press foot and leg into each other and keep the hips level.",
                "Bring palms together at the chest or raise the arms overhead.",
                "Fix your gaze on a still point, hold, then switch sides.",
            ),
            focusPoints = listOf("Standing leg strong", "Hips level", "Steady gaze"),
            safety = listOf("Practise near a wall or chair for support.", "Avoid pressing the foot against the side of the knee."),
            breathing = listOf(
                "Keep the breath slow and even — it helps the balance.",
                "Inhale as you raise the arms, exhale to settle into the standing leg.",
            ),
            commonMistakes = listOf(
                "Pressing the foot against the knee joint.",
                "Letting the standing hip push out to the side.",
                "Holding the breath while balancing.",
            ),
            mediaKey = "tree_pose",
            demoLeadIn = listOf(DemoFigures.FRONT_STAND, DemoFigures.TREE_PREP),
            demoNote = "Shows the left foot lifted — repeat on the other side.",
            cameraView = CameraView.FRONT,
            figure = PoseFigure(
                head = p(.50f, .17f),
                shoulderL = p(.58f, .29f), shoulderR = p(.42f, .29f),
                elbowL = p(.62f, .15f), elbowR = p(.38f, .15f),
                wristL = p(.515f, .03f), wristR = p(.485f, .03f),
                hipL = p(.55f, .55f), hipR = p(.45f, .55f),
                kneeL = p(.69f, .64f), kneeR = p(.45f, .74f),
                ankleL = p(.475f, .68f), ankleR = p(.45f, .93f),
            ),
        ),
        AsanaInfo(
            asana = Asana.WARRIOR_II,
            name = "Warrior II", sanskrit = "Virabhadrasana II",
            difficulty = Difficulty.BEGINNER,
            categories = listOf(Category.STRENGTH, Category.STANDING),
            holdSeconds = 20..30, perSide = true,
            summary = "A wide, powerful stance that strengthens the legs and builds stamina.",
            steps = listOf(
                "Step the feet wide apart, about one leg-length.",
                "Turn the front foot out 90° and the back foot in slightly.",
                "Bend the front knee toward a right angle, knee over the ankle.",
                "Keep the back leg straight and the torso upright.",
                "Extend the arms parallel to the floor and look over the front hand.",
            ),
            focusPoints = listOf("Front knee over ankle", "Torso upright", "Arms level"),
            safety = listOf("Do not let the front knee pass beyond the toes.", "Shorten the stance if the knee or hip feels strained."),
            breathing = listOf(
                "Inhale as you extend the arms out to the sides.",
                "Exhale as you bend the front knee.",
                "Keep breathing steadily while you hold.",
            ),
            commonMistakes = listOf(
                "Front knee drifting inward or past the toes.",
                "Leaning the torso over the front leg.",
                "Letting the arms drop below shoulder height.",
            ),
            mediaKey = "warrior_ii",
            demoLeadIn = listOf(DemoFigures.FRONT_STAND, DemoFigures.WIDE_ARMS_OUT),
            demoNote = "Shows the left knee bent — repeat on the other side.",
            cameraView = CameraView.FRONT,
            figure = PoseFigure(
                head = p(.50f, .18f),
                shoulderL = p(.57f, .30f), shoulderR = p(.43f, .30f),
                elbowL = p(.72f, .30f), elbowR = p(.28f, .30f),
                wristL = p(.88f, .30f), wristR = p(.12f, .30f),
                hipL = p(.55f, .55f), hipR = p(.45f, .55f),
                kneeL = p(.72f, .64f), kneeR = p(.33f, .72f),
                ankleL = p(.72f, .89f), ankleR = p(.20f, .89f),
            ),
        ),
        AsanaInfo(
            asana = Asana.CHAIR,
            name = "Chair Pose", sanskrit = "Utkatasana",
            difficulty = Difficulty.BEGINNER,
            categories = listOf(Category.STRENGTH),
            holdSeconds = 20..30, perSide = false,
            summary = "A seated-in-the-air squat that builds strength in the thighs, glutes and back.",
            steps = listOf(
                "Stand with feet together or hip-width apart.",
                "Bend the knees and sit the hips back as if onto a chair.",
                "Keep the weight in the heels and the knees behind the toes.",
                "Lean the chest slightly forward while lengthening the spine.",
                "Raise the arms alongside the ears and hold.",
            ),
            focusPoints = listOf("Weight in heels", "Long spine", "Knees together"),
            safety = listOf("Reduce the depth if you feel knee discomfort.", "Keep arms lower if your shoulders feel strained."),
            breathing = listOf(
                "Inhale as you raise the arms.",
                "Exhale as you bend the knees and sit back.",
                "Keep the breath smooth; do not hold it.",
            ),
            commonMistakes = listOf(
                "Knees moving far past the toes.",
                "Rounding the lower back.",
                "Lifting the heels off the floor.",
            ),
            mediaKey = "chair_pose",
            demoLeadIn = listOf(DemoFigures.SIDE_STAND, DemoFigures.CHAIR_HALF),
            demoNote = "Shown from the side, the view the camera needs for this pose.",
            cameraView = CameraView.SIDE,
            figure = PoseFigure(
                head = p(.56f, .26f),
                shoulderL = p(.52f, .37f), shoulderR = p(.51f, .37f),
                elbowL = p(.65f, .25f), elbowR = p(.64f, .25f),
                wristL = p(.78f, .13f), wristR = p(.77f, .13f),
                hipL = p(.42f, .60f), hipR = p(.41f, .60f),
                kneeL = p(.61f, .66f), kneeR = p(.60f, .66f),
                ankleL = p(.56f, .91f), ankleR = p(.55f, .91f),
            ),
        ),
        AsanaInfo(
            asana = Asana.TRIANGLE,
            name = "Triangle Pose", sanskrit = "Trikonasana",
            difficulty = Difficulty.INTERMEDIATE,
            categories = listOf(Category.STRETCH, Category.STANDING),
            holdSeconds = 20..30, perSide = true,
            summary = "A side-bending stretch that opens the hamstrings, hips and side body.",
            steps = listOf(
                "Step the feet wide apart and turn the front foot out 90°.",
                "Keep both legs straight without locking the knees.",
                "Reach forward over the front leg, then tilt the torso sideways.",
                "Rest the lower hand on the shin, a block or the floor.",
                "Extend the top arm straight up, stacking the shoulders.",
            ),
            focusPoints = listOf("Both legs straight", "Long side body", "Arms in one line"),
            safety = listOf("Place the lower hand higher on the leg if you feel strain.", "Keep the neck neutral; look down if looking up is uncomfortable."),
            breathing = listOf(
                "Inhale to lengthen the spine before you tilt.",
                "Exhale as you reach down over the front leg.",
                "Keep breathing into the side body while you hold.",
            ),
            commonMistakes = listOf(
                "Bending the front knee.",
                "Collapsing the chest toward the floor.",
                "Forcing the lower hand to the floor.",
            ),
            mediaKey = "triangle_pose",
            demoLeadIn = listOf(DemoFigures.FRONT_STAND, DemoFigures.WIDE_ARMS_OUT_LONG),
            demoNote = "Shows the left leg forward — repeat on the other side.",
            cameraView = CameraView.FRONT,
            figure = PoseFigure(
                head = p(.78f, .37f),
                shoulderL = p(.73f, .49f), shoulderR = p(.65f, .39f),
                elbowL = p(.735f, .61f), elbowR = p(.645f, .26f),
                wristL = p(.74f, .73f), wristR = p(.64f, .12f),
                hipL = p(.55f, .56f), hipR = p(.45f, .56f),
                kneeL = p(.685f, .73f), kneeR = p(.315f, .73f),
                ankleL = p(.82f, .90f), ankleR = p(.18f, .90f),
            ),
        ),
    )

    fun byId(id: String?): AsanaInfo? = all.firstOrNull { it.id == id }
    fun of(asana: Asana): AsanaInfo? = all.firstOrNull { it.asana == asana }
}

/** Pure search + filter used by Explore. Query matches English name, Sanskrit name and category. */
object AsanaFilter {
    fun apply(
        items: List<AsanaInfo>,
        query: String,
        difficulty: Difficulty?,
        category: Category?,
    ): List<AsanaInfo> {
        val q = query.trim().lowercase()
        return items.filter { a ->
            (difficulty == null || a.difficulty == difficulty) &&
                (category == null || category in a.categories) &&
                (q.isEmpty() || a.name.lowercase().contains(q) || a.sanskrit.lowercase().contains(q) ||
                    a.categories.any { it.label.lowercase().contains(q) })
        }
    }
}
