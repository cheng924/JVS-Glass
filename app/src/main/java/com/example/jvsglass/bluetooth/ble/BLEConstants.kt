package com.example.jvsglass.bluetooth.ble

import android.bluetooth.BluetoothGattDescriptor
import java.util.UUID

object BLEConstants {
    // 自定义UUID（正式项目应使用唯一生成的UUID）
    val SERVICE_UUID: UUID = UUID.fromString("F48A23C0-F69A-11E8-8EB2-F2801F1B9FD1")
    val WRITE_CHAR_UUID: UUID = UUID.fromString("F48A24C1-F69A-11E8-8EB2-F2801F1B9FD1")
    val NOTIFY_CHAR_UUID: UUID = UUID.fromString("F48A25C2-F69A-11E8-8EB2-F2801F1B9FD1")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    var CURRENT_MTU = 517   // MTU值，可根据实际情况调整，此处设置为517字节（最大值）

    const val REQUEST_ENABLE_BT = 1001 // 蓝牙启用请求码
    const val REQUEST_CODE_BLE_PERMISSIONS = 1002 // 权限请求码

    const val MAX_RETRY = 3 // 重试次数
    const val RETRY_INTERVAL = 2_000L   // 重连间隔
    const val HEART_INTERVAL = 10_000L  // 心跳监测间隔
    const val SCAN_TIMEOUT = 10_000L // 10秒超时
}