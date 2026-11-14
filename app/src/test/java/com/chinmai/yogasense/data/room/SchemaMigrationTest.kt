package com.chinmai.yogasense.data.room

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.chinmai.yogasense.data.db.ConsentDecision
import com.chinmai.yogasense.data.db.YogaSenseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Executes the real v1 → v2 migration on SQLite: builds a v1 database from the exported 1.json,
 * fills it, then opens it with the current Room database (which runs MIGRATION_1_2 and validates
 * the result against the v2 schema compiled into the app).
 */
class SchemaMigrationTest {
    private val file = File.createTempFile("yogasense-mig", ".db").also { it.delete() }
    private val schemaDir = File("schemas/com.chinmai.yogasense.data.db.YogaSenseDatabase")

    @Before @After fun clean() { listOf("", "-journal", "-wal", "-shm").forEach { File(file.path + it).delete() } }

    /** createSql statements of an exported schema (entities + indices). */
    private fun createStatements(version: Int): List<String> {
        val json = File(schemaDir, "$version.json").readText()
        val tables = Regex("\"tableName\":\\s*\"(\\w+)\"").findAll(json).map { it.groupValues[1] }.toList()
        // Each entity block lists its createSql first, then its indices' createSql.
        val out = mutableListOf<String>()
        val blocks = json.split("\"tableName\":").drop(1)
        blocks.forEachIndexed { i, b ->
            Regex("\"createSql\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(b).forEach { m ->
                out += m.groupValues[1].replace("\\\"", "\"").replace("\${TABLE_NAME}", tables[i])
            }
        }
        return out.filterNot { it.contains("room_master_table") }
    }

    private fun createV1WithData() {
        BundledSQLiteDriver().open(file.path).apply {
            createStatements(1).forEach { execSQL(it) }
            execSQL("INSERT INTO user_profiles (user_id,display_name,experience_level,created_at,updated_at,status) VALUES ('u1','Asha','BEGINNER',1,1,'ACTIVE')")
            execSQL("INSERT INTO user_preferences (user_id,voice_mode,spoken_encouragement,voice_countdown,correction_announcements,speech_rate,live_skeleton_enabled,target_skeleton_enabled,visual_corrections_enabled,reference_figure_enabled,preferred_practice_minutes,updated_at) VALUES ('u1','MINIMAL',1,1,1,1.0,1,0,1,1,NULL,1)")
            execSQL("INSERT INTO practice_sessions (session_id,user_id,started_at,ended_at,duration_ms,completion) VALUES ('s1','u1',10,20,10,'COMPLETED')")
            execSQL("INSERT INTO pose_results (result_id,session_id,asana,alignment_score,stability_score,stable_hold_total_ms,longest_hold_ms) VALUES ('r1','s1','TREE',NULL,55,5000,5000)")
            execSQL("INSERT INTO wellness_check_ins (check_in_id,user_id,epoch_day,recorded_at,mood,stress,sleep_hours,sleep_quality,reflection_ciphertext) VALUES ('c1','u1',100,9,5,3,7.5,NULL,NULL)")
            execSQL("INSERT INTO wellness_assessments (assessment_id,user_id,instrument,instrument_version,completed_at,total_score,safety_flag) VALUES ('a1','u1','GAD7','v',9,3,NULL)")
            execSQL("INSERT INTO consent_records (consent_id,user_id,consent_type,consent_version,granted_at,withdrawn_at) VALUES ('k1','u1','WELLNESS_DATA_STORAGE','v1',5,NULL)")
            // Room's identity row for v1, so the open is treated as an upgrade of THIS schema.
            val identity = Regex("\"identityHash\":\\s*\"(\\w+)\"").find(File(schemaDir, "1.json").readText())!!.groupValues[1]
            execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$identity')")
            execSQL("PRAGMA user_version = 1")
            close()
        }
    }

