package com.example.jvsglass.activities.jvsai

import android.annotation.SuppressLint
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewSwitcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.SystemFileOpener
import com.example.jvsglass.utils.ToastUtils
import com.example.jvsglass.utils.VoiceManager
import java.io.File

class JVSAIActivity : AppCompatActivity(), SystemFileOpener.FileResultCallback {

    private lateinit var voiceManager: VoiceManager
    private lateinit var fileOpener: SystemFileOpener

    private var currentAudioPath: String? = null // 记录当前录音文件路径
    private var recordingStartTime: Long = 0L  // 录音开始时间戳（毫秒）
    private var recordingDuration: Int = 0     // 录音时长（秒）

    private val messageList = mutableListOf<AiMessage>()
    private val cardItems = mutableListOf<CardItem>()
    private lateinit var messageAdapter: AiMessageAdapter
    private lateinit var cardAdapter: CardAdapter
    private var isVoiceInput = false
    private var startY = 0f
    private var isCanceled = false

    private lateinit var rvMessages: RecyclerView
    private lateinit var flCardViewContainer: FrameLayout
    private lateinit var rvCardView: RecyclerView
    private lateinit var llTextInput: LinearLayout
    private lateinit var ivInput: ImageView
    private lateinit var inputSwitcher: ViewSwitcher
    private lateinit var etMessage: EditText
    private lateinit var tvVoiceChoose: TextView
    private lateinit var ivAdd: ImageView
    private lateinit var ivSend: ImageView

    private lateinit var llVoiceInput: LinearLayout
    private lateinit var tvVoiceInputTip: TextView
    private lateinit var tvVoiceInput: TextView

