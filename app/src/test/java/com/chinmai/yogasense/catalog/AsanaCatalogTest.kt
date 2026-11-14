package com.chinmai.yogasense.catalog

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.asana.AsanaReferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsanaCatalogTest {
    private val all = AsanaCatalog.all

    @Test fun liveAsanasAreExactlyTheRecognisedOnesAndTheRestAreLessonOnly() {
        assertEquals(AsanaReferences.ALL.map { it.asana }.toSet(), AsanaCatalog.live.map { it.asana }.toSet())
        assertEquals(setOf(Asana.BHUJANGASANA, Asana.CHAKRASANA), all.filterNot { it.liveSupported }.map { it.asana }.toSet())
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }

    @Test fun entriesAreComplete() {
        all.forEach { a ->
            assertTrue(a.name.isNotBlank() && a.englishName.isNotBlank() && a.summary.isNotBlank())
            assertTrue("${a.id} steps", a.steps.size >= 3)
            assertTrue("${a.id} safety", a.safety.isNotEmpty())
            assertTrue("${a.id} categories", a.categories.isNotEmpty())
            assertTrue("${a.id} hold", a.holdSeconds.first in 5..a.holdSeconds.last)
        }
    }

    @Test fun cameraViewMatchesRecognitionEngineLimits() {
        assertEquals(CameraView.SIDE, AsanaCatalog.of(Asana.CHAIR)!!.cameraView)
        AsanaCatalog.live.filter { it.asana != Asana.CHAIR }.forEach { assertEquals(CameraView.FRONT, it.cameraView) }
    }

    @Test fun figuresStayInsideUnitBox() {
        all.forEach { a ->
            with(a.figure) {
                listOf(head, shoulderL, shoulderR, elbowL, elbowR, wristL, wristR, hipL, hipR, kneeL, kneeR, ankleL, ankleR)
                    .forEach { (x, y) -> assertTrue("${a.id} $x,$y", x in 0f..1f && y in 0f..1f) }
            }
        }
    }

    @Test fun lookupById() {
        assertEquals(Asana.TREE, AsanaCatalog.byId("TREE")!!.asana)
        assertNull(AsanaCatalog.byId("NOPE"))
        assertNull(AsanaCatalog.byId(null))
    }

    // ---- AsanaFilter ----------------------------------------------------------------------

    private fun ids(q: String = "", d: Difficulty? = null, c: Category? = null) =
        AsanaFilter.apply(all, q, d, c).map { it.asana }.toSet()

    @Test fun emptyQueryReturnsAll() = assertEquals(all.map { it.asana }.toSet(), ids())

    @Test fun searchIsCaseInsensitiveAndMatchesSanskritAndCategory() {
        assertEquals(setOf(Asana.TREE), ids("tReE"))
        assertEquals(setOf(Asana.TRIANGLE), ids("trikon"))
        assertEquals(setOf(Asana.TREE), ids("balance"))
        assertEquals(setOf(Asana.WARRIOR_II), ids("  warrior "))
        assertTrue(ids("xyz").isEmpty())
    }

    @Test fun difficultyAndCategoryFiltersCombine() {
        assertEquals(setOf(Asana.TRIANGLE), ids(d = Difficulty.INTERMEDIATE))
        assertEquals(setOf(Asana.WARRIOR_II, Asana.CHAIR, Asana.BHUJANGASANA, Asana.CHAKRASANA), ids(c = Category.STRENGTH))
        assertEquals(setOf(Asana.WARRIOR_II, Asana.CHAIR, Asana.BHUJANGASANA), ids(d = Difficulty.BEGINNER, c = Category.STRENGTH))
        assertEquals(setOf(Asana.CHAKRASANA), ids(d = Difficulty.ADVANCED))
        assertTrue(ids("tree", c = Category.STRENGTH).isEmpty())
    }
}
