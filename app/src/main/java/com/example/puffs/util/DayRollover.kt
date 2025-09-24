package com.example.puffs.util

import java.time.*
import java.time.format.DateTimeFormatter

object DayRollover {
    // Change this to 3, 4, 5… (hour of day when a new day starts)
    const val ROLLOVER_HOUR = 4

    val offsetSecs: Long get() = ROLLOVER_HOUR * 3600L
    val offsetMillis: Long get() = offsetSecs * 1000L

    /** Returns YYYY-MM-DD string for the “logical” date of this timestamp. */
    fun logicalDate(tsMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val shifted = Instant.ofEpochMilli(tsMillis - offsetMillis)
        return LocalDateTime.ofInstant(shifted, zone).toLocalDate().toString()
    }

    /** Format a clock time (respects system 12/24 setting if you prefer DateFormat.getTimeFormat(ctx)) */
    fun fmtHm(tsMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = LocalDateTime.ofInstant(Instant.ofEpochMilli(tsMillis), zone)
        return d.format(DateTimeFormatter.ofPattern("HH:mm"))
    }
}
