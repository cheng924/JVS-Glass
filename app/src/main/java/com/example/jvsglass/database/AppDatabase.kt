package com.example.jvsglass.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TeleprompterArticleEntity::class, AiMessageEntity::class,
                TranslateHistoryEntity::class, TranslateItemEntity::class,
                AiConversationEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun TeleprompterArticleDao(): TeleprompterArticleDao
    abstract fun AiMessageDao(): AiMessageDao
    abstract fun AiConversationDao(): AiConversationDao
    abstract fun TranslateHistoryDao(): TranslateHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        internal fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "AppDatabase.db"  // 统一数据库名称
                ).build().also { INSTANCE = it }
            }
        }
    }
}
