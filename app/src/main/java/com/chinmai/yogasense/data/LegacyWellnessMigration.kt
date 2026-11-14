package com.chinmai.yogasense.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.chinmai.yogasense.data.db.AppMetadataEntity
import com.chinmai.yogasense.data.db.AssessmentResponseEntity
import com.chinmai.yogasense.data.db.ConsentDecision
import com.chinmai.yogasense.data.db.ConsentRecordEntity
import com.chinmai.yogasense.data.db.ConsentType
import com.chinmai.yogasense.data.db.LegacyImportPlan
import com.chinmai.yogasense.data.db.LegacyImportVerificationException
import com.chinmai.yogasense.data.db.WellnessAssessmentEntity
import com.chinmai.yogasense.data.db.WellnessCheckInEntity
import com.chinmai.yogasense.data.db.YogaSenseDatabase
import com.chinmai.yogasense.profile.UserProfile
import com.chinmai.yogasense.wellness.DailyCheckIn
import com.chinmai.yogasense.wellness.QuestionnaireRecord
import com.chinmai.yogasense.wellness.QuestionnaireType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/*
 * One-time import of the Phase 7 device-wide `wellness.db` into the profile-scoped Room database.
 *
 * Ownership (never guessed):
 *  - The legacy file has no user. Its records are assigned only to a DESIGNATED owner profile.
 *  - The owner is designated automatically only when the FIRST profile is created on the device
 *    (before Phase 7B the app had exactly one implicit user); the creation screen says so.
 *  - Otherwise (profiles already exist and no valid owner) the import stops in [LegacyWellnessStatus.NeedsOwner]
 *    and the data stays in wellness.db until the user explicitly assigns it to a profile.
 *
 * Safety:
 *  - The legacy file is opened READ-ONLY and never modified by the import.
 *  - Mapping is all-or-nothing: if any record cannot be mapped, nothing is imported ([LegacyWellnessStatus.Failed]).
 *  - Insert + read-back verification + completion marker run in ONE Room transaction
 *    (LegacyImportDao.importVerified); a crash or a
 *    verification mismatch rolls everything back, and a retry starts clean. Imported ids are
 *    deterministic, so a retry can never duplicate rows.
 *  - Wellness screens use Room only when the status is [LegacyWellnessStatus.isResolved]; until then no
 *    store is writable, so two competing wellness stores are never active.
 *  - Nothing here logs record values.
 */

/** Raw rows exactly as stored by Phase 7's SqliteWellnessRepository. */
data class LegacyCheckInRow(
    val epochDay: Long, val mood: Int?, val stress: Int?, val sleepHours: Float?, val sleepQuality: Int?,
    val reflection: String?, val updatedAt: Long,
)

data class LegacyQuestionnaireRow(
    val id: Long, val type: String, val completedAt: Long, val answers: String, val total: Int,
    val band: String, val difficulty: Int?, val item9: Int,
)

data class LegacyWellnessSnapshot(
    val checkIns: List<LegacyCheckInRow>,
    val questionnaires: List<LegacyQuestionnaireRow>,
    val consent: String?,     // meta 'consent' value, null if absent
) {
    val recordCount: Int get() = checkIns.size + questionnaires.size
    val isEmpty: Boolean get() = recordCount == 0 && (consent == null || consent == "UNKNOWN")

    companion object { val EMPTY = LegacyWellnessSnapshot(emptyList(), emptyList(), null) }
}

interface LegacyWellnessSource {
    fun exists(): Boolean
    fun read(): LegacyWellnessSnapshot
    /** Deletes the legacy file. Only called after a verified import AND an explicit user deletion. */
    fun erase()
}

/** Reads `databases/wellness.db` read-only. Never creates the file. */
class SqliteLegacyWellnessSource(context: Context, private val name: String = WellnessDatabase.NAME) : LegacyWellnessSource {
    private val app = context.applicationContext
    private val file get() = app.getDatabasePath(name)

    override fun exists(): Boolean = file.exists()

