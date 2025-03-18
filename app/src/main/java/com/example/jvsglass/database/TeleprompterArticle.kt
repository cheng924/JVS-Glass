package com.example.jvsglass.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.jvsglass.activities.FileItem

@Entity(tableName = "teleprompter_articles")
data class TeleprompterArticle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "create_date") val createDate: String, // 格式：yyyy/M/d HH:mm
    @ColumnInfo(name = "content") val content: String
)

fun TeleprompterArticle.toFileItem(): FileItem {
    return FileItem(
        fileName = title,
        fileDate = createDate,
        fileContent = content
    )
}
