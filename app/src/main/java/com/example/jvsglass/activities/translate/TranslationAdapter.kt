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

    /**
     * 将传入的 source 与 target 累积更新到当前组中，
     * 同时判断如果两侧都以标点结尾则认为该组完成（isPartial 置为 false），
     * 否则保持临时状态（isPartial 为 true）。
     *
     * 当最后一项已非临时时，说明上一组已完成，新来的更新会开启新组显示。
     */
    fun updatePartialPair(source: String, target: String) {
        // 判断是否同时以标点结尾
        val finished = endsWithPunctuation(source) && endsWithPunctuation(target)
        if (items.isNotEmpty() && items.last().isPartial) {
            items[items.lastIndex] = TranslationResult(source, target, isPartial = !finished)
            notifyItemChanged(items.lastIndex)
        } else {
            items.add(TranslationResult(source, target, isPartial = !finished))
            notifyItemInserted(items.lastIndex)
        }
    }

    // 简单判断字符串最后一个字符是否为常见标点符号
    private fun endsWithPunctuation(text: String): Boolean {
        if (text.isEmpty()) return false
        val punctuations = listOf('.', '。', ',', '，', '!', '！', '?', '？')
        return punctuations.contains(text.last())
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