package com.example.jvsglass.activities.jvsai

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
        val messageDate: TextView = view.findViewById(R.id.tvTimeSent)

        val ivVoiceIcon: ImageView = view.findViewById(R.id.ivVoiceIcon)
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
            AiMessage.TYPE_TEXT -> {
                holder.messageText.visibility = View.VISIBLE
                holder.ivVoiceIcon.visibility = View.GONE
                if (message.isSent) {
                    // 发送消息样式
                    holder.llMessageLayout.setBackgroundResource(R.drawable.bg_sent_message)
                    params.marginStart = dpToPx(100f, holder.itemView.context)
                    params.marginEnd = dpToPx(8f, holder.itemView.context)
                    (holder.llMessageLayout.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.END
                } else {
                    // 接收消息样式
                    holder.llMessageLayout.setBackgroundResource(R.drawable.bg_received_message)
                    params.marginStart = dpToPx(8f, holder.itemView.context)
                    params.marginEnd = dpToPx(100f, holder.itemView.context)
                    (holder.llMessageLayout.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.START
                }
                val displayMetrics = holder.itemView.context.resources.displayMetrics
                val maxWidth = (displayMetrics.widthPixels * 0.75).toInt()
                holder.messageText.maxWidth = maxWidth
            }

            AiMessage.TYPE_VOICE -> {
                holder.messageText.visibility = View.GONE
                holder.ivVoiceIcon.visibility = View.VISIBLE
                holder.llMessageLayout.setBackgroundResource(R.drawable.bg_sent_message)
                params.marginStart = dpToPx(100f, holder.itemView.context)
                params.marginEnd = dpToPx(8f, holder.itemView.context)
                (holder.llMessageLayout.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.END

                // 设置动态宽度
                val voiceDuration = message.duration // 单位：秒
                val voiceParams = holder.ivVoiceIcon.layoutParams
                when {
                    voiceDuration >= 60 -> {
                        // 1分钟及以上，固定300dp
                        voiceParams.width = dpToPx(300f, holder.itemView.context)
                    }
                    voiceDuration <= 30 -> {
                        // 小于等于30秒，固定150dp
                        voiceParams.width = dpToPx(150f, holder.itemView.context)
                    }
                    else -> {
                        // 30秒到60秒之间，等比缩放，从150dp到300dp线性插值
                        val scale = (voiceDuration - 30) / 30f // 比例因子 (0到1之间)
                        val widthDp = 150 + (150 * scale) // 150dp到300dp之间
                        voiceParams.width = dpToPx(widthDp, holder.itemView.context)
                    }
                }
                holder.ivVoiceIcon.layoutParams = voiceParams
            }
        }

        holder.llMessageLayout.layoutParams = params

        holder.ivVoiceIcon.setOnClickListener {
            val filePath = message.path
            onVoiceItemClickListener?.onVoiceItemClick(filePath, position)
        }
    }

    override fun getItemCount() = messages.size

    private fun dpToPx(dp: Float, context: android.content.Context): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}