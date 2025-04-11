package com.example.jvsglass.activities.jvsai

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
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
import com.example.jvsglass.network.ChatRequest
import com.example.jvsglass.network.ChatResponse
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.SystemFileOpener
import com.example.jvsglass.utils.ToastUtils
import com.example.jvsglass.utils.VoiceManager
import com.example.jvsglass.network.NetworkManager
import com.example.jvsglass.network.TranscribeResponse
import retrofit2.HttpException
import java.io.File
import androidx.core.graphics.drawable.toDrawable
import com.example.jvsglass.network.TOSManager

class JVSAIActivity : AppCompatActivity(), SystemFileOpener.FileResultCallback {

    private lateinit var voiceManager: VoiceManager
    private lateinit var fileOpener: SystemFileOpener

    private var currentAudioPath: String? = null // 记录当前录音文件路径
    private var loadingDialog: Dialog? = null
    private var isUiReady = false
    private val messageList = mutableListOf<AiMessage>()
    private val cardItems = mutableListOf<CardItem>()
    private lateinit var messageAdapter: AiMessageAdapter
    private lateinit var cardAdapter: CardAdapter
    private var isVoiceInput = false
    private var startY = 0f
    private var isCanceled = false

    private val chatMessages = mutableListOf<ChatRequest.Message>()

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
        updateButtonVisibility()

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

        etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                updateButtonVisibility()
            }
        })
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
            adapter = cardAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = AiMessageAdapter(messageList)
        rvMessages.apply {
            layoutManager = LinearLayoutManager(this@JVSAIActivity)
            adapter = messageAdapter
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
    }

    private fun sendMessage(messageText: String? = null) {
        val message = messageText ?: etMessage.text.toString().trim()
        val pendingCards = cardItems.toList()

        if (message.isEmpty() && pendingCards.isEmpty()) {
            ToastUtils.show(this, "请输入内容或添加文件")
            return
        }

        addMessageWithAttachments(message, pendingCards)

        when {
            pendingCards.any { it.tag == "IMAGE" } -> {
                val imageFiles = pendingCards
                    .filter { it.tag == "IMAGE" }
                    .map { File(it.fileUri) }
//                sendImageToModel(message, imageFiles)
                sendImageToCozeModel(message, imageFiles)
            }

            // 处理文件附件请求
            pendingCards.isNotEmpty() -> {
//                sendFilesToModel(text, pendingCards)
            }

            else -> sendTextToModel(message)
        }

        etMessage.text.clear()
        cardItems.clear()
        cardAdapter.submitList(cardItems.toList())
        rvCardView.visibility = View.GONE
        updateButtonVisibility()
    }

    private fun addMessageWithAttachments(text: String, attachments: List<CardItem>) {
        attachments.forEach { card ->
            when (card.tag) {
                "IMAGE" -> addMessage(
                    "[图片]",
                    true,
                    AiMessage.TYPE_IMAGE,
                    path = card.fileUri
                )
                else -> addMessage(
                    "[文件]",
                    true,
                    AiMessage.TYPE_FILE,
                    path = card.fileUri
                )
            }
        }
        if (text.isNotEmpty()) {
            addMessage(text, true)
        }
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
        path: String = ""
    ): AiMessage {
        val newMessage = AiMessage(
            message,
            System.currentTimeMillis().toString(),
            isSent,
            type,
            path
        )
        messageList.add(newMessage)
        messageAdapter.notifyItemInserted(messageList.size - 1)
        rvMessages.scrollToPosition(messageList.size - 1)
        return newMessage
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

    private fun updateButtonVisibility() {
        val hasContent = cardItems.isNotEmpty() || etMessage.text.toString().isNotEmpty()
        ivAdd.isVisible = !hasContent
        ivSend.isVisible = hasContent
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startVoiceRecording() {
        LogUtils.info("开始录音...")
        currentAudioPath = voiceManager.startRecording()
        if (currentAudioPath == null) {
            LogUtils.error("录音失败")
        }
    }

    private fun stopVoiceRecording() {
        voiceManager.stopRecording()
        currentAudioPath?.let { path ->
            val audioFile = File(path)
            if (audioFile.exists()) {
                LogUtils.info("开始语音识别...")
                showLoadingDialog()
                NetworkManager.getInstance().transcribeAudio(
                    audioFile,
                    object : NetworkManager.ModelCallback<TranscribeResponse> {
                        override fun onSuccess(result: TranscribeResponse) {
                            dismissLoadingDialog()
                            val pendingCards = cardItems.toList()
                            if (pendingCards.isNotEmpty()) {
                                sendMessage(result.text)
                                cardItems.clear()
                                currentAudioPath = null
                                updateCardList()
                            } else {
                                sendMessage(result.text)
                            }
                        }

                        override fun onFailure(error: Throwable) {
                            dismissLoadingDialog()
                            if (error is HttpException) {
                                val code = error.code()
                                val responseBody = error.response()?.errorBody()?.string()
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
    }

    private fun sendTextToModel(userMessage: String) {
        val thinkingMessage = addMessage("思考中...", false)
        val userMsg = ChatRequest.Message(
            role = "user",
            content = userMessage
        )
        chatMessages.add(userMsg)

        NetworkManager.getInstance().chatTextCompletion(
            messages = chatMessages,
            temperature = 0.7,
            object : NetworkManager.ModelCallback<ChatResponse> {
                override fun onSuccess(result: ChatResponse) {
                    result.choices.firstOrNull()?.let { choice ->
                        val aiContent = choice.message.content
                        chatMessages.add(ChatRequest.Message(
                            role = choice.message.role,
                            content = aiContent
                        ))
                        updateMessage(thinkingMessage.id, aiContent)
                    } ?: addMessage("未收到有效回复", false)
                }

                override fun onFailure(error: Throwable) {
                    updateMessage(thinkingMessage.id, "请求失败，请重试")
                    LogUtils.error("请求失败: ${error.message?.substringBefore("\n") ?: "未知错误"}")
                }
            })
    }

    private fun sendImageToModel(message: String, imageFiles: List<File>) {
        val processingMessage = addMessage("识图中...", false)
        NetworkManager.getInstance().uploadImageCompletion(
            images = imageFiles,
            question = message.takeIf { it.isNotEmpty() },
            object : NetworkManager.ModelCallback<ChatResponse> {
                override fun onSuccess(result: ChatResponse) {
                    result.choices.firstOrNull()?.let { choice ->
                        val aiContent = choice.message.content
                        chatMessages.add(ChatRequest.Message(
                            role = choice.message.role,
                            content = aiContent
                        ))
                        updateMessage(processingMessage.id, aiContent)
                    } ?: addMessage("未收到有效回复", false)
                }

                override fun onFailure(error: Throwable) {
                    updateMessage(processingMessage.id, "图片解析失败，请重试")
                    LogUtils.error("图片处理失败", error)
                }
            }
        )
    }

    private fun sendImageToCozeModel(message: String, imageFiles: List<File>) {
        val processingMessage = addMessage("识图中...", false)
        val imageUrls = mutableListOf<String>()
        val lock = Any()
        var completedCount = 0

        imageFiles.forEach { file ->
            TOSManager.getInstance().uploadImageFile(
                objectKey = file.name,
                filePath = file.absolutePath
            ) { url ->
                synchronized(lock) {
                    // 线程安全地更新状态
                    url?.let { imageUrls.add(it) }
                    completedCount++

                    // 当所有文件处理完成时触发（无论成功失败）
                    if (completedCount == imageFiles.size) {
                        // 如果没有成功上传任何文件则报错
                        if (imageUrls.isEmpty()) {
                            updateMessage(processingMessage.id, "图片上传失败，请重试")
                            return@synchronized
                        }

                        NetworkManager.getInstance().uploadImageCozeCompletion(
                            images = imageUrls,
                            question = message.takeIf { it.isNotEmpty() },
                            object : NetworkManager.ModelCallback<ChatResponse> {
                                override fun onSuccess(result: ChatResponse) {
                                    result.choices.firstOrNull()?.let { choice ->
                                        val aiContent = choice.message.content
                                        chatMessages.add(ChatRequest.Message(
                                            role = choice.message.role,
                                            content = aiContent
                                        ))
                                        updateMessage(processingMessage.id, aiContent)
                                    } ?: addMessage("未收到有效回复", false)
                                }

                                override fun onFailure(error: Throwable) {
                                    updateMessage(processingMessage.id, "图片解析失败，请重试")
                                    LogUtils.error("图片处理失败", error)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun updateMessage(messageId: String, newContent: String) {
        val index = messageList.indexOfFirst { it.id == messageId }
        if (index != -1) {
            messageList[index] = messageList[index].copy(message = newContent)
            messageAdapter.notifyItemChanged(index)
            rvMessages.scrollToPosition(messageList.size - 1)
        }
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
            updateButtonVisibility()
        }
    }

    private fun createLoadingDialog() {
        if (isFinishing || isDestroyed) return

        loadingDialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_loading)
            window?.apply {
                // 关键配置
                setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                setDimAmount(0.2f) // 背景遮罩透明度
                attributes = attributes.apply {
                    width = ViewGroup.LayoutParams.WRAP_CONTENT
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    gravity = Gravity.CENTER // 强制对话框居中
                }
            }
            setCancelable(false)
        }
    }

    private fun showLoadingDialog() {
        if (loadingDialog == null) {
            createLoadingDialog()
        }
        if (!isFinishing && !isDestroyed) {
            loadingDialog?.show()
        }
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.takeIf { it.isShowing }?.dismiss()
    }

    override fun onError(errorMessage: String) {
        ToastUtils.show(this, errorMessage)
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.release()
        NetworkManager.getInstance().dispose()
        dismissLoadingDialog()
    }
}
