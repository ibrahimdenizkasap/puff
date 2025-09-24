package com.example.puffs.data
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey val date: String, // YYYY-MM-DD (local)
    val count: Int
)
