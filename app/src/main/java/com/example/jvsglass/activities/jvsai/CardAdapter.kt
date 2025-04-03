package com.example.jvsglass.activities.jvsai

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.utils.LogUtils

class CardAdapter(
    private val onCardAdapterListener: OnCardAdapterListener?
) : ListAdapter<CardItem, CardAdapter.ViewHolder>(CardItemDiffCallback) {

    interface OnCardAdapterListener {
        fun onAddCardClicked(position: Int)
        fun onDeleteCard(position: Int)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tv_Title)
        val tvTag: TextView = view.findViewById(R.id.tv_Tag)
        val ivDelete: ImageView = view.findViewById(R.id.iv_delete)
        val ivCardAdd: ImageView = view.findViewById(R.id.iv_card_add)

        val llCardFile: LinearLayout = view.findViewById(R.id.ll_card_file)
        val ivCardImage: ImageView = view.findViewById(R.id.iv_card_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_card, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        if (item.tag == "IMAGE") {
            holder.llCardFile.visibility = View.GONE
            holder.ivCardImage.visibility = View.VISIBLE
        } else {
            holder.llCardFile.visibility = View.VISIBLE
            holder.ivCardImage.visibility = View.GONE

            holder.tvTitle.text = item.title
            holder.tvTag.text = item.tag
        }

        holder.ivCardAdd.visibility = if (
            position == currentList.lastIndex && currentList.size < 9
        ) View.VISIBLE else View.GONE

        holder.ivCardAdd.setOnClickListener {
            onCardAdapterListener?.onAddCardClicked(position)
        }

        holder.ivDelete.setOnClickListener {
            val currentPosition = holder.bindingAdapterPosition // 获取动态索引
            if (currentPosition != RecyclerView.NO_POSITION) {
                val newList = currentList.toMutableList().apply {
                    removeAt(currentPosition) // 使用动态位置
                }
                submitList(newList) {
                    LogUtils.info("删除成功")
                }
            }
            onCardAdapterListener?.onDeleteCard(currentPosition)
        }
    }

    companion object {
        private val CardItemDiffCallback = object : DiffUtil.ItemCallback<CardItem>() {
            override fun areItemsTheSame(oldItem: CardItem, newItem: CardItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CardItem, newItem: CardItem) =
                oldItem == newItem
        }
    }
}