package com.example.jvsglass.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translate_history")
data class TranslateHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    /** 翻译类型：1＝同声传译，2＝文档翻译 */
    @ColumnInfo(name = "type")
    val type: Int,

    /** 语言对，例如 "zh/en" */
    @ColumnInfo(name = "content")
    val content: String,

    /** 额外信息：录音路径 or 文件名 */
    @ColumnInfo(name = "extra")
    val extra: String
)
