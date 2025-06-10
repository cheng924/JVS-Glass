package com.example.jvsglass.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.jvsglass.ui.teleprompter.FileItem

@Entity(tableName = "teleprompter_articles")
data class TeleprompterArticleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "create_date")
    val createDate: String, // 格式：yyyy/MM/dd HH:mm:ss

    @ColumnInfo(name = "content")
    val content: String
)

fun TeleprompterArticleEntity.toFileItem(): FileItem {
    return FileItem(
        fileName = title,
        fileDate = createDate,
        fileContent = content
    )
}

