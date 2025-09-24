package com.example.puffs.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Finalized session with start/end times
@Entity(tableName = "sessions2")
data class Session2(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTs: Long,
    val endTs: Long,
    val puffCount: Int
)

// Single-row draft session (open session in progress)
@Entity(tableName = "draft_session")
data class DraftSession(
    @PrimaryKey val id: Int = 1,
    val startTs: Long,
    val lastPuffTs: Long,
    val puffCount: Int
)
