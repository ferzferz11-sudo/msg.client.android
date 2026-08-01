package lavender.client.android.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.data.proto.DeviceInfoProto
import lavender.client.android.data.proto.ProtoUtils
import lavender.client.android.theme.ThemeStore
import androidx.core.graphics.toColorInt

class DeviceAdapter(
    private val currentDeviceId: String,
    private val onItemClick: (DeviceInfoProto) -> Unit,
    private val onDeleteClick: (DeviceInfoProto) -> Unit
) : ListAdapter<DeviceInfoProto, DeviceAdapter.ViewHolder>(DeviceDiffCallback()) {

    fun setDevices(newDevices: List<DeviceInfoProto>) {
        submitList(newDevices)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<DeviceInfoProto>() {
        override fun areItemsTheSame(oldItem: DeviceInfoProto, newItem: DeviceInfoProto): Boolean =
            oldItem.deviceId == newItem.deviceId
        override fun areContentsTheSame(oldItem: DeviceInfoProto, newItem: DeviceInfoProto): Boolean =
            oldItem == newItem
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.deviceIcon)
        private val name: TextView = view.findViewById(R.id.deviceName)
        private val info: TextView = view.findViewById(R.id.deviceInfo)
        private val action: ImageView = view.findViewById(R.id.deviceAction)

        fun bind(device: DeviceInfoProto) {
            val context = itemView.context
            val theme = ThemeStore.currentTheme()
            val primaryColor = theme.primaryColor.toColorInt()
            val textColor = theme.textPrimaryColor.toColorInt()
            val secondaryColor = theme.textSecondaryColor.toColorInt()

            name.text = device.deviceName
            name.setTextColor(textColor)

            val isCurrent = device.deviceId == currentDeviceId
            
            val lastSeen = ProtoUtils.formatLastSeen(device.lastSeenAt, context)
            info.text = if (isCurrent) context.getString(R.string.this_device) else lastSeen
            info.setTextColor(secondaryColor)

            icon.imageTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            action.imageTintList = android.content.res.ColorStateList.valueOf(if (isCurrent) secondaryColor else "#FF5252".toColorInt())
            
            action.visibility = if (isCurrent) View.GONE else View.VISIBLE
            action.setOnClickListener { onDeleteClick(device) }
            
            itemView.setOnClickListener { onItemClick(device) }
        }
    }
}
