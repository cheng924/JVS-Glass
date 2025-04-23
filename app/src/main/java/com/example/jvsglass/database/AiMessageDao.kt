package com.example.jvsglass.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AiMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<AiMessageEntity>)

    @Query("SELECT * FROM ai_messages WHERE conversation_id = :convId ORDER BY timestamp ASC")
    suspend fun getByConversationId(convId: String): List<AiMessageEntity>

    @Query("DELETE FROM ai_messages WHERE conversation_id = :convId")
    suspend fun deleteByConversationId(convId: String)
}