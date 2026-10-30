package com.ltq.count.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CounterEntity::class,
        CountEventEntity::class,
        TagEntity::class,
        CounterTagEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class CountDatabase : RoomDatabase() {
    abstract fun counterDao(): CounterDao
    abstract fun eventDao(): EventDao
    abstract fun tagDao(): TagDao
    abstract fun counterTagDao(): CounterTagDao

    companion object {
        private const val NAME = "count.db"

        @Volatile
        private var instance: CountDatabase? = null

        fun get(context: Context): CountDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CountDatabase::class.java,
                    NAME,
                ).build().also { instance = it }
            }
    }
}
