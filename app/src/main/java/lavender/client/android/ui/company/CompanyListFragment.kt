package lavender.client.android.ui.company

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcCompanyClient

class CompanyListFragment : Fragment() {

    companion object {
        private const val ARG_COMPANY_ID = "company_id"
        private const val ARG_TYPE = "type"

        const val TYPE_MEMBERS = "members"
        const val TYPE_POSITIONS = "positions"
        const val TYPE_CHATS = "chats"

        fun newInstance(companyId: String, type: String): CompanyListFragment {
            return CompanyListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_COMPANY_ID, companyId)
                    putString(ARG_TYPE, type)
                }
            }
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private var companyId: String = ""
    private var type: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_company_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        companyId = arguments?.getString(ARG_COMPANY_ID) ?: ""
        type = arguments?.getString(ARG_TYPE) ?: TYPE_MEMBERS

        recyclerView = view.findViewById(R.id.recyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        recyclerView.layoutManager = LinearLayoutManager(context)

        loadData()
    }

    private fun loadData() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            when (type) {
                TYPE_MEMBERS -> loadMembers()
                TYPE_POSITIONS -> loadPositions()
                TYPE_CHATS -> loadChats()
            }
        }
    }

    private suspend fun loadMembers() {
        val response = withContext(Dispatchers.IO) {
            GrpcCompanyClient.listMembers(companyId)
        }
        progressBar.visibility = View.GONE

        if (response?.members.isNullOrEmpty()) {
            tvEmpty.visibility = View.VISIBLE
        } else {
            val adapter = CompanyMemberAdapter(
                onMemberClick = { },
                onMoreClick = { member, view -> showMemberOptions(member, view) }
            )
            recyclerView.adapter = adapter
            adapter.submitList(response?.members)
        }
    }

    private fun showMemberOptions(member: lavender.client.android.data.proto.CompanyMemberProto, anchorView: View) {
        val options = arrayOf(
            getString(R.string.remove_member),
            getString(R.string.change_position)
        )

        AlertDialog.Builder(requireContext())
            .setItems(options) { _, which ->
                when (which) {
                    0 -> removeMember(member)
                    1 -> showChangePositionDialog(member)
                }
            }
            .show()
    }

    private fun removeMember(member: lavender.client.android.data.proto.CompanyMemberProto) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.remove_member)
            .setMessage("Remove ${member.username} from company?")
            .setPositiveButton(R.string.remove_member) { _, _ ->
                lifecycleScope.launch {
                    val response = withContext(Dispatchers.IO) {
                        GrpcCompanyClient.removeMember(companyId, member.userId)
                    }
                    if (response?.success == true) {
                        Toast.makeText(context, R.string.member_removed, Toast.LENGTH_SHORT).show()
                        loadData()
                    } else {
                        Toast.makeText(context, getString(R.string.error_colon, "Failed"), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel_dialog, null)
            .show()
    }

    private fun showChangePositionDialog(member: lavender.client.android.data.proto.CompanyMemberProto) {
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
            val currentIndex = positions.indexOfFirst { it.id == member.position?.id }.coerceAtLeast(0)

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.change_position)
                .setSingleChoiceItems(titles, currentIndex) { dialog, which ->
                    val selectedPosition = positions[which]
                    lifecycleScope.launch {
                        val response = withContext(Dispatchers.IO) {
                            GrpcCompanyClient.updateMemberPosition(companyId, member.userId, selectedPosition.id)
                        }
                        if (response?.success == true) {
                            Toast.makeText(context, R.string.position_updated, Toast.LENGTH_SHORT).show()
                            loadData()
                        } else {
                            Toast.makeText(context, getString(R.string.error_colon, "Failed"), Toast.LENGTH_LONG).show()
                        }
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel_dialog, null)
                .show()
        }
    }

    private suspend fun loadPositions() {
        val response = withContext(Dispatchers.IO) {
            GrpcCompanyClient.listPositions(companyId)
        }
        progressBar.visibility = View.GONE

        if (response?.positions.isNullOrEmpty()) {
            tvEmpty.visibility = View.VISIBLE
        } else {
            val adapter = CompanyPositionAdapter(
                onPositionClick = { },
                onMoreClick = { position, view -> showPositionOptions(position, view) }
            )
            recyclerView.adapter = adapter
            adapter.submitList(response?.positions)
        }
    }

    private fun showPositionOptions(position: lavender.client.android.data.proto.CompanyPositionProto, anchorView: View) {
        val options = arrayOf(
            getString(R.string.edit_position),
            getString(R.string.delete_position)
        )

        AlertDialog.Builder(requireContext())
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditPositionDialog(position)
                    1 -> deletePosition(position)
                }
            }
            .show()
    }

    private fun showEditPositionDialog(position: lavender.client.android.data.proto.CompanyPositionProto) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_position, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etPositionTitle)
        val etLevel = dialogView.findViewById<EditText>(R.id.etPositionLevel)

        etTitle.setText(position.title)
        etLevel.setText(position.level.toString())

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.create_position)
            .setView(dialogView)
            .setPositiveButton(R.string.change) { _, _ ->
                val title = etTitle.text.toString().trim()
                val level = etLevel.text.toString().toIntOrNull() ?: 0

                if (title.isEmpty()) {
                    Toast.makeText(context, R.string.position_title_hint, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val response = withContext(Dispatchers.IO) {
                        GrpcCompanyClient.updatePosition(position.id, title, level, position.chatAccess)
                    }
                    if (response?.success == true) {
                        Toast.makeText(context, R.string.position_updated, Toast.LENGTH_SHORT).show()
                        loadData()
                    } else {
                        Toast.makeText(context, getString(R.string.error_colon, "Failed"), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel_dialog, null)
            .show()
    }

    private fun deletePosition(position: lavender.client.android.data.proto.CompanyPositionProto) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_position)
            .setMessage("Delete position '${position.title}'?")
            .setPositiveButton(R.string.delete_position) { _, _ ->
                lifecycleScope.launch {
                    val response = withContext(Dispatchers.IO) {
                        GrpcCompanyClient.deletePosition(position.id)
                    }
                    if (response?.success == true) {
                        Toast.makeText(context, R.string.position_deleted, Toast.LENGTH_SHORT).show()
                        loadData()
                    } else {
                        Toast.makeText(context, getString(R.string.error_colon, response?.message ?: "Failed"), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel_dialog, null)
            .show()
    }

    private suspend fun loadChats() {
        val response = withContext(Dispatchers.IO) {
            GrpcCompanyClient.getCompanyChats(companyId)
        }
        progressBar.visibility = View.GONE

        if (response?.chats.isNullOrEmpty()) {
            tvEmpty.visibility = View.VISIBLE
        } else {
            val adapter = CompanyChatAdapter(
                onChatClick = { }
            )
            recyclerView.adapter = adapter
            adapter.submitList(response?.chats)
        }
    }
}