    private fun open(migrations: Boolean) = Room.databaseBuilder(stub, YogaSenseDatabase::class.java, file.path)
        .apply { if (migrations) addMigrations(*YogaSenseDatabase.MIGRATIONS) }
        .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO)
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).allowMainThreadQueries() // WAL, as on the phone
        .build()

    private val stub = object : android.content.ContextWrapper(null) {
        override fun getApplicationContext() = this
        override fun getDatabasePath(name: String) = File(name).absoluteFile
    }

    @Test fun v1ToV2PreservesEveryRowAndValidatesSchema() = runBlocking<Unit> {
        createV1WithData()
        val db = open(migrations = true)
        assertEquals("Asha", db.profiles().findById("u1")!!.displayName)
        assertEquals(55, db.practice().poseResults("u1", "s1").single().stabilityScore)
        assertNull(db.practice().poseResults("u1", "s1").single().alignmentScore)
        val c = db.wellness().checkIns("u1").single()
        assertEquals(5, c.mood); assertEquals(3, c.stress); assertEquals(7.5f, c.sleepHours); assertNull(c.reflection)                 // new column, no value invented
        val a = db.wellness().assessments("u1").single()
        assertNull(a.bandLabel); assertNull(a.difficulty)
        assertEquals(ConsentDecision.GRANTED, db.wellness().consentHistory("u1").single().decision) // existing consent stays granted
        db.metadata().put(com.chinmai.yogasense.data.db.AppMetadataEntity("k", "v"))           // new table usable
        assertEquals("v", db.metadata().get("k"))
        db.close()
    }

    /** A Phase 7B (v2) database with a profile, practice history and wellness data. */
    private fun createV2WithData() {
        BundledSQLiteDriver().open(file.path).apply {
            createStatements(2).forEach { execSQL(it) }
            execSQL("INSERT INTO user_profiles (user_id,display_name,experience_level,created_at,updated_at,status) VALUES ('u1','Asha','ADVANCED',1,1,'ACTIVE')")
            execSQL("INSERT INTO user_preferences (user_id,voice_mode,spoken_encouragement,voice_countdown,correction_announcements,speech_rate,live_skeleton_enabled,target_skeleton_enabled,visual_corrections_enabled,reference_figure_enabled,preferred_practice_minutes,updated_at) VALUES ('u1','OFF',1,1,1,1.1,0,0,1,1,NULL,1)")
            execSQL("INSERT INTO practice_sessions (session_id,user_id,started_at,ended_at,duration_ms,completion) VALUES ('s1','u1',10,20,90000,'COMPLETED')")
            execSQL("INSERT INTO pose_results (result_id,session_id,asana,alignment_score,stability_score,stable_hold_total_ms,longest_hold_ms) VALUES ('r1','s1','TREE',77,NULL,5000,5000)")
            execSQL("INSERT INTO practice_corrections (correction_id,session_id,asana,correction_type,timestamp,improvement_observed) VALUES ('k1','s1','TREE','TREE.torso_upright.high',15,1)")
            execSQL("INSERT INTO wellness_check_ins (check_in_id,user_id,epoch_day,recorded_at,mood,stress,sleep_hours,sleep_quality,reflection_ciphertext,reflection) VALUES ('c1','u1',100,9,4,2,7.0,NULL,NULL,'calm')")
            execSQL("INSERT INTO wellness_assessments (assessment_id,user_id,instrument,instrument_version,completed_at,total_score,safety_flag,band_label,difficulty) VALUES ('a1','u1','PHQ9','v',9,5,0,'Mild',1)")
            execSQL("INSERT INTO assessment_responses (response_id,assessment_id,question_number,response_value) VALUES ('q1','a1',1,2)")
            execSQL("INSERT INTO consent_records (consent_id,user_id,consent_type,consent_version,granted_at,withdrawn_at,decision) VALUES ('cn','u1','WELLNESS_DATA_STORAGE','v1',5,NULL,'GRANTED')")
            execSQL("INSERT INTO app_metadata (`key`,`value`) VALUES ('legacy.wellness.status','completed')")
            val identity = Regex("\"identityHash\":\\s*\"(\\w+)\"").find(File(schemaDir, "2.json").readText())!!.groupValues[1]
            execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$identity')")
            execSQL("PRAGMA user_version = 2")
            close()
        }
    }

    @Test fun v2ToV3KeepsHistoryAndWellnessAndDoesNotForceOnboarding() = runBlocking<Unit> {
        createV2WithData()
        val db = open(migrations = true)
        assertEquals(com.chinmai.yogasense.data.db.ExperienceLevel.ADVANCED, db.profiles().findById("u1")!!.experienceLevel)
        assertEquals(com.chinmai.yogasense.coaching.VoiceMode.OFF, db.preferences().get("u1")!!.voiceMode)
        assertEquals(90_000L, db.practice().findSession("u1", "s1")!!.durationMs)
        val r = db.practice().poseResults("u1", "s1").single(); assertEquals(77, r.alignmentScore); assertNull(r.stabilityScore)
        assertTrue(db.practice().corrections("u1", "s1").single().improvementObserved)
        assertEquals("calm", db.wellness().checkIns("u1").single().reflection)
        assertEquals("Mild", db.wellness().assessments("u1").single().bandLabel)
        assertEquals(1, db.wellness().allResponses("u1").size)
        assertEquals("completed", db.metadata().get("legacy.wellness.status"))
        // Existing profile: no personalisation row → never forced through onboarding, prompt offered.
        assertNull(db.personalisation().get("u1"))
        val v = kotlinx.coroutines.withTimeout(5_000) { com.chinmai.yogasense.data.RoomPersonalisationRepository(db, "u1").observe().first() }
        assertTrue(!v.mustOnboard && v.showPrompt)
        assertEquals(com.chinmai.yogasense.data.db.ExperienceLevel.ADVANCED, v.experienceLevel)
        db.close()
    }

    @Test fun missingMigrationRefusesToOpenAndKeepsData() = runBlocking<Unit> {
        createV1WithData()
        val db = open(migrations = false)
        try { db.profiles().findById("u1"); fail("expected missing-migration failure") } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("migration", ignoreCase = true))
        }
        db.close()
        BundledSQLiteDriver().open(file.path).apply {
            prepare("SELECT display_name FROM user_profiles WHERE user_id = 'u1'").use { st -> assertTrue(st.step()); assertEquals("Asha", st.getText(0)) }
            close()
        }
    }
}
