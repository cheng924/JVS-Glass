package com.example.jvsglass.activities.teleprompter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.lifecycleScope
import com.example.jvsglass.R
import com.example.jvsglass.ble.BLEGattClient
import com.example.jvsglass.utils.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class TeleprompterDisplayActivity : AppCompatActivity() {

    private val bleClient by lazy { BLEGattClient.getInstance(this) }
    private val vibrator by lazy { getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    private var totalLines = 0
    private var scrollLines = 0
    private var fileContent = ""
    private var isAtTopOrBottom = false
    private lateinit var gestureDetector: GestureDetectorCompat
    private var autoScrollJob: Job? = null
    private var scrollIntervalMs = 15_000L

    private lateinit var scrollView: ScrollView
    private lateinit var tvContent: TextView

    @SuppressLint("ClickableViewAccessibility")
    private val manualTouchListener = View.OnTouchListener { _, event ->
        gestureDetector.onTouchEvent(event)
        scrollDetector.handleTouchEvent(event)
        false
    }

    @SuppressLint("ClickableViewAccessibility")
    private val disabledTouchListener = View.OnTouchListener { _, _ ->
        true
    }

    private val editFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 新建/编辑保存成功，自己finish掉，回到上一个界面
            finish()
        }
    }

    private val settingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val newMs = result.data?.getLongExtra("scrollIntervalMs", scrollIntervalMs)
            if (newMs != null) scrollIntervalMs = newMs
        }
    }

    @SuppressLint("MissingPermission")
    private val scrollDetector = VerticalScrollDetector { deltaY ->
        showScrollResult(deltaY)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState : Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teleprompter_display)

        initSetting()
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

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @SuppressLint("ClickableViewAccessibility")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun initView() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<LinearLayout>(R.id.ll_voice_control).setOnClickListener {
            ToastUtils.show(this, "正在开发")
        }

        findViewById<TextView>(R.id.tv_title).text = intent.getStringExtra("fileName") ?: ""
        findViewById<TextView>(R.id.tv_date).text = intent.getStringExtra("fileDate") ?: ""
        fileContent = intent.getStringExtra("fileContent") ?: ""

        tvContent = findViewById(R.id.tv_content)
        val splitResult = SmartTextScroller.splitIntoBlocks(fileContent, 0)
        tvContent.text = splitResult.displayBlock
        totalLines = splitResult.totalLines

        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                super.onLongPress(e)
                val intent = Intent(this@TeleprompterDisplayActivity, TeleprompterNewFileActivity::class.java).apply {
                    putExtra("fileName", findViewById<TextView>(R.id.tv_title).text.toString())
                    putExtra("fileDate", findViewById<TextView>(R.id.tv_date).text.toString())
                    putExtra("fileContent", fileContent)
                }
                editFileLauncher.launch(intent)
            }
        })

        scrollView = findViewById(R.id.scrollView)
        scrollView.setOnTouchListener(manualTouchListener)

        findViewById<ImageView>(R.id.teleprompter_settings).setOnClickListener {
            if (autoScrollJob?.isActive == true) {
                ToastUtils.show(this, "请先“停止滚动”")
                return@setOnClickListener
            }
            val intent = Intent(this, TeleprompterSettingActivity::class.java)
                .putExtra("scrollIntervalMs", scrollIntervalMs)
            settingLauncher.launch(intent)
        }

        findViewById<LinearLayout>(R.id.ll_continue_scroll).setOnClickListener {
            if (autoScrollJob?.isActive == true) {
                autoScrollJob?.cancel()
                scrollView.setOnTouchListener(manualTouchListener)
                ToastUtils.show(this, "已停止自动滚动")
                findViewById<TextView>(R.id.tv_scroll_status)?.text = "匀速滚动"
                findViewById<ImageView>(R.id.iv_scroll_status).setImageResource(R.drawable.ic_continue)
            } else {
                if (scrollLines == totalLines - 1) {
                    scrollLines = 0
                    // 重置文本块
                    val split0 = SmartTextScroller.splitIntoBlocks(fileContent, 0)
                    tvContent.text = split0.displayBlock
                    scrollView.scrollTo(0, 0)
                    sendMessage(split0.sendBlock)
                }
                scrollView.setOnTouchListener(disabledTouchListener)
                startAutoScroll(scrollIntervalMs)
                ToastUtils.show(this, "开始自动滚动：${scrollIntervalMs/1000}秒/行")
                findViewById<TextView>(R.id.tv_scroll_status)?.text = "停止滚动"
                findViewById<ImageView>(R.id.iv_scroll_status).setImageResource(R.drawable.ic_suspend)
            }
        }
    }

    private fun initSetting() {
        val prefs = getSharedPreferences("teleprompter_setting", Context.MODE_PRIVATE)
        scrollIntervalMs = prefs.getLong("speed", 15_000L)
    }

    @SuppressLint("ClickableViewAccessibility")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startAutoScroll(intervalMs: Long) {
        // 先停掉任何已有的自动滚动
        autoScrollJob?.cancel()
        autoScrollJob = lifecycleScope.launch {
            // 从当前的scrollLines继续
            while (isActive) {
                delay(intervalMs)
                // 计算下一行的索引，最多到最后一行
                scrollLines = (scrollLines + 1).coerceAtMost(totalLines - 1)
                val split = SmartTextScroller.splitIntoBlocks(fileContent, scrollLines)
                tvContent.text = split.displayBlock
                scrollView.scrollTo(0, 0)   // 每次重置ScrollView到顶部，确保从第0像素开始

                sendMessage(split.sendBlock)

                if (scrollLines == totalLines - 1) {
                    withContext(Dispatchers.Main) {
                        scrollView.setOnTouchListener(manualTouchListener)
                        ToastUtils.show(this@TeleprompterDisplayActivity, "已滚动到末尾")
                        findViewById<TextView>(R.id.tv_scroll_status)?.text = "重新开始"
                        findViewById<ImageView>(R.id.iv_scroll_status).setImageResource(R.drawable.ic_continue)
                    }
                    break
                }
            }
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
//        sendMessage(sendBlock)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendMessage(fileContent: String) {
        if (fileContent.isEmpty()) return
        bleClient.sendMessage(fileContent)
    }
}