package com.example.informationfatigue.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DataRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dataRecordDao(): DataRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "information_fatigue_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
