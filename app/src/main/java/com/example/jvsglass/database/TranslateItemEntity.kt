package com.example.jvsglass.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// 翻译条目实体，每条记录是一句原文-译文对
@Entity(
    tableName = "translate_item",
    foreignKeys = [
        ForeignKey(
            entity = TranslateHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["history_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("history_id")]
)

data class TranslateItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "history_id")
    var historyId: Long = 0L,

    @ColumnInfo(name = "order_index")
    val orderIndex: Int,

    @ColumnInfo(name = "source_text")
    val sourceText: String,

    @ColumnInfo(name = "target_text")
    val targetText: String
)
