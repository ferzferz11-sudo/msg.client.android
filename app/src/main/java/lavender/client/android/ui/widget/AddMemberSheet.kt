package lavender.client.android.ui.widget

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.*
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.GrpcCompanyClient
import lavender.client.android.data.proto.CompanyPositionProto
import lavender.client.android.theme.ThemeStore
import lavender.client.android.ui.adapter.SelectableUserAdapter

class AddMemberSheet(
    private val context: Context,
    private val mode: Mode,
    private val targetId: String,
    private val onMembersAdded: (() -> Unit)? = null
) {
    enum class Mode { GROUP, COMPANY }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sheet: SearchableListBottomSheet? = null
    private lateinit var userAdapter: SelectableUserAdapter

    fun showAddMember() {
        sheet = SearchableListBottomSheet(context, ThemeStore.currentTheme())
            .setTitle(context.getString(R.string.add_participants))
            .setActionButtonText(context.getString(R.string.add))
            .setExtraInputVisible(false)
            .setLoading(true)

        userAdapter = SelectableUserAdapter(scope, avatarCache = GrpcClient.getAvatarCache()) { count ->
            sheet?.setActionButtonEnabled(count > 0)
            sheet?.setActionButtonText(
                if (count > 0) "${context.getString(R.string.add)} ($count)"
                else context.getString(R.string.add)
            )
        }
        sheet?.setAdapter(userAdapter)

        if (mode == Mode.COMPANY) {
            loadCompanyUsers()
        } else {
            loadGroupUsers()
        }

        sheet?.onSearchTextChanged { query -> userAdapter.filter(query) }

        sheet?.onActionClick {
            val selected = userAdapter.getSelectedUsers()
            if (selected.isEmpty()) return@onActionClick
            if (mode == Mode.COMPANY) {
                addCompanyMembers(selected)
            } else {
                addGroupMembers(selected)
            }
        }

        sheet?.setOnDismissListener { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
        sheet?.show()
    }

    private fun loadGroupUsers() {
        GrpcClient.getAllChats { chats ->
            val chat = chats.find { it.id == targetId }
            val currentParticipants = try {
                val arr = org.json.JSONArray(chat?.participants ?: "[]")
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) set.add(arr.getString(i))
                set
            } catch (_: Exception) { emptySet<String>() }

            GrpcClient.getContacts(GrpcClient.getCurrentUsername() ?: "") { contacts ->
                val currentContacts = contacts.toSet()
                scope.launch {
                    GrpcClient.allUsers.collect { allUsersList ->
                        val filtered = allUsersList
                            .map { it.username }
                            .filter {
                                it != GrpcClient.getCurrentUsername()
                                    && !currentParticipants.contains(it)
                                    && currentContacts.contains(it)
                            }
                        withContext(Dispatchers.Main) {
                            sheet?.setLoading(false)
                            userAdapter.setUsers(filtered)
                            if (filtered.isEmpty()) {
                                sheet?.setEmptyState(true, context.getString(R.string.all_contacts_already_in_group))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadCompanyUsers() {
        scope.launch {
            withContext(Dispatchers.IO) { GrpcClient.loadAllUsers() }
            val allUsers = GrpcClient.allUsers.value
            sheet?.setLoading(false)
            userAdapter.setUsers(allUsers.map { it.username })
            if (allUsers.isEmpty()) {
                sheet?.setEmptyState(true, context.getString(R.string.no_contacts))
            }
        }
    }

    private fun addGroupMembers(selected: List<String>) {
        GrpcClient.addParticipants(targetId, selected) { success, msg ->
            scope.launch {
                sheet?.dismiss()
                Toast.makeText(
                    context,
                    if (success) context.getString(R.string.member_added) else msg,
                    Toast.LENGTH_SHORT
                ).show()
                if (success) onMembersAdded?.invoke()
            }
        }
    }

    private fun addCompanyMembers(selected: List<String>) {
        scope.launch {
            val positions = withContext(Dispatchers.IO) {
                GrpcCompanyClient.listPositions(targetId)?.positions ?: emptyList()
            }
            if (positions.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.error_colon, "No positions"), Toast.LENGTH_SHORT).show()
                return@launch
            }
            addCompanyMembersSequentially(selected, positions, 0)
        }
    }

    private fun addCompanyMembersSequentially(users: List<String>, positions: List<CompanyPositionProto>, index: Int) {
        if (index >= users.size) {
            sheet?.dismiss()
            onMembersAdded?.invoke()
            return
        }
        val username = users[index]
        val user = GrpcClient.allUsers.value.find { it.username == username }
        if (user == null) {
            addCompanyMembersSequentially(users, positions, index + 1)
            return
        }

        val titles = positions.map { it.title }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("${context.getString(R.string.add_member)}: $username")
            .setItems(titles) { _, which ->
                scope.launch {
                    val response = withContext(Dispatchers.IO) {
                        GrpcCompanyClient.addMember(targetId, user.userId, positions[which].id)
                    }
                    if (response?.success == true) {
                        Toast.makeText(context, context.getString(R.string.member_added), Toast.LENGTH_SHORT).show()
                    }
                    addCompanyMembersSequentially(users, positions, index + 1)
                }
            }
            .setNegativeButton(context.getString(R.string.cancel_dialog)) { _, _ ->
                addCompanyMembersSequentially(users, positions, index + 1)
            }
            .show()
    }
}
