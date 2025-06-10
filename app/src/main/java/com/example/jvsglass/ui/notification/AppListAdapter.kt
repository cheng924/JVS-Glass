package com.example.jvsglass.ui.notification

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R

class AppListAdapter(
    private val appItems: List<AppItem>
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAppIcon: ImageView = view.findViewById(R.id.iv_app_icon)
        val tvAppName: TextView = view.findViewById(R.id.tv_app_name)
        @SuppressLint("UseSwitchCompatOrMaterialCode")
        val switchEnabled: Switch = view.findViewById(R.id.switch_enabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = appItems.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = appItems[position]
        holder.tvAppIcon.setImageDrawable(item.appIcon)
        holder.tvAppName.text = item.appName
        holder.switchEnabled.isChecked = item.isEnabled
        holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            item.isEnabled = isChecked
        }
    }
}