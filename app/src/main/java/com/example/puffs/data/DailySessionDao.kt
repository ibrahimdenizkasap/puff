package com.example.puffs.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: Session)

    @Query("SELECT count FROM sessions WHERE date = date('now','localtime') LIMIT 1")
    fun todaySavedCountFlow(): Flow<Int?>
}
