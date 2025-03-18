package com.example.jvsglass.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TeleprompterArticleDao {
    @Insert
    suspend fun insert(article: TeleprompterArticle): Long

    @Query("SELECT * FROM teleprompter_articles ORDER BY id DESC")
    fun getAll(): Flow<List<TeleprompterArticle>>

    @Query("DELETE FROM teleprompter_articles WHERE id = :articleId")
    suspend fun delete(articleId: Long)
}