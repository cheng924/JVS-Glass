package com.example.jvsglass.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TeleprompterArticleDao {
    @Insert
    suspend fun insert(article: TeleprompterArticleEntity): Long

    @Query("SELECT * FROM teleprompter_articles ORDER BY id DESC")
    fun getAll(): Flow<List<TeleprompterArticleEntity>>

    @Query("DELETE FROM teleprompter_articles WHERE create_date = :articleDate")
    suspend fun delete(articleDate: String)

    @Query("SELECT COUNT(*) FROM teleprompter_articles")
    fun getArticleCount(): Flow<Int>
}