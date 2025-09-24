package com.example.puffs.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PuffDao {
    @Insert suspend fun insert(puff: Puff)
    @Delete suspend fun delete(puff: Puff)

    @Query("SELECT * FROM puffs ORDER BY timestamp DESC")
    fun streamAll(): Flow<List<Puff>>

    @Query("SELECT COUNT(*) FROM puffs WHERE date(timestamp/1000,'unixepoch','localtime') = date('now','localtime')")
    fun todayCountFlow(): Flow<Int>

    @Query("SELECT * FROM puffs WHERE date(timestamp/1000,'unixepoch','localtime') = date('now','localtime') ORDER BY timestamp DESC")
    fun todayPuffsFlow(): Flow<List<Puff>>

    @Query("DELETE FROM puffs WHERE id IN (SELECT id FROM puffs ORDER BY timestamp DESC LIMIT :n)")
    suspend fun deleteLastN(n: Int)

    @Query("""
SELECT COUNT(*) FROM puffs 
WHERE date(timestamp/1000,'unixepoch','localtime') >= date('now','-6 days','localtime')
""")
    fun last7DaysTotalFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM puffs WHERE date(timestamp/1000,'unixepoch','localtime') = date('now','localtime')")
    suspend fun todayCountOnce(): Int

    @Query("SELECT timestamp FROM puffs")
    suspend fun allTimestampsOnce(): List<Long>

    @Query("SELECT * FROM puffs ORDER BY timestamp ASC")
    suspend fun streamAllOnce(): List<Puff>

    @Query("SELECT * FROM puffs WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    suspend fun puffsBetween(from: Long, to: Long): List<Puff>

    // Latest puff (by timestamp, newest first)
    @Query("SELECT * FROM puffs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastOne(): Puff?

    // Delete by primary key
    @Query("DELETE FROM puffs WHERE id = :id")
    suspend fun deleteById(id: Long)

// === Offset-aware "Today" and "Last 7 days" using a custom rollover hour ===
// Pass :offsetSecs = ROLLOVER_HOUR * 3600 (e.g., 4*3600 for 4 AM)

    // Today count with custom rollover (works for any timezone)
    @Query("""
SELECT COUNT(*) FROM puffs
WHERE date(((timestamp/1000) - :offsetSecs), 'unixepoch','localtime') =
      date((strftime('%s','now') - :offsetSecs), 'unixepoch','localtime')
""")
    fun todayCountFlowWithOffset(offsetSecs: Long): Flow<Int>

    @Query("""
SELECT * FROM puffs
WHERE date(((timestamp/1000) - :offsetSecs), 'unixepoch','localtime') =
      date((strftime('%s','now') - :offsetSecs), 'unixepoch','localtime')
ORDER BY timestamp DESC
""")
    fun todayPuffsFlowWithOffset(offsetSecs: Long): Flow<List<Puff>>

    @Query("""
SELECT COUNT(*) FROM puffs
WHERE date(((timestamp/1000) - :offsetSecs), 'unixepoch','localtime') >=
      date((strftime('%s','now') - :offsetSecs), 'unixepoch','localtime', '-6 day')
""")
    fun last7DaysTotalFlowWithOffset(offsetSecs: Long): Flow<Int>
}