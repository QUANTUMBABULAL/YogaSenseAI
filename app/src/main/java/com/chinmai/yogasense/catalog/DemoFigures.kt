package com.chinmai.yogasense.catalog

/**
 * Intermediate stick-figure keyframes used only by the animated-illustration fallback
 * (lead-in poses before each asana's final [AsanaInfo.figure]). Unit-box coordinates.
 */
internal object DemoFigures {
    private fun p(x: Float, y: Float) = x to y

    /** Relaxed, slightly slouched stance before aligning into Tadasana. */
    val RELAXED_STAND = PoseFigure(
        head = p(.50f, .13f),
        shoulderL = p(.585f, .26f), shoulderR = p(.415f, .26f),
        elbowL = p(.62f, .39f), elbowR = p(.38f, .39f),
        wristL = p(.64f, .51f), wristR = p(.36f, .51f),
        hipL = p(.555f, .53f), hipR = p(.445f, .53f),
        kneeL = p(.575f, .725f), kneeR = p(.425f, .725f),
        ankleL = p(.585f, .92f), ankleR = p(.415f, .92f),
    )

    /** Neutral front-facing stance (same as Tadasana). */
    val FRONT_STAND = PoseFigure(
        head = p(.50f, .11f),
        shoulderL = p(.58f, .24f), shoulderR = p(.42f, .24f),
        elbowL = p(.60f, .38f), elbowR = p(.40f, .38f),
        wristL = p(.61f, .51f), wristR = p(.39f, .51f),
        hipL = p(.555f, .52f), hipR = p(.445f, .52f),
        kneeL = p(.555f, .72f), kneeR = p(.445f, .72f),
        ankleL = p(.555f, .92f), ankleR = p(.445f, .92f),
    )

    /** Left foot lifted to the calf, hands together at the chest. */
    val TREE_PREP = PoseFigure(
        head = p(.50f, .12f),
        shoulderL = p(.58f, .24f), shoulderR = p(.42f, .24f),
        elbowL = p(.63f, .36f), elbowR = p(.37f, .36f),
        wristL = p(.515f, .33f), wristR = p(.485f, .33f),
        hipL = p(.55f, .52f), hipR = p(.45f, .52f),
        kneeL = p(.62f, .70f), kneeR = p(.45f, .72f),
        ankleL = p(.47f, .80f), ankleR = p(.45f, .92f),
    )

    /** Wide stance, legs straight, arms extended to shoulder height. */
    val WIDE_ARMS_OUT = PoseFigure(
        head = p(.50f, .15f),
        shoulderL = p(.57f, .27f), shoulderR = p(.43f, .27f),
        elbowL = p(.72f, .27f), elbowR = p(.28f, .27f),
        wristL = p(.88f, .27f), wristR = p(.12f, .27f),
        hipL = p(.55f, .52f), hipR = p(.45f, .52f),
        kneeL = p(.65f, .71f), kneeR = p(.35f, .71f),
        ankleL = p(.75f, .90f), ankleR = p(.25f, .90f),
    )

    /** Wider stance for Triangle, arms extended. */
    val WIDE_ARMS_OUT_LONG = WIDE_ARMS_OUT.copy(
        hipL = p(.55f, .56f), hipR = p(.45f, .56f),
        kneeL = p(.685f, .73f), kneeR = p(.315f, .73f),
        ankleL = p(.82f, .90f), ankleR = p(.18f, .90f),
    )

    /** Standing, seen from the side (facing image-right). */
    val SIDE_STAND = PoseFigure(
        head = p(.51f, .12f),
        shoulderL = p(.50f, .24f), shoulderR = p(.49f, .24f),
        elbowL = p(.51f, .38f), elbowR = p(.50f, .38f),
        wristL = p(.52f, .51f), wristR = p(.51f, .51f),
        hipL = p(.49f, .52f), hipR = p(.48f, .52f),
        kneeL = p(.50f, .72f), kneeR = p(.49f, .72f),
        ankleL = p(.50f, .92f), ankleR = p(.49f, .92f),
    )

    /** Half-way into Chair: knees bending, arms reaching forward. */
    val CHAIR_HALF = PoseFigure(
        head = p(.53f, .21f),
        shoulderL = p(.51f, .32f), shoulderR = p(.50f, .32f),
        elbowL = p(.62f, .33f), elbowR = p(.61f, .33f),
        wristL = p(.74f, .33f), wristR = p(.73f, .33f),
        hipL = p(.45f, .56f), hipR = p(.44f, .56f),
        kneeL = p(.56f, .66f), kneeR = p(.55f, .66f),
        ankleL = p(.53f, .91f), ankleR = p(.52f, .91f),
    )

    /** Lying on the back, knees bent, hands beside the ears (lead-in for Chakrasana), side view. */
    val SUPINE_KNEES_BENT = PoseFigure(
        head = .22f to .82f,
        shoulderL = .33f to .84f, shoulderR = .31f to .84f,
        elbowL = .30f to .70f, elbowR = .28f to .70f,
        wristL = .24f to .80f, wristR = .22f to .80f,
        hipL = .56f to .84f, hipR = .54f to .84f,
        kneeL = .70f to .60f, kneeR = .68f to .60f,
        ankleL = .78f to .86f, ankleR = .76f to .86f,
    )

    /** Lying on the front, hands under the shoulders (lead-in for Bhujangasana), side view. */
    val PRONE = PoseFigure(
        head = .84f to .80f,
        shoulderL = .74f to .82f, shoulderR = .72f to .82f,
        elbowL = .70f to .74f, elbowR = .68f to .74f,
        wristL = .75f to .86f, wristR = .73f to .86f,
        hipL = .52f to .84f, hipR = .50f to .84f,
        kneeL = .32f to .85f, kneeR = .30f to .85f,
        ankleL = .13f to .86f, ankleR = .11f to .86f,
    )
}
