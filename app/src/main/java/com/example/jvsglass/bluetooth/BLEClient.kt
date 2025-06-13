package com.example.jvsglass.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import com.example.jvsglass.bluetooth.BluetoothConstants.CURRENT_MTU
import com.example.jvsglass.bluetooth.BluetoothConstants.MAX_RETRY
import com.example.jvsglass.bluetooth.BluetoothConstants.RETRY_INTERVAL
import com.example.jvsglass.bluetooth.BluetoothConstants.HEARTBEAT_INTERVAL
import com.example.jvsglass.bluetooth.BluetoothConstants.HEARTBEAT_TIMEOUT
import com.example.jvsglass.bluetooth.PacketCommandUtils.parseDbClickPacket
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.toHexString
import java.lang.ref.WeakReference

class BLEClient private constructor(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: BLEClient? = null

        fun getInstance(context: Context): BLEClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BLEClient(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    var connectionListener: ((connected: Boolean, deviceName: String?, deviceAddress: String?) -> Unit)? = null

    private var connectedDevice: BluetoothDevice? = null
    private val contextRef = WeakReference(context)
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectionState = BluetoothProfile.STATE_DISCONNECTED
    private var retryCount = 0
    private var writeRetryCount = 0
    private var isSending = false
    var isConnecting = false
    private var servicesDiscovered = false
    private var heartbeatHandler: Handler = Handler(Looper.getMainLooper())
    private var lastHeartbeatResponse: Long = 0L
    private var heartbeatRunnable: Runnable? = null
    private val pendingDescriptors = ArrayDeque<BluetoothGattDescriptor>()

    private val packetQueue = ArrayDeque<ByteArray>()
    private var isWritingPacket = false

    interface MessageListener {
        fun onMessageReceived(value: ByteArray)
    }
    var messageListener: MessageListener? = null

    interface AudioListener {
        fun onAudioChunk(data: ByteArray)
    }
    var audioListener: AudioListener? = null

    // GATT客户端回调
    private val gattClientCallback = object : BluetoothGattCallback() {
        @RequiresPermission(
            allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
        )
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            LogUtils.info("[BLE] onConnectionStateChange: status=$status, newState=$newState")
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        LogUtils.info("[BLE] 已连接到设备 ${gatt.device.address}")
                        LogUtils.info("[BLE] 协议栈版本：${gatt.device.type}，地址：${gatt.device.address}")
                        retryCount = 0
                        gatt.requestMtu(CURRENT_MTU)
                        connectionState = BluetoothProfile.STATE_CONNECTED
                        isConnecting = false
                        lastHeartbeatResponse = System.currentTimeMillis()
                        connectionListener?.invoke(true, gatt.device.name ,gatt.device.address)
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        LogUtils.info("[BLE] 物理层断开，断开时的连接参数：${gatt.device}")
                        if (isConnecting) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                reconnect()
                            }, RETRY_INTERVAL)
                        }
                        connectionState = BluetoothProfile.STATE_DISCONNECTED
                        isConnecting = false
                        connectionListener?.invoke(false, "", "")
                        stopHeartbeat()
                        connectedDevice?.let { BluetoothConnectManager.reconnectDevice(it) }
                    }
                }
                else -> {
                    LogUtils.error("[BLE] 连接失败，错误码：$status")
                    reconnect()
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
            servicesDiscovered = true

            // 文字
            gatt.getService(BluetoothConstants.SERVICE_CHAR_UUID)?.let { svc ->
                svc.getCharacteristic(BluetoothConstants.NOTIFY_CHAR_UUID)?.let { char ->
                    enqueueEnableNotify(gatt, char)
                } ?: LogUtils.error("[BLE] 未找到文字 Notify 特征 ${BluetoothConstants.NOTIFY_CHAR_UUID}")
            } ?: LogUtils.error("[BLE] 未找到文字服务 ${BluetoothConstants.SERVICE_CHAR_UUID}")

            // 音频
            gatt.getService(BluetoothConstants.SERVICE_AUDIO_UUID)?.let { svc ->
                svc.getCharacteristic(BluetoothConstants.NOTIFY_AUDIO_UUID)?.let { char ->
                    enqueueEnableNotify(gatt, char)
                } ?: LogUtils.error("[BLE] 未找到音频 Notify 特征 ${BluetoothConstants.NOTIFY_AUDIO_UUID}")
            } ?: LogUtils.error("[BLE] 未找到音频服务 ${BluetoothConstants.SERVICE_AUDIO_UUID}")
        }

        @Deprecated("Deprecated in Java")
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            when (characteristic.uuid) {
                BluetoothConstants.NOTIFY_CHAR_UUID -> {
                    lastHeartbeatResponse = System.currentTimeMillis()
                    Handler(Looper.getMainLooper()).post {
                        LogUtils.info("[BLE] ${System.currentTimeMillis()} 收到消息：${value.toHexString()}")
                        if (value[1] == 0x86.toByte()) {
                            val key = parseDbClickPacket(value)
                            when (key) {
                                PacketCommandUtils.DbClickKeyValue.STATUS_START -> { LogUtils.info("[BLE] 双击开始") }
                                PacketCommandUtils.DbClickKeyValue.STATUS_STOP -> { LogUtils.info("[BLE] 双击停止") }
                                PacketCommandUtils.DbClickKeyValue.STATUS_CLOSE -> { LogUtils.info("[BLE] 双击关闭") }
                                else -> { LogUtils.error("[BLE] 未知的双击动作：${value.toHexString()}") }
                            }
                        } else {
                            messageListener?.onMessageReceived(value)
                        }
                    }
                }
                BluetoothConstants.NOTIFY_AUDIO_UUID -> {
                    audioListener?.onAudioChunk(value)
                }
                else -> {
                    LogUtils.warn("[BLE] 收到未知特征的通知：${characteristic.uuid}")
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            LogUtils.info("[BLE] onDescriptorWrite ${descriptor.characteristic.uuid}, status=$status")
            // 移除已完成的
            pendingDescriptors.removeFirstOrNull()
            // 如果队列还有，就写下一个
            pendingDescriptors.firstOrNull()?.let {
                LogUtils.info("[BLE] 继续写入下一个 ${it.characteristic.uuid}")
                gatt.writeDescriptor(it)
            }
        }

        // MTU变化处理
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            LogUtils.info("[BLE] onMtuChanged: status=$status, newMTU=$mtu")
            CURRENT_MTU = if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23
            LogUtils.info("[BLE] MTU更新为：$mtu")
            // MTU协商成功后发起服务发现
            val success = gatt.discoverServices()
            LogUtils.debug("[BLE] 服务发现请求结果：$success")
        }

        // 写入确认回调
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            LogUtils.info("[BLE] onCharacteristicWrite 状态码：$status")
            isWritingPacket = false
            if (status == BluetoothGatt.GATT_SUCCESS) {
                packetQueue.removeFirstOrNull()
                LogUtils.debug("[BLE] 数据包已成功发送")
            } else {
                LogUtils.error("[BLE] 数据包发送失败，状态码：$status")
            }
            isSending = false
            writeNextPacket()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun enqueueEnableNotify(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        // 本地打开
        val enableNotify = gatt.setCharacteristicNotification(characteristic, true)
        LogUtils.info("[BLE] 启用 ${characteristic.uuid} 通知结果: $enableNotify")

        // 写入描述符值
        val descriptor = characteristic.getDescriptor(BluetoothConstants.CCCD_UUID)
            ?: run {
                LogUtils.error("[BLE] 找不到 CCCD 描述符 ${characteristic.uuid}")
                return
            }
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        pendingDescriptors += descriptor
        LogUtils.info("[BLE] 已入队 CCCD ${characteristic.uuid}, 队列长度=${pendingDescriptors.size}")

        if (pendingDescriptors.size == 1) {
            LogUtils.info("[BLE] 立即调用 writeDescriptor for ${descriptor.characteristic.uuid}")
            gatt.writeDescriptor(descriptor)
        }
    }


    fun isConnected(): Boolean {
        return connectionState == BluetoothProfile.STATE_CONNECTED
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
    )
    fun reconnect() {
        if (isConnecting || connectionState == BluetoothProfile.STATE_CONNECTED) return
        if (retryCount < MAX_RETRY) {
            retryCount++
            val delay = RETRY_INTERVAL * (1 shl retryCount) // 指数退避
            LogUtils.info("[BLE] 准备重连：${retryCount}/${MAX_RETRY}，延迟：$delay ms")

            disconnect()
            Handler(Looper.getMainLooper()).postDelayed({
                connectedDevice?.let { device ->
                    connectToDevice(device)
                } ?: LogUtils.error("[BLE] 无法重连，设备引用为空")
            }, delay)
        } else {
            LogUtils.error("[BLE] 已达到最大重试次数，停止重连")
            retryCount = 0
            connectedDevice = null
        }
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
    )
    fun connectToDevice(device: BluetoothDevice) {
        if (isConnecting || connectionState == BluetoothProfile.STATE_CONNECTED) {
            LogUtils.info("[BLE] 已处于连接中或已连接，取消新连接请求")
//            return
        }
        isConnecting = true
        LogUtils.info("[BLE] 尝试连接设备 ${device.address}")
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
        lastHeartbeatResponse = System.currentTimeMillis()
//        startHeartbeat()
        saveDeviceAddress(device.address) // 连接时保存设备地址
    }

    /*********************
    正常断开（保留存储）  disconnect()
    断开并清除存储  disconnect(shouldClearSavedDevice = true)
    *********************/
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect(clearSavedDevice: Boolean = false) {
        bluetoothGatt?.let {
            it.disconnect()
            it.close()
            bluetoothGatt = null
            isSending = false
            isConnecting = false
            connectionState = BluetoothProfile.STATE_DISCONNECTED
            stopHeartbeat()
            if (clearSavedDevice) clearSavedDeviceAddress()
            LogUtils.info("[BLE] 连接已断开")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendCommand(command: ByteArray) {
        if (!isConnected() || !servicesDiscovered) {
            LogUtils.warn("[BLE] 未连接或服务未发现，稍后重试")
            return
        }
        LogUtils.info("[BLE] 发送指令：${command.toHexString()}")
//        sendPacket(command)
        packetQueue += command
        writeNextPacket()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendMessage(message: String) {
        val packet = PacketMessageUtils.createPacket(message)
        LogUtils.info("[BLE] 发送消息 $message")
        sendPacket(packet)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun writeNextPacket() {
        if (isWritingPacket || packetQueue.isEmpty() || bluetoothGatt == null) return
        val packet = packetQueue.first()
        isWritingPacket = true

        val service = bluetoothGatt!!.getService(BluetoothConstants.SERVICE_CHAR_UUID) ?: run {
            LogUtils.error("[BLE] 服务不可用")
            isWritingPacket = false
            return
        }
        val characteristic = service.getCharacteristic(BluetoothConstants.WRITE_CHAR_UUID) ?: run {
            LogUtils.error("[BLE] 特征值不可用")
            isWritingPacket = false
            return
        }

        // 保持原有写类型选择逻辑
        when {
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ->
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 ->
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }

        LogUtils.info("[BLE] 发送数据包：${packet.toHexString()}")
        characteristic.value = packet
        val ok = bluetoothGatt!!.writeCharacteristic(characteristic)
        if (!ok) {
            LogUtils.error("[BLE] 写出错，稍后重试本包")
            // 简单延迟一会儿再试
            Handler(Looper.getMainLooper()).postDelayed({
                isWritingPacket = false
                writeNextPacket()
            }, RETRY_INTERVAL)
        }
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

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
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

        val service = gatt.getService(BluetoothConstants.SERVICE_CHAR_UUID) ?: run {
            LogUtils.error("[BLE] 服务不可用")
            isSending = false
            return
        }

        val characteristic = service.getCharacteristic(BluetoothConstants.WRITE_CHAR_UUID) ?: run {
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
                if (writeRetryCount < MAX_RETRY) {
                    writeRetryCount++
                    Handler(Looper.getMainLooper()).postDelayed({ sendPacket(packet) }, RETRY_INTERVAL)
                } else {
                    LogUtils.error("[BLE] 写入失败，已达最大重试次数")
                    isSending = false
                    writeRetryCount = 0
                }
            } else {
                writeRetryCount = 0
            }
        } catch (e: Exception) {
            LogUtils.error("[BLE] 数据包发送异常", e)
            isSending = false
        }
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
    )
    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatRunnable = Runnable {
            if (System.currentTimeMillis() - lastHeartbeatResponse > HEARTBEAT_TIMEOUT) {
                BluetoothConnectManager.reconnectDevice(connectedDevice!!)
            } else {
                val heartbeatPacket = byteArrayOf(0x01)
                sendPacket(heartbeatPacket)
                LogUtils.debug("[BLE] 发送心跳包")
                heartbeatHandler.postDelayed(heartbeatRunnable!!, HEARTBEAT_INTERVAL)
            }
        }
        heartbeatHandler.postDelayed(heartbeatRunnable!!, HEARTBEAT_INTERVAL)
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let { heartbeatHandler.removeCallbacks(it) }
        heartbeatRunnable = null
    }

    // 存储/读取设备地址
    @SuppressLint("UseKtx")
    private fun saveDeviceAddress(address: String) {
        contextRef.get()?.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)?.edit()
            ?.putString("last_connected_device", address)?.apply()
    }

    fun getDeviceAddress(): String? {
        return contextRef.get()?.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
            ?.getString("last_connected_device", null)
    }

    @SuppressLint("UseKtx")
    private fun clearSavedDeviceAddress() {
        contextRef.get()?.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)?.edit()
            ?.putString("last_connected_device", null)?.apply()
    }
}