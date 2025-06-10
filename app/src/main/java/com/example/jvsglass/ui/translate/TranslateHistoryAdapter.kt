package com.example.jvsglass.ui.translate

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.database.TranslateHistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TranslateHistoryAdapter(
    private val onItemClick: (TranslateHistoryEntity) -> Unit,
    private val onSelectionCountChanged: (count: Int) -> Unit,
    private val onDeleteClick: (TranslateHistoryEntity) -> Unit
) : RecyclerView.Adapter<TranslateHistoryAdapter.ViewHolder>() {
    private var isSelectionMode = false
    private val selectedPositions = mutableSetOf<Int>()
    private val items = mutableListOf<TranslateHistoryEntity>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val clHistoryItemContainer: ConstraintLayout = view.findViewById(R.id.clHistoryItemContainer)
        val tvHistory: TextView = view.findViewById(R.id.tvHistoryItem)
        val ivSelectionDot: ImageView = view.findViewById(R.id.ivSelectionDot)
        val ivDelete: ImageView = view.findViewById(R.id.ivDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_translation_history, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = items[position]
        holder.tvHistory.text = if (session.type == 1) {
            dateFormat.format(Date(session.timestamp))
        } else {
            "《${session.extra.substringBefore(".txt")}》"
        }

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
            else onItemClick(session)
        }

        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) enterSelectionMode(position)
            true
        }

        holder.ivDelete.visibility = if (isSelectionMode) View.INVISIBLE else View.VISIBLE
        holder.ivDelete.setOnClickListener {
            onDeleteClick(session)
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

    override fun getItemCount() = items.size

    fun getSelectedItems(): List<TranslateHistoryEntity> =
        selectedPositions.map { items[it] }

    @SuppressLint("NotifyDataSetChanged")
    fun setData(data: List<TranslateHistoryEntity>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }
}
