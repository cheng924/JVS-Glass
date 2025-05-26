package com.example.jvsglass

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.activities.ai.JVSAIActivity
import com.example.jvsglass.activities.notification.NotificationActivity
import com.example.jvsglass.activities.teleprompter.TeleprompterActivity
import com.example.jvsglass.activities.translate.TranslateActivity
import com.example.jvsglass.utils.ToastUtils

class FunctionAdapter(private val items: List<FunctionItem>) :
    RecyclerView.Adapter<FunctionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_icon)
        val function: TextView = view.findViewById(R.id.tv_function)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_function, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        // 设置图标和文本
        holder.icon.setImageResource(item.iconRes)
        holder.function.text = item.title

        // 点击事件处理
        holder.itemView.setOnClickListener {
            when (item.targetActivity) {
                TeleprompterActivity::class.java -> {
                    context.startActivity(Intent(context, item.targetActivity))
                }
                JVSAIActivity::class.java -> {
                    context.startActivity(Intent(context, item.targetActivity))
                }
                TranslateActivity::class.java -> {
                    context.startActivity(Intent(context, item.targetActivity))
                }
                NotificationActivity::class.java -> {
                    context.startActivity(Intent(context, item.targetActivity))
                }
                else -> {
                    // 其他功能
                    ToastUtils.show(context, context.getString(R.string.development_tips))
                }
            }
        }
    }

    override fun getItemCount() = items.size
}