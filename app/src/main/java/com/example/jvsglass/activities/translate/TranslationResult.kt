package com.example.jvsglass.activities.translate

data class TranslationResult(
    val sourceText: String,
    val targetText: String,
    val isPartial: Boolean = false
)
