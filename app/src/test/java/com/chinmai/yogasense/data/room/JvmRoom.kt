package com.chinmai.yogasense.data.room

import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.chinmai.yogasense.data.db.YogaSenseDatabase
import com.chinmai.yogasense.data.db.YogaSenseDatabase.Companion.configure
import kotlinx.coroutines.Dispatchers

/**
 * Real Room + real SQLite on the JVM (bundled native SQLite driver), so DAO, transaction,
 * foreign-key and migration behaviour is executed in unit tests without a device.
 * This is NOT the Android framework SQLite used on the phone; the instrumented tests cover that.
 */
object JvmRoom {
    /** Minimal stand-in: Room only needs the application context and database paths here. */
    private val stubContext = object : ContextWrapper(null) {
        override fun getApplicationContext() = this
        override fun getDatabasePath(name: String) = java.io.File(name).absoluteFile
        override fun getPackageName() = "com.chinmai.yogasense.test"
    }

    fun inMemory(): YogaSenseDatabase =
        Room.inMemoryDatabaseBuilder(stubContext, YogaSenseDatabase::class.java).jvm().build()

    fun file(path: String): YogaSenseDatabase =
        Room.databaseBuilder(stubContext, YogaSenseDatabase::class.java, path).jvm().build()

    private fun RoomDatabase.Builder<YogaSenseDatabase>.jvm() = configure()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .allowMainThreadQueries() // JVM has no Android main looper; Room's main-thread check would NPE
}
