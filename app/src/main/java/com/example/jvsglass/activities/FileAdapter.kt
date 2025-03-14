package com.example.jvsglass.activities

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.utils.ToastUtils
import java.text.SimpleDateFormat
import java.util.Locale

class FileAdapter(private val items: List<FileItem>) :
    RecyclerView.Adapter<FileAdapter.ViewHolder>() {

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
        val item = items[position]
        val context = holder.itemView.context

        // 设置图标和文本
        holder.fileName.text = item.fileName
        holder.fileDate.text = item.fileDate

        // 点击事件处理
        holder.itemView.setOnClickListener {
//            ToastUtils.show(context, "已选中文本："+item.fileName)
            Intent(context, TextDisplayActivity::class.java).apply {
                putExtra("filename", item.fileName)
                putExtra("filedate", item.fileDate)
                putExtra("filecontent", item.fileContent)
                context.startActivity(this)
            }
        }

//        val file = files[position]
//        holder.fileName.text = file.name
//        holder.fileDate.text = SimpleDateFormat("yyyy/M/d HH:mm", Locale.getDefault())
//            .format(file.date)
    }

    override fun getItemCount() = items.size
}