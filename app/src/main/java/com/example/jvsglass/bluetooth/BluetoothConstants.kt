package com.example.jvsglass.bluetooth

import java.util.UUID

object BluetoothConstants {
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    val SERVICE_CHAR_UUID: UUID = UUID.fromString("F48A23C0-F69A-11E8-8EB2-F2801F1B9FD1")
    val WRITE_CHAR_UUID: UUID = UUID.fromString("F48A24C1-F69A-11E8-8EB2-F2801F1B9FD1")
    val NOTIFY_CHAR_UUID: UUID = UUID.fromString("F48A25C2-F69A-11E8-8EB2-F2801F1B9FD1")

    val SERVICE_AUDIO_UUID: UUID = UUID.fromString("e49a3001-f69a-11e8-8eb2-f2801f1b9fd1")
    val NOTIFY_AUDIO_UUID: UUID = UUID.fromString("e49a3003-f69a-11e8-8eb2-f2801f1b9fd1")

    val UUID_RFCOMM: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    var CURRENT_MTU = 517   // MTU值，可根据实际情况调整，此处设置为517字节（最大值）

    const val REQUEST_LOCATION = 1003

    const val MAX_RETRY = 3             // 重试次数
    const val RETRY_INTERVAL = 2_000L   // 重试间隔
    const val HEARTBEAT_INTERVAL = 10_000L  // 心跳监测间隔
    const val HEARTBEAT_TIMEOUT = 5_000L    // 心跳超时
    const val CONNECT_TIMEOUT = 30_000L
    const val DELAY_1S = 1_000L

    // 接收缓冲区大小
    const val RECEIVE_BUFFER_SIZE = 1024
    const val MAX_HISTORY_SIZE = 50
}