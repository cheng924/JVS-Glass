package com.example.jvsglass.classic

import java.util.UUID

object ClassicConstants {
    // 通用UUID
    val BT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // 服务名称
    const val SERVICE_NAME = "ClassicBluetoothDemo"

    // 请求码
    const val REQUEST_ENABLE_BT = 1001
    const val REQUEST_DISCOVERABLE = 1002
    const val REQUEST_LOCATION = 1003

    // 重连机制
    const val MAX_RECONNECT_ATTEMPTS = 3
    const val RECONNECT_DELAY_MS = 3_000L

    // 连接超时
    const val CONNECT_TIMEOUT_MS = 10_000L

    // 接收缓冲区大小
    const val RECEIVE_BUFFER_SIZE = 1024
    const val MAX_HISTORY_SIZE = 50

    const val SERVER_DISCOVERABLE_TIME = 120_000L
}