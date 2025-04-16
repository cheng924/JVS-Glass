package com.example.jvsglass.activities.translate

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R

class TranslationAdapter(
    private val context: Context,
    private var items: MutableList<TranslationResult>,
    private var displayMode: Int
) : RecyclerView.Adapter<TranslationAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSource: TextView = itemView.findViewById(R.id.tv_source)
        val tvTarget: TextView = itemView.findViewById(R.id.tv_target)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_translation_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        when (displayMode) {
            0 -> { // 显示两者
                holder.tvSource.visibility = View.VISIBLE
                holder.tvTarget.visibility = View.VISIBLE
            }
            1 -> { // 仅显示源
                holder.tvSource.visibility = View.VISIBLE
                holder.tvTarget.visibility = View.GONE
            }
            2 -> { // 仅显示目标
                holder.tvSource.visibility = View.GONE
                holder.tvTarget.visibility = View.VISIBLE
            }
        }

        holder.tvSource.text = item.sourceText
        holder.tvTarget.text = item.targetText
    }

    fun updatePartialResult(source: String, target: String) {
        if (items.isNotEmpty() && items.last().isPartial) {
            items[items.lastIndex] = TranslationResult(source, target, isPartial = true)
        } else {
            items.add(TranslationResult(source, target, isPartial = true))
        }
        notifyItemChanged(items.lastIndex)
    }

    override fun getItemCount() = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateDisplayMode(newMode: Int) {
        displayMode = newMode
        notifyDataSetChanged()
    }

    fun addItem(item: TranslationResult) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }
}