package lavender.client.android.ui.widget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.GrpcCompanyClient
import lavender.client.android.data.proto.UserInfoProto

class AddMemberBottomSheet : BottomSheetDialogFragment() {

    private var companyId: String = ""
    private var onMemberAdded: (() -> Unit)? = null

    companion object {
        fun newInstance(companyId: String, onMemberAdded: () -> Unit): AddMemberBottomSheet {
            return AddMemberBottomSheet().apply {
                this.companyId = companyId
                this.onMemberAdded = onMemberAdded
                arguments = Bundle().apply { putString("COMPANY_ID", companyId) }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_add_member, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        companyId = arguments?.getString("COMPANY_ID") ?: companyId
        if (companyId.isEmpty()) {
            dismiss()
            return
        }

        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE

            GrpcClient.loadAllUsers()
            val users = GrpcClient.allUsers.value

            progressBar.visibility = View.GONE

            if (users.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
            } else {
                val adapter = ContactSheetAdapter(users) { user ->
                    showSelectPositionDialog(user)
                }
                recyclerView.adapter = adapter
            }
        }
    }

    private fun showSelectPositionDialog(user: UserInfoProto) {
        lifecycleScope.launch {
            val positionsResponse = withContext(Dispatchers.IO) {
                GrpcCompanyClient.listPositions(companyId)
            }
            val positions = positionsResponse?.positions ?: emptyList()

            if (positions.isEmpty()) {
                Toast.makeText(context, getString(R.string.error_colon, "No positions"), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val titles = positions.map { it.title }.toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_member)
                .setItems(titles) { _, which ->
                    val selectedPosition = positions[which]
                    addMember(user.userId, user.username, selectedPosition.id)
                }
                .setNegativeButton(R.string.cancel_dialog, null)
                .show()
        }
    }

    private fun addMember(userId: String, username: String, positionId: String) {
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                GrpcCompanyClient.addMember(companyId, userId, positionId)
            }
            if (response?.success == true) {
                Toast.makeText(context, getString(R.string.member_added), Toast.LENGTH_SHORT).show()
                onMemberAdded?.invoke()
                dismiss()
            } else {
                Toast.makeText(context, getString(R.string.error_colon, "Failed"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private class ContactSheetAdapter(
        private val contacts: List<UserInfoProto>,
        private val onContactClick: (UserInfoProto) -> Unit
    ) : RecyclerView.Adapter<ContactSheetAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvPosition: TextView = view.findViewById(R.id.tvPosition)
            val positionBubble: View = view.findViewById(R.id.positionBubble)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_company_member, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount() = contacts.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val contact = contacts[position]
            holder.tvName.text = contact.username
            holder.tvPosition.text = contact.email.ifEmpty { contact.username }
            holder.positionBubble.visibility = View.GONE
            holder.itemView.setOnClickListener { onContactClick(contact) }
        }
    }
}
