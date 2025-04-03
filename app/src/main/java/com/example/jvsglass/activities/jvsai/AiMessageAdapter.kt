package com.example.jvsglass.activities.jvsai

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.jvsglass.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.core.net.toUri
import com.example.jvsglass.utils.TextFormatter
import com.example.jvsglass.utils.ToastUtils
import java.io.File
import java.util.Locale

class AiMessageAdapter(private val messages: MutableList<AiMessage>) :
    RecyclerView.Adapter<AiMessageAdapter.ViewHolder>() {

    var onVoiceItemClickListener: OnVoiceItemClickListener? = null
    var isPlayingCheck: (String) -> Boolean = { false }

    interface OnVoiceItemClickListener {
        fun onVoiceItemClick(filePath: String, position: Int)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val llMessageLayout: LinearLayout = view.findViewById(R.id.llMessageLayout)
        val messageText: TextView = view.findViewById(R.id.tvMessage)
        val ivVoiceIcon: ImageView = view.findViewById(R.id.ivVoiceIcon)
        val ivImage: ImageView = view.findViewById(R.id.ivImage)
        val llFile: LinearLayout = view.findViewById(R.id.llFile)
        val tvFileName: TextView = view.findViewById(R.id.tvFileName)

        val messageDate: TextView = view.findViewById(R.id.tvTimeSent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val fileDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
        holder.messageDate.text = fileDate

        val message = messages[position]
        holder.messageText.text = message.message
        val params = holder.llMessageLayout.layoutParams as ViewGroup.MarginLayoutParams

        when (message.type) {
            AiMessage.TYPE_TEXT -> handleTextMessage(holder, message, params)
            AiMessage.TYPE_VOICE -> handleVoiceMessage(holder, message, params)
            AiMessage.TYPE_IMAGE -> handleImageMessage(holder, message, params)
            AiMessage.TYPE_FILE -> handleFileMessage(holder, message, params)
        }

        holder.llMessageLayout.layoutParams = params

        setupCommonClickListeners(holder, message, position)
    }

    override fun getItemCount() = messages.size

    private fun dpToPx(dp: Float, context: Context): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun handleTextMessage(holder: ViewHolder, message: AiMessage, params: ViewGroup.MarginLayoutParams) {
        // 控件可见性设置
        holder.messageText.visibility = View.VISIBLE
        holder.ivVoiceIcon.visibility = View.GONE
        holder.ivImage.visibility = View.GONE
        holder.llFile.visibility = View.GONE

        // 布局样式配置
        setupMessageLayoutAppearance(holder, message, params)

        // 文本消息特有设置
        val displayMetrics = holder.itemView.context.resources.displayMetrics
        val maxWidth = (displayMetrics.widthPixels * 0.75).toInt()
        holder.messageText.maxWidth = maxWidth
    }

    private fun handleVoiceMessage(holder: ViewHolder, message: AiMessage, params: ViewGroup.MarginLayoutParams) {
        // 控件可见性设置
        holder.messageText.visibility = View.GONE
        holder.ivVoiceIcon.visibility = View.VISIBLE
        holder.ivImage.visibility = View.GONE
        holder.llFile.visibility = View.GONE

        // 布局样式配置
        setupMessageLayoutAppearance(holder, message, params)

        // 动态语音条宽度设置
        val voiceDuration = message.duration // 单位：秒
        val voiceParams = holder.ivVoiceIcon.layoutParams
        voiceParams.width = when {
            voiceDuration >= 60 -> dpToPx(300f, holder.itemView.context)
            voiceDuration <= 30 -> dpToPx(150f, holder.itemView.context)
            else -> {
                val scale = (voiceDuration - 30) / 30f
                dpToPx(150 + (150 * scale), holder.itemView.context)
            }
        }
        holder.ivVoiceIcon.layoutParams = voiceParams
    }

    private fun handleImageMessage(holder: ViewHolder, message: AiMessage, params: ViewGroup.MarginLayoutParams) {
        // 控件可见性设置
        holder.messageText.visibility = View.GONE
        holder.ivVoiceIcon.visibility = View.GONE
        holder.ivImage.visibility = View.VISIBLE
        holder.llFile.visibility = View.GONE

        // 布局样式配置
        setupMessageLayoutAppearance(holder, message, params)

        // 图片加载与点击事件
        Glide.with(holder.itemView.context)
            .load(message.path.toUri())
            .into(holder.ivImage)
    }

    private fun handleFileMessage(
        holder: ViewHolder,
        message: AiMessage,
        params: ViewGroup.MarginLayoutParams
    ) {
        holder.messageText.visibility = View.GONE
        holder.ivVoiceIcon.visibility = View.GONE
        holder.ivImage.visibility = View.GONE
        holder.llFile.visibility = View.VISIBLE

        // 文件名显示
        holder.tvFileName.text = TextFormatter.formatFileName(
            message.path.substringAfterLast('/').substringAfter("FILE_")
        )

        // 布局样式配置
        setupMessageLayoutAppearance(holder, message, params)
    }

    private fun setupMessageLayoutAppearance(holder: ViewHolder, message: AiMessage, params: ViewGroup.MarginLayoutParams) {
        val isSent = message.isSent
        val context = holder.itemView.context

        // 背景设置
        holder.llMessageLayout.setBackgroundResource(
            if (isSent) R.drawable.bg_sent_message else R.drawable.bg_received_message
        )

        // 边距设置
        params.marginStart = if (isSent) dpToPx(100f, context) else dpToPx(8f, context)
        params.marginEnd = if (isSent) dpToPx(8f, context) else dpToPx(100f, context)

        // 布局对齐方式
        (holder.llMessageLayout.layoutParams as FrameLayout.LayoutParams).gravity =
            if (isSent) Gravity.END else Gravity.START
    }

    private fun setupCommonClickListeners(holder: ViewHolder, message: AiMessage, position: Int) {
        // 语音点击事件
        holder.ivVoiceIcon.setOnClickListener {
            onVoiceItemClickListener?.onVoiceItemClick(message.path, position)
        }

        // 图片点击事件
        holder.ivImage.setOnClickListener {
            Intent(holder.itemView.context, FullScreenImageActivity::class.java).apply {
                putExtra("image_uri", message.path)
                holder.itemView.context.startActivity(this)
            }
        }

        // 文件点击事件
        holder.llMessageLayout.setOnClickListener {
            if (message.isSent && message.type == AiMessage.TYPE_FILE) {
                openFile(message.path, holder.itemView.context)
            }
        }
    }

    private fun openFile(path: String, context: Context) {
        val file = File(path)
        when (val mimeType = getMimeType(file)) {
            "text/plain" -> {
                // 启动自定义文件查看器
                val intent = Intent(context, FileViewerActivity::class.java).apply {
                    putExtra("file_path", path)
                }
                context.startActivity(intent)
            }
            else -> {
                // 其他文件类型保持原有逻辑
//                val uri = FileProvider.getUriForFile(
//                    context,
//                    "${context.packageName}.provider",
//                    file
//                )
//                val intent = Intent(Intent.ACTION_VIEW).apply {
//                    setDataAndType(uri, mimeType)
//                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                }
//                try {
//                    context.startActivity(intent)
//                } catch (e: ActivityNotFoundException) {
                    ToastUtils.show(context, "没有找到可打开此文件的应用")
//                }
            }
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.toLowerCase(Locale.ROOT)) {
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "*/*"
        }
    }
}