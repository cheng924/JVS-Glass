package com.example.jvsglass.ui.ai;

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewSwitcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.database.AiConversationEntity
import com.example.jvsglass.database.AiMessageEntity
import com.example.jvsglass.database.AppDatabase
import com.example.jvsglass.database.AppDatabaseProvider
import com.example.jvsglass.network.ChatRequest
import com.example.jvsglass.network.ChatResponse
import com.example.jvsglass.network.NetworkManager
import com.example.jvsglass.network.RealtimeAsrClient
import com.example.jvsglass.network.TOSManager
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.ToastUtils
import com.example.jvsglass.utils.SystemFileOpener
import com.example.jvsglass.utils.VoiceManager
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

class AiFragment : Fragment(), SystemFileOpener.FileResultCallback {
    private lateinit var voiceManager: VoiceManager
    private lateinit var fileOpener: SystemFileOpener
    private lateinit var realtimeAsrClient: RealtimeAsrClient
    private val db: AppDatabase by lazy { AppDatabaseProvider.db }

    private var isUiReady = false
    private val messageList = mutableListOf<AiMessage>()
    private val cardItems = mutableListOf<CardItem>()
    private lateinit var messageAdapter: AiMessageAdapter
    private lateinit var cardAdapter: CardAdapter
    private var isVoiceInput = false
    private var startY = 0f
    private var isCanceled = false
    private var isStreaming = false
    private var tempMessageId: String? = null // 跟踪临时消息
    private val chatMessages = mutableListOf<ChatRequest.Message>()
    private var streamDisposable: Disposable? = null
    private var currentVoicePath = ""
    private var currentConversationId: String? = null
    private var isLoadedFromHistory = false

