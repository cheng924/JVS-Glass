package com.example.jvsglass.activities.notification

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R

class NotificationAdapter(
    private var data: List<NotificationModel>,
    private val onClick: (NotificationModel) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tv_title)
        val tvSender: TextView = view.findViewById(R.id.tv_sender)
        val tvText: TextView = view.findViewById(R.id.tv_text)
        val container: LinearLayout = view.findViewById(R.id.container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = data.size

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = data[position]
        holder.tvTitle.text = "[消息来源：${item.appName}]"
        holder.tvSender.text = item.sender
        holder.tvText.text = "[${item.unreadCount}条] 点击查看详情信息"
        holder.container.setOnClickListener { onClick(item) }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newData: List<NotificationModel>) {
        data = newData
        notifyDataSetChanged()
    }
}