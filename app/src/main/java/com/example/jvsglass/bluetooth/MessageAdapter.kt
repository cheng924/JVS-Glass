package com.example.jvsglass.bluetooth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R

class MessageAdapter(
    private val messages: List<MessageItem>,
    private val onPlayClick: (String) -> Unit
) : RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.textView)
        val playButton: Button = view.findViewById(R.id.playButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val messageItem = messages[position]
        holder.textView.text = messageItem.text
        if (messageItem.voiceFilePath != null) {
            holder.playButton.visibility = View.VISIBLE
            holder.playButton.setOnClickListener {
                onPlayClick(messageItem.voiceFilePath)
            }
        } else {
            holder.playButton.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = messages.size
}