package lavender.client.android.ui.hermes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R

data class HermesCommand(
    val command: String,
    val description: String
)

class HermesCommandAdapter(
    private val commands: List<HermesCommand>,
    private val onCommandClick: (HermesCommand) -> Unit
) : RecyclerView.Adapter<HermesCommandAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hermes_command, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(commands[position])
    }

    override fun getItemCount(): Int = commands.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val commandName: TextView = itemView.findViewById(R.id.commandName)
        private val commandDescription: TextView = itemView.findViewById(R.id.commandDescription)

        fun bind(command: HermesCommand) {
            commandName.text = command.command
            commandDescription.text = command.description
            itemView.setOnClickListener { onCommandClick(command) }
        }
    }
}