package com.example.jvsglass.bluetooth.classic

import java.util.UUID

object ClassicConstants {
    // 通用UUID
    val UUID_RFCOMM: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    const val REQUEST_LOCATION = 1003

    // 重连机制
    const val MAX_RECONNECT_ATTEMPTS = 3
    const val RECONNECT_DELAY_MS = 3_000L

    // 连接超时
    const val CONNECT_TIMEOUT_MS = 30_000L

    // 接收缓冲区大小
    const val RECEIVE_BUFFER_SIZE = 1024
    const val MAX_HISTORY_SIZE = 50
}