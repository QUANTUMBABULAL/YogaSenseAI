package com.chinmai.yogasense.data.room

import com.chinmai.yogasense.data.RoomProfileRepository
import com.chinmai.yogasense.data.db.ExperienceLevel
import com.chinmai.yogasense.data.db.YogaSenseDatabase
import com.chinmai.yogasense.profile.ActiveProfileManager
import com.chinmai.yogasense.profile.ActiveProfileStore
import com.chinmai.yogasense.profile.CreateResult
import com.chinmai.yogasense.profile.ProfileGate
import com.chinmai.yogasense.profile.ProfileLifecycle
import com.chinmai.yogasense.profile.ProfileSetup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActiveProfileRoomTest {
    private lateinit var db: YogaSenseDatabase
    private lateinit var repo: RoomProfileRepository
    private val scopes = mutableListOf<CoroutineScope>()

    private class MemStore(var id: String? = null) : ActiveProfileStore {
        override fun read() = id
        override fun write(userId: String?) { id = userId }
    }

    @Before fun setUp() { db = JvmRoom.inMemory(); repo = RoomProfileRepository(db) }
    @After fun tearDown() { scopes.forEach { it.cancel() }; db.close() }

    private fun manager(store: ActiveProfileStore, lifecycle: ProfileLifecycle = object : ProfileLifecycle {}) =
        ActiveProfileManager(repo, store, lifecycle, CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }, Dispatchers.IO)

    private suspend fun ActiveProfileManager.awaitGate(p: (ProfileGate) -> Boolean) = withTimeout(5_000) { gate.first(p) }

    @Test fun noProfilesThenCreateActivatesAndPersists() = runBlocking<Unit> {
        val store = MemStore()
        val m = manager(store)
        m.awaitGate { it == ProfileGate.NoProfiles }
        val p = (m.create("Asha", ExperienceLevel.BEGINNER) as CreateResult.Created).profile
        val g = m.awaitGate { it is ProfileGate.Active } as ProfileGate.Active
        assertEquals(p, g.profile)
        assertEquals(p.userId, store.id)
        assertEquals(p.userId, m.activeUserId.value)
    }

    @Test fun restoresTheSavedProfileAfterRestart() = runBlocking<Unit> {
        val store = MemStore()
        val p = (manager(store).create("Asha", ExperienceLevel.BEGINNER) as CreateResult.Created).profile
        val restarted = manager(store) // new process: same stored id, same database
        assertEquals(p.userId, (restarted.awaitGate { it is ProfileGate.Active } as ProfileGate.Active).profile.userId)
    }

    @Test fun storedIdOfADeletedProfileIsClearedOnRestore() = runBlocking<Unit> {
        val a = repo.createProfile("A", ExperienceLevel.BEGINNER)
        repo.createProfile("B", ExperienceLevel.BEGINNER)
        val store = MemStore(a.userId)
        repo.deleteProfile(a.userId) // deleted while the app was closed
        val m = manager(store)
        val g = m.awaitGate { it is ProfileGate.ChooseProfile } as ProfileGate.ChooseProfile
        assertEquals(listOf("B"), g.profiles.map { it.displayName })
        withTimeout(5_000) { while (store.id != null) kotlinx.coroutines.delay(10) }
        assertNull(m.activeUserId.value)
    }

    @Test fun listAndSwitchBetweenProfiles() = runBlocking<Unit> {
        val m = manager(MemStore())
        val a = (m.create("A", ExperienceLevel.BEGINNER) as CreateResult.Created).profile
        val b = (m.create("B", ExperienceLevel.ADVANCED) as CreateResult.Created).profile
        assertEquals(b.userId, (m.awaitGate { it is ProfileGate.Active && it.profile.userId == b.userId } as ProfileGate.Active).profile.userId)
        m.clear()
        val choose = m.awaitGate { it is ProfileGate.ChooseProfile } as ProfileGate.ChooseProfile
        assertEquals(listOf(a, b), choose.profiles)
        assertTrue(m.select(a.userId))
        assertEquals(a, (m.awaitGate { it is ProfileGate.Active && it.profile.userId == a.userId } as ProfileGate.Active).profile)
        assertFalse(m.select("no-such-profile"))
        assertEquals(a.userId, m.activeUserId.value) // unchanged
    }

    @Test fun deletingTheActiveProfileReturnsToSelection() = runBlocking<Unit> {
        val store = MemStore()
        val m = manager(store)
        val a = (m.create("A", ExperienceLevel.BEGINNER) as CreateResult.Created).profile
        val b = (m.create("B", ExperienceLevel.BEGINNER) as CreateResult.Created).profile
        m.delete(b.userId) // active
        val g = m.awaitGate { it is ProfileGate.ChooseProfile && it.profiles.size == 1 } as ProfileGate.ChooseProfile
        assertEquals(listOf(a), g.profiles)
        assertTrue(m.gate.value !is ProfileGate.Active)
        assertNull(store.id)
        m.select(a.userId)
        m.delete(a.userId)
        m.awaitGate { it == ProfileGate.NoProfiles }
    }

    @Test fun deletingAnotherProfileKeepsTheActiveOne() = runBlocking<Unit> {
        val m = manager(MemStore())
        val a = (m.create("A", ExperienceLevel.BEGINNER) as CreateResult.Created).profile
        val b = (m.create("B", ExperienceLevel.BEGINNER) as CreateResult.Created).profile
        m.select(a.userId)
        m.delete(b.userId)
        assertEquals(listOf(a), (m.awaitGate { it is ProfileGate.Active && it.profiles.size == 1 } as ProfileGate.Active).profiles)
    }

    @Test fun repeatedTapsCreateOnlyOneProfile() = runBlocking<Unit> {
        val release = CompletableDeferred<Unit>()
        val slow = object : ProfileLifecycle {
            override suspend fun setupFor(isFirstProfile: Boolean): ProfileSetup { release.await(); return ProfileSetup() }
        }
        val m = manager(MemStore(), slow)
        val taps = (1..5).map { async(Dispatchers.Default) { m.create("Asha", ExperienceLevel.BEGINNER) } }
        withTimeout(5_000) { while (!m.creating.value) kotlinx.coroutines.delay(5) }
        release.complete(Unit)
        val results = taps.awaitAll()
        assertEquals(1, results.count { it is CreateResult.Created })
        assertEquals(4, results.count { it == CreateResult.Ignored })
        assertEquals(1, repo.profileCount())
    }

    @Test fun invalidNameCreatesNothing() = runBlocking<Unit> {
        val m = manager(MemStore())
        assertTrue(m.create("   ", ExperienceLevel.BEGINNER) is CreateResult.Invalid)
        assertTrue(m.create("x".repeat(41), ExperienceLevel.BEGINNER) is CreateResult.Invalid)
        assertEquals(0, repo.profileCount())
        assertFalse(m.creating.value)
    }

    @Test fun firstProfileFlagIsPassedToLifecycle() = runBlocking<Unit> {
        val seen = mutableListOf<Boolean>()
        val m = manager(MemStore(), object : ProfileLifecycle {
            override suspend fun setupFor(isFirstProfile: Boolean): ProfileSetup { seen += isFirstProfile; return ProfileSetup() }
        })
        m.create("A", ExperienceLevel.BEGINNER); m.create("B", ExperienceLevel.BEGINNER)
        assertEquals(listOf(true, false), seen)
    }
}
