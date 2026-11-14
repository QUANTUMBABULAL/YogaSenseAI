package com.chinmai.yogasense.perf

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import android.os.SystemClock
import android.util.Log

/**
 * Debug-only performance markers (logcat tag "YsPerf"), used to measure startup and practice-loop
 * cost the same way before and after optimisation. Disabled in non-debuggable builds. Never logs
 * personal data — only timings and counts.
 */
object PerfLog {
    const val TAG = "YsPerf"
    @Volatile private var enabled = false

    fun init(context: Context) { enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }

    /** Milliseconds since this process started. */
    fun sinceProcessStart(): Long = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()

    fun mark(event: String) { if (enabled) Log.i(TAG, "$event at +${sinceProcessStart()} ms") }

    fun log(message: () -> String) { if (enabled) Log.i(TAG, message()) }
}
