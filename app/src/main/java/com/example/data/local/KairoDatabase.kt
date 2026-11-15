package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProviderEntity::class,
        ModelEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KairoDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: KairoDatabase? = null

        fun getInstance(context: Context): KairoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KairoDatabase::class.java,
                    "kairo_chat.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
