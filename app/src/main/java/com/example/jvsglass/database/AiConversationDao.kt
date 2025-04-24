package com.example.jvsglass.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: AiConversationEntity)

    @Query("SELECT * FROM ai_conversations ORDER BY timestamp DESC")
    suspend fun getAll(): List<AiConversationEntity>

    @Query("SELECT * FROM ai_conversations WHERE conversationId = :id")
    suspend fun getById(id: String): AiConversationEntity

    @Query("DELETE FROM ai_conversations WHERE conversationId = :convId")
    suspend fun deleteById(convId: String)

    @Query("SELECT COUNT(*) FROM ai_conversations")
    fun getConversationCount(): Flow<Int>

    @Query("DELETE FROM ai_conversations WHERE conversationId = :conversationId")
    suspend fun delete(conversationId: String)
}