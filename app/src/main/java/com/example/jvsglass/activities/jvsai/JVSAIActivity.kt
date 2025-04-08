package com.example.jvsglass.activities.jvsai

import android.Manifest
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
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewSwitcher
import androidx.annotation.RequiresPermission
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
import com.example.jvsglass.network.NetworkManager
import com.example.jvsglass.network.TranscribeResponse
import com.example.jvsglass.network.UploadResult
import retrofit2.HttpException
import java.io.File

class JVSAIActivity : AppCompatActivity(), SystemFileOpener.FileResultCallback {

    private lateinit var voiceManager: VoiceManager
    private lateinit var fileOpener: SystemFileOpener

    private var currentAudioPath: String? = null // 记录当前录音文件路径
    private var recordingStartTime: Long = 0L  // 录音开始时间戳（毫秒）
    private var recordingDuration: Int = 0     // 录音时长（秒）

    private var isUiReady = false
    private val messageList = mutableListOf<AiMessage>()
    private val cardItems = mutableListOf<CardItem>()
    private lateinit var messageAdapter: AiMessageAdapter
    private lateinit var cardAdapter: CardAdapter
    private var isVoiceInput = false
    private var startY = 0f
    private var isCanceled = false

    private lateinit var rvMessages: RecyclerView
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

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jvsai)

        voiceManager = VoiceManager(this)
        fileOpener = SystemFileOpener(this)
        fileOpener.registerLaunchers(this, this)

        setupUI()
        setupCardView()

        rvCardView.viewTreeObserver.addOnPreDrawListener {
            isUiReady = true
            true
        }

        setupRecyclerView()
        setupClickListeners()
    }

    private fun setupUI() {
        rvMessages = findViewById(R.id.rvMessages)
        rvCardView = findViewById(R.id.rvCardView)
        rvCardView.layoutManager = LinearLayoutManager(this)
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
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
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
                    if (cardItems.size < CardAdapter.MAX_CARD_ITEM) {
                        if (cardItems[0].tag == "IMAGE") {
                            fileOpener.openCamera()
                        } else {
                            fileOpener.openFilePicker()
                        }
                    }
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

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
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
            if (cardItems.size != 0) {
                if (cardItems[0].tag == "IMAGE") {
                    if (cardItems.size == CardAdapter.MAX_CARD_ITEM) {
                        ToastUtils.show(this, "最多支持上传${CardAdapter.MAX_CARD_ITEM}张图片")
                        return@setOnClickListener
                    }

                    fileOpener.openCamera()
                    hideMediaButtons()
                } else {
                    ToastUtils.show(this, "请选择上传文件")
                }
            } else {
                fileOpener.openCamera()
                hideMediaButtons()
            }
        }

        ivFile.setOnClickListener {
            if (cardItems.size != 0) {
                if (cardItems[0].tag != "IMAGE") {
                    if (cardItems.size == CardAdapter.MAX_CARD_ITEM) {
                        ToastUtils.show(this, "最多支持上传${CardAdapter.MAX_CARD_ITEM}个文件")
                        return@setOnClickListener
                    }

                    fileOpener.openFilePicker()
                    hideMediaButtons()
                } else {
                    ToastUtils.show(this, "请选择拍照")
                }
            } else {
                fileOpener.openFilePicker()
                hideMediaButtons()
            }
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
        val message = etMessage.text.toString().trim()
        val pendingCards = cardItems.toList()

        etMessage.text.clear()
        cardItems.clear()
        cardAdapter.submitList(cardItems.toList())
        rvCardView.visibility = View.GONE

        if (message.isNotEmpty() || pendingCards.isNotEmpty()) {
            addMessage(
                message = message,
                isSent = true,
                attachments = pendingCards // 传入附件集合
            )
            uploadFile(message, pendingCards)
        }

        // 模拟回复
        Handler(Looper.getMainLooper()).postDelayed({
            addMessage("已收到${message.ifEmpty { "信息" }}", false)
        }, 1000)
    }

    private fun deleteOldCard(deletedPosition: Int) {
        if (deletedPosition in 0 until cardItems.size) {
            val newList = cardItems.toMutableList().apply { removeAt(deletedPosition) }
            cardItems.clear()
            cardItems.addAll(newList)
            updateCardList()
            rvCardView.visibility = if (cardItems.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun addMessage(
        message: String,
        isSent: Boolean,
        type: Int = AiMessage.TYPE_TEXT,
        duration: Int = 0,
        path: String = "",
        attachments: List<CardItem> = emptyList()
    ) {
        attachments.forEach { card ->
            when (card.tag) {
                "IMAGE" -> {
                    messageList.add(AiMessage(
                        "[图片]",
                        System.currentTimeMillis().toString(),
                        isSent,
                        AiMessage.TYPE_IMAGE,
                        path = card.fileUri
                    ))
                }
                else -> {
                    messageList.add(AiMessage(
                        "[文件]",
                        System.currentTimeMillis().toString(),
                        isSent,
                        AiMessage.TYPE_FILE,
                        path = card.fileUri
                    ))
                }
            }
            messageAdapter.notifyItemInserted(messageList.size - 1)
            rvMessages.scrollToPosition(messageList.size - 1)
        }

        messageList.add(AiMessage(message, System.currentTimeMillis().toString(), isSent, type, duration, path))
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

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startVoiceRecording() {
        LogUtils.info("开始录音...")
        recordingStartTime = System.currentTimeMillis() // 记录开始时间
        currentAudioPath = voiceManager.startRecording()
        if (currentAudioPath == null) {
            LogUtils.error("录音失败")
        }
    }

//    private fun stopVoiceRecording() {
//        LogUtils.info("结束录音...")
//        voiceManager.stopRecording() // 停止录音
//
//        val endTime = System.currentTimeMillis()
//        recordingDuration = Math.round((endTime - recordingStartTime) / 1000.0).toInt()
//
//        currentAudioPath?.let { path ->
//            // 将录音文件添加到消息列表
//            addMessage("[语音]", true, AiMessage.TYPE_VOICE, recordingDuration, path)
//            // 模拟自动回复
//            Handler(Looper.getMainLooper()).postDelayed({
//                addMessage("已收到语音消息", false)
//            }, 1000)
//        }
//        currentAudioPath = null // 清空路径
//    }

    private fun stopVoiceRecording() {
        voiceManager.stopRecording()
        currentAudioPath?.let { path ->
            val audioFile = File(path)
            if (audioFile.exists()) {
                // 显示加载状态
                LogUtils.info("开始语音识别...")

                NetworkManager.getInstance().transcribeAudio(
                    audioFile,
                    object : NetworkManager.ModelCallback<TranscribeResponse> {
                        override fun onSuccess(result: TranscribeResponse) {
                            // 添加识别结果
                            addMessage(result.text, true, AiMessage.TYPE_TEXT)
                        }

                        override fun onFailure(error: Throwable) {
                            addMessage("识别失败，请重试", true)
                            if (error is HttpException) {
                                val code = error.code() // HTTP 状态码
                                val responseBody = error.response()?.errorBody()?.string() // 响应体内容
                                LogUtils.error("语音识别失败: HTTP $code, 响应: $responseBody")
                            } else {
                                LogUtils.error("语音识别失败: ${error.message}, 堆栈: ${error.stackTraceToString()}")
                            }
                        }
                    })
            } else {
                ToastUtils.show(this, "录音文件不存在")
            }
        }
        // 计算录音时长
        recordingDuration = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
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
        if (!isUiReady) {
            ToastUtils.show(this, "请等待界面初始化完成")
            return
        }

        uri?.let {
            val fileName = uri.path?.substringAfter("/my_files/")
            val filePath = fileName?.let { name ->
                getExternalFilesDir(null)?.resolve(name)?.absolutePath
            }

            val newCard = CardItem(
                id = System.currentTimeMillis().toString(),
                title = "待发送图片",
                tag = "IMAGE",
                fileUri = filePath ?: uri.toString()
            )

            if (cardItems.none { item -> item.fileUri == newCard.fileUri }) {
                cardItems.add(newCard)
                updateCardList(scroll = true)
                rvCardView.visibility = View.VISIBLE
            }
        }
    }

    override fun onFileSelected(path: String?) {
        if (!isUiReady) {
            ToastUtils.show(this, "请等待界面初始化完成")
            return
        }

        path?.let {
            val fileExtension = it.substringAfterLast(".", "")
            val fileName = File(it).name.substringAfter("FILE_").substringBefore(".")
            val newCard = CardItem(
                id = System.currentTimeMillis().toString(),
                title = fileName,
                tag = fileExtension,
                fileUri = it
            )

            cardItems.add(newCard)
            updateCardList(scroll = true)
            rvCardView.visibility = View.VISIBLE
        } ?: run {
            ToastUtils.show(this, "文件保存失败")
        }
    }

    private fun updateCardList(scroll: Boolean = false) {
        val displayList = mutableListOf<CardItem>()
        displayList.addAll(cardItems)
        if (cardItems.size < CardAdapter.MAX_CARD_ITEM) {
            displayList.add(
                CardItem(
                    id = "ADD", // 固定ID或特殊标识
                    title = "",
                    tag = "",
                    fileUri = "",
                    isAddItem = true
                )
            )
        }
        cardAdapter.submitList(displayList) {
            // 列表更新完成后执行滚动
            if (scroll) {
                // 确保 itemCount 大于0，再调用 smoothScrollToPosition
                if (cardAdapter.itemCount > 0) {
                    rvCardView.smoothScrollToPosition(cardAdapter.itemCount - 1)
                }
            }
        }
    }

    private fun uploadFile(message: String, attachments: List<CardItem> = emptyList()) {
        if (message.isEmpty() && attachments.isEmpty()) {
            LogUtils.warn("消息和附件不能同时为空")
            return
        }

        if (attachments.size == 1) {
            val filePath = attachments[0].fileUri
            uploadSingleFile(message, filePath)
        } else {
            val filePath = attachments.map { it.fileUri }.filter { it.isNotBlank() }
            uploadMultipleFiles(message, filePath)
        }
    }

    private fun uploadSingleFile(message: String, filePath: String) {
        LogUtils.info("文件路径：$filePath (长度：${filePath.length})")
        val file = File(filePath)
        LogUtils.info("文件绝对路径：${file.absolutePath} (长度：${file.absolutePath.length})")
        if (!file.exists()) {
            LogUtils.error("文件不存在: ${file.absolutePath}")
            return
        }
        LogUtils.info("文件路径：$filePath")

        NetworkManager.getInstance().uploadSingleFile(
            file = file,
            description = message,
            callback = object : NetworkManager.UploadCallback {
                override fun onProgress(percent: Float) {
//                    LogUtils.info("--- ${percent * 100} ---")
                }

                override fun onSuccess(result: UploadResult) {
                    LogUtils.info("上传成功")
                }

                override fun onFailure(error: Throwable) {
                    LogUtils.error("上传失败: ${error.message}")
                }
            }
        )
    }

    private fun uploadMultipleFiles(message: String, fileUrl: List<String>) {

    }

    override fun onError(errorMessage: String) {
        ToastUtils.show(this, errorMessage)
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.release()
        NetworkManager.getInstance().dispose()
    }
}