    override fun read(): LegacyWellnessSnapshot {
        if (!exists()) return LegacyWellnessSnapshot.EMPTY
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            fun hasTable(t: String) = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(t)).use { it.moveToFirst() }
            val checkIns = if (!hasTable("check_ins")) emptyList() else db.rawQuery(
                "SELECT epoch_day, mood, stress, sleep_hours, sleep_quality, reflection, updated_at FROM check_ins ORDER BY epoch_day", null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) add(
                        LegacyCheckInRow(
                            c.getLong(0), if (c.isNull(1)) null else c.getInt(1), if (c.isNull(2)) null else c.getInt(2),
                            if (c.isNull(3)) null else c.getFloat(3), if (c.isNull(4)) null else c.getInt(4),
                            if (c.isNull(5)) null else c.getString(5), c.getLong(6),
                        ),
                    )
                }
            }
            val qs = if (!hasTable("questionnaires")) emptyList() else db.rawQuery(
                "SELECT id, type, completed_at, answers, total, band, difficulty, item9_positive FROM questionnaires ORDER BY id", null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) add(
                        LegacyQuestionnaireRow(
                            c.getLong(0), c.getString(1), c.getLong(2), c.getString(3), c.getInt(4), c.getString(5),
                            if (c.isNull(6)) null else c.getInt(6), c.getInt(7),
                        ),
                    )
                }
            }
            val consent = if (!hasTable("meta")) null else
                db.rawQuery("SELECT value FROM meta WHERE key = 'consent'", null).use { if (it.moveToFirst()) it.getString(0) else null }
            return LegacyWellnessSnapshot(checkIns, qs, consent)
        }
    }

    override fun erase() { app.deleteDatabase(name) }
}

/** Pure, JVM-testable mapping and verification. */
object LegacyWellnessMapper {
    const val ID_PREFIX = "legacy7"
    fun checkInId(userId: String, epochDay: Long) = "$ID_PREFIX:$userId:checkin:$epochDay"
    fun assessmentId(userId: String, legacyId: Long) = "$ID_PREFIX:$userId:q:$legacyId"
    fun responseId(userId: String, legacyId: Long, n: Int) = "$ID_PREFIX:$userId:q:$legacyId:$n"
    fun consentId(userId: String) = "$ID_PREFIX:$userId:consent"

    data class Mapped(
        val checkIns: List<WellnessCheckInEntity>,
        val assessments: List<Pair<WellnessAssessmentEntity, List<AssessmentResponseEntity>>>,
        val consent: ConsentRecordEntity?,
    )

    sealed interface Result {
        data class Ok(val mapped: Mapped) : Result
        /** Problems describe record ids/fields only — never values. */
        data class Unmappable(val problems: List<String>) : Result
    }

    /** Legacy row → Phase 7 domain model, exactly as SqliteWellnessRepository read it. */
    fun domain(r: LegacyCheckInRow) = DailyCheckIn(r.epochDay, r.mood, r.stress, r.sleepHours, r.sleepQuality, r.reflection, r.updatedAt)

    fun parseAnswers(s: String): List<Int>? = s.split(',').filter { it.isNotEmpty() }.map { it.trim().toIntOrNull() ?: return null }

    fun domain(r: LegacyQuestionnaireRow): QuestionnaireRecord? {
        val type = runCatching { QuestionnaireType.valueOf(r.type) }.getOrNull() ?: return null
        val answers = parseAnswers(r.answers) ?: return null
        return QuestionnaireRecord(r.id.toString(), type, r.completedAt, answers, r.total, r.band, r.difficulty, r.item9 == 1)
    }

    fun map(s: LegacyWellnessSnapshot, userId: String, nowMillis: Long): Result {
        val problems = mutableListOf<String>()
        val checkIns = s.checkIns.map { r ->
            with(WellnessMappers) { domain(r).toEntity(userId, checkInId(userId, r.epochDay)) }
        }
        val assessments = s.questionnaires.mapNotNull { r ->
            val d = domain(r)
            if (d == null) { problems += "Questionnaire #${r.id}: unknown type or unreadable answers"; null }
            else if (r.item9 != 0 && r.item9 != 1) { problems += "Questionnaire #${r.id}: unexpected safety flag"; null }
            else with(WellnessMappers) { d.toRows(userId, assessmentId(userId, r.id)) { n -> responseId(userId, r.id, n) } }
        }
        val consent = when (s.consent) {
            null, "UNKNOWN" -> null
            "GRANTED" -> ConsentRecordEntity(consentId(userId), userId, ConsentType.WELLNESS_DATA_STORAGE, WellnessMappers.CONSENT_VERSION, nowMillis, null, ConsentDecision.GRANTED)
            "DECLINED" -> ConsentRecordEntity(consentId(userId), userId, ConsentType.WELLNESS_DATA_STORAGE, WellnessMappers.CONSENT_VERSION, nowMillis, null, ConsentDecision.DECLINED)
            else -> { problems += "Consent: unrecognised stored value"; null }
        }
        return if (problems.isEmpty()) Result.Ok(Mapped(checkIns, assessments, consent)) else Result.Unmappable(problems)
    }

