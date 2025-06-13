package com.example.jvsglass.ui.bt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED
import android.bluetooth.BluetoothDevice.ACTION_FOUND
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.bluetooth.BluetoothDevice.BOND_NONE
import android.bluetooth.BluetoothDevice.EXTRA_BOND_STATE
import android.bluetooth.BluetoothDevice.EXTRA_DEVICE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.example.jvsglass.bluetooth.BluetoothConnectManager
import com.example.jvsglass.bluetooth.BluetoothConstants.MAX_HISTORY_SIZE
import com.example.jvsglass.bluetooth.PacketCommandUtils
import com.example.jvsglass.bluetooth.PacketCommandUtils.CLOSE_MIC
import com.example.jvsglass.bluetooth.PacketCommandUtils.OPEN_MIC
import com.example.jvsglass.bluetooth.PacketCommandUtils.CMDKey
import com.example.jvsglass.bluetooth.PacketCommandUtils.createPacket
import com.example.jvsglass.bluetooth.PacketMessageUtils
import com.example.jvsglass.network.NetworkManager
import com.example.jvsglass.network.RealtimeAsrClient
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.ToastUtils
import com.example.jvsglass.utils.VoiceManager
import com.example.jvsglass.utils.toHexString
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BluetoothConnectActivity : AppCompatActivity() {
    private lateinit var btnSearch: Button
    private lateinit var tvStatus: TextView
    private lateinit var devicesTip: TextView
    private lateinit var lvDevices: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSendText: Button
    private lateinit var recyclerView: RecyclerView

    private var isConnected = false
    private var deviceName = ""
    private val devices = mutableListOf<BluetoothDevice>()
    private val deviceItems = mutableListOf<DeviceItem>()
    private lateinit var deviceListAdapter: DeviceAdapter
    private val btAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }

    private val messageHistory = mutableListOf<MessageItem>()
    private lateinit var messageAdapter: MessageAdapter
    private var connectedDeviceName: String? = null
    private lateinit var voiceManager: VoiceManager
    private lateinit var realtimeAsrClient: RealtimeAsrClient

    private var asrConnected: Boolean = false
    private val asrTimeoutHandler = Handler(Looper.getMainLooper())
    private val asrTimeoutRunnable = Runnable {
        if (asrConnected) {
            BluetoothConnectManager.onAudioStreamReceived = null
            realtimeAsrClient.disconnect()
            asrConnected = false
            runOnUiThread {
                LogUtils.info("[BluetoothConnectActivity] ASR已断开：超过5秒未收到音频")
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == ACTION_FOUND) {
                if (hasPermissions(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                ) {
                    val device = intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
                    device?.let {
                        runOnUiThread {
                            addDevice(it)
                            BluetoothConnectManager.onClassicDeviceFound?.invoke(it)
                        }
                    }
                }
            }
        }
    }

    private val bondReceiver = object: BroadcastReceiver() {
        @RequiresPermission(
            allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
        )
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == ACTION_BOND_STATE_CHANGED) {
                val dev = intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE) ?: return
                val state = intent.getIntExtra(EXTRA_BOND_STATE, BOND_NONE)
                LogUtils.info("[BluetoothConnectActivity] 设备: ${dev.address}, 配对状态: $state")
                if (state == BOND_BONDED) {
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                    BluetoothConnectManager.connectClassic(dev)
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
        setContentView(R.layout.activity_bluetooth_connect)

        voiceManager = VoiceManager(this)
        initRealtimeAsrClient()

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
                    BluetoothConnectManager.connectBle(it)
                    BluetoothConnectManager.connectClassic(it)
                    ToastUtils.show(this, "正在连接 ...")
                } else {
                    requestPermissionsIfNeeded()
                }
            }
        }
        lvDevices.adapter = deviceListAdapter
        lvDevices.layoutManager = LinearLayoutManager(this)

        btAdapter?.let {
            BluetoothConnectManager.initialize(this, voiceManager)
        } ?: run {
            ToastUtils.show(this, "设备不支持蓝牙")
            finish()
            return
        }
        BluetoothConnectManager.onBleDeviceFound = { device -> runOnUiThread { addDevice(device) } }
        BluetoothConnectManager.onClassicDeviceFound = { device -> runOnUiThread { addDevice(device) } }
        BluetoothConnectManager.onDeviceConnected = { device ->
            runOnUiThread {
                connectedDeviceName = device.name
                tvStatus.text = "已连接：${device.name}"
                devicesTip.visibility = View.GONE
                lvDevices.visibility = View.GONE
            }
        }
        BluetoothConnectManager.onMessageReceived = { msg ->
            LogUtils.info("[BluetoothConnectActivity] 接收到消息：${msg.toHexString()}")
            val result = PacketCommandUtils.parseValuePacket(msg)
            if (result != null) {
                val (operationCmd, isSuccess) = result
                if (isSuccess) {
                    LogUtils.info("操作成功，指令: 0x${operationCmd.toString(16)}")
                } else {
                    LogUtils.info("操作失败，指令: 0x${operationCmd.toString(16)}")
                }
            } else {
                LogUtils.info("解析数据包失败")
//                val message = PacketMessageUtils.processPacket(msg)
//                addMessageToHistory("[收到] $message")
            }
        }
        BluetoothConnectManager.onVoiceReceived = { data ->
            val timestamp = System.currentTimeMillis()
            val audioFile = File(cacheDir, "audio_record_${timestamp}.3gp")
            try {
                audioFile.writeBytes(data)
                addMessageToHistory("[收到] 语音 ${data.size} 字节", audioFile.absolutePath)
                LogUtils.info("[BluetoothConnectActivity] 语音路径：${audioFile.absolutePath}")
            } catch (e: IOException) {
                LogUtils.error("[BluetoothConnectActivity] 保存接收音频失败: ${e.message}")
                runOnUiThread { ToastUtils.show(this, "保存接收音频失败: ${e.message}") }
            }
        }
        BluetoothConnectManager.onAudioStreamReceived = { data ->
            LogUtils.debug("[BluetoothConnectActivity] 接收到音频流数据，长度：${data.size} 字节")
            if (!asrConnected) {
                realtimeAsrClient.connect()
                asrConnected = true
                LogUtils.info("[BluetoothConnectActivity] ASR连接已建立，开始发送音频数据")
            }
            // 发送当前音频块
            realtimeAsrClient.sendAudioChunk(data)

            asrTimeoutHandler.removeCallbacks(asrTimeoutRunnable)
            asrTimeoutHandler.postDelayed(asrTimeoutRunnable, 5_000L)
        }

        // 注册 Classic 发现广播
        registerReceiver(receiver, IntentFilter(ACTION_FOUND))

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            onBackPressed()
        }

        btnSearch.setOnClickListener {
            requestPermissionsIfNeeded()
            deviceItems.clear()
            deviceListAdapter.submitList(deviceItems.toList())
            BluetoothConnectManager.startAsClient()
            registerReceiver(bondReceiver, IntentFilter(ACTION_BOND_STATE_CHANGED))
            btnSearch.isEnabled = false
        }

        btnSendText.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text == "打开") {
                BluetoothConnectManager.sendCommand(createPacket(CMDKey.MIC_COMMAND, OPEN_MIC))
            }

            if (text == "关闭") {
                BluetoothConnectManager.sendCommand(createPacket(CMDKey.MIC_COMMAND, CLOSE_MIC))
            }

            if (text.isNotEmpty()) {
                if (hasPermissions(Manifest.permission.BLUETOOTH_CONNECT)) {
                    BluetoothConnectManager.sendMessage(text)
                    addMessageToHistory("[发送] $text")
                } else {
                    requestPermissionsIfNeeded()
                }
            }
        }

        setupRecyclerView()

        isConnected = intent.getBooleanExtra("isConnected", false)
        deviceName = intent.getStringExtra("deviceName").toString()
        if (isConnected) {
            devicesTip.visibility = View.GONE
            lvDevices.visibility = View.GONE
            tvStatus.text = "已连接：$deviceName"
        } else {
            devicesTip.visibility = View.VISIBLE
            lvDevices.visibility = View.VISIBLE
            tvStatus.text = "待连接"
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.messageRecyclerView)
        messageAdapter = MessageAdapter(messageHistory) { filePath ->
            voiceManager.playVoiceMessage(filePath)
        }
        recyclerView.adapter = messageAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun initRealtimeAsrClient() {
        realtimeAsrClient = NetworkManager.getInstance()
            .createRealtimeAsrClient(object : RealtimeAsrClient.RealtimeAsrCallback {
                override fun onPartialResult(text: String) {
                    runOnUiThread { addMessageToHistory("[ASR识别] $text") }
                }

                override fun onFinalResult(text: String) {
                    runOnUiThread {
                        LogUtils.info("[BluetoothConnectActivity] ASR结果：$text")
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread { LogUtils.error(error) }
                }

                override fun onConnectionChanged(connected: Boolean) {
                    LogUtils.info("ASR连接状态: $connected")
                    if (!connected) {
                        asrConnected = false
                        LogUtils.info("ASR 已断开")
                    }
                }

                override fun onSessionReady() {
                    LogUtils.info("ASR session ready")
                }
            })
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
            LogUtils.info("[BluetoothConnectActivity] 所有权限已授予")
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun addMessageToHistory(message: String, voiceFilePath: String? = null) {
        val timestamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
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
            setResult(RESULT_CANCELED)
        }
        super.onBackPressed()
    }
}