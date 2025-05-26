package com.example.jvsglass.bluetooth.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import com.example.jvsglass.bluetooth.PacketCommandUtils
import com.example.jvsglass.bluetooth.ble.BLEConstants.CURRENT_MTU
import com.example.jvsglass.bluetooth.ble.BLEConstants.RETRY_INTERVAL
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.bluetooth.PacketMessageUtils
import com.example.jvsglass.utils.toHexString
import java.lang.ref.WeakReference

class BLEGattClient private constructor(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: BLEGattClient? = null

        fun getInstance(context: Context): BLEGattClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BLEGattClient(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var connectedDevice: BluetoothDevice? = null    // 设备引用
    private val contextRef = WeakReference(context)
    private var bluetoothGatt: BluetoothGatt? = null
    private var isDisconnecting = false // 断联状态标记
    private var connectionState = BluetoothProfile.STATE_DISCONNECTED // 连接状态跟踪
    private var retryCount = 0  // 重试计数
    private var lastSentMessage: String? = null
    private var isSending = false
    var isConnecting = false
    private var servicesDiscovered = false

    // 消息回调接口
    interface MessageListener {
        fun onMessageReceived(message: String)
        fun onMessageSent(message: String)
    }
    // 消息监听器
    var messageListener: MessageListener? = null

    // GATT客户端回调
    private val gattClientCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            LogUtils.info("[BLE-Callback] onConnectionStateChange: status=$status, newState=$newState")
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        LogUtils.info("[BLE] 已连接到设备 ${gatt.device.address}")
                        LogUtils.info("[BLE] 协议栈版本：${gatt.device.type}，地址：${gatt.device.address}")
                        retryCount = 0
                        gatt.requestMtu(CURRENT_MTU)    // 连接成功时请求MTU
                        connectionState = BluetoothProfile.STATE_CONNECTED
                        isConnecting = false
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        LogUtils.info("[BLE] GATT_SUCCESS 物理层断开")
                        LogUtils.info("[BLE] 断开时的连接参数：${gatt.device}")
                        if (isConnecting) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                reconnect()
                            }, RETRY_INTERVAL)
                        }
                        connectionState = BluetoothProfile.STATE_DISCONNECTED
                    }
                }
                else -> {
                    LogUtils.error("[BLE] 连接失败，错误码：$status")
                    reconnect() // 连接失败触发重连
                    connectionState = BluetoothProfile.STATE_DISCONNECTED
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                LogUtils.error("[BLE] 服务发现失败，状态码：$status")
                return
            }

            // 获取服务
            servicesDiscovered = true
            val service = gatt.getService(BLEConstants.SERVICE_UUID)
            if (service == null) {
                LogUtils.error("[BLE] 未找到服务 UUID=${BLEConstants.SERVICE_UUID}")
                return
            }

            // 写入特征值
            val writeCharacteristic = service.getCharacteristic(BLEConstants.WRITE_CHAR_UUID)
            if (writeCharacteristic == null) {
                LogUtils.error("[BLE] 未找到写入特征值 UUID=${BLEConstants.WRITE_CHAR_UUID}")
                return
            }

            // 通知特征值
            val notifyCharacteristic = service.getCharacteristic(BLEConstants.NOTIFY_CHAR_UUID)
            if (notifyCharacteristic == null) {
                LogUtils.error("[BLE] 未找到通知特征值 UUID=${BLEConstants.NOTIFY_CHAR_UUID}")
                return
            }

            // 启动通知
            val enableNotify = gatt.setCharacteristicNotification(notifyCharacteristic, true)
            LogUtils.debug("[BLE] 启用通知结果：$enableNotify")

            // 写入描述符值
            val descriptor = notifyCharacteristic.getDescriptor(BLEConstants.CCCD_UUID)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val writeSuccess = gatt.writeDescriptor(descriptor)
            if (!writeSuccess) {
                LogUtils.error("[BLE] 无法写入描述符")
            } else {
                LogUtils.info("[BLE] 已请求启用通知")
            }
        }

        @Deprecated("Deprecated in Java")
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == BLEConstants.NOTIFY_CHAR_UUID) {
                Handler(Looper.getMainLooper()).post {
                    try {
//                        val message = PacketMessageUtils.processPacket(value)
//                        messageListener?.onMessageReceived(message)

                        val parsed = PacketCommandUtils.parsePacket(value)
                        parsed?.let {
                            val returnCmd = it.cmd          // 主命令类型
                            var resultCmd: Byte? = null     // TLV_RESULT_CMD
                            var resultCode: Byte? = null    // TLV_RESULT_CODE

                            for (tlv in it.tlvs) {
                                when (tlv.type) {
                                    0x01.toByte() -> { resultCmd = tlv.value[0] }
                                    0x02.toByte() -> { resultCode = tlv.value[0] }
                                }
                            }
                            LogUtils.info("[BLE] 接收到消息：$resultCmd|$resultCode")
                            messageListener?.onMessageReceived("$resultCmd|$resultCode")
                        }
                    } catch (e: Exception) {
                        LogUtils.error("[BLE] 消息解析异常，数据：${value.toHexString()}", e)
                    }
                }
            } else {
                LogUtils.warn("收到未知特征的通知：${characteristic.uuid}")
            }
        }

        // MTU变化处理
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            LogUtils.info("[BLE-Callback] onMtuChanged: status=$status, newMTU=$mtu")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                CURRENT_MTU = mtu
                LogUtils.info("[BLE] MTU更新为：$mtu")
                // MTU 协商成功后发起服务发现
                val success = gatt.discoverServices()
                LogUtils.debug("[BLE] 服务发现请求结果：$success")
                if (!success) {
                    LogUtils.error("[BLE] 服务发现请求失败")
                }
            } else {
                LogUtils.error("[BLE] MTU更新失败，状态码：$status")
            }
        }

        // 写入确认回调
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            LogUtils.info("[BLE] onCharacteristicWrite 状态码：$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                LogUtils.debug("[BLE] 数据包已成功发送")
                isSending = false
                lastSentMessage?.let { msg ->
                    messageListener?.onMessageSent(msg)
                    lastSentMessage = null
                }
            } else {
                LogUtils.error("[BLE] 数据包发送失败，状态码：$status")
                isSending = false
            }
        }
    }

    init {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isConnected()) {
                autoConnect()
            }
        }, 2 * 1000L)
    }

    fun isConnected(): Boolean {
        return connectionState == BluetoothProfile.STATE_CONNECTED
    }

    @SuppressLint("MissingPermission")
    fun autoConnect() {
        getDeviceAddress()?.let { address ->
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter.isEnabled) {
                val device = bluetoothAdapter.getRemoteDevice(address)
                connectToDevice(device)
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun reconnect() {
        if (isConnecting || connectionState == BluetoothProfile.STATE_CONNECTED) return

        if (retryCount < BLEConstants.MAX_RETRY) {
            retryCount++
            LogUtils.info("准备第 $retryCount 次重连，最大次数 ${BLEConstants.MAX_RETRY}")
            disconnect()    // 释放连接资源

            Handler(Looper.getMainLooper()).postDelayed({
                connectedDevice?.let { device ->
                    LogUtils.debug("[BLE] 尝试重新连接...")
                    connectToDevice(device)
                } ?: LogUtils.error("无法重连，设备引用为空")
            }, RETRY_INTERVAL) // 2秒后重试
        } else {
            LogUtils.error("[BLE] 已达到最大重试次数，停止重连")
            retryCount = 0
            connectedDevice = null // 清除设备引用
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BluetoothDevice) {
        if (isConnecting || connectionState == BluetoothProfile.STATE_CONNECTED) {
            LogUtils.info("[BLE] 已处于连接中或已连接，取消新连接请求")
//            return
        }
        isConnecting = true
//        disconnect()    // 先关闭旧连接
        LogUtils.info("[BLE] 尝试连接设备 ${device.address}")
        LogUtils.info("[BLE] 开始创建GATT连接，传输模式：${BluetoothDevice.TRANSPORT_LE}")
        connectedDevice = device // 保存设备对象
        val ctx = contextRef.get() ?: run {
            LogUtils.error("[BLE] 上下文已释放，无法创建GATT连接")
            return
        }
        bluetoothGatt = device.connectGatt(ctx, false, gattClientCallback, BluetoothDevice.TRANSPORT_LE).also {
            if (it == null) {
                LogUtils.error("[BLE] connectGatt返回空对象，可能达到连接数限制")
            } else {
                LogUtils.debug("[BLE] GATT对象创建成功：${it.device.address}")
            }
        }
        saveDeviceAddress(device.address) // 连接时保存设备地址
    }

    /*********************
    // 正常断开（保留存储）
    disconnect()
    // 断开并清除存储
    disconnect(shouldClearSavedDevice = true)
    *********************/
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect(clearSavedDevice: Boolean = false) {
        if (isDisconnecting) return // 避免重复调用
        bluetoothGatt?.let {
            isDisconnecting = true
            try {
                Handler(Looper.getMainLooper()).postDelayed({
                    bluetoothGatt?.disconnect()
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    isDisconnecting = false
                    isSending = false // 重置发送状态
                    isConnecting = false
                    connectionState = BluetoothProfile.STATE_DISCONNECTED
                    if (clearSavedDevice) {
                        clearSavedDeviceAddress()
                    }
                    LogUtils.debug("[BLE] 资源完全释放完成")
                }, 500) // 增加延迟确保异步操作完成
                LogUtils.info("[BLE] 连接已主动断开")
            } catch (e: Exception) {
                // 处理权限被拒绝的情况
                LogUtils.error("[BLE] 断开异常: ${e.javaClass.simpleName}")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendCommand(command: ByteArray) {
        if (!isConnected() || !servicesDiscovered) {
            LogUtils.warn("[BLE] 未连接或服务未发现，稍后重试")
            return
        }
        LogUtils.info("[BLE] 发送指令：${command.toHexString()}")
        sendPacket(command)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendMessage(message: String) {
        lastSentMessage = message

        val packet = PacketMessageUtils.createPacket(message)
        LogUtils.info("[BLE] 发送消息 $message")
        sendPacket(packet)
    }

    // 分包发送逻辑
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendPacket(packet: ByteArray) {
        val context = contextRef.get() ?: run {
            LogUtils.error("[BLE] 上下文已释放")
            isSending = false
            return
        }

        val gatt = bluetoothGatt ?: run {
            LogUtils.error("[BLE] GATT连接不可用")
            isSending = false
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device = connectedDevice ?: run {
            LogUtils.error("[BLE] 设备引用丢失")
            isSending = false
            return
        }

        when (bluetoothManager.getConnectionState(device, BluetoothProfile.GATT)) {
            BluetoothProfile.STATE_CONNECTED -> {
                LogUtils.debug("[BLE] 设备连接正常 (${device.address})")
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                LogUtils.error("[BLE] 设备已断开连接")
                isSending = false
                return
            }
            else -> {
                LogUtils.error("[BLE] 设备连接状态异常")
                isSending = false
                return
            }
        }

        val service = gatt.getService(BLEConstants.SERVICE_UUID) ?: run {
            LogUtils.error("[BLE] 服务不可用")
            isSending = false
            return
        }

        val characteristic = service.getCharacteristic(BLEConstants.WRITE_CHAR_UUID) ?: run {
            LogUtils.error("[BLE] 特征值不可用")
            isSending = false
            return
        }

        // 检查特征值属性
        when {
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 -> {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 -> {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> {
                LogUtils.error("[BLE] 特征值不支持写入")
                isSending = false
                return
            }
        }

        LogUtils.info("[BLE] 发送数据包：${packet.toHexString()}")

        try {
            isSending = true
            characteristic.value = packet

            if (!gatt.writeCharacteristic(characteristic)) {
                LogUtils.error("[BLE] 写入特征值失败")
                LogUtils.info("[BLE] 特征值UUID: ${characteristic.uuid}")
                LogUtils.debug("[BLE] 当前MTU: $CURRENT_MTU")
                LogUtils.debug("[BLE] 数据包长度: ${packet.size}")
                isSending = false

                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isSending && bluetoothManager.getConnectionState(device, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED) {
                        if (gatt.writeCharacteristic(characteristic)) {
                            LogUtils.debug("[BLE] 重试写入成功")
                        } else {
                            LogUtils.error("[BLE] 重试写入仍失败")
                        }
                    } else {
                        LogUtils.warn("[BLE] 重试时连接不可用或正在发送")
                    }
                }, 1_000L)
            } else {
                LogUtils.debug("[BLE] 数据包已提交写入")
            }
        } catch (e: SecurityException) {
            LogUtils.error("[BLE] 权限异常：${e.message}")
            isSending = false
        } catch (e: IllegalStateException) {
            LogUtils.error("[BLE] GATT状态异常：${e.message}")
            isSending = false
        } catch (e: Exception) {
            LogUtils.error("[BLE] 数据包发送异常", e)
            isSending = false
        }
    }

    // 存储/读取设备地址
    @SuppressLint("UseKtx")
    private fun saveDeviceAddress(address: String) {
        contextRef.get()?.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)?.edit()
            ?.putString("last_connected_device", address)?.apply()
    }

    private fun getDeviceAddress(): String? {
        return contextRef.get()?.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
            ?.getString("last_connected_device", null)
    }

    @SuppressLint("UseKtx")
    private fun clearSavedDeviceAddress() {
        contextRef.get()?.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)?.edit()
            ?.putString("last_connected_device", null)?.apply()
    }
}