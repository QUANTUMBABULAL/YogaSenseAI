package com.chinmai.yogasense.ui.learning

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.ui.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonContentTest {

    @Test fun everyPoseHasCompleteLessonContent() {
        AsanaCatalog.all.forEach { a ->
            val l = LessonContent.of(a.id)!!
            assertTrue("${a.id} intro", l.intro.isNotBlank())
            assertTrue("${a.id} steps", l.steps.size >= 3)
            assertTrue("${a.id} breathing", l.breathing.isNotEmpty())
            assertTrue("${a.id} focus", l.focusPoints.isNotEmpty())
            assertTrue("${a.id} mistakes", l.commonMistakes.isNotEmpty())
            assertTrue("${a.id} safety", l.safety.isNotEmpty())
            (l.steps + l.breathing + l.focusPoints + l.commonMistakes + l.safety).forEach {
                assertTrue("${a.id} blank line", it.isNotBlank())
            }
        }
    }

    @Test fun eachPoseOpensItsOwnLesson() {
        val expected = mapOf(
            "TADASANA" to "Mountain Pose", "TREE" to "Tree Pose", "WARRIOR_II" to "Warrior II",
            "CHAIR" to "Chair Pose", "TRIANGLE" to "Triangle Pose",
        )
        expected.forEach { (id, name) ->
            val l = LessonContent.of(id)!!
            assertEquals(id, l.id)
            assertEquals(name, l.info.name)
            assertEquals(AsanaCatalog.byId(id)!!.steps, l.steps) // single source of truth
        }
        // Lessons are distinct, not one hard-coded pose.
        assertEquals(5, expected.keys.map { LessonContent.of(it)!!.steps }.toSet().size)
    }

    @Test fun unknownIdHasNoLesson() {
        assertNull(LessonContent.of("NOPE"))
        assertNull(LessonContent.of(null))
    }

    @Test fun skipAndContinueKeepTheSelectedAsana() {
        AsanaCatalog.all.forEach { a ->
            val l = LessonContent.of(a.id)!!
            assertEquals("prepare?asana=${a.id}", l.practiceRoute)
            assertTrue(!l.practiceRoute.contains("explore"))
        }
    }

    @Test fun sidedPosesSayWhichSideIsDemonstrated() {
        listOf(Asana.TREE, Asana.WARRIOR_II, Asana.TRIANGLE).forEach {
            assertTrue(LessonContent.of(it.name)!!.demoNote!!.contains("other side"))
        }
    }

    @Test fun routes() {
        assertEquals("learn/TREE", Routes.learn("TREE"))
        assertEquals("asana/CHAIR", Routes.asana("CHAIR"))
        assertEquals("practice", Routes.practice())
        assertEquals("practice?asana=WARRIOR_II", Routes.practice("WARRIOR_II"))
        assertEquals("learn/{id}", Routes.LEARN)
        assertEquals("practice?asana={asana}", Routes.PRACTICE)
        assertEquals("prepare", Routes.prepare())
        assertEquals("prepare?asana=TREE", Routes.prepare("TREE"))
        assertEquals("prepare?asana={asana}", Routes.PREPARE)
    }
}
