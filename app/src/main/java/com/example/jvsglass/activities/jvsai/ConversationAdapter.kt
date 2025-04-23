package com.example.jvsglass.activities.jvsai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.database.AiConversationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val data: List<AiConversationEntity>,
    private val onClick: (AiConversationEntity) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvTime : TextView = itemView.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation_preview, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = data[position]
        holder.tvTitle.text = item.title
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        holder.tvTime.text = sdf.format(Date(item.timestamp))
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = data.size
}