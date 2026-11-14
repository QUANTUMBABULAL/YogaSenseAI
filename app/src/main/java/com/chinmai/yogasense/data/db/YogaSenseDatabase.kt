package com.chinmai.yogasense.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL

/**
 * Local Room database `yogasense.db`. Schema JSON is exported to app/schemas/ for review and
 * migration tests.
 *
 * Migration policy: every version bump adds an explicit [Migration] to [YogaSenseDatabase.MIGRATIONS].
 * There is NO destructive fallback: a missing migration makes Room throw on open, leaving the
 * existing file untouched instead of wiping user data.
 */
@Database(
    version = YogaSenseDatabase.VERSION,
    exportSchema = true,
    entities = [
        UserProfileEntity::class, UserPreferencesEntity::class,
        PracticeSessionEntity::class, PoseResultEntity::class, PracticeCorrectionEntity::class,
        WellnessCheckInEntity::class, WellnessAssessmentEntity::class, AssessmentResponseEntity::class,
        ConsentRecordEntity::class, AppMetadataEntity::class,
        PersonalisationEntity::class, ProfileGoalEntity::class, ProfileBodyAreaEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class YogaSenseDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun preferences(): PreferencesDao
    abstract fun practice(): PracticeDao
    abstract fun wellness(): WellnessDao
    abstract fun metadata(): MetadataDao
    abstract fun legacyImport(): LegacyImportDao
    abstract fun personalisation(): PersonalisationDao

    companion object {
        const val NAME = "yogasense.db"
        const val VERSION = 3

        /**
         * v1 -> v2 (Phase 7B), additive only; no existing row is rewritten or dropped:
         *  - wellness_check_ins.reflection (plaintext until Phase 7D encryption),
         *  - wellness_assessments.band_label, .difficulty (to import Phase 7 results losslessly),
         *  - consent_records.decision (GRANTED / DECLINED; existing rows become GRANTED),
         *  - app_metadata table (one-time migration markers).
         */
        val MIGRATION_1_2_SQL = listOf(
            "ALTER TABLE `wellness_check_ins` ADD COLUMN `reflection` TEXT",
            "ALTER TABLE `wellness_assessments` ADD COLUMN `band_label` TEXT",
            "ALTER TABLE `wellness_assessments` ADD COLUMN `difficulty` INTEGER",
            "ALTER TABLE `consent_records` ADD COLUMN `decision` TEXT NOT NULL DEFAULT 'GRANTED'",
            "CREATE TABLE IF NOT EXISTS `app_metadata` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
        )

        /** Same statements for both Room modes (framework SupportSQLite on the phone; SQLiteDriver in JVM tests). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = MIGRATION_1_2_SQL.forEach(db::execSQL)
            override fun migrate(connection: SQLiteConnection) = MIGRATION_1_2_SQL.forEach { connection.execSQL(it) }
        }

        /**
         * v2 -> v3 (Phase 7B.5), create-only: personalisation tables. No existing row is touched.
         * Existing profiles get NO personalisation row, which means "onboarding optional" — they are
         * never forced through onboarding and keep their experience level and preferences.
         */
        val MIGRATION_2_3_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `profile_personalisation` (`user_id` TEXT NOT NULL, `height_cm` REAL, `weight_kg` REAL, `height_unit` TEXT NOT NULL, `weight_unit` TEXT NOT NULL, `weekly_frequency` TEXT, `no_particular_area` INTEGER NOT NULL, `onboarding_required` INTEGER NOT NULL, `onboarding_status` TEXT NOT NULL, `onboarding_step` INTEGER NOT NULL, `onboarding_version` INTEGER NOT NULL, `prompt_dismissed` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`), FOREIGN KEY(`user_id`) REFERENCES `user_profiles`(`user_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `profile_goals` (`user_id` TEXT NOT NULL, `goal` TEXT NOT NULL, PRIMARY KEY(`user_id`, `goal`), FOREIGN KEY(`user_id`) REFERENCES `user_profiles`(`user_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `profile_body_areas` (`user_id` TEXT NOT NULL, `area` TEXT NOT NULL, `intent` TEXT, PRIMARY KEY(`user_id`, `area`), FOREIGN KEY(`user_id`) REFERENCES `user_profiles`(`user_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) = MIGRATION_2_3_SQL.forEach(db::execSQL)
            override fun migrate(connection: SQLiteConnection) = MIGRATION_2_3_SQL.forEach { connection.execSQL(it) }
        }

        /** Every migration, in order; never remove old ones. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        /** Foreign-key enforcement on every connection (also enforced by Room for FK entities). */
        val FOREIGN_KEYS = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }

        fun <T : RoomDatabase> RoomDatabase.Builder<T>.configure(): RoomDatabase.Builder<T> =
            addMigrations(*MIGRATIONS).addCallback(FOREIGN_KEYS)

        @Volatile private var instance: YogaSenseDatabase? = null

        fun get(context: Context): YogaSenseDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, YogaSenseDatabase::class.java, NAME)
                .configure()
                .build()
                .also { instance = it }
        }
    }
}
