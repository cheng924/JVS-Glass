package com.example.jvsglass.ui.ai

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.database.AiConversationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiHistoryAdapter(
    private var data: List<AiConversationEntity>,
    private val onItemClick: (AiConversationEntity) -> Unit,
    private val onSelectionCountChanged: (count: Int) -> Unit,
    private val onDeleteClick: (AiConversationEntity) -> Unit
) : RecyclerView.Adapter<AiHistoryAdapter.VH>() {
    private var isSelectionMode = false
    private val selectedPositions = mutableSetOf<Int>()

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val clHistoryItemContainer: ConstraintLayout = itemView.findViewById(R.id.clHistoryItemContainer)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvTime : TextView = itemView.findViewById(R.id.tvTime)
        val ivSelectionDot: ImageView = itemView.findViewById(R.id.ivSelectionDot)
        val ivDelete: ImageView = itemView.findViewById(R.id.ivDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation_preview, parent, false)
        return VH(v)
    }

    override fun getItemCount() = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = data[position]
        holder.tvTitle.text = item.title
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
        holder.tvTime.text = sdf.format(Date(item.timestamp))

        val isSelected = selectedPositions.contains(position)
        holder.clHistoryItemContainer.setBackgroundResource(
            if (isSelected) R.drawable.rounded_button_selected
            else R.drawable.rounded_button
        )
        holder.ivSelectionDot.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        holder.ivSelectionDot.setImageResource(
            if (isSelected) R.drawable.ic_circle_selected
            else R.drawable.ic_circle_unselected
        )

        holder.itemView.setOnClickListener {
            if (isSelectionMode) toggleSelection(position)
            else onItemClick(item)
        }

        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                enterSelectionMode(position)
            }
            true
        }

        holder.ivDelete.visibility = if (isSelectionMode) View.INVISIBLE else View.VISIBLE
        holder.ivDelete.setOnClickListener {
            onDeleteClick(item)
        }
    }

    private fun toggleSelection(position: Int) {
        if (!isSelectionMode) return
        if (selectedPositions.contains(position)) selectedPositions.remove(position)
        else selectedPositions.add(position)
        notifyItemChanged(position)
        onSelectionCountChanged(selectedPositions.size)
        if (selectedPositions.isEmpty()) exitSelectionMode()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun enterSelectionMode(position: Int) {
        isSelectionMode = true
        selectedPositions.add(position)
        notifyDataSetChanged()
        onSelectionCountChanged(selectedPositions.size)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedPositions.clear()
        notifyDataSetChanged()
        onSelectionCountChanged(0)
    }

    fun getSelectedConversations(): List<AiConversationEntity> =
        selectedPositions.map { data[it] }

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newData: List<AiConversationEntity>) {
        data = newData
        exitSelectionMode()
    }
}

