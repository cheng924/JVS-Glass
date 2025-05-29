package com.example.jvsglass.bluetooth.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.annotation.RequiresPermission
import com.example.jvsglass.bluetooth.BluetoothConstants
import com.example.jvsglass.utils.LogUtils

object BleGattModule {
    private lateinit var appContext: Context
    private val bluetoothManager by lazy { appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter get() = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? get() = bluetoothAdapter.bluetoothLeScanner
    private var client: BLEGattClient? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        client = BLEGattClient.getInstance(appContext)
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION]
    )
    fun startScan(onFound: (BluetoothDevice) -> Unit) {
        val scanner = bleScanner ?: run {
            LogUtils.error("[BleModule] 不支持 BLE 扫描")
            return
        }
        val callback = object : ScanCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val uuids = result.scanRecord?.serviceUuids
                if (uuids?.any { it.uuid == BluetoothConstants.SERVICE_UUID } == true) {
                    scanner.stopScan(this)
                    LogUtils.info("[BleModule] 停止扫描，准备连接 BLE 设备 ${result.device.address}")
                    onFound(result.device)
                }
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { it.device?.let(onFound) }
            }
            override fun onScanFailed(errorCode: Int) {
                LogUtils.error("[BleModule] BLE 扫描失败: $errorCode")
            }
        }
        scanner.startScan(callback)
        scanCallback = callback
        LogUtils.info("[BleModule] 启动 BLE 扫描")
    }

    private var scanCallback: ScanCallback? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        scanCallback?.let {
            bleScanner?.stopScan(it)
            LogUtils.info("[BleModule] 停止 BLE 扫描")
        }
        scanCallback = null
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectAsClient(device: BluetoothDevice, listener: BLEGattClient.MessageListener) {
        client?.apply {
            messageListener = listener
            connectToDevice(device)
        }
    }

    /**
     * 发送文本消息（BLE）
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendClientMessage(message: String) {
        client?.sendMessage(message)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectClient() {
        client?.disconnect()
    }
}