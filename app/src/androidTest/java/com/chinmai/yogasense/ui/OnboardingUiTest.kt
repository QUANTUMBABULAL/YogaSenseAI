package com.chinmai.yogasense.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chinmai.yogasense.data.RoomPersonalisationRepository
import com.chinmai.yogasense.data.RoomProfileRepository
import com.chinmai.yogasense.data.db.ExperienceLevel
import com.chinmai.yogasense.data.db.YogaSenseDatabase
import com.chinmai.yogasense.data.db.YogaSenseDatabase.Companion.configure
import com.chinmai.yogasense.pose.PoseLandmarkerEngine
import com.chinmai.yogasense.profile.OnboardingStatus
import com.chinmai.yogasense.profile.PracticeFrequency
import com.chinmai.yogasense.ui.onboarding.OnboardingMode
import com.chinmai.yogasense.ui.onboarding.OnboardingScreen
import com.chinmai.yogasense.ui.onboarding.OnboardingViewModel
import com.chinmai.yogasense.ui.theme.YogaSenseAITheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: YogaSenseDatabase

    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(context, YogaSenseDatabase::class.java).configure().build() }
    @After fun tearDown() = db.close()

    @Test fun everyScreenRendersAndCompletionIsStored() {
        val (repo, view, userId) = runBlocking {
            val p = RoomProfileRepository(db).createProfile("Asha", ExperienceLevel.BEGINNER)
            val r = RoomPersonalisationRepository(db, p.userId)
            Triple(r, r.observe().first(), p.userId)
        }
        var exited = false
        val vm = OnboardingViewModel(repo, view, OnboardingMode.Required)
        compose.setContent { YogaSenseAITheme { OnboardingScreen(vm, "Asha", onExit = { exited = true }) } }

        compose.onNodeWithText("Let's make yoga yours.").assertIsDisplayed()
        compose.onNodeWithText("Let's begin").performClick()

        compose.onNodeWithText("How tall are you?").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("Slide the ruler to choose").assertIsDisplayed()  // untouched: nothing chosen
        compose.onNodeWithContentDescription("Height ruler").performSemanticsAction(SemanticsActions.SetProgress) { it(169f) }
        compose.onNodeWithContentDescription("Increase").performClick()        // 169 → 170 cm
        compose.onNodeWithText("≈ 5′ 7″").assertIsDisplayed()                   // live conversion
        compose.onNodeWithContentDescription("Unit FT / IN").performClick()
        compose.onNodeWithText("5′ 7″").assertIsDisplayed()
        compose.onNodeWithText("≈ 170 cm").assertIsDisplayed()
        compose.onNodeWithContentDescription("Unit CM").performClick()
        compose.onNodeWithText("Continue").assertIsEnabled().performClick()

        compose.onNodeWithText("What's your current weight?").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("170").assertIsDisplayed()                       // answer kept after Back
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Skip").performClick()                           // weight skipped

        compose.onNodeWithText("How familiar are you with yoga?").assertIsDisplayed()
        compose.onNodeWithText("Intermediate").performClick()
        compose.onNodeWithText("Continue").performClick()

        compose.onNodeWithText("Which areas would you like us to pay extra attention to?").assertIsDisplayed()
        compose.onNodeWithText("Hips").performClick()
        compose.onNodeWithText("Continue").performClick()

        compose.onNodeWithText("What would you like to improve?").assertIsDisplayed()
        compose.onNodeWithText("Balance").performClick()
        compose.onNodeWithText("Continue").performClick()

        compose.onNodeWithText("How much time can you give yourself?").assertIsDisplayed()
        compose.onNodeWithContentDescription("10 minutes").performClick()
        compose.onNodeWithText("Continue").performClick()

        compose.onNodeWithText("How often would you like to practise?").assertIsDisplayed()
        compose.onNodeWithText("Every day").performClick()
        compose.onNodeWithText("Continue").performClick()

        compose.onNodeWithText("Your journey starts here.").assertIsDisplayed()
        assertFalse(exited)
        compose.onNodeWithText("Explore my YogaSenseAI").performClick()
        compose.waitUntil(5_000) { exited }

        val stored = runBlocking { RoomPersonalisationRepository(db, userId).observe().first() }
        assertEquals(OnboardingStatus.COMPLETED, stored.p.status)
        assertEquals(170f, stored.p.heightCm)
        assertEquals(null, stored.p.weightKg)                                    // skipped stays empty
        assertEquals(ExperienceLevel.INTERMEDIATE, stored.experienceLevel)
        assertEquals(10, stored.preferredMinutes)
        assertEquals(PracticeFrequency.DAILY, stored.p.weeklyFrequency)
        assertTrue(!stored.mustOnboard)
    }
}

/** Camera/MediaPipe lifecycle: after close, the engine accepts no frames and publishes nothing. */
@RunWith(AndroidJUnit4::class)
class PoseEngineLifecycleTest {
    @Test fun closedEngineRejectsFrames() {
        val engine = PoseLandmarkerEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue("model loaded", engine.stats.value.ready)
        assertTrue(engine.canAccept())
        engine.close()
        assertFalse(engine.canAccept())
        val bmp = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        assertFalse(engine.submit(bmp, 64, 48))
        assertEquals(null, engine.frames.value)
        engine.close() // idempotent
    }
}
