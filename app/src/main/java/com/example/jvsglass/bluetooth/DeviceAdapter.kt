package com.example.jvsglass.bluetooth

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R

class DeviceAdapter : ListAdapter<DeviceItem, DeviceAdapter.ViewHolder>(DeviceItemDiffCallback()) {
    var onItemClick: ((Int) -> Unit)? = null

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deviceName: TextView = view.findViewById(R.id.tvDeviceName)
        val deviceIcon: ImageView = view.findViewById(R.id.ivDeviceIcon)

        init {
            itemView.setOnClickListener {
                onItemClick?.invoke(adapterPosition)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.deviceName.text = item.deviceName
        when (item.deviceType) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> holder.deviceIcon.setBackgroundResource(R.drawable.ic_bt1)
            BluetoothDevice.DEVICE_TYPE_LE -> holder.deviceIcon.setBackgroundResource(R.drawable.ic_bt2)
            BluetoothDevice.DEVICE_TYPE_DUAL -> holder.deviceIcon.setBackgroundResource(R.drawable.ic_bt3)
            BluetoothDevice.DEVICE_TYPE_UNKNOWN -> holder.deviceIcon.setBackgroundResource(R.drawable.ic_bt4)
            else -> holder.deviceIcon.setBackgroundResource(R.drawable.ic_bt4)
        }
    }

    class DeviceItemDiffCallback : DiffUtil.ItemCallback<DeviceItem>() {
        override fun areItemsTheSame(oldItem: DeviceItem, newItem: DeviceItem): Boolean {
            return oldItem.deviceName == newItem.deviceName
        }

        override fun areContentsTheSame(oldItem: DeviceItem, newItem: DeviceItem): Boolean {
            return oldItem == newItem
        }
    }
}