package com.example.jvsglass

import android.app.Application
import com.example.jvsglass.database.AppDatabaseProvider

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDatabaseProvider.init(this) // 数据库初始化
    }
}