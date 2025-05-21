package com.example.jvsglass.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jvsglass.R
import com.example.jvsglass.bluetooth.ble.BLEConstants.REQUEST_ENABLE_BT
import com.example.jvsglass.bluetooth.ble.BLEConstants.REQUEST_CODE_BLE_PERMISSIONS
import com.example.jvsglass.bluetooth.ble.BLEConstants.SCAN_TIMEOUT
import com.example.jvsglass.bluetooth.ble.BLEGattClient
import com.example.jvsglass.bluetooth.ble.HeartbeatDetectorManager
import com.example.jvsglass.databinding.ActivityBluetoothConnectBinding
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.ToastUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BluetoothConnectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBluetoothConnectBinding
    private val context = this
    private lateinit var bleClient: BLEGattClient

    private var isScanning = false
    private val scannedDevices = mutableMapOf<String, BluetoothDevice>()
    private lateinit var deviceListAdapter: DeviceAdapter    // 设备列表适配器
    private val filteredDevicesList = mutableListOf<BluetoothDevice>()
    private val scanHandler = Handler(Looper.getMainLooper())

    private val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .build()

    private val filters: List<ScanFilter>? = null

    private val leScanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.device?.let { device ->
                val addName = result.scanRecord?.deviceName
                val address = device.address

                // 去重逻辑：使用设备地址作为唯一标识
                if (!scannedDevices.containsKey(device.address)) {
                    scannedDevices[device.address] = device

                    if (device.name?.isNotBlank() == true) {
                        runOnUiThread {
                            // 过滤逻辑：仅显示有名称且不是未知设备的
                            filteredDevicesList.add(device)
                            LogUtils.debug("发现设备：系统名称：${device.name ?: "null"} 广告名称：$addName 地址：$address")

                            val deviceItems = filteredDevicesList.map {
                                DeviceItem(
//                                    deviceName = "${it.name} (${it.address})"
                                    deviceName = it.name,
                                    deviceType = it.type
                                )
                            }
                            deviceListAdapter.submitList(deviceItems)
                            binding.lvDevices.visibility = View.VISIBLE
                        }
                    }
                    LogUtils.debug("[BLE Scan] 发现新设备：${device.address}")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false // 扫描状态重置
            val errorMsg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "扫描已在进行中"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "应用注册失败"
                SCAN_FAILED_INTERNAL_ERROR -> "内部错误"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "不支持该功能"
                else -> "未知错误"
            }
            LogUtils.error("[BLE Scan] $errorMsg (代码:$errorCode)")
            runOnUiThread { ToastUtils.show(context, "扫描失败：$errorMsg") }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothConnectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeBleComponents()
        initUI()
        checkPermissionsAndSetup()
    }

    // 初始化BLE组件
    @SuppressLint("MissingPermission")
    private fun initializeBleComponents() {
        bleClient = BLEGattClient.getInstance(this)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun initUI() {
        initDeviceList()
        initButtons()
    }

    // 初始化设备列表
    @SuppressLint("SetTextI18n")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun initDeviceList() {
        deviceListAdapter = DeviceAdapter()
        binding.lvDevices.apply {
            layoutManager = LinearLayoutManager(this@BluetoothConnectActivity)
            adapter = deviceListAdapter
            setHasFixedSize(true)
        }

        deviceListAdapter.setOnItemClickListener { position ->
            if (position < filteredDevicesList.size) {
                val selectedDevice = filteredDevicesList[position]
                LogUtils.info("[UI] 用户选择设备：${selectedDevice.name ?: "Unnamed"} (${selectedDevice.address})")
                LogUtils.info("[UI] 设备类型：${selectedDevice.type}，绑定状态：${selectedDevice.bondState}")
                ToastUtils.show(context, "已选择设备：${selectedDevice.name}")
                bleClient.connectToDevice(selectedDevice)
                binding.tvDevices.text = "已连接：" + selectedDevice.name
                binding.lvDevices.visibility = View.GONE
            } else {
                LogUtils.error("无效的列表位置: $position")
            }
        }
    }

    private fun DeviceAdapter.setOnItemClickListener(onItemClick: (Int) -> Unit) {
        this.onItemClick = { position ->
            onItemClick(position)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun checkPermissionsAndSetup() {
        if (!hasBluetoothPermissions()) {
            LogUtils.info("[UI] 缺少必要权限")
            requestBluetoothPermissions()
        } else {
            initButtons()
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ),
            REQUEST_CODE_BLE_PERMISSIONS
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun initButtons() {
//        binding.btnClient.visibility = if (bleClient.isConnected()) View.GONE else View.VISIBLE
        binding.btnClient.setOnClickListener {
            if (hasBluetoothPermissions()) {
                startClientMode()
            } else {
                requestBluetoothPermissions()
            }
            binding.tvDevices.visibility = View.VISIBLE
        }

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun startDeviceScan() {
        LogUtils.info("[BLE Scan] 扫描回调初始化状态：已就绪")

        // 状态检查
        if (isScanning) {
            LogUtils.warn("[BLE Scan] 扫描已在进行中")
            return
        }

        // 检查权限
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            LogUtils.error("[BLE Scan] 设备不支持蓝牙")
            ToastUtils.show(context, "设备不支持蓝牙")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            LogUtils.error("[BLE Scan] 蓝牙未启用")
            ToastUtils.show(context, "请启用蓝牙")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            return
        }

        // 动态权限检查
        val requiredPermissions = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        if (requiredPermissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
            LogUtils.warn("[BLE Scan] 缺少必要权限")
            ActivityCompat.requestPermissions(
                this,
                requiredPermissions.toTypedArray(),
                REQUEST_CODE_BLE_PERMISSIONS
            )
            return
        }

        LogUtils.info("[BLE Scan] 开始扫描BLE设备...")
        val leScanner = bluetoothAdapter.bluetoothLeScanner

        runOnUiThread {
            scannedDevices.clear()
            filteredDevicesList.clear()
            deviceListAdapter.submitList(emptyList())
            binding.lvDevices.visibility = View.VISIBLE
        }

        try {
            // 先停止可能存在的扫描
            stopScan()

            // 启动扫描
            leScanner.startScan(filters, settings, leScanCallback)
            isScanning = true // 更新状态

            // 超时停止
            scanHandler.postDelayed({
                LogUtils.info("[BLE Scan] 扫描超时自动停止")
                stopScan()
            }, SCAN_TIMEOUT)

        } catch (e: SecurityException) {
            isScanning = false
            LogUtils.error("[BLE Scan] 权限异常", e)
            ToastUtils.show(context, "缺少蓝牙扫描权限")
        } catch (e: Exception) {
            isScanning = false
            LogUtils.error("[BLE Scan] 扫描异常", e)
            ToastUtils.show(context, "扫描启动失败")
        }
    }

    // 停止扫描方法
    private fun stopScan() {
        if (!isScanning) return // 状态检查

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val leScanner = bluetoothAdapter?.bluetoothLeScanner

        runOnUiThread {
            if (scannedDevices.isEmpty()) {
                binding.lvDevices.visibility = View.GONE
            }
        }

        try {
            // 权限检查
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED) {
                LogUtils.warn("[BLE Scan] 停止扫描失败：缺少权限")
                return
            }

            // 执行停止
            leScanner?.stopScan(leScanCallback)
            scanHandler.removeCallbacksAndMessages(null)
            LogUtils.info("[BLE Scan] 已停止扫描")

        } catch (e: SecurityException) {
            LogUtils.error("[BLE Scan] 停止扫描权限异常", e)
            ToastUtils.show(context, "缺少扫描权限")
        } catch (e: Exception) {
            LogUtils.error("[BLE Scan] 停止扫描异常", e)
        } finally {
            isScanning = false // 确保状态重置
        }
    }

    // 更新界面显示
    private fun updateUI() {
        binding.tvDevices.visibility = View.VISIBLE
    }

    // 添加权限检查
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_BLE_PERMISSIONS -> handlePermissionResult(permissions, grantResults)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handlePermissionResult(permissions: Array<String>, grantResults: IntArray) {
        val deniedPermissions = permissions.zip(grantResults.toList())
            .filter { (_, result) -> result != PackageManager.PERMISSION_GRANTED } // 显式解构Pair
            .map { (permission, _) -> permission } // 获取被拒绝的权限名

        if (deniedPermissions.isNotEmpty()) {
            LogUtils.info("[UI] 未被授权的权限: ${deniedPermissions.joinToString()}")
        }

        when {
            deniedPermissions.isEmpty() -> {
                ToastUtils.show(context, "权限已授予")
            }
            else -> {
                ToastUtils.show(context, "需要蓝牙权限以扫描和连接设备")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onResume() {
        super.onResume()
        if (hasBluetoothPermissions()) {
            LogUtils.info("恢复客户端模式")
        } else {
            // 用户仍未授权，完全重置状态
            ToastUtils.show(context, "权限未授予，无法恢复模式")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startClientMode() {
        startDeviceScan()
        setupClientCallbacks()
        updateUI()
    }

    private fun appendFormattedMessage(content: String, isSent: Boolean) {
        runOnUiThread {
            val direction = if (isSent) "发送" else "接收"
            val messageDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val formatted = "${messageDateFormat.format(Date())} [$direction] $content\n"
            LogUtils.info("[BLE] Message: $formatted")
        }
    }

    /* 回调设置 */
    private fun setupClientCallbacks() {
        bleClient.messageListener = object : BLEGattClient.MessageListener {
            override fun onMessageReceived(message: String) {
                appendFormattedMessage(message, false)
            }

            override fun onMessageSent(message: String) {
                appendFormattedMessage(message, true) // 客户端发送成功
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onConnectionEvent(event: HeartbeatDetectorManager.ConnectionEvent) {
        LogUtils.info("[BluetoothConnectActivity] Event received: ${event.isConnected}")
        val btnClient = findViewById<TextView>(R.id.btn_client)
        if (event.isConnected) {
            btnClient.visibility = View.GONE
        } else {
            btnClient.visibility = View.VISIBLE
        }
    }
}