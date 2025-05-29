package com.example.jvsglass.bluetooth.dual

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.bluetooth.BluetoothConstants.MAX_HISTORY_SIZE
import com.example.jvsglass.bluetooth.DeviceAdapter
import com.example.jvsglass.bluetooth.DeviceItem
import com.example.jvsglass.bluetooth.MessageAdapter
import com.example.jvsglass.bluetooth.MessageItem
import com.example.jvsglass.bluetooth.PacketMessageUtils
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.ToastUtils
import com.example.jvsglass.utils.VoiceManager
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DualBluetoothActivity : AppCompatActivity() {
    private lateinit var btnSearch: Button
    private lateinit var tvStatus: TextView
    private lateinit var devicesTip: TextView
    private lateinit var lvDevices: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSendText: Button
    private lateinit var recyclerView: RecyclerView

    private val devices = mutableListOf<BluetoothDevice>()
    private val deviceItems = mutableListOf<DeviceItem>()
    private lateinit var deviceListAdapter: DeviceAdapter
    private val btAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }

    private val messageHistory = mutableListOf<MessageItem>()
    private lateinit var messageAdapter: MessageAdapter
    private var connectedDeviceName: String? = null
    private lateinit var voiceManager: VoiceManager

    private val receiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_FOUND) {
                if (hasPermissions(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                ) {
                    val d = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    d?.let {
                        runOnUiThread {
                            addDevice(it)
                            DualBluetoothManager.onClassicDeviceFound?.invoke(it)
                        }
                    }
                }
            }
        }
    }

    private val bondReceiver = object: BroadcastReceiver() {
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                LogUtils.info("[BondReceiver] 设备: ${dev.address}, 配对状态: $state")
                if (state == BluetoothDevice.BOND_BONDED) {
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                    DualBluetoothManager.connectClassic(dev)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    @RequiresPermission(
        allOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ]
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dual_bluetooth)

        voiceManager = VoiceManager(this)

        btnSearch = findViewById(R.id.btnSearch)
        tvStatus = findViewById(R.id.tvStatus)
        devicesTip = findViewById(R.id.devicesTip)
        lvDevices = findViewById(R.id.lvDevices)
        etMessage = findViewById(R.id.etMessage)
        btnSendText = findViewById(R.id.btnSendText)

        deviceListAdapter = DeviceAdapter()
        deviceListAdapter.onItemClick = { position ->
            val deviceItem = deviceItems[position]
            val device = devices.find { it.name == deviceItem.deviceName }
            device?.let {
                if (hasPermissions(Manifest.permission.BLUETOOTH_CONNECT)) {
                    DualBluetoothManager.connectBle(it)
                    DualBluetoothManager.connectClassic(it)
                    ToastUtils.show(this, "正在连接 BLE 和 Classic ...")
                } else {
                    requestPermissionsIfNeeded()
                }
            }
        }
        lvDevices.adapter = deviceListAdapter
        lvDevices.layoutManager = LinearLayoutManager(this)

        // 初始化 DualBluetoothManager
        btAdapter?.let {
            DualBluetoothManager.initialize(this, it, voiceManager)
        } ?: run {
            ToastUtils.show(this, "设备不支持蓝牙")
            finish()
            return
        }
        DualBluetoothManager.onBleDeviceFound = { device -> runOnUiThread { addDevice(device) } }
        DualBluetoothManager.onClassicDeviceFound = { device -> runOnUiThread { addDevice(device) } }
        DualBluetoothManager.onDeviceConnected = { device ->
            runOnUiThread {
                connectedDeviceName = device.name
                tvStatus.text = "已连接：服务端 ${device.name}"
                LogUtils.info("[DualBluetoothActivity] 已连接：服务端 ${device.name}")
                devicesTip.visibility = View.GONE
                lvDevices.visibility = View.GONE
            }
        }
        DualBluetoothManager.onMessageReceived = { msg ->
            val message = PacketMessageUtils.processPacket(msg)
            addMessageToHistory("[收到] $message")
        }
        DualBluetoothManager.onVoiceReceived = { data ->
            val timestamp = System.currentTimeMillis()
            val audioFile = File(cacheDir, "audio_record_${timestamp}.3gp")
            try {
                audioFile.writeBytes(data)
                addMessageToHistory("[收到] 语音 ${data.size} 字节", audioFile.absolutePath)
                LogUtils.info("[DualBluetoothActivity] 语音路径：${audioFile.absolutePath}")
            } catch (e: IOException) {
                LogUtils.error("[DualBluetoothActivity] 保存接收音频失败: ${e.message}")
                runOnUiThread { ToastUtils.show(this, "保存接收音频失败: ${e.message}") }
            }
        }

        // 注册 Classic 发现广播
        registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            onBackPressed()
        }

        btnSearch.setOnClickListener {
            requestPermissionsIfNeeded()
            deviceItems.clear()
            deviceListAdapter.submitList(deviceItems.toList())
            // 启动 BLE+Classic 扫描，并注册 bondReceiver
            DualBluetoothManager.startAsClient()
            registerReceiver(bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
            btnSearch.isEnabled = false
        }

        btnSendText.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                if (hasPermissions(Manifest.permission.BLUETOOTH_CONNECT)) {
                    DualBluetoothManager.sendText(text)
                    addMessageToHistory("[发送] $text")
                } else {
                    requestPermissionsIfNeeded()
                }
            }
        }

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.messageRecyclerView)
        messageAdapter = MessageAdapter(messageHistory) { filePath -> voiceManager.playVoiceMessage(filePath) }
        recyclerView.adapter = messageAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun addDevice(device: BluetoothDevice) {
        if (!device.name.isNullOrBlank() && !deviceItems.any { it.deviceName == device.name }) {
            devices.add(device)
            val deviceItem = DeviceItem(device.name, device.type)
            deviceItems.add(deviceItem)
            deviceListAdapter.submitList(deviceItems.toList())
        }
    }

    private fun hasPermissions(vararg perms: String): Boolean {
        return perms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        ).forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                needed.add(it)
            }
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 0)
        } else {
            LogUtils.info("[DualBluetoothActivity] 所有权限已授予")
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun addMessageToHistory(message: String, voiceFilePath: String? = null) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val messageWithTime = "[$timestamp] $message"
        if (messageHistory.size >= MAX_HISTORY_SIZE) messageHistory.removeAt(0)
        messageHistory.add(MessageItem(messageWithTime, voiceFilePath))
        runOnUiThread {
            messageAdapter.notifyDataSetChanged()
            recyclerView.scrollToPosition(messageHistory.size - 1)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (connectedDeviceName != null) {
            setResult(RESULT_OK, Intent().apply {
                putExtra("CONNECTED_DEVICE", connectedDeviceName)
            })
        } else {
            setResult(RESULT_CANCELED)  // 用户没连上，则告知取消/失败
        }
        super.onBackPressed()
    }
}