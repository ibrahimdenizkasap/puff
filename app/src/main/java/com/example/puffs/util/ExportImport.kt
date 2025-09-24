package com.example.puffs.util

import android.content.Context
import android.net.Uri
import com.example.puffs.data.AppDb
import com.example.puffs.data.Puff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import java.time.format.DateTimeParseException

// Call this after the user picks a file
suspend fun importCsvFromUri(ctx: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
    val cr = ctx.contentResolver
    val input = cr.openInputStream(uri) ?: return@withContext 0
    val reader = BufferedReader(InputStreamReader(input))

    // collect any ISO-8601 timestamps (e.g., 2025-09-24T16:05:30.123Z)
    val isoRegex = Regex("""^\d{4}-\d{2}-\d{2}T[0-9:\.\+\-Z]+$""")
    val parsedMillis = mutableListOf<Long>()
    reader.forEachLine { raw ->
        val line = raw.trim()
        if (isoRegex.matches(line)) {
            try {
                parsedMillis += Instant.parse(line).toEpochMilli()
            } catch (_: DateTimeParseException) {
                // ignore bad line
            }
        }
    }
    reader.close()

    if (parsedMillis.isEmpty()) return@withContext 0

    // merge + dedupe: build a Set of existing millis to avoid dup inserts
    val db = AppDb.get(ctx)
    val dao = db.puffDao()
    val existing = dao.allTimestampsOnce().toHashSet()
    var added = 0
    parsedMillis.sorted().forEach { ts ->
        if (existing.add(ts)) {
            dao.insert(Puff(timestamp = ts))
            added++
        }
    }
    return@withContext added
}

// ---------- EXPORT (write current DB -> CSV with one timestamp per line) ----------
suspend fun exportCsvToUri(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
    val db = AppDb.get(ctx)
    val dao = db.puffDao()
    val list = dao.streamAllOnce()  // needs DAO helper in section B
    ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
        out.write("timestamp\n")
        list.forEach { puff -> out.write("${Instant.ofEpochMilli(puff.timestamp)}\n") }
    }
}


