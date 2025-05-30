package com.example.jvsglass.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.annotation.RequiresPermission
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.VoiceManager

object DualBluetoothManager {
    var onBleDeviceFound: ((BluetoothDevice) -> Unit)? = null
    var onClassicDeviceFound: ((BluetoothDevice) -> Unit)? = null
    var onDeviceConnected: ((BluetoothDevice) -> Unit)? = null
    var onMessageReceived: ((ByteArray) -> Unit)? = null
    var onVoiceReceived: ((ByteArray) -> Unit)? = null

    private lateinit var appContext: Context
    private lateinit var bleClientListener: BLEGattClient.MessageListener
    private lateinit var classicCallback: ClassicRfcommClient.BluetoothCallback
    private var bleGattClient: BLEGattClient? = null
    private var classicRfcommClient: ClassicRfcommClient? = null

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

        bleClientListener = object : BLEGattClient.MessageListener {
            override fun onMessageReceived(value: ByteArray) {
                onMessageReceived?.invoke(value)
            }
        }
        bleGattClient = BLEGattClient.getInstance(appContext)

        classicCallback = object : ClassicRfcommClient.BluetoothCallback {
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
                voiceManager?.playStreamingAudio(streamData)
            }

            override fun onDisconnected() {  }
        }
        classicRfcommClient = ClassicRfcommClient(bluetoothAdapter, classicCallback)
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT]
    )
    fun stop() {
        scanCallback?.let {
            bleScanner.stopScan(it)
            LogUtils.info("[DualBluetoothManager] 停止扫描")
        }
        scanCallback = null

        bleGattClient?.disconnect()
        classicRfcommClient?.disconnect()
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
        classicRfcommClient?.startDiscovery()
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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectBle(device: BluetoothDevice) {
        bleGattClient?.apply {
            messageListener = bleClientListener
            connectToDevice(device)
        }
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
    )
    fun connectClassic(device: BluetoothDevice) {
        classicRfcommClient?.connectToDevice(device)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendText(message: String) {
        bleGattClient?.sendMessage(message)
    }
}