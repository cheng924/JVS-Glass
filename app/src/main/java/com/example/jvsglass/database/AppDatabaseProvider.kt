package com.example.jvsglass.database

import android.content.Context

object AppDatabaseProvider {
    private var _db: AppDatabase? = null
    private val lock = Any()

    fun init(context: Context) {
        synchronized(lock) {
            if (_db == null) {
                _db = AppDatabase.getInstance(context.applicationContext)
            }
        }
    }

    val db: AppDatabase
        get() = _db ?: throw IllegalStateException("AppDatabase 未初始化，请先在 Application 中调用 init()")

    fun close() {
        _db?.close()
        _db = null
    }
}