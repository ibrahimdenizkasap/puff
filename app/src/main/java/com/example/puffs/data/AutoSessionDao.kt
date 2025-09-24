package com.example.puffs.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AutoSessionDao {
    // finalized sessions
    @Insert
    suspend fun insertSession(s: Session2): Long

    @Query("SELECT * FROM sessions2 ORDER BY endTs DESC")
    fun sessionsDescFlow(): Flow<List<Session2>>

    @Query("SELECT * FROM sessions2 ORDER BY endTs DESC LIMIT :limit")
    fun latestSessions(limit: Int): Flow<List<Session2>>

    // draft session (single row id=1)
    @Query("SELECT * FROM draft_session WHERE id = 1")
    suspend fun getDraftOnce(): DraftSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(d: DraftSession)

    @Query("DELETE FROM draft_session WHERE id = 1")
    suspend fun clearDraft()

    // 👇 add this
    @Query("DELETE FROM sessions2")
    suspend fun clearSessions()

}
