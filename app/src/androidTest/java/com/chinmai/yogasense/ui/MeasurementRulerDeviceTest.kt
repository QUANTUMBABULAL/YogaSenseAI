package com.chinmai.yogasense.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chinmai.yogasense.data.RoomPersonalisationRepository
import com.chinmai.yogasense.data.RoomProfileRepository
import com.chinmai.yogasense.data.db.ExperienceLevel
import com.chinmai.yogasense.data.db.YogaSenseDatabase
import com.chinmai.yogasense.data.db.YogaSenseDatabase.Companion.configure
import com.chinmai.yogasense.profile.MeasurementScales
import com.chinmai.yogasense.profile.OnboardingStep
import com.chinmai.yogasense.profile.ProfileSetup
import com.chinmai.yogasense.ui.onboarding.OnboardingMode
import com.chinmai.yogasense.ui.onboarding.OnboardingScreen
import com.chinmai.yogasense.ui.onboarding.OnboardingViewModel
import com.chinmai.yogasense.ui.theme.YogaSenseAITheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real touch gestures on the rulers (throwaway in-memory database). Screenshots are written to
 * the app's files/ruler-shots/ for visual review (adb shell run-as … ls files/ruler-shots).
 */
@RunWith(AndroidJUnit4::class)
class MeasurementRulerDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: YogaSenseDatabase

    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(context, YogaSenseDatabase::class.java).configure().build() }
    @After fun tearDown() = db.close()

    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File(context.filesDir, "ruler-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun editor(step: OnboardingStep): Pair<OnboardingViewModel, RoomPersonalisationRepository> = runBlocking {
        val p = RoomProfileRepository(db).createProfile("Asha", ExperienceLevel.BEGINNER, ProfileSetup(requireOnboarding = false))
        val r = RoomPersonalisationRepository(db, p.userId)
        OnboardingViewModel(r, r.observe().first(), OnboardingMode.Edit(step)) to r
    }

    @Test fun heightRulerDragsSnapsConvertsAndSaves() {
        val (vm, repo) = editor(OnboardingStep.HEIGHT)
        compose.setContent { YogaSenseAITheme { OnboardingScreen(vm, "Asha", onExit = {}) } }
        shot("height-cm-untouched")
        compose.onNodeWithContentDescription("Height ruler").performTouchInput { swipeLeft(startX = centerX + width * 0.3f, endX = centerX - width * 0.1f, durationMillis = 600) }
        compose.waitForIdle()
        val cm = vm.controller.state.value.answers.heightCm
        assertNotNull("a drag chooses a value", cm)
        assertTrue("moved right of the 170 default", cm!! > 170f)
        assertEquals("settled exactly on a 1 cm increment", cm, Math.round(cm).toFloat())
        shot("height-cm-dragged")
        compose.onNodeWithContentDescription("Unit FT / IN").performClick()
        assertEquals("unit switch keeps the measurement", cm, vm.controller.state.value.answers.heightCm)
        compose.onNodeWithText(MeasurementScales.HEIGHT_IN.format(MeasurementScales.HEIGHT_IN.fromCanonical(cm))).assertExists()
        shot("height-ftin")
        compose.onNodeWithContentDescription("Height ruler").performTouchInput { swipeRight(startX = centerX - width * 0.2f, endX = centerX + width * 0.2f, durationMillis = 500) }
        compose.waitForIdle()
        val inches = vm.controller.state.value.answers.heightCm!! / 2.54f
        assertEquals("FT / IN settles on whole inches", inches, Math.round(inches).toFloat(), 1e-3f)
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        val stored = runBlocking { repo.observe().first() }
        assertEquals(vm.controller.state.value.answers.heightCm, stored.p.heightCm)
    }

    @Test fun weightRulerFlingSettlesInRangeAndLbsConvert() {
        val (vm, _) = editor(OnboardingStep.WEIGHT)
        compose.setContent { YogaSenseAITheme { OnboardingScreen(vm, "Asha", onExit = {}) } }
        shot("weight-kg-untouched")
        compose.onNodeWithContentDescription("Weight ruler").performTouchInput { swipeLeft(durationMillis = 120) } // fast fling
        compose.waitForIdle()
        val kg = vm.controller.state.value.answers.weightKg!!
        assertNotEquals(70f, kg)
        assertTrue(kg in 25f..300f)
        assertEquals("0.1 kg increments", kg * 10f, Math.round(kg * 10f).toFloat(), 1e-2f)
        shot("weight-kg-flung")
        compose.onNodeWithContentDescription("Unit LBS").performClick()
        assertEquals(kg, vm.controller.state.value.answers.weightKg)
        shot("weight-lbs")
        // Boundary: a huge fling to the right clamps at the minimum.
        repeat(6) { compose.onNodeWithContentDescription("Weight ruler").performTouchInput { swipeRight(durationMillis = 60) } }
        compose.waitForIdle()
        assertEquals(56, MeasurementScales.WEIGHT_LB.fromCanonical(vm.controller.state.value.answers.weightKg!!))
        shot("weight-lbs-min")
    }
}
