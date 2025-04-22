package com.example.jvsglass.activities.translate

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.jvsglass.R
import com.example.jvsglass.dialog.LanguagePickerDialog
import com.example.jvsglass.dialog.WaitingDialog
import com.example.jvsglass.utils.FileHandler
import com.example.jvsglass.network.ChatRequest
import com.example.jvsglass.network.ChatResponse
import com.example.jvsglass.network.NetworkManager
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.ToastUtils
import com.example.jvsglass.utils.WarningDialogUtil

class TranslateFileActivity : AppCompatActivity(), FileHandler.FileReadResultCallback, FileHandler.FileWriteResultCallback {

    private lateinit var fileHandler: FileHandler
    private lateinit var tvTranslateTitle: TextView
    private lateinit var svTranslateContent: ScrollView
    private lateinit var tvTranslateContent: TextView
    private lateinit var tvLanguagePicker: TextView
    private lateinit var llImport: LinearLayout
    private lateinit var llExport: LinearLayout

    private var currentLanguage = "英语"
    var translateContent: String = ""

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate_file)

        fileHandler = FileHandler(this)
        setupUI()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun setupUI() {
        tvLanguagePicker = findViewById(R.id.tv_language_picker)
        llImport = findViewById(R.id.ll_import)
        llExport = findViewById(R.id.ll_export)
        tvTranslateTitle = findViewById(R.id.tv_translate_title)
        svTranslateContent = findViewById(R.id.sv_translate_content)
        tvTranslateContent = findViewById(R.id.tv_translate_content)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            finish()
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
        }

        tvLanguagePicker.setOnClickListener {
            showLanguagePickerDialog()
        }

        llImport.setOnClickListener {
            WarningDialogUtil.showDialog(
                context = this@TranslateFileActivity,
                title = "文件格式提示",
                message = "当前仅支持导入txt格式文本",
                positiveButtonText = "确定",
                negativeButtonText = "取消",
                listener = object : WarningDialogUtil.DialogButtonClickListener {
                    override fun onPositiveButtonClick() {
                        fileHandler.openFilePicker(this@TranslateFileActivity)
                    }

                    override fun onNegativeButtonClick() { }
            })
        }

        llExport.setOnClickListener {
            if (translateContent.isEmpty()) {
                ToastUtils.show(this@TranslateFileActivity, "无文本导出")
                return@setOnClickListener
            }
            fileHandler.saveFile(tvTranslateContent.text.toString(), translateContent, this@TranslateFileActivity)
        }
    }

    override fun onReadSuccess(name: String, content: String) {
        sendTextToTranslateModel(name.substringBefore(".txt"), content)
    }

    override fun onReadFailure(errorMessage: String) {
        LogUtils.error("错误: $errorMessage")
    }

    override fun onWriteSuccess(savedUri: Uri) {
        LogUtils.info("导出成功：$savedUri")
    }

    override fun onWriteFailure(errorMessage: String) {
        LogUtils.error("导出失败，错误信息：$errorMessage")
    }

    private fun sendTextToTranslateModel(textName: String, textContent: String) {
        val waitingDialog = WaitingDialog.show(this@TranslateFileActivity)
        waitingDialog.setMessage("翻译中，请稍候...")

        val chatMessages = mutableListOf<ChatRequest.Message>()
        val userMsg = ChatRequest.Message(
            role = "user",
            content = getTranslationInstruction(textName, textContent, tvLanguagePicker.text.toString())
        )
        chatMessages.add(userMsg)

        NetworkManager.getInstance().uploadFileTextCompletion(
            messages = chatMessages,
            temperature = 0.7,
            object : NetworkManager.ModelCallback<ChatResponse> {
                override fun onSuccess(result: ChatResponse) {
                    waitingDialog.dismiss()
                    result.choices.firstOrNull()?.let { choice ->
                        val aiContent = choice.message.content
                        chatMessages.add(
                            ChatRequest.Message(
                            role = choice.message.role,
                            content = aiContent
                        ))
                        LogUtils.info(aiContent)
                        val filename = extractFilename(aiContent)
                        tvTranslateTitle.text = filename.uppercase()
                        translateContent = extractFileContent(filename, aiContent)
                        val combinedText = combineBilingualText(textContent, translateContent)
                        LogUtils.info(combinedText)
                        tvTranslateContent.text = combinedText.trim()
                    }
                }

                override fun onFailure(error: Throwable) {
                    waitingDialog.dismiss()
                    LogUtils.error("请求失败: ${error.message?.substringBefore("\n") ?: "未知错误"}")
                }
            })
    }

    fun extractFilename(translated: String): String {
        return translated.substringBefore('\n').trim()
    }

    fun extractFileContent(filename: String, translated: String): String {
        return translated.substringAfter(filename)
    }

    fun combineBilingualText(original: String, translated: String): String {
        // 用一个或多个换行符作为分隔符
        val paraRegex = Regex("""\r?\n+""")

        // 拆分、去首尾空白并过滤掉完全空的段
        val origParas = original
            .split(paraRegex)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val transParas = translated
            .split(paraRegex)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // 按最大段数对齐，多出的部分用空串补齐
        val maxCount = maxOf(origParas.size, transParas.size)

        return buildString {
            for (i in 0 until maxCount) {
                val o = origParas.getOrElse(i) { "" }
                val t = transParas.getOrElse(i) { "" }

                // 原文段
                append(o)
                // 如果有译文段，加一行再写译文
                if (t.isNotEmpty()) {
                    append("\n")
                    append(t)
                }
                // 段与段之间空两行
                if (i < maxCount - 1) append("\n\n")
            }
        }
    }

    private fun getTranslationInstruction(filename: String, content: String, targetLanguage: String): String {
        return """
            $filename
            $content
            将以上文件名及内容都翻译成${targetLanguage}，无需出现原文，以两个换行符分段。
            请严格按照原文段落格式输出，不要改变句式及段落结构。
        """.trimIndent()
    }

    private fun showLanguagePickerDialog() {
        val dialog = LanguagePickerDialog(
            context = this,
            currentLanguage = currentLanguage,
            onConfirm = { selectedLanguage ->
                currentLanguage = selectedLanguage
                tvLanguagePicker.text = selectedLanguage
                tvTranslateTitle.text = ""
                tvTranslateContent.text = ""
            }
        )
        dialog.show()
    }
}