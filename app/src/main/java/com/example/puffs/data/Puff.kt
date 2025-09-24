package com.example.puffs.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "puffs")
data class Puff(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)