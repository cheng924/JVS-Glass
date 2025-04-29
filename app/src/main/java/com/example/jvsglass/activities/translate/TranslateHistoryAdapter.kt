package com.example.jvsglass.activities.translate

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.database.TranslateHistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TranslateHistoryAdapter : RecyclerView.Adapter<TranslateHistoryAdapter.ViewHolder>() {
    private val items = mutableListOf<TranslateHistoryEntity>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    interface OnItemClickListener {
        fun onItemClick(session: TranslateHistoryEntity)
    }
    private var listener: OnItemClickListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.listener = listener
    }

    inner class ViewHolder(val tv: TextView) : RecyclerView.ViewHolder(tv) {
        init {
            tv.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    listener?.onItemClick(items[pos])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val tv = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_translation_history, parent, false) as TextView
        return ViewHolder(tv)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = items[position]
        if (session.type == 1)
            holder.tv.text = dateFormat.format(Date(session.timestamp))
        else
            holder.tv.text = "《${session.extra.substringBefore(".txt")}》"
    }

    override fun getItemCount() = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun setData(data: List<TranslateHistoryEntity>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }
}
