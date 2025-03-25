package com.example.jvsglass.activities.teleprompter

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import com.example.jvsglass.R
import com.example.jvsglass.ble.BLEGattClient
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.ToastUtils
import kotlin.math.abs

class TeleprompterDisplayActivity : AppCompatActivity() {

    private val bleClient by lazy { BLEGattClient.getInstance(this) }
    private val vibrator by lazy { getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    private var totalLines = 0
    private var scrollLines = 0
    private var fileContent = ""
    private var isAtTopOrBottom = false

    private lateinit var scrollView: ScrollView
    private lateinit var tvContent: TextView

    @SuppressLint("MissingPermission")
    private val scrollDetector = VerticalScrollDetector { deltaY ->
        showScrollResult(deltaY)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState : Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teleprompter_display)

        initBluetoothConnection()
        initView()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun initBluetoothConnection() {
        // 检查是否有保存的设备地址，并尝试自动连接
        val prefs = getSharedPreferences("ble_prefs", MODE_PRIVATE)
        val lastDeviceAddress = prefs.getString("last_connected_device_address", null)
        if (lastDeviceAddress != null) {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter.getRemoteDevice(lastDeviceAddress)
            bleClient.connectToDevice(device) // 触发自动连接
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun initView() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<LinearLayout>(R.id.btnSettings).setOnClickListener {
            ToastUtils.show(this, "设置文本")
        }

        findViewById<LinearLayout>(R.id.btnStart).setOnClickListener {
            ToastUtils.show(this, "开始")
            sendMessage(fileContent)
        }

        findViewById<TextView>(R.id.tv_title).text = intent.getStringExtra("fileName") ?: ""
        findViewById<TextView>(R.id.tv_date).text = intent.getStringExtra("fileDate") ?: ""
        fileContent = intent.getStringExtra("fileContent") ?: ""

        tvContent = findViewById(R.id.tv_content)
        val splitResult = SmartTextScroller.splitIntoBlocks(fileContent, 0)
        tvContent.text = splitResult.displayBlock
        totalLines = splitResult.totalLines

        scrollView = findViewById(R.id.scrollView)
        scrollView.setOnTouchListener { _, event ->
            scrollDetector.handleTouchEvent(event)
            true
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun showScrollResult(deltaY: Float) {
        val scrollPerLine = tvContent.lineHeight.toFloat()  // 每行高度71.0px
        val deltaYAbs = abs(deltaY)
        val halfLine = scrollPerLine / 2

        // 计算实际滚动行数
        val exactLines = deltaYAbs / scrollPerLine
        val lines = when {
            deltaYAbs < halfLine -> 0   // 不足半行不计
            exactLines - exactLines.toInt() >= 0.5 -> exactLines.toInt() + 1  // 超过半行进位
            else -> exactLines.toInt()  // 整行处理
        }

//        val info = """
//            |════════ 滚动监测 ════════
//            |总位移: ${"%.1f".format(abs(deltaY))}px
//            |方向: ${if (deltaY > 0) "↓ 向下" else "↑ 向上"}
//            |滚动行数: $lines 行
//            |设备DPI: ${resources.displayMetrics.densityDpi}dpi
//            |═════════════════════════
//        """.trimMargin()
//        LogUtils.info(info)

        scrollLines = if (deltaY > 0) scrollLines-lines else scrollLines+lines
        scrollLines = scrollLines.coerceIn(0, totalLines - 1)
//        LogUtils.info("滚动行数：$scrollLines 行")
        val splitResult = SmartTextScroller.splitIntoBlocks(fileContent, scrollLines)
//        LogUtils.info("显示的内容：${splitResult.displayBlock}")
//        LogUtils.info("发送的内容：${splitResult.sendBlock}")

        if (scrollLines == 0 || scrollLines == totalLines - 1) {
            if (!isAtTopOrBottom) {
                isAtTopOrBottom = true
                handleAction(splitResult.displayBlock, splitResult.sendBlock)
            }
        } else {
            isAtTopOrBottom = false
            handleAction(splitResult.displayBlock, splitResult.sendBlock)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleAction(displayBlock: String, sendBlock: String) {
        val effect = VibrationEffect.createPredefined(
            VibrationEffect.EFFECT_CLICK // 短点击反馈
        )
        vibrator.vibrate(effect)

        tvContent.text = displayBlock
        LogUtils.info("发送的内容：${sendBlock}")
        sendMessage(sendBlock)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendMessage(fileContent: String) {
        if (fileContent.isEmpty()) return
        bleClient.sendMessage(fileContent)
    }
}