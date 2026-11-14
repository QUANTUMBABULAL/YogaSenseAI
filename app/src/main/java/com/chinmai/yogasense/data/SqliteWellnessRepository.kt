package com.chinmai.yogasense.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.chinmai.yogasense.wellness.DailyCheckIn
import com.chinmai.yogasense.wellness.QuestionnaireRecord
import com.chinmai.yogasense.wellness.QuestionnaireType
import com.chinmai.yogasense.wellness.WellnessConsent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Local SQLite database (Android framework SQLiteOpenHelper — no extra dependency).
 * File: app-private `databases/wellness.db`, excluded from cloud backup and device transfer.
 *
 * Schema versioning: [VERSION] with explicit, additive migrations in [migrate]. An unknown
 * upgrade path throws rather than silently dropping user data.
 */
class WellnessDatabase(context: Context, name: String? = NAME) :
    SQLiteOpenHelper(context.applicationContext, name, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE check_ins (
                epoch_day INTEGER PRIMARY KEY,
                mood INTEGER, stress INTEGER, sleep_hours REAL, sleep_quality INTEGER,
                reflection TEXT, updated_at INTEGER NOT NULL)""",
        )
        db.execSQL(
            """CREATE TABLE questionnaires (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL, completed_at INTEGER NOT NULL, answers TEXT NOT NULL,
                total INTEGER NOT NULL, band TEXT NOT NULL, difficulty INTEGER,
                item9_positive INTEGER NOT NULL)""",
        )
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = migrate(db, oldVersion, newVersion)

    companion object {
        const val NAME = "wellness.db"
        const val VERSION = 1

        /** Upgrades the schema from version [v] to v + 1. Add a branch for every new version. */
        @Suppress("UNUSED_PARAMETER")
        fun migrationStep(db: SQLiteDatabase, v: Int) {
            when (v) {
                // 1 -> db.execSQL("ALTER TABLE check_ins ADD COLUMN ...")   // version 2 example
                else -> throw IllegalStateException("No migration from wellness schema $v; refusing to drop data")
            }
        }

        /** Apply each step in order. */
        fun migrate(db: SQLiteDatabase, from: Int, to: Int) {
            for (v in from until to) {
                migrationStep(db, v)
            }
        }
    }
}

class SqliteWellnessRepository(private val database: WellnessDatabase) : WellnessRepository {
    private val io = Dispatchers.IO
    private val mutex = Mutex()
    private val _checkIns = MutableStateFlow<List<DailyCheckIn>>(emptyList())
    private val _questionnaires = MutableStateFlow<List<QuestionnaireRecord>>(emptyList())
    private val _consent = MutableStateFlow(WellnessConsent.UNKNOWN)
    override val checkIns: StateFlow<List<DailyCheckIn>> = _checkIns.asStateFlow()
    override val questionnaires: StateFlow<List<QuestionnaireRecord>> = _questionnaires.asStateFlow()
    override val consent: StateFlow<WellnessConsent> = _consent.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + io).launch { reload() }
    }

    suspend fun reload() = withContext(io) {
        mutex.withLock {
            val db = database.readableDatabase
            _consent.value = db.rawQuery("SELECT value FROM meta WHERE key = 'consent'", null).use { c ->
                if (c.moveToFirst()) runCatching { WellnessConsent.valueOf(c.getString(0)) }.getOrDefault(WellnessConsent.UNKNOWN) else WellnessConsent.UNKNOWN
            }
            _checkIns.value = db.rawQuery(
                "SELECT epoch_day, mood, stress, sleep_hours, sleep_quality, reflection, updated_at FROM check_ins ORDER BY epoch_day DESC", null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) add(
                        DailyCheckIn(
                            epochDay = c.getLong(0),
                            mood = if (c.isNull(1)) null else c.getInt(1),
                            stress = if (c.isNull(2)) null else c.getInt(2),
                            sleepHours = if (c.isNull(3)) null else c.getFloat(3),
                            sleepQuality = if (c.isNull(4)) null else c.getInt(4),
                            reflection = if (c.isNull(5)) null else c.getString(5),
                            updatedAtMillis = c.getLong(6),
                        ),
                    )
                }
            }
            _questionnaires.value = db.rawQuery(
                "SELECT id, type, completed_at, answers, total, band, difficulty, item9_positive FROM questionnaires ORDER BY completed_at DESC", null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) add(
                        QuestionnaireRecord(
                            id = c.getLong(0).toString(),
                            type = QuestionnaireType.valueOf(c.getString(1)),
                            completedAtMillis = c.getLong(2),
                            answers = c.getString(3).split(',').filter { it.isNotEmpty() }.map { it.toInt() },
                            total = c.getInt(4),
                            bandLabel = c.getString(5),
                            difficulty = if (c.isNull(6)) null else c.getInt(6),
                            item9Positive = c.getInt(7) == 1,
                        ),
                    )
                }
            }
        }
    }

    private fun requireConsent() { if (_consent.value != WellnessConsent.GRANTED) throw ConsentRequiredException() }

    override suspend fun upsertCheckIn(checkIn: DailyCheckIn) {
        requireConsent()
        withContext(io) {
            mutex.withLock {
                val v = ContentValues().apply {
                    put("epoch_day", checkIn.epochDay)
                    checkIn.mood?.let { put("mood", it) } ?: putNull("mood")
                    checkIn.stress?.let { put("stress", it) } ?: putNull("stress")
                    checkIn.sleepHours?.let { put("sleep_hours", it) } ?: putNull("sleep_hours")
                    checkIn.sleepQuality?.let { put("sleep_quality", it) } ?: putNull("sleep_quality")
                    checkIn.reflection?.let { put("reflection", it) } ?: putNull("reflection")
                    put("updated_at", checkIn.updatedAtMillis)
                }
                database.writableDatabase.insertWithOnConflict("check_ins", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
        reload()
    }

    override suspend fun deleteCheckIn(epochDay: Long) {
        withContext(io) { mutex.withLock { database.writableDatabase.delete("check_ins", "epoch_day = ?", arrayOf(epochDay.toString())) } }
        reload()
    }

    override suspend fun addQuestionnaire(record: QuestionnaireRecord): String {
        requireConsent()
        val id = withContext(io) {
            mutex.withLock {
                val v = ContentValues().apply {
                    put("type", record.type.name)
                    put("completed_at", record.completedAtMillis)
                    put("answers", record.answers.joinToString(","))
                    put("total", record.total)
                    put("band", record.bandLabel)
                    record.difficulty?.let { put("difficulty", it) } ?: putNull("difficulty")
                    put("item9_positive", if (record.item9Positive) 1 else 0)
                }
                database.writableDatabase.insertOrThrow("questionnaires", null, v)
            }
        }
        reload()
        return id.toString()
    }

    override suspend fun setConsent(consent: WellnessConsent) {
        withContext(io) {
            mutex.withLock {
                val v = ContentValues().apply { put("key", "consent"); put("value", consent.name) }
                database.writableDatabase.insertWithOnConflict("meta", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
        reload()
    }

    override suspend fun deleteAll() {
        withContext(io) {
            mutex.withLock {
                val db = database.writableDatabase
                db.beginTransaction()
                try {
                    db.delete("check_ins", null, null)
                    db.delete("questionnaires", null, null)
                    db.delete("meta", null, null)
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
        }
        reload()
    }
}

/*
 * Phase 7B: the app no longer opens this repository. wellness.db is read once, read-only, by
 * SqliteLegacyWellnessSource for the import into Room (LegacyWellnessMigration.kt). The class is kept
 * for its instrumented contract test and as the reference for the legacy schema.
 */
