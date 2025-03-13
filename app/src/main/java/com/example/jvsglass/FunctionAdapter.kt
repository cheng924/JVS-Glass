package com.example.jvsglass

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.activities.TelepromptActivity
import com.example.jvsglass.utils.ToastUtils

class FunctionAdapter(private val items: List<FunctionItem>) :
    RecyclerView.Adapter<FunctionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val title: TextView = view.findViewById(R.id.tvFunction)
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
        holder.title.text = item.title

        // 点击事件处理
        holder.itemView.setOnClickListener {
            when (item.targetActivity) {
                TelepromptActivity::class.java -> {
                    // Teleprompt 正常跳转
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