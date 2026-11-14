package com.chinmai.yogasense.catalog

import com.chinmai.yogasense.asana.Asana

/**
 * Real demonstration videos bundled in `app/src/main/assets/asana/`, mapped to catalogue asanas.
 *
 * The files keep the exact names they were provided with (nothing is renamed). Each mapping was
 * verified by looking at frames of the clip, not inferred from the filename alone (2026-11-14):
 *  - tadasana.mp4                      720×1280, 5.09 s  — standing Mountain Pose
 *  - Vrukshasana-Tree pose.mp4        1280×720, 10.03 s — lifts foot to the standing leg, hands at chest
 *  - Warrior pose-Veerabhadrasana.mp4 1280×720, 10.03 s — wide stance, front knee bent, arms out (Warrior II)
 *  - Trikonasana.mp4                  1280×720, 10.03 s — wide stance moving into the side bend
 *  - bhujangasana.mp4 / chakrasana.mp4 — lesson-only poses (no live recognition)
 * Chair (Utkatasana) has no video and keeps the labelled illustration fallback.
 */
object AsanaVideoAssets {
    const val DIR = "asana"

    val FILES: Map<Asana, String> = mapOf(
        Asana.TADASANA to "tadasana.mp4",
        Asana.TREE to "Vrukshasana-Tree pose.mp4",
        Asana.WARRIOR_II to "Warrior pose-Veerabhadrasana.mp4",
        Asana.TRIANGLE to "Trikonasana.mp4",
        Asana.BHUJANGASANA to "bhujangasana.mp4",
        Asana.CHAKRASANA to "chakrasana.mp4",
    )

    /** Bundled clips with no catalogue asana (none at present). */
    val UNMAPPED: Map<String, String> = emptyMap()

    /** Asset paths to try, in order: the catalogue's canonical name, then the provided file. */
    fun candidates(info: AsanaInfo): List<String> =
        listOfNotNull(info.demoAsset, FILES[info.asana]?.let { "$DIR/$it" }).distinct()
}
