package com.prehmus.selli.data.logging

import android.util.Log
import com.prehmus.selli.domain.logging.CalendarLogger

object AndroidCalendarLogger : CalendarLogger {
    override fun error(source: String, cause: Throwable) {
        Log.e(TAG, "Failed to fetch events from $source", cause)
    }

    private const val TAG = "SelliCalendar"
}