    /** What was read back from Room for the owner after inserting. */
    data class ReadBack(
        val checkIns: List<WellnessCheckInEntity>,
        val assessments: List<WellnessAssessmentEntity>,
        val responses: List<AssessmentResponseEntity>,
        val consent: ConsentRecordEntity?,
    )

    /**
     * Verifies counts, every imported row, and the user-visible values (legacy domain model vs the
     * model the Room repository will show). Returns problems (empty = verified). Never includes values.
     */
    fun verify(s: LegacyWellnessSnapshot, m: Mapped, back: ReadBack, userId: String): List<String> {
        val problems = mutableListOf<String>()
        val prefix = "$ID_PREFIX:"
        val importedCheckIns = back.checkIns.filter { it.checkInId.startsWith(prefix) }
        val importedAssessments = back.assessments.filter { it.assessmentId.startsWith(prefix) }
        if (importedCheckIns.size != s.checkIns.size) problems += "Check-in count ${importedCheckIns.size} ≠ ${s.checkIns.size}"
        if (importedAssessments.size != s.questionnaires.size) problems += "Questionnaire count ${importedAssessments.size} ≠ ${s.questionnaires.size}"

        val backCheckIns = back.checkIns.associateBy { it.checkInId }
        m.checkIns.forEach { e -> if (backCheckIns[e.checkInId] != e) problems += "Check-in for day ${e.epochDay} differs" }
        s.checkIns.forEach { r ->
            val shown = backCheckIns[checkInId(userId, r.epochDay)]?.let { with(WellnessMappers) { it.toDomain() } }
            if (shown == null || shown != domain(r)) problems += "Check-in for day ${r.epochDay} would display differently"
        }

        val backAssessments = back.assessments.associateBy { it.assessmentId }
        val backResponses = back.responses.groupBy { it.assessmentId }
        m.assessments.forEachIndexed { i, (a, rs) ->
            if (backAssessments[a.assessmentId] != a) problems += "Questionnaire #${s.questionnaires[i].id} differs"
            if (backResponses[a.assessmentId].orEmpty().sortedBy { it.questionNumber } != rs.sortedBy { it.questionNumber }) {
                problems += "Questionnaire #${s.questionnaires[i].id} answers differ"
            }
            val shown = backAssessments[a.assessmentId]?.let { WellnessMappers.toDomain(it, backResponses[a.assessmentId].orEmpty()) }
            val expected = domain(s.questionnaires[i])
            if (shown == null || expected == null || shown.copy(id = "") != expected.copy(id = "")) {
                problems += "Questionnaire #${s.questionnaires[i].id} would display differently"
            }
        }
        if (m.consent != back.consent) problems += "Consent decision differs"
        return problems
    }
}

sealed interface LegacyWellnessStatus {
    data object Checking : LegacyWellnessStatus
    /** No Phase 7 data on this device. */
    data object NotNeeded : LegacyWellnessStatus
    data class Completed(val ownerId: String, val checkIns: Int, val questionnaires: Int) : LegacyWellnessStatus
    /** Legacy data exists and no profile exists yet: the first profile created will receive it. */
    data class AwaitingFirstProfile(val records: Int) : LegacyWellnessStatus
    /** Legacy data exists but ownership can't be established automatically; the user must choose. */
    data class NeedsOwner(val records: Int) : LegacyWellnessStatus
    data object InProgress : LegacyWellnessStatus
    /** Nothing was imported; legacy data is untouched. [reason] contains no record values. */
    data class Failed(val reason: String) : LegacyWellnessStatus

    /** Wellness screens may use the Room store only in these states. */
    val isResolved: Boolean get() = this is NotNeeded || this is Completed
}

