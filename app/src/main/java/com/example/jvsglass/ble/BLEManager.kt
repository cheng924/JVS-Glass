package com.example.jvsglass.ble

import android.content.Context

object BLEManager {
    private var bleClient: BLEGattClient? = null
    private val lock = Any()

    fun getClient(context: Context): BLEGattClient {
        synchronized(lock) {
            return bleClient ?: BLEGattClient(context.applicationContext).also {
                bleClient = it
            }
        }
    }
}