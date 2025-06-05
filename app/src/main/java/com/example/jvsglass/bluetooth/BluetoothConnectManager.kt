package com.example.jvsglass.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import com.example.jvsglass.bluetooth.BluetoothConstants.DELAY_1S
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.VoiceManager
import org.greenrobot.eventbus.EventBus

object BluetoothConnectManager {
    var onBleDeviceFound: ((BluetoothDevice) -> Unit)? = null
    var onClassicDeviceFound: ((BluetoothDevice) -> Unit)? = null
    var onDeviceConnected: ((BluetoothDevice) -> Unit)? = null
    var onMessageReceived: ((ByteArray) -> Unit)? = null
    var onVoiceReceived: ((ByteArray) -> Unit)? = null
    var onAudioStreamReceived: ((ByteArray) -> Unit)? = null

    data class ConnectionEvent(val isConnected: Boolean)
    private lateinit var appContext: Context
    private lateinit var bleClientListener: BLEClient.MessageListener
    private lateinit var classicCallback: ClassicClient.BluetoothCallback
    private var bleClient: BLEClient? = null
    private var classicClient: ClassicClient? = null

    private val bluetoothManager by lazy {
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter get() = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner get() = bluetoothAdapter.bluetoothLeScanner
    private var scanCallback: ScanCallback? = null

    fun initialize(
        context: Context,
        voiceManager: VoiceManager? = null
    ) {
        appContext = context.applicationContext

        bleClientListener = object : BLEClient.MessageListener {
            override fun onMessageReceived(value: ByteArray) {
                onMessageReceived?.invoke(value)
            }
        }
        bleClient = BLEClient.getInstance(appContext)
        bleClient!!.connectionListener = { EventBus.getDefault().post(ConnectionEvent(it))}

        classicCallback = object : ClassicClient.BluetoothCallback {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionSuccess(deviceName: String) {
                bluetoothAdapter.bondedDevices.find { it.name == deviceName }?.let {
                    onDeviceConnected?.invoke(it)
                }
            }
            override fun onConnectionFailed(message: String) {  }
            override fun onVoiceMessageReceived(voiceData: ByteArray) {
                onVoiceReceived?.invoke(voiceData)
            }

            override fun onAudioStreamReceived(streamData: ByteArray) {
//                voiceManager?.playStreamingAudio(streamData)
                onAudioStreamReceived?.invoke(streamData)
            }

            override fun onDisconnected() {  }
        }
        classicClient = ClassicClient(bluetoothAdapter, classicCallback)
        classicClient!!.connectionListener = { EventBus.getDefault().post(ConnectionEvent(it))}
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT]
    )
    fun disconnect() {
        scanCallback?.let {
            bleScanner.stopScan(it)
            LogUtils.info("[DualBluetoothManager] 停止扫描")
        }
        scanCallback = null

        bleClient?.disconnect()
        classicClient?.disconnect()
    }

    @RequiresPermission(
        allOf = [
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT
        ]
    )
    fun startAsClient() {
        startScan { device ->
            onBleDeviceFound?.invoke(device)
        }
        classicClient?.startDiscovery()
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION]
    )
    fun startScan(onFound: (BluetoothDevice) -> Unit) {
        val callback = object : ScanCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val uuids = result.scanRecord?.serviceUuids
                if (uuids?.any { it.uuid == BluetoothConstants.SERVICE_UUID } == true) {
                    bleScanner.stopScan(this)
                    LogUtils.info("[DualBluetoothManager] 停止扫描，连接设备 ${result.device.address}")
                    onFound(result.device)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { it.device?.let(onFound) }
            }

            override fun onScanFailed(errorCode: Int) {
                LogUtils.error("[DualBluetoothManager] 扫描失败: $errorCode")
            }
        }
        bleScanner.startScan(callback)
        scanCallback = callback
        LogUtils.info("[DualBluetoothManager] 启动扫描")
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
    )
    fun connectBle(device: BluetoothDevice) {
        bleClient?.apply {
            messageListener = bleClientListener
            connectToDevice(device)
        }
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
    )
    fun connectClassic(device: BluetoothDevice) {
        classicClient?.connectToDevice(device)
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
    )
    fun reconnectDevice(device: BluetoothDevice) {
        if (bleClient?.isConnected() != true) connectBle(device)
        if (classicClient?.coreState?.get() != ClassicClient.ConnectionState.CONNECTED) {
            Handler(Looper.getMainLooper()).postDelayed({
                connectClassic(device)
            }, DELAY_1S)  // 延迟1秒避免冲突
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendText(message: String) {
        bleClient?.sendMessage(message)
    }
}