class LegacyWellnessMigrator(
    private val db: YogaSenseDatabase,
    private val source: LegacyWellnessSource,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val KEY_STATUS = "legacy.wellness.status"      // "completed"
        const val KEY_OWNER = "legacy.wellness.owner"        // designated owner userId
        const val KEY_RESULT = "legacy.wellness.result"      // counts + time (no values)
        const val KEY_ERASED = "legacy.wellness.erased_at"
    }

    private val mutex = Mutex()
    private val _status = MutableStateFlow<LegacyWellnessStatus>(LegacyWellnessStatus.Checking)
    val status: StateFlow<LegacyWellnessStatus> = _status.asStateFlow()

    /** True while legacy data waits for an owner (used when the first profile is created). */
    suspend fun hasPendingData(): Boolean = withContext(Dispatchers.IO) {
        db.metadata().get(KEY_STATUS) != "completed" && source.exists() && !source.read().isEmpty
    }

    /** Re-evaluates the state for the current [profiles]; imports automatically for a valid designated owner. */
    suspend fun refresh(profiles: List<UserProfile>): LegacyWellnessStatus = mutex.withLock {
        withContext(Dispatchers.IO) { evaluate(profiles) }.also { _status.value = it }
    }

    /** Explicit user decision: assign the legacy data to [userId], then import. */
    suspend fun assignTo(userId: String, profiles: List<UserProfile>): LegacyWellnessStatus = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (profiles.none { it.userId == userId }) return@withContext LegacyWellnessStatus.Failed("That profile no longer exists.")
            db.metadata().put(AppMetadataEntity(KEY_OWNER, userId))
            evaluate(profiles)
        }.also { _status.value = it }
    }

    /**
     * Called after the owner deleted their wellness data or their profile. Once the import is
     * verified complete, the legacy copy is an exact duplicate, so it is erased too — otherwise
     * "delete" would leave a plaintext copy behind.
     */
    suspend fun onOwnerDataErased(userId: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (db.metadata().get(KEY_STATUS) == "completed" && db.metadata().get(KEY_OWNER) == userId && source.exists()) {
                source.erase()
                db.metadata().put(AppMetadataEntity(KEY_ERASED, clock().toString()))
            }
        }
    }

    private suspend fun evaluate(profiles: List<UserProfile>): LegacyWellnessStatus {
        val meta = db.metadata()
        if (meta.get(KEY_STATUS) == "completed") return completed(meta.get(KEY_OWNER).orEmpty(), meta.get(KEY_RESULT))
        if (!source.exists()) return LegacyWellnessStatus.NotNeeded
        val snapshot = try { source.read() } catch (e: Exception) {
            return LegacyWellnessStatus.Failed("The existing wellness data couldn't be read (${e.javaClass.simpleName}). It has not been changed.")
        }
        if (snapshot.isEmpty) return LegacyWellnessStatus.NotNeeded
        val owner = meta.get(KEY_OWNER)?.takeIf { id -> profiles.any { it.userId == id } }
        return when {
            owner != null -> { _status.value = LegacyWellnessStatus.InProgress; import(snapshot, owner) }
            profiles.isEmpty() -> LegacyWellnessStatus.AwaitingFirstProfile(snapshot.recordCount)
            else -> LegacyWellnessStatus.NeedsOwner(snapshot.recordCount)
        }
    }

    private suspend fun import(snapshot: LegacyWellnessSnapshot, ownerId: String): LegacyWellnessStatus {
        val mapped = when (val r = LegacyWellnessMapper.map(snapshot, ownerId, clock())) {
            is LegacyWellnessMapper.Result.Unmappable ->
                return LegacyWellnessStatus.Failed("${r.problems.size} record(s) couldn't be converted, so nothing was moved: ${r.problems.joinToString("; ")}")
            is LegacyWellnessMapper.Result.Ok -> r.mapped
        }
        val result = "checkIns=${mapped.checkIns.size};questionnaires=${mapped.assessments.size};consent=${mapped.consent?.decision ?: "none"};at=${clock()}"
        val plan = LegacyImportPlan(
            ownerId = ownerId,
            checkIns = mapped.checkIns,
            assessments = mapped.assessments,
            consent = mapped.consent,
            completionMarkers = listOf(AppMetadataEntity(KEY_RESULT, result), AppMetadataEntity(KEY_STATUS, "completed")),
            verify = { c, a, r, k -> LegacyWellnessMapper.verify(snapshot, mapped, LegacyWellnessMapper.ReadBack(c, a, r, k), ownerId) },
        )
        return try {
            db.legacyImport().importVerified(plan) // one transaction; throws (and rolls back) on any problem
            LegacyWellnessStatus.Completed(ownerId, mapped.checkIns.size, mapped.assessments.size)
        } catch (e: LegacyImportVerificationException) {
            LegacyWellnessStatus.Failed("Verification failed, nothing was moved: ${e.problems.take(3).joinToString("; ")}")
        } catch (e: Exception) {
            LegacyWellnessStatus.Failed("The data couldn't be moved (${e.javaClass.simpleName}); nothing was changed. You can try again.")
        }
    }

    private fun completed(owner: String, result: String?): LegacyWellnessStatus {
        val parts = result.orEmpty().split(';').mapNotNull { p -> p.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        return LegacyWellnessStatus.Completed(owner, parts["checkIns"]?.toIntOrNull() ?: 0, parts["questionnaires"]?.toIntOrNull() ?: 0)
    }
}
