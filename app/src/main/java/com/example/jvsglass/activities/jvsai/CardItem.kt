package com.example.jvsglass.activities.jvsai

import java.util.UUID

data class CardItem(
    val id: String = UUID.randomUUID().toString(), // 唯一标识符
    val title: String,
    val tag: String,
    val fileUri: String = "",
    val isAddItem: Boolean = false
)
