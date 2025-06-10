package com.example.jvsglass.ui.translate

data class TranslationResult(
    val sourceText: String,
    val targetText: String,
    val isPartial: Boolean = false  // 默认非临时结果
)
