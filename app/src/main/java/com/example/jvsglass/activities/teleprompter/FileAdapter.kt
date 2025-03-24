package com.example.jvsglass.activities.teleprompter

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R

class FileAdapter : ListAdapter<FileItem, FileAdapter.ViewHolder>(FileItemDiffCallback()) {
    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<Int>()
    val selectedItem: Set<Int>
        get() = this.selectedItems.toSet()

    interface OnSelectionModeChangeListener {
        fun onSelectionModeChanged(isActive: Boolean)
        fun onSelectedItemsChanged(selectedCount: Int)
    }

    var onSelectionModeChangeListener: OnSelectionModeChangeListener? = null

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileName: TextView = view.findViewById(R.id.tvFileName)
        val fileDate: TextView = view.findViewById(R.id.tvDate)
        val ivSelectionDot: ImageView = view.findViewById(R.id.ivSelectionDot)
        val ivRightIcon: ImageView = view.findViewById(R.id.ivRightIcon)
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

        val isSelected = selectedItems.contains(position)
        holder.ivSelectionDot.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        holder.ivSelectionDot.setImageResource(
            if (isSelected) R.drawable.ic_circle_selected
            else R.drawable.ic_circle_unselected
        )
        holder.ivRightIcon.visibility = if (isSelectionMode) View.GONE else View.VISIBLE

        // 点击事件处理
        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(position)
            } else {
                openDetailActivity(context, item)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                enterSelectionMode(position)
                true
            } else {
                false
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(currentList.indices)
        notifyDataSetChanged()
        onSelectionModeChangeListener?.onSelectedItemsChanged(selectedItems.size)
    }

    private fun toggleSelection(position: Int) {
        if (selectedItems.contains(position)) {
            selectedItems.remove(position)
            if (selectedItems.isEmpty()) exitSelectionMode()
        } else {
            selectedItems.add(position)
        }
        notifyItemChanged(position)
        onSelectionModeChangeListener?.onSelectedItemsChanged(selectedItems.size)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun enterSelectionMode(position: Int) {
        isSelectionMode = true
        selectedItems.add(position)
        notifyDataSetChanged()
        onSelectionModeChangeListener?.onSelectionModeChanged(true)
        onSelectionModeChangeListener?.onSelectedItemsChanged(1)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionModeChangeListener?.onSelectionModeChanged(false)
    }

    private fun openDetailActivity(context: android.content.Context, item: FileItem) {
        Intent(context, TeleprompterDisplayActivity::class.java).apply {
            putExtra("fileName", item.fileName)
            putExtra("fileDate", item.fileDate)
            putExtra("fileContent", item.fileContent)
            context.startActivity(this)
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