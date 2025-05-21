package com.example.jvsglass.bluetooth.ble

import java.util.UUID

object BLEConstants {
    // 自定义UUID（正式项目应使用唯一生成的UUID）
    val SERVICE_UUID: UUID = UUID.fromString("E49A3001-F69A-11E8-8EB2-F2801F1B9FD1")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("E49A3002-F69A-11E8-8EB2-F2801F1B9FD1")
    val DESCRIPTOR_UUID: UUID = UUID.fromString("E49A3003-F69A-11E8-8EB2-F2801F1B9FD1")

    var CURRENT_MTU = 517   // MTU值，可根据实际情况调整，此处设置为517字节（最大值）

    const val REQUEST_ENABLE_BT = 1001 // 蓝牙启用请求码
    const val REQUEST_CODE_BLE_PERMISSIONS = 1002 // 权限请求码

    const val MAX_RETRY = 3 // 重试次数
    const val HEART_INTERVAL = 10_000L  // 心跳监测间隔
    const val SCAN_TIMEOUT = 10_000L // 10秒超时
}