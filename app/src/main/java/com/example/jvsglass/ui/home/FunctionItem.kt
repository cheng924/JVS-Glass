package com.example.jvsglass.ui.home

data class FunctionItem(
    val iconRes: Int,
    val title: String,
    val description: String,
    val targetActivity: Class<*>
)