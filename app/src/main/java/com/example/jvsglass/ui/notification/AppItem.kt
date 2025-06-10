package com.example.jvsglass.ui.notification

import android.graphics.drawable.Drawable

data class AppItem(
    val packageName: String,    // 应用包名
    val appName: String,        // app名
    val appIcon: Drawable,
    var isEnabled: Boolean
)
