package com.example.jvsglass.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction

// 组合查询：一个会话对应多条翻译条目
data class HistoryWithItems(
    @Embedded val history: TranslateHistoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "history_id"
    ) val items: List<TranslateItemEntity>
)

@Dao
interface TranslateHistoryDao {
    @Insert
    suspend fun insertHistory(history: TranslateHistoryEntity): Long

    @Insert
    suspend fun insertItems(items: List<TranslateItemEntity>)

    @Transaction
    suspend fun saveFullSession(
        history: TranslateHistoryEntity,
        items: List<TranslateItemEntity>
    ) {
        // 插入 session，获取自增ID
        val sessionId = insertHistory(history)
        // 将 sessionId 写入每个条目
        items.forEach { it.historyId = sessionId }
        insertItems(items)
    }

    @Transaction
    @Query("SELECT * FROM translate_history ORDER BY timestamp DESC")
    suspend fun getAllHistoriesWithItems(): List<HistoryWithItems>

    @Transaction
    @Query("SELECT * FROM translate_history WHERE id = :sessionId")
    suspend fun getHistoryById(sessionId: Long): HistoryWithItems?

    @Query("DELETE FROM translate_history WHERE id = :sessionId")
    suspend fun deleteHistory(sessionId: Long)
}
