package com.example.jvsglass.activities.teleprompter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R

class FileAdapter : ListAdapter<FileItem, FileAdapter.ViewHolder>(FileItemDiffCallback()) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileName: TextView = view.findViewById(R.id.tvFileName)
        val fileDate: TextView = view.findViewById(R.id.tvDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        // 设置图标和文本
        holder.fileName.text = item.fileName
        holder.fileDate.text = item.fileDate

        // 点击事件处理
        holder.itemView.setOnClickListener {
            Intent(context, TeleprompterDisplayActivity::class.java).apply {
                putExtra("fileName", item.fileName)
                putExtra("fileDate", item.fileDate)
                putExtra("fileContent", item.fileContent)
                context.startActivity(this)
            }
        }
    }

    class FileItemDiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.fileName == newItem.fileName
        }

        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem == newItem
        }
    }
}