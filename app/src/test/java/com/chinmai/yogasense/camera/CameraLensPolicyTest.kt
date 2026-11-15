package com.chinmai.yogasense.camera

import androidx.camera.core.CameraSelector.LENS_FACING_BACK
import androidx.camera.core.CameraSelector.LENS_FACING_FRONT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraLensPolicyTest {
    private val both = setOf(LENS_FACING_FRONT, LENS_FACING_BACK)

    @Test fun guidedSessionDefaultsToTheFrontCamera() {
        assertEquals(LENS_FACING_FRONT, CameraLensPolicy.defaultLens(guided = true))
    }

    @Test fun freePracticeKeepsItsExistingBackCameraDefault() {
        assertEquals(LENS_FACING_BACK, CameraLensPolicy.defaultLens(guided = false))
    }

    @Test fun choicesMapToTheCameraXLensConstants() {
        assertEquals(LENS_FACING_FRONT, CameraChoice.FRONT.lensFacing)
        assertEquals(LENS_FACING_BACK, CameraChoice.BACK.lensFacing)
        assertEquals(CameraChoice.BACK, CameraChoice.of(LENS_FACING_BACK))
    }

    @Test fun theSelectedCameraIsUsedWhenAvailable() {
        for (lens in both) {
            val r = CameraLensPolicy.resolve(lens, both)!!
            assertEquals(lens, r.bound)
            assertFalse(r.fellBack)
            assertNull(CameraLensPolicy.fallbackMessage(r))
        }
    }

    @Test fun missingBackCameraFallsBackToFrontWithAClearMessage() {
        val r = CameraLensPolicy.resolve(LENS_FACING_BACK, setOf(LENS_FACING_FRONT))!!
        assertEquals(LENS_FACING_FRONT, r.bound)
        assertTrue(r.fellBack)
        assertEquals("Back camera isn't available on this device — using the front camera instead.", CameraLensPolicy.fallbackMessage(r))
    }

    @Test fun missingFrontCameraFallsBackToBack() {
        val r = CameraLensPolicy.resolve(LENS_FACING_FRONT, setOf(LENS_FACING_BACK))!!
        assertEquals(LENS_FACING_BACK, r.bound)
        assertNotNull(CameraLensPolicy.fallbackMessage(r))
    }

    @Test fun noCameraAtAllGivesNoResolution() {
        assertNull(CameraLensPolicy.resolve(LENS_FACING_FRONT, emptySet()))
    }
}
