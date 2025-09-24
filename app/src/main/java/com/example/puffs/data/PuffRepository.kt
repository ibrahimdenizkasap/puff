package com.example.puffs.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlin.math.max
import com.example.puffs.util.DayRollover
import androidx.room.withTransaction

class PuffRepository(ctx: Context) {
    private val db = AppDb.get(ctx)
    private val puffs = db.puffDao()
    private val daily = db.dailySessionDao()     // old
    private val auto = db.autoSessionDao()       // new
    private val offset = DayRollover.offsetSecs


    private val SESSION_TIMEOUT_MS = 12L * 60 * 1000

    // existing flows you use
    fun todayCount() = puffs.todayCountFlowWithOffset(offset)
    fun todayPuffs() = puffs.todayPuffsFlowWithOffset(offset)
    fun last7DaysTotal() = puffs.last7DaysTotalFlowWithOffset(offset)
    fun all() = puffs.streamAll()

    // new session flows
    fun sessionsDesc() = auto.sessionsDescFlow()
    fun latestSessions(limit: Int) = auto.latestSessions(limit)

    // keep the old one temporarily if needed by UI:
    fun todaySavedCountOld() = daily.todaySavedCountFlow()

    suspend fun addPuff(now: Long = System.currentTimeMillis()) {
        finalizeIfTimedOut(now)

        puffs.insert(Puff(timestamp = now))
        val draft = auto.getDraftOnce()
        if (draft == null) {
            auto.upsertDraft(DraftSession(1, startTs = now, lastPuffTs = now, puffCount = 1))
        } else {
            auto.upsertDraft(draft.copy(lastPuffTs = now, puffCount = draft.puffCount + 1))
        }
    }

    suspend fun undo() {
        val last = puffs.getLastOne() ?: return
        puffs.deleteById(last.id)
        val d = auto.getDraftOnce() ?: return
        val newCount = (d.puffCount - 1)
        if (newCount <= 0) {
            auto.clearDraft()
        } else {
            val within = puffs.puffsBetween(d.startTs, d.lastPuffTs)
            val newLast = within.lastOrNull()?.timestamp ?: d.startTs
            auto.upsertDraft(d.copy(lastPuffTs = newLast, puffCount = newCount))
        }
    }

    suspend fun endSessionNow(now: Long = System.currentTimeMillis()) {
        val d = auto.getDraftOnce() ?: return
        if (d.puffCount > 0) {
            auto.insertSession(Session2(startTs = d.startTs, endTs = max(d.startTs, d.lastPuffTs), puffCount = d.puffCount))
        }
        auto.clearDraft()
    }

    suspend fun finalizeIfTimedOut(now: Long = System.currentTimeMillis()) {
        val d = auto.getDraftOnce() ?: return
        if (now - d.lastPuffTs >= SESSION_TIMEOUT_MS && d.puffCount > 0) {
            auto.insertSession(Session2(startTs = d.startTs, endTs = d.lastPuffTs, puffCount = d.puffCount))
            auto.clearDraft()
        }
    }
    suspend fun getDraftOnce() = auto.getDraftOnce()

    // Add near other public actions
    suspend fun puffsInRange(from: Long, to: Long) = withContext(Dispatchers.IO) {
        db.puffDao().puffsBetween(from, to)
    }
// Rebuild sessions from existing per-puff data (e.g., after importing a CSV).
// Splits a session when the gap between puffs > SESSION_TIMEOUT_MS.
// Keeps the most recent "fresh" session as a draft if it ended within timeout.
    suspend fun rebuildSessionsFromPuffs(timeoutMs: Long = SESSION_TIMEOUT_MS) {
        // 1) Read all puffs ASC (oldest → newest)
        val list = puffs.streamAllOnce()
        if (list.isEmpty()) {
            db.withTransaction {
                auto.clearSessions()   // make sure AutoSessionDao has this @Query("DELETE FROM sessions2")
                auto.clearDraft()
            }
            return
        }

        data class Acc(var start: Long, var last: Long, var count: Int)
        var acc: Acc? = null
        val sessions = mutableListOf<Session2>()

        // 2) Build sessions in memory
        for (p in list) {
            if (acc == null) {
                acc = Acc(p.timestamp, p.timestamp, 1)
            } else {
                if (p.timestamp - acc!!.last > timeoutMs) {
                    sessions += Session2(startTs = acc!!.start, endTs = acc!!.last, puffCount = acc!!.count)
                    acc = Acc(p.timestamp, p.timestamp, 1)
                } else {
                    acc!!.last = p.timestamp
                    acc!!.count += 1
                }
            }
        }
        acc?.let { a -> sessions += Session2(startTs = a.start, endTs = a.last, puffCount = a.count) }

        // 3) Write to DB in a single transaction
        val now = System.currentTimeMillis()
        db.withTransaction {
            auto.clearSessions()
            auto.clearDraft()

            if (sessions.isEmpty()) return@withTransaction

            val last = sessions.last()
            if (now - last.endTs <= timeoutMs) {
                // Insert all but the last as finalized
                for (i in 0 until sessions.lastIndex) auto.insertSession(sessions[i])
                // Recreate the last as a live draft
                auto.upsertDraft(
                    DraftSession(
                        id = 1,
                        startTs = last.startTs,
                        lastPuffTs = last.endTs,
                        puffCount = last.puffCount
                    )
                )
            } else {
                // All historical
                for (s in sessions) auto.insertSession(s)
            }
        }
    }
}





