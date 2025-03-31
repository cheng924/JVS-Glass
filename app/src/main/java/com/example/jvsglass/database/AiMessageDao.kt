package com.example.jvsglass.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiMessageDao {
    @Insert
    suspend fun insert(message: AiMessageEntity): Long

    @Query("SELECT * FROM ai_messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<AiMessageEntity>>

    @Query("DELETE FROM ai_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    @Query("DELETE FROM ai_messages WHERE file_path = :filePath")
    suspend fun deleteByFilePath(filePath: String)

    @Query("SELECT COUNT(*) FROM ai_messages WHERE is_sent = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("DELETE FROM ai_messages")
    suspend fun clearAll()
}