    private lateinit var mediaButtons: LinearLayout
    private lateinit var icGallery: ImageView
    private lateinit var ivCamera: ImageView
    private lateinit var ivFile: ImageView
    private lateinit var ivCall: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jvsai)

        voiceManager = VoiceManager(this)
        fileOpener = SystemFileOpener(this)
        fileOpener.registerLaunchers(this, this)

        setupUI()
        setupCardView()
        setupRecyclerView()
        setupClickListeners()
    }

    private fun setupUI() {
        rvMessages = findViewById(R.id.rvMessages)
        flCardViewContainer = findViewById(R.id.flCardViewContainer)
        rvCardView = findViewById(R.id.rvCardView)
        llTextInput = findViewById(R.id.llTextInput)
        ivInput = findViewById(R.id.ivInput)
        inputSwitcher = findViewById(R.id.inputSwitcher)
        etMessage = findViewById(R.id.etMessage)
        tvVoiceChoose = findViewById(R.id.tvVoiceChoose)
        ivAdd = findViewById(R.id.ivAdd)
        ivSend = findViewById(R.id.ivSend)

        llVoiceInput = findViewById(R.id.llVoiceInput)
        tvVoiceInputTip = findViewById(R.id.tvVoiceInputTip)
        tvVoiceInput = findViewById(R.id.tvVoiceInput)

        mediaButtons = findViewById(R.id.mediaButtons)
        icGallery = findViewById(R.id.icGallery)
        ivCamera = findViewById(R.id.ivCamera)
        ivFile = findViewById(R.id.ivFile)
        ivCall = findViewById(R.id.ivCall)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<ImageView>(R.id.ivAiHistory).setOnClickListener {
            ToastUtils.show(this@JVSAIActivity, "历史记录")
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupCardView() {
        cardAdapter = CardAdapter(
            object : CardAdapter.OnCardAdapterListener {
                override fun onAddCardClicked(position: Int) {
//                    addNewCard()
                    fileOpener.openCamera()
                }

                override fun onDeleteCard(position: Int) {
                    deleteOldCard(position)
                }
            }).apply {
                submitList(cardItems.toList())
        }

        rvCardView.apply {
            layoutManager = LinearLayoutManager(
                this@JVSAIActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = cardAdapter // 使用已赋值的属性
            setHasFixedSize(true)
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = AiMessageAdapter(messageList)
        rvMessages.apply {
            layoutManager = LinearLayoutManager(this@JVSAIActivity)
            adapter = messageAdapter
        }

        messageAdapter.apply {
            onVoiceItemClickListener = object : AiMessageAdapter.OnVoiceItemClickListener {
                override fun onVoiceItemClick(filePath: String, position: Int) {
                    if (voiceManager.isPlaying(filePath)) {
                        voiceManager.stopPlayback()
                    } else {
                        voiceManager.stopPlayback()
                        voiceManager.playVoiceMessage(filePath)
                    }
                    messageAdapter.notifyItemChanged(position)
                }
            }

            isPlayingCheck = { filePath ->
                voiceManager.isPlaying(filePath)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        ivInput.setOnClickListener {
            toggleInputMode(inputSwitcher, ivInput)
        }

        tvVoiceChoose.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    isCanceled = false
                    startVoiceRecording()
                    llVoiceInput.visibility = View.VISIBLE
                    llTextInput.visibility = View.GONE
                    tvVoiceInputTip.text = "松手发送，上移取消"
                    tvVoiceInput.apply {
                        setTextColor(ContextCompat.getColor(context, R.color.white))
                        setBackgroundResource(R.drawable.bg_voice_recording)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = startY - event.rawY
                    if (deltaY > 100.dpToPx()) { // 滑动超过100dp视为取消
                        if (!isCanceled) {
                            isCanceled = true
                            llVoiceInput.visibility = View.VISIBLE
                            llTextInput.visibility = View.GONE
                            tvVoiceInputTip.text = "松手取消"
                            tvVoiceInput.apply {
                                setTextColor(ContextCompat.getColor(context, R.color.white))
                                setBackgroundResource(R.drawable.bg_voice_cancel)
                                startAnimation(AnimationUtils.loadAnimation(context, R.anim.shake))
                            }
                        }
                    } else {
                        isCanceled = false
                        llVoiceInput.visibility = View.VISIBLE
                        llTextInput.visibility = View.GONE
                        tvVoiceInputTip.text = "松手发送，上移取消"
                        tvVoiceInput.apply {
                            setTextColor(ContextCompat.getColor(context, R.color.white))
                            setBackgroundResource(R.drawable.bg_voice_recording)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    tvVoiceChoose.apply {
                        text = "按住说话"
                        llVoiceInput.visibility = View.GONE
                        llTextInput.visibility = View.VISIBLE
                        setTextColor(ContextCompat.getColor(context, R.color.black))
                        setBackgroundResource(R.drawable.voice_input_bg)
                        clearAnimation()
                    }
                    if (isCanceled) {
                        LogUtils.info("取消录音")
                        voiceManager.stopRecording()
                        currentAudioPath?.let { path ->
                            File(path).delete() // 删除临时文件
                            LogUtils.info("已删除录音文件: $path")
                        }
                        currentAudioPath = null
                        recordingDuration = 0
                    } else {
                        stopVoiceRecording()
                    }
                    true
                }
                else -> false
            }
        }

        ivAdd.setOnClickListener {
            toggleMediaButtons()
        }

        ivSend.setOnClickListener {
            sendMessage()
        }

        icGallery.setOnClickListener {
            addMessage("[相册]", true)
            hideMediaButtons()
        }

        ivCamera.setOnClickListener {
            fileOpener.openCamera()
            hideMediaButtons()
        }

        ivFile.setOnClickListener {
            fileOpener.openFilePicker()
            hideMediaButtons()
        }

        ivCall.setOnClickListener {
            addMessage("[打电话]", true)
            hideMediaButtons()
        }

        voiceManager.onPlaybackCompleteListener = object : VoiceManager.OnPlaybackCompleteListener {
            override fun onPlaybackComplete(filePath: String) {
                val position = messageList.indexOfFirst {
                    it.type == AiMessage.TYPE_VOICE && it.path == filePath
                }
                if (position != -1) messageAdapter.notifyItemChanged(position)
            }
        }
    }

    private fun sendMessage() {
        val pendingCards = cardItems.filter { it.tag == "IMAGE" }.toList()
        pendingCards.forEach { card ->
            addMessage("[图片]", true, AiMessage.TYPE_IMAGE, path = card.imageUri)
        }
        cardItems.clear()
        cardAdapter.submitList(emptyList())

        flCardViewContainer.visibility = View.GONE
        cardAdapter.submitList(cardItems.toList())

        val message = etMessage.text.toString().trim()
        if (message.isNotEmpty()) {
            addMessage(message, true)
            etMessage.text.clear()
        }

        // 模拟回复
        Handler(Looper.getMainLooper()).postDelayed({
            addMessage("已收到${message.ifEmpty { "图片" }}", false)
        }, 1000)
    }

    private fun addNewCard() {
        if (!::cardAdapter.isInitialized) {
            LogUtils.error("CardAdapter not initialized!")
            return
        }

        if (cardItems.size < 9) {
            // 更新旧最后一张卡片的 isGone 状态
            val lastIndex = cardItems.lastIndex
            if (lastIndex >= 0) {
                val lastItem = cardItems[lastIndex]
                cardItems[lastIndex] = lastItem.copy(isGone = true) // 创建新实例
            }

            val newCard = CardItem(
                title = "新场景 ${cardItems.size + 1}",
                tag = "标签"
            )
            cardItems.add(newCard)
            cardAdapter.submitList(cardItems.toList()) {
                rvCardView.post {
                    rvCardView.smoothScrollToPosition(cardItems.size - 1)
                }
            }
        }
    }

    private fun deleteOldCard(deletedPosition: Int) {
        val newList = cardItems.toMutableList().apply { removeAt(deletedPosition) }

        // 重新计算最后一项状态
        val lastIndex = newList.lastIndex
        val updatedList = newList.mapIndexed { index, item ->
            if (newList.size < 9 && index == lastIndex) {
                item.copy(isGone = false)
            } else {
                item.copy(isGone = true)
            }
        }

        cardItems.clear()
        cardItems.addAll(updatedList)
        cardAdapter.submitList(updatedList) {
            cardAdapter.notifyItemChanged(updatedList.lastIndex)
        }
    }

    private fun addMessage(
        content: String,
        isSent: Boolean,
        type: Int = AiMessage.TYPE_TEXT,
        duration: Int = 0,
        path: String = ""
    ) {
        messageList.add(AiMessage(content, System.currentTimeMillis().toString(), isSent, type, duration, path))
        messageAdapter.notifyItemInserted(messageList.size - 1)
        rvMessages.scrollToPosition(messageList.size - 1)
    }

    private fun toggleInputMode(switcher: ViewSwitcher, icon: ImageView) {
        isVoiceInput = !isVoiceInput
        switcher.showNext() // 切换视图动画
        icon.setImageResource(
            if (isVoiceInput) R.drawable.ic_input_keyboard
            else R.drawable.ic_input_voice
        )
        if (isVoiceInput) {
            hideKeyboard()
        } else {
            showKeyboard()
        }
    }

    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(etMessage.windowToken, 0)
    }

    private fun showKeyboard() {
        etMessage.requestFocus()
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(etMessage, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun startVoiceRecording() {
        LogUtils.info("开始录音...")
        recordingStartTime = System.currentTimeMillis() // 记录开始时间
        currentAudioPath = voiceManager.startRecording()
        if (currentAudioPath == null) {
            LogUtils.error("录音失败")
        }
    }

    private fun stopVoiceRecording() {
        LogUtils.info("结束录音...")
        voiceManager.stopRecording() // 停止录音

        val endTime = System.currentTimeMillis()
        recordingDuration = Math.round((endTime - recordingStartTime) / 1000.0).toInt()

        currentAudioPath?.let { path ->
            // 将录音文件添加到消息列表
            addMessage("[语音]", true, AiMessage.TYPE_VOICE, recordingDuration, path)
            // 模拟自动回复
            Handler(Looper.getMainLooper()).postDelayed({
                addMessage("已收到语音消息", false)
            }, 1000)
        }
        currentAudioPath = null // 清空路径
    }

    private fun toggleMediaButtons() {
        if (mediaButtons.isVisible) {
            hideMediaButtons()
            ivAdd.setImageResource(R.drawable.ic_add_collapse)
        } else {
            ivAdd.setImageResource(R.drawable.ic_add_expand)
            mediaButtons.visibility = View.VISIBLE
        }
    }

    private fun hideMediaButtons() {
        mediaButtons.visibility = View.GONE
        ivAdd.setImageResource(R.drawable.ic_add_collapse)
    }

    private fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()

    override fun onCameraPhotoCaptured(uri: Uri?) {
//        uri?.let {
//            addMessage("[相机]", true, AiMessage.TYPE_IMAGE, path = it.toString())
//            Handler(Looper.getMainLooper()).postDelayed({
//                addMessage("已收到图片消息", false)
//            }, 1000)
//        }
        flCardViewContainer.visibility = View.VISIBLE
        uri?.let {
            if (cardItems.none { item -> item.imageUri == it.toString() }) {
                val newCard = CardItem(
                    title = "待发送图片",
                    tag = "IMAGE",
                    imageUri = it.toString()
                )

                cardItems.add(newCard)
                cardAdapter.submitList(cardItems.toList()) {
                    rvCardView.post {
                        rvCardView.smoothScrollToPosition(cardItems.size - 1)
                    }
                }
            }
        }
    }

    override fun onFileSelected(path: String?) {
        path?.let {
            addMessage("[文件]", true, AiMessage.TYPE_FILE, path = it)
            Handler(Looper.getMainLooper()).postDelayed({
                addMessage("已收到文件消息", false)
            }, 1000)
        } ?: run {
            ToastUtils.show(this, "文件保存失败")
        }
    }

    override fun onError(errorMessage: String) {
        ToastUtils.show(this, errorMessage)
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.release() // 释放资源
    }
}
