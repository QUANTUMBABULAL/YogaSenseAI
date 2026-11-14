package com.chinmai.yogasense.catalog

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.session.GuidedConfig
import com.chinmai.yogasense.session.GuidedEvent
import com.chinmai.yogasense.session.GuidedSessionMachine
import com.chinmai.yogasense.session.GuidedStage
import com.chinmai.yogasense.ui.learning.player.LessonMedia
import com.chinmai.yogasense.ui.learning.player.LessonMediaResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AsanaVideoAssetsTest {
    private val dir = File("src/main/assets/${AsanaVideoAssets.DIR}")
    private fun exists(path: String) = File("src/main/assets/$path").isFile

    /** Minimal container check: an MP4 starts with an 'ftyp' box. */
    private fun isMp4(f: File) = f.inputStream().use { s -> val b = ByteArray(8); s.read(b) == 8 && String(b, 4, 4, Charsets.US_ASCII) == "ftyp" }

    @Test fun everyMappedVideoExistsAndIsAnMp4() {
        AsanaVideoAssets.FILES.forEach { (asana, name) ->
            val f = File(dir, name)
            assertTrue("$asana → $name missing", f.isFile)
            assertTrue("$name is not an MP4", isMp4(f))
        }
        assertEquals("one clip per asana", AsanaVideoAssets.FILES.size, AsanaVideoAssets.FILES.values.toSet().size)
    }

    @Test fun eachAsanaResolvesToItsOwnRealVideo() {
        AsanaVideoAssets.FILES.forEach { (asana, name) ->
            val m = LessonMediaResolver.resolve(AsanaCatalog.of(asana)!!, ::exists)
            assertEquals(asana.name, LessonMedia.Video("asana/$name"), m) // a video, never the stick figure
        }
    }

    @Test fun chairHasNoVideoAndKeepsTheLabelledFallback() {
        val m = LessonMediaResolver.resolve(AsanaCatalog.of(Asana.CHAIR)!!, ::exists)
        assertTrue("$m", m is LessonMedia.AnimatedIllustration)
    }

    @Test fun everyBundledClipIsEitherMappedOrExplicitlyReportedAsUnmapped() {
        val bundled = dir.listFiles().orEmpty().filter { it.isFile }.map { it.name }.toSet()
        assertEquals(bundled, AsanaVideoAssets.FILES.values.toSet() + AsanaVideoAssets.UNMAPPED.keys)
        // Unsupported poses' clips are never shown for a catalogue asana.
        AsanaCatalog.all.forEach { a ->
            val m = LessonMediaResolver.resolve(a, ::exists)
            assertTrue((m as? LessonMedia.Video)?.assetPath?.substringAfter('/') !in AsanaVideoAssets.UNMAPPED.keys)
        }
    }

    @Test fun canonicalNameStillWinsIfAddedLater() {
        val tree = AsanaCatalog.of(Asana.TREE)!!
        assertEquals(LessonMedia.Video("asana/tree_pose.mp4"), LessonMediaResolver.resolve(tree) { true })
    }

    @Test fun duplicateOrReplayedEndCallbacksAdvanceTheGuidedSessionOnce() {
        val cfg = GuidedConfig()
        val m = GuidedSessionMachine(listOf(Asana.TREE, Asana.WARRIOR_II), cfg)
        var now = 0L
        m.start(now)
        fun tick(ms: Long) { var l = ms; while (l > 0) { now += 100; l -= 100; m.onEvent(GuidedEvent.Tick(now)) } }
        tick(cfg.introMs)
        val demoEpoch = m.state.epoch
        tick(10_030)                                           // the 10.03 s clip plays to the end
        repeat(3) { m.onEvent(GuidedEvent.DemoEnded(demoEpoch)) } // ended reported several times (and after a replay)
        assertEquals(GuidedStage.PREPARATION_COUNTDOWN, m.state.stage)
        assertEquals(Asana.TREE, m.state.current)
        m.onEvent(GuidedEvent.DemoEnded(demoEpoch))           // a late callback from Tree's video
        tick(cfg.prepMs)
        assertEquals(GuidedStage.WAITING_FOR_POSE, m.state.stage)
        assertEquals("never skipped ahead to the next asana", Asana.TREE, m.state.current)
    }
}