    private lateinit var tvConversationTitle: TextView
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
    private lateinit var llImageAdd: LinearLayout
    private lateinit var llGallery: LinearLayout
    private lateinit var llCamera: LinearLayout
    private lateinit var icGallery: ImageView
    private lateinit var ivCamera: ImageView
    private lateinit var ivFile: ImageView
    private lateinit var ivCall: ImageView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ai, container, false)
    }

    @SuppressLint("NotifyDataSetChanged")
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        voiceManager = VoiceManager(requireContext())
        fileOpener = SystemFileOpener(requireContext())
        fileOpener.registerLaunchers(this, this)

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }

        setupUI(view)
        setupCardView()
        updateButtonVisibility()

        rvCardView.viewTreeObserver.addOnPreDrawListener {
            isUiReady = true
            true
        }

        setupRecyclerView()
        setupClickListeners()
        initRealtimeAsrClient()

        // 处理来自导航的 Arguments
        arguments?.getString("conversationId")?.let { convId ->
            val fromHistory = arguments?.getBoolean("fromHistory", false) ?: false
            if (fromHistory && messageList.isNotEmpty()) {
                saveConversationToDb()
            }
            currentConversationId = convId
            isLoadedFromHistory = true
            messageList.clear()
            messageAdapter.notifyDataSetChanged()
            loadConversation(convId)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun setupUI(view: View) {
        tvConversationTitle = view.findViewById(R.id.tvConversationTitle)
        rvMessages = view.findViewById(R.id.rvMessages)
        rvCardView = view.findViewById(R.id.rvCardView)
        rvCardView.layoutManager = LinearLayoutManager(requireContext())
        llTextInput = view.findViewById(R.id.llTextInput)
        ivInput = view.findViewById(R.id.ivInput)
        inputSwitcher = view.findViewById(R.id.inputSwitcher)
        etMessage = view.findViewById(R.id.etMessage)
        tvVoiceChoose = view.findViewById(R.id.tvVoiceChoose)
        ivAdd = view.findViewById(R.id.ivAdd)
        ivSend = view.findViewById(R.id.ivSend)

        llVoiceInput = view.findViewById(R.id.llVoiceInput)
        tvVoiceInputTip = view.findViewById(R.id.tvVoiceInputTip)
        tvVoiceInput = view.findViewById(R.id.tvVoiceInput)

        mediaButtons = view.findViewById(R.id.mediaButtons)
        llImageAdd = view.findViewById(R.id.llImageAdd)
        llGallery = view.findViewById(R.id.llGallery)
        llCamera = view.findViewById(R.id.llCamera)
        icGallery = view.findViewById(R.id.icGallery)
        ivCamera = view.findViewById(R.id.ivCamera)
        ivFile = view.findViewById(R.id.ivFile)
        ivCall = view.findViewById(R.id.ivCall)

        view.findViewById<ImageView>(R.id.ivAiHistory).setOnClickListener {
            // 导航到历史记录 Fragment 或 Activity
            // 例如：NavHostFragment.findNavController(this).navigate(R.id.action_to_history)
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
                        if (cardItems.isNotEmpty() && cardItems[0].tag == "IMAGE") {
                            llImageAdd.visibility = View.VISIBLE
                            llGallery.setOnClickListener {
                                fileOpener.openGallery()
                                llImageAdd.visibility = View.GONE
                            }
                            llCamera.setOnClickListener {
                                fileOpener.openCamera()
                                llImageAdd.visibility = View.GONE
                            }
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
                requireContext(),
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
            layoutManager = LinearLayoutManager(requireContext())
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
                    currentVoicePath = voiceManager.startRecording(object : VoiceManager.AudioRecordCallback {
                        override fun onAudioData(data: ByteArray) {
                            if (!isCanceled) {
                                realtimeAsrClient.sendAudioChunk(data)
                                LogUtils.debug("实时发送音频块，大小=${data.size}字节")
                            }
                        }
                    }).toString()
                    llVoiceInput.visibility = View.VISIBLE
                    llTextInput.visibility = View.GONE
                    tvVoiceInputTip.text = "松手发送，上移取消"
                    tvVoiceInput.apply {
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                        setBackgroundResource(R.drawable.bg_voice_recording)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = startY - event.rawY
                    if (deltaY > 100.dpToPx()) {
                        if (!isCanceled) {
                            isCanceled = true
                            llVoiceInput.visibility = View.VISIBLE
                            llTextInput.visibility = View.GONE
                            tvVoiceInputTip.text = "松手取消"
                            tvVoiceInput.apply {
                                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                                setBackgroundResource(R.drawable.bg_voice_cancel)
                                startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.shake))
                            }
                        }
                    } else {
                        isCanceled = false
                        llVoiceInput.visibility = View.VISIBLE
                        llTextInput.visibility = View.GONE
                        tvVoiceInputTip.text = "松手发送，上移取消"
                        tvVoiceInput.apply {
                            setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
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
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
                        setBackgroundResource(R.drawable.voice_input_bg)
                        clearAnimation()
                    }
                    if (isCanceled) {
                        LogUtils.info("取消录音")
                        voiceManager.stopRecording()
                        voiceManager.deleteVoiceFile(currentVoicePath)
                        tempMessageId?.let { tempId ->
                            val index = messageList.indexOfFirst { it.id == tempId }
                            if (index != -1) {
                                messageList.removeAt(index)
                                messageAdapter.notifyItemRemoved(index)
                            }
                            tempMessageId = null
                        }
                        realtimeAsrClient.resetSession()
                    } else {
                        voiceManager.stopRecording()
                        realtimeAsrClient.commitAudio()
                        LogUtils.info("录音结束，提交音频缓冲区")
                    }
                    true
                }
                else -> false
            }
        }

        ivAdd.setOnClickListener {
            hideKeyboard()
            toggleMediaButtons()
        }

        ivSend.setOnClickListener {
            sendMessage()
        }

        icGallery.setOnClickListener {
            fileOpener.openGallery()
            hideMediaButtons()
        }

        ivCamera.setOnClickListener {
            if (cardItems.size != 0) {
                if (cardItems[0].tag == "IMAGE") {
                    if (cardItems.size == CardAdapter.MAX_CARD_ITEM) {
                        ToastUtils.show(requireContext(), "最多支持上传${CardAdapter.MAX_CARD_ITEM}张图片")
                        return@setOnClickListener
                    }
                    fileOpener.openCamera()
                    hideMediaButtons()
                } else {
                    ToastUtils.show(requireContext(), "请选择上传文件")
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
                        ToastUtils.show(requireContext(), "最多支持上传${CardAdapter.MAX_CARD_ITEM}个文件")
                        return@setOnClickListener
                    }
                    fileOpener.openFilePicker()
                    hideMediaButtons()
                } else {
                    ToastUtils.show(requireContext(), "请选择拍照")
                }
            } else {
                fileOpener.openFilePicker()
                hideMediaButtons()
            }
        }

        ivCall.setOnClickListener {
            ToastUtils.show(requireContext(), "开发中，敬请期待")
            hideMediaButtons()
        }
    }

    private fun sendMessage(messageText: String? = null) {
        var finalMessageText = messageText
        tempMessageId?.let { tempId ->
            val index = messageList.indexOfFirst { it.id == tempId }
            if (index != -1) {
                val tempMessage = messageList.removeAt(index)
                finalMessageText = tempMessage.message
                messageAdapter.notifyItemRemoved(index)
            }
            tempMessageId = null
        }

        val message = finalMessageText ?: etMessage.text.toString().trim()
        val pendingCards = cardItems.toList()

        if (message.isEmpty() && pendingCards.isEmpty()) {
            ToastUtils.show(requireContext(), "请输入内容或添加文件")
            return
        }

        addMessageWithAttachments(message, pendingCards)

        when {
            pendingCards.any { it.tag == "IMAGE" } -> {
                val imageFiles = pendingCards
                    .filter { it.tag == "IMAGE" }
                    .map { File(it.fileUri) }
                    .filter { it.exists() }
                if (imageFiles.isEmpty()) {
                    ToastUtils.show(requireContext(), "无法获取图片文件")
                } else {
                    sendImageToCozeModelStream(message, imageFiles)
                }
            }
            pendingCards.isNotEmpty() -> {
                sendFilesToModel(message, pendingCards)
            }
            else -> sendTextToCozeModelStream(message)
        }

        if (tvConversationTitle.text == "AI助手") {
            generateConversationTitle(message)
        }

        hideKeyboard()
        etMessage.text.clear()
        cardItems.clear()
        cardAdapter.submitList(cardItems.toList())
        rvCardView.visibility = View.GONE
        updateButtonVisibility()
    }

    private fun addMessageWithAttachments(text: String, attachments: List<CardItem>) {
        attachments.forEach { card ->
            when (card.tag) {
                "IMAGE" -> addMessage("[图片]", true, AiMessage.TYPE_IMAGE, path = card.fileUri)
                else -> addMessage("[文件]", true, AiMessage.TYPE_FILE, path = card.fileUri)
            }
        }
        if (text.isNotEmpty()) {
            addMessage(text, true)
        }
    }

    private fun deleteOldCard(deletedPosition: Int) {
        if (deletedPosition in 0 until cardItems.size) {
            File(cardItems[deletedPosition].fileUri).takeIf { it.exists() }?.delete()
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
        val newMessage = AiMessage(message, System.currentTimeMillis(), isSent, type, path)
        messageList.add(newMessage)
        messageAdapter.notifyItemInserted(messageList.size - 1)
        rvMessages.scrollToPosition(messageList.size - 1)
        return newMessage
    }

    private fun toggleInputMode(switcher: ViewSwitcher, icon: ImageView) {
        isVoiceInput = !isVoiceInput
        switcher.showNext()
        icon.setImageResource(
            if (isVoiceInput) R.drawable.ic_input_keyboard else R.drawable.ic_input_voice
        )
        if (isVoiceInput) {
            hideKeyboard()
            realtimeAsrClient.connect()
            realtimeAsrClient.keepConnectionOpen()
        } else {
            showKeyboard()
            realtimeAsrClient.disconnect()
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(etMessage.windowToken, 0)
    }

    private fun showKeyboard() {
        etMessage.requestFocus()
        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        imm.showSoftInput(etMessage, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun updateButtonVisibility() {
        val hasContent = cardItems.isNotEmpty() || etMessage.text.toString().isNotEmpty()
        ivAdd.isVisible = !hasContent
        ivSend.isVisible = hasContent
    }

    private fun initRealtimeAsrClient() {
        realtimeAsrClient = NetworkManager.getInstance().createRealtimeAsrClient(
            object : RealtimeAsrClient.RealtimeAsrCallback {
                override fun onPartialResult(text: String) {
                    requireActivity().runOnUiThread { updateTranscript(text, isFinal = false) }
                }

                override fun onFinalResult(text: String) {
                    requireActivity().runOnUiThread {
                        updateTranscript(text, isFinal = true)
                        messageList.find { it.id == tempMessageId }?.let { msg ->
                            sendMessage(msg.message)
                        }
                        voiceManager.deleteVoiceFile(currentVoicePath)
                    }
                }

                override fun onError(error: String) {
                    requireActivity().runOnUiThread { LogUtils.error(error) }
                }

                override fun onConnectionChanged(connected: Boolean) {
                    LogUtils.info("ASR连接状态: $connected")
                    if (!connected) {
                        requireActivity().runOnUiThread {
                            tempMessageId = null
                            isStreaming = false
                        }
                    }
                }

                override fun onSessionReady() {
                    LogUtils.info("ASR session ready")
                }
            })
    }

    private fun updateTranscript(text: String, isFinal: Boolean) {
        if (text.isBlank()) return

        if (tempMessageId == null) {
            val newMessage = addMessage(text, true).apply { isTemp = !isFinal }
            tempMessageId = newMessage.id
        } else {
            messageList.find { it.id == tempMessageId }?.let { message ->
                val index = messageList.indexOf(message)
                val updated = message.copy(message = text, isTemp = !isFinal)
                messageList[index] = updated
                messageAdapter.notifyItemChanged(index)

                if (isFinal) {
                    sendMessage(updated.message)
                    tempMessageId = null
                }
            }
        }
    }

    private fun generateConversationTitle(firstMessage: String) {
        val titlePrompt = "请为以下用户提问生成一个不超过7个字的简洁会话标题，不能叫\"AI助手\"：\n“$firstMessage”"
        NetworkManager.getInstance().chatTextCompletion(
            messages = listOf(ChatRequest.Message(role = "user", content = titlePrompt)),
            temperature = 0.7,
            object : NetworkManager.ModelCallback<ChatResponse> {
                override fun onSuccess(result: ChatResponse) {
                    val title = result.choices.firstOrNull()?.message?.content?.trim()
                    requireActivity().runOnUiThread {
                        if (!title.isNullOrEmpty() && title.length <= 7) {
                            tvConversationTitle.text = title
                        } else {
                            LogUtils.error("生成超长标题：$title")
                        }
                    }
                }
                override fun onFailure(error: Throwable) {
                    LogUtils.error("生成标题失败：${error.message}")
                }
            }
        )
    }

    private fun sendTextToCozeModelStream(userMessage: String) {
        val thinkingMessage = addMessage("思考中...", false)
        val fullResponse = StringBuilder()
        val userMsg = ChatRequest.Message(role = "user", content = userMessage)
        chatMessages.add(userMsg)
        streamDisposable = NetworkManager.getInstance().chatTextCompletionStream(
            messages = chatMessages,
            temperature = 0.7,
            callback = object : NetworkManager.StreamCallback {
                override fun onNewMessage(text: String) {
                    fullResponse.append(text)
                    LogUtils.info("收到消息：$text")
                    updateMessage(thinkingMessage.id, fullResponse.toString())
                }

                override fun onCompleted() {
                    LogUtils.info("对话结束")
                    if (fullResponse.isEmpty()) updateMessage(thinkingMessage.id, "未收到有效回复")
                }

                override fun onError(error: Throwable) {
                    LogUtils.error("出错了：${error.message}")
                    updateMessage(thinkingMessage.id, "请求失败，请重试")
                }
            }
        )
    }

    private fun sendImageToCozeModelStream(message: String, imageFiles: List<File>) {
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
                    url?.let { imageUrls.add(it) }
                    completedCount++

                    if (completedCount == imageFiles.size) {
                        if (imageUrls.isEmpty()) {
                            updateMessage(processingMessage.id, "图片上传失败，请重试")
                            return@synchronized
                        }

                        NetworkManager.getInstance().uploadImageCozeCompletionStream(
                            images = imageUrls,
                            question = message.takeIf { it.isNotEmpty() },
                            object : NetworkManager.StreamCallback {
                                private val buffer = StringBuilder()

                                override fun onNewMessage(text: String) {
                                    buffer.append(text)
                                    updateMessage(processingMessage.id, buffer.toString())
                                }

                                override fun onCompleted() {
                                    LogUtils.info("图片问答流式响应已完成")
                                }

                                override fun onError(error: Throwable) {
                                    updateMessage(processingMessage.id, "图片解析失败，请重试")
                                    LogUtils.error("流式解析出错", error)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun sendFilesToModel(message: String, files: List<CardItem>) {
        val processingMessage = addMessage("处理中...", false)

        val fileContents = files.joinToString("\n\n") { card ->
            val path = card.fileUri
            val title = card.title.ifBlank { File(path).name }
            val content = runCatching { File(path).readText() }.getOrNull() ?: "[无法读取文件内容]"
            "文件【$title】内容：\n$content"
        }

        LogUtils.info("文件内容：\n$fileContents")

        val instruction = buildString {
            append("请根据以下文件内容和用户需求给出回答：\n\n")
            append(fileContents)
            append("\n\n用户需求：\n$message")
        }

        val userMsg = ChatRequest.Message(role = "user", content = instruction)
        NetworkManager.getInstance().uploadFileTextCompletion(
            messages = listOf(userMsg),
            temperature = 0.7,
            object : NetworkManager.ModelCallback<ChatResponse> {
                override fun onSuccess(result: ChatResponse) {
                    val aiContent = result.choices.firstOrNull()?.message?.content ?: "未收到有效回复"
                    chatMessages.add(ChatRequest.Message(role = "assistant", content = aiContent))
                    updateMessage(processingMessage.id, aiContent)
                }

                override fun onFailure(error: Throwable) {
                    updateMessage(processingMessage.id, "文件处理失败，请重试")
                    LogUtils.error("文件处理失败", error)
                }
            }
        )
    }

    private fun updateMessage(messageId: String, newContent: String) {
        val index = messageList.indexOfFirst { it.id == messageId }
        if (index != -1) {
            messageList[index] = messageList[index].copy(message = newContent, timestamp = System.currentTimeMillis())
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
            ToastUtils.show(requireContext(), "请等待界面初始化完成")
            return
        }

        uri?.let {
            val fileName = uri.path?.substringAfter("/my_files/")
            val filePath = fileName?.let { name ->
                requireContext().getExternalFilesDir(null)?.resolve(name)?.absolutePath
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
            ToastUtils.show(requireContext(), "请等待界面初始化完成")
            return
        }

        val actualPath = run {
            val uri = path?.toUri()
            if (uri?.scheme == "content") {
                fileOpener.copyGalleryImageToAppDir(uri)
            } else {
                path
            }
        } ?: run {
            ToastUtils.show(requireContext(), "图片复制失败")
            return
        }

        val lower = actualPath.lowercase(Locale.getDefault())
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".bmp") || lower.endsWith(".webp") || lower.endsWith(".gif")
        ) {
            val newCard = CardItem(
                id = System.currentTimeMillis().toString(),
                title = "待发送图片",
                tag = "IMAGE",
                fileUri = actualPath
            )
            if (cardItems.none { it.fileUri == actualPath }) {
                cardItems.add(newCard)
                updateCardList(scroll = true)
                rvCardView.visibility = View.VISIBLE
            }
        } else {
            val fileExtension = actualPath.substringAfterLast(".", "")
            val fileName = File(actualPath).name.substringAfterLast("_").substringBefore(".")
            val newCard = CardItem(
                id = System.currentTimeMillis().toString(),
                title = fileName,
                tag = fileExtension,
                fileUri = actualPath
            )

            cardItems.add(newCard)
            updateCardList(scroll = true)
            rvCardView.visibility = View.VISIBLE
        }
    }

    private fun updateCardList(scroll: Boolean = false) {
        val displayList = mutableListOf<CardItem>()
        displayList.addAll(cardItems)
        if (cardItems.size < CardAdapter.MAX_CARD_ITEM) {
            displayList.add(
                CardItem(
                    id = "ADD",
                    title = "",
                    tag = "",
                    fileUri = "",
                    isAddItem = true
                )
            )
        }
        cardAdapter.submitList(displayList) {
            if (scroll && cardAdapter.itemCount > 0) {
                rvCardView.smoothScrollToPosition(cardAdapter.itemCount - 1)
            }
            updateButtonVisibility()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadConversation(conversationId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val conv = db.AiConversationDao().getById(conversationId)
            val msgs = db.AiMessageDao().getByConversationId(conversationId)

            val aiMessages = msgs.map { entity ->
                AiMessage(
                    message = entity.message,
                    timestamp = entity.timestamp,
                    isSent = entity.isSent,
                    type = entity.type,
                    path = entity.path
                )
            }
            withContext(Dispatchers.Main) {
                tvConversationTitle.text = conv.title
                tvConversationTitle.visibility = View.VISIBLE

                messageList.clear()
                messageList.addAll(aiMessages)
                messageAdapter.notifyDataSetChanged()
                rvMessages.scrollToPosition(messageList.size - 1)
            }
        }
    }

    private fun saveConversationToDb() {
        if (messageList.isEmpty()) return

        val convId = currentConversationId ?: UUID.randomUUID().toString()
        val title = tvConversationTitle.text.toString().ifBlank { "AI助手" }
        val conversationTimeMillis = messageList.first().timestamp
        val convEntity = AiConversationEntity(
            conversationId = convId,
            title = title,
            timestamp = conversationTimeMillis
        )

        val msgEntities = messageList.map { msg ->
            AiMessageEntity(
                conversationId = convEntity.conversationId,
                message = msg.message,
                timestamp = msg.timestamp,
                isSent = msg.isSent,
                type = msg.type,
                path = msg.path
            )
        }

        lifecycleScope.launch(Dispatchers.IO) {
            db.AiMessageDao().deleteByConversationId(convId)
            db.AiConversationDao().deleteById(convId)
            db.AiConversationDao().insert(convEntity)
            db.AiMessageDao().insertAll(msgEntities)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        streamDisposable?.dispose()
        voiceManager.release()
        NetworkManager.getInstance().dispose()
        realtimeAsrClient.shouldReconnect = false
        realtimeAsrClient.disconnect()
    }

    override fun onError(errorMessage: String) {
        ToastUtils.show(requireContext(), errorMessage)
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            ToastUtils.show(requireContext(), "需要相机权限才能拍照")
        }
    }
}
