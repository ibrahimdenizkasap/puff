package com.example.puffs.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Puff::class,
        Session::class,      // old daily aggregate
        Session2::class,     // new finalized session
        DraftSession::class  // new draft
    ],
    version = 3, // bump from your current
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun puffDao(): PuffDao
    abstract fun dailySessionDao(): DailySessionDao       // renamed old DAO
    abstract fun autoSessionDao(): AutoSessionDao         // new DAO

    companion object {
        @Volatile private var INSTANCE: AppDb? = null
        fun get(context: Context): AppDb = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context, AppDb::class.java, "puffs.db")
                .addMigrations(object : Migration(2, 3) {    // adjust from your previous version
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS sessions2 (" +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                    "startTs INTEGER NOT NULL," +
                                    "endTs INTEGER NOT NULL," +
                                    "puffCount INTEGER NOT NULL)"
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS draft_session (" +
                                    "id INTEGER NOT NULL PRIMARY KEY," +
                                    "startTs INTEGER NOT NULL," +
                                    "lastPuffTs INTEGER NOT NULL," +
                                    "puffCount INTEGER NOT NULL)"
                        )
                    }
                })
                .build().also { INSTANCE = it }
        }
    }
}

