package com.example.jvsglass.activities.jvsai

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.jvsglass.R
import com.example.jvsglass.utils.ToastUtils

class CardAdapter(
    private val onCardAdapterListener: OnCardAdapterListener?
) : ListAdapter<CardItem, RecyclerView.ViewHolder>(CardItemDiffCallback) {

    companion object {
        private const val TYPE_CARD = 0
        private const val TYPE_ADD = 1
        const val MAX_CARD_ITEM = 9 // 最大卡片数

        private val CardItemDiffCallback = object : DiffUtil.ItemCallback<CardItem>() {
            override fun areItemsTheSame(oldItem: CardItem, newItem: CardItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CardItem, newItem: CardItem) =
                oldItem == newItem
        }
    }

    interface OnCardAdapterListener {
        fun onAddCardClicked(position: Int)
        fun onDeleteCard(position: Int)
    }

    inner class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tv_Title)
        val tvTag: TextView = view.findViewById(R.id.tv_Tag)
        val ivDelete: ImageView = view.findViewById(R.id.iv_delete)

        val rlCardFile: RelativeLayout = view.findViewById(R.id.rl_card_file)
        val ivCardImage: ImageView = view.findViewById(R.id.iv_card_image)
    }

    inner class AddViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemCount(): Int {
        return currentList.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isAddItem) TYPE_ADD else TYPE_CARD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_CARD) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_card, parent, false)
            CardViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_card_add, parent, false)
            AddViewHolder(view)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (!item.isAddItem) {
            val cardHolder = holder as CardViewHolder
            if (item.tag == "IMAGE") {
                cardHolder.rlCardFile.visibility = View.GONE
                cardHolder.ivCardImage.visibility = View.VISIBLE
                Glide.with(holder.itemView)
                    .load(item.fileUri)
                    .into(cardHolder.ivCardImage)
            } else {
                cardHolder.rlCardFile.visibility = View.VISIBLE
                cardHolder.ivCardImage.visibility = View.GONE
                cardHolder.tvTitle.text = item.title
                cardHolder.tvTag.text = item.tag
            }

            cardHolder.ivDelete.setOnClickListener {
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    onCardAdapterListener?.onDeleteCard(currentPosition)
                }
            }

            cardHolder.ivCardImage.setOnClickListener {
                Intent(holder.itemView.context, FullScreenImageActivity::class.java).apply {
                    putExtra("image_uri", item.fileUri)
                    holder.itemView.context.startActivity(this)
                }
            }

            cardHolder.rlCardFile.setOnClickListener {
                if (item.tag == "txt") {
                    Intent(holder.itemView.context, FileViewerActivity::class.java).apply {
                        putExtra("file_path", item.fileUri)
                        holder.itemView.context.startActivity(this)
                    }
                } else {
                    ToastUtils.show(holder.itemView.context, "没有找到可打开此文件的应用")
                }
            }
        } else {
            val addHolder = holder as AddViewHolder
            addHolder.itemView.setOnClickListener {
                onCardAdapterListener?.onAddCardClicked(position)
            }
        }
    }
}