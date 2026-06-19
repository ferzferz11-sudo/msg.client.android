package lavender.client.android.ui.chatlist

import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import lavender.client.android.NewChatActivity
import lavender.client.android.R
import lavender.client.android.data.crypto.E2EEManager
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.AIChatInfo
import lavender.client.android.data.session.SessionManager
import lavender.client.android.ui.widget.AIBottomSheet
import lavender.client.android.ui.widget.ActionBottomSheet
import lavender.client.android.ui.widget.SearchableListBottomSheet
import lavender.client.android.ui.widget.SheetAction
import lavender.client.android.ui.widget.SheetNavigator
import lavender.client.android.ui.adapter.UserAdapter
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import lavender.client.android.data.grpc.*
import lavender.client.android.data.proto.UserInfoProto

/**
 * ChatListFABs — FAB buttons and action sheets for ChatListActivity.
 * Extracted from ChatListActivity to reduce its size.
 */

internal fun setupFABs(activity: ChatListActivity) {
    activity.findViewById<View>(R.id.fabAi)?.setOnClickListener {
        showAIBottomSheet(activity)
    }
    activity.findViewById<View>(R.id.fabAddChat)?.setOnClickListener {
        showChatActionSheet(activity)
    }
}

// ======= FAB [+] Action Sheet =======

internal fun showChatActionSheet(activity: ChatListActivity) {
    SheetNavigator.clear()
    ActionBottomSheet(activity)
        .setActions(listOf(
            SheetAction(R.id.actionAddContact, R.drawable.ic_contacts, activity.getString(R.string.add_contact)) {
                showAddContactDialog(activity)
            },
            SheetAction(R.id.actionCreateChat, R.drawable.ic_add, activity.getString(R.string.start_chat)) {
                showCreateChatDialog(activity)
            },
            SheetAction(R.id.actionCreateSecretChat, R.drawable.ic_lock, activity.getString(R.string.secret_chat)) {
                showCreateSecretChatDialog(activity)
            },
            SheetAction(R.id.actionCreateConference, R.drawable.ic_videocam_on, activity.getString(R.string.conference_in_development)) {
                showCreateConferenceDialog(activity)
            }
        )).showWithNavigation()
}

// ======= Add Contact Dialog =======

internal fun showAddContactDialog(activity: ChatListActivity) {
    val username = SessionManager.session.value.username
    val sheet = SearchableListBottomSheet(activity)
        .setTitle(activity.getString(R.string.add_contact))
        .setActionButtonText(activity.getString(R.string.add))
        .setExtraInputVisible(false)
        .setLoading(true)
        .setCreateChatCheckboxVisible(true, activity.getString(R.string.create_direct_chat_after))

    val currentContacts = mutableSetOf<String>()

    val userAdapter = UserAdapter(
        scope = activity.lifecycleScope,
        onUserClick = { selected ->
            (sheet.recyclerView?.adapter as? UserAdapter)?.toggleSelection(selected)
        },
        onSelectionChanged = { count ->
            sheet.setActionButtonEnabled(count > 0)
            sheet.setActionButtonText(if (count > 0) "${activity.getString(R.string.add)} ($count)" else activity.getString(R.string.add))
        },
        avatarCache = GrpcClient.getAvatarCache(),
        onlineUsers = GrpcClient.users.value
    )

    sheet.setAdapter(userAdapter)

    GrpcClient.getContacts(username) { contacts ->
        currentContacts.clear()
        currentContacts.addAll(contacts)

        activity.lifecycleScope.launch {
            GrpcClient.allUsers.collect { allUsersList ->
                val filtered = allUsersList
                    .map { it.username }
                    .filter { it != username && !currentContacts.contains(it) }
                activity.runOnUiThread {
                    sheet.setLoading(false)
                    userAdapter.setUsers(filtered)
                }
            }
        }
    }

    sheet.onSearchTextChanged { query ->
        userAdapter.filter(query)
    }

    sheet.onActionClick {
        val selected = userAdapter.getSelectedUsers()
        if (selected.isEmpty()) return@onActionClick

        var added = 0
        val total = selected.size
        for (contact in selected) {
            GrpcClient.addContact(username, contact) { success, _ ->
                if (success) added++
                if (added == total || (added + (total - selected.indexOf(contact) - 1)) == total) {
                    activity.runOnUiThread {
                        sheet.dismiss()
                        if (sheet.isCreateChatChecked() && selected.isNotEmpty()) {
                            val firstContact = selected.first()
                            GrpcClient.createDirectChat(username, firstContact) { chatId ->
                                if (chatId != null) {
                                    activity.runOnUiThread {
                                        val intent = Intent(activity, NewChatActivity::class.java).apply {
                                            putExtra("USERNAME", username)
                                            putExtra("ROOM_ID", chatId)
                                            putExtra("CHAT_NAME", firstContact)
                                            putExtra("IS_DIRECT", true)
                                            putExtra("PARTICIPANTS", JSONArray(listOf(username, firstContact)).toString())
                                        }
                                        activity.startActivity(intent)
                                    }
                                }
                            }
                        }
                        Toast.makeText(activity, activity.getString(R.string.contacts_added, added), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    sheet.showWithNavigation()
}

// ======= Create Chat Dialog =======

internal fun showCreateChatDialog(activity: ChatListActivity) {
    val username = SessionManager.session.value.username
    val sheet = SearchableListBottomSheet(activity)
        .setTitle(activity.getString(R.string.start_chat))
        .setActionButtonText(activity.getString(R.string.create))
        .setExtraInputVisible(false, activity.getString(R.string.enter_group_name))
        .setLoading(true)

    val userAdapter = UserAdapter(
        scope = activity.lifecycleScope,
        onUserClick = { selected ->
            (sheet.recyclerView?.adapter as? UserAdapter)?.toggleSelection(selected)
        },
        onSelectionChanged = { count ->
            sheet.setActionButtonEnabled(count > 0)
            sheet.setActionButtonText(if (count > 1) "${activity.getString(R.string.create)} ($count)" else activity.getString(R.string.create))
            sheet.setExtraInputVisible(count > 1, activity.getString(R.string.enter_group_name))
        },
        avatarCache = GrpcClient.getAvatarCache(),
        onlineUsers = GrpcClient.users.value
    )

    sheet.setAdapter(userAdapter)

    GrpcClient.getContacts(username) { contacts ->
        val currentContacts = contacts.toSet()
        activity.lifecycleScope.launch {
            GrpcClient.allUsers.collect { allUsersList ->
                val filtered = allUsersList
                    .map { it.username }
                    .filter { it != username && currentContacts.contains(it) }
                activity.runOnUiThread {
                    sheet.setLoading(false)
                    userAdapter.setUsers(filtered)
                }
            }
        }
    }

    sheet.onSearchTextChanged { query ->
        userAdapter.filter(query)
    }

    sheet.onActionClick {
        val selected = userAdapter.getSelectedUsers()
        if (selected.isEmpty()) return@onActionClick

        if (selected.size == 1) {
            val targetUser = selected.first()
            GrpcClient.createDirectChat(username, targetUser) { chatId ->
                if (chatId != null) {
                    activity.runOnUiThread {
                        sheet.dismiss()
                        val intent = Intent(activity, NewChatActivity::class.java).apply {
                            putExtra("USERNAME", username)
                            putExtra("ROOM_ID", chatId)
                            putExtra("CHAT_NAME", targetUser)
                            putExtra("IS_DIRECT", true)
                            putExtra("PARTICIPANTS", JSONArray(listOf(username, targetUser)).toString())
                        }
                        activity.startActivity(intent)
                    }
                } else {
                    activity.runOnUiThread {
                        Toast.makeText(activity, R.string.failed_to_create_chat, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            val groupName = sheet.extraEditText?.text?.toString()?.trim()?.ifEmpty {
                activity.getString(R.string.default_group_name)
            } ?: activity.getString(R.string.default_group_name)
            val participants = selected + username
            GrpcClient.createGroupChat(groupName, participants, username) { chatId ->
                if (chatId != null) {
                    activity.runOnUiThread {
                        sheet.dismiss()
                        val intent = Intent(activity, NewChatActivity::class.java).apply {
                            putExtra("USERNAME", username)
                            putExtra("ROOM_ID", chatId)
                            putExtra("CHAT_NAME", groupName)
                            putExtra("IS_DIRECT", false)
                            putExtra("PARTICIPANTS", JSONArray(participants).toString())
                            putExtra("CREATOR", username)
                        }
                        activity.startActivity(intent)
                    }
                } else {
                    activity.runOnUiThread {
                        Toast.makeText(activity, R.string.failed_to_create_chat, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    sheet.showWithNavigation()
}

// ======= Create Secret Chat Dialog =======

internal fun showCreateSecretChatDialog(activity: ChatListActivity) {
    val username = SessionManager.session.value.username
    val sheet = SearchableListBottomSheet(activity)
        .setTitle(activity.getString(R.string.secret_chat))
        .setActionButtonText(activity.getString(R.string.create))
        .setLoading(true)

    val userAdapter = UserAdapter(
        scope = activity.lifecycleScope,
        onUserClick = { selected ->
            val clickAdapter = sheet.recyclerView?.adapter as? UserAdapter
            clickAdapter?.let {
                it.clearSelection()
                it.toggleSelection(selected)
            }
        },
        onSelectionChanged = { count ->
            sheet.setActionButtonEnabled(count == 1)
        },
        avatarCache = GrpcClient.getAvatarCache(),
        onlineUsers = GrpcClient.users.value
    )

    sheet.setAdapter(userAdapter)

    GrpcClient.getContacts(username) { contacts ->
        val currentContacts = contacts.toSet()
        activity.lifecycleScope.launch {
            GrpcClient.allUsers.collect { allUsersList ->
                val filtered = allUsersList
                    .map { it.username }
                    .filter { it != username && currentContacts.contains(it) }
                activity.runOnUiThread {
                    sheet.setLoading(false)
                    if (filtered.isEmpty()) {
                        sheet.setEmptyState(true, activity.getString(R.string.no_contacts))
                        sheet.setActionButtonEnabled(false)
                    } else {
                        sheet.setEmptyState(false)
                        userAdapter.setUsers(filtered)
                    }
                }
            }
        }
    }

    sheet.onSearchTextChanged { query ->
        userAdapter.filter(query)
    }

    sheet.onActionClick {
        val selected = userAdapter.getSelectedUsers()
        if (selected.isEmpty()) return@onActionClick
        val targetUser = selected.first()

        sheet.setLoading(true)

        val publicKey = E2EEManager.getPublicKeyBase64(activity)

        GrpcClient.createSecretChat(targetUser, publicKey) { chatId, success, message, _ ->
            activity.runOnUiThread {
                sheet.setLoading(false)
                if (success && chatId.isNotEmpty()) {
                    sheet.dismiss()
                    val intent = Intent(activity, NewChatActivity::class.java).apply {
                        putExtra("USERNAME", username)
                        putExtra("ROOM_ID", chatId)
                        putExtra("CHAT_NAME", "🔒 $targetUser")
                        putExtra("CHAT_TYPE", "secret")
                        putExtra("IS_DIRECT", true)
                        putExtra("PARTICIPANTS", JSONArray(listOf(username, targetUser)).toString())
                        putExtra("IS_SECRET", "true")
                    }
                    activity.startActivity(intent)
                } else {
                    Toast.makeText(activity, message.ifEmpty { "Failed to create secret chat" }, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    sheet.showWithNavigation()
}

// ======= Create Conference Dialog =======

internal fun showCreateConferenceDialog(activity: ChatListActivity) {
    val username = SessionManager.session.value.username
    val sheet = SearchableListBottomSheet(activity)
        .setTitle(activity.getString(R.string.conference))
        .setActionButtonText(activity.getString(R.string.create))
        .setExtraInputVisible(true, activity.getString(R.string.edit_topic))
        .setLoading(true)

    val userAdapter = UserAdapter(
        scope = activity.lifecycleScope,
        onUserClick = { selected ->
            (sheet.recyclerView?.adapter as? UserAdapter)?.toggleSelection(selected)
        },
        onSelectionChanged = { count ->
            sheet.setActionButtonEnabled(count > 0)
            sheet.setActionButtonText(if (count > 0) "${activity.getString(R.string.create)} ($count)" else activity.getString(R.string.create))
        },
        avatarCache = GrpcClient.getAvatarCache(),
        onlineUsers = GrpcClient.users.value
    )

    sheet.setAdapter(userAdapter)

    GrpcClient.getContacts(username) { contacts ->
        val currentContacts = contacts.toSet()
        activity.lifecycleScope.launch {
            GrpcClient.allUsers.collect { allUsersList ->
                val filtered = allUsersList
                    .map { it.username }
                    .filter { it != username && currentContacts.contains(it) }
                activity.runOnUiThread {
                    sheet.setLoading(false)
                    userAdapter.setUsers(filtered)
                }
            }
        }
    }

    sheet.onSearchTextChanged { query ->
        userAdapter.filter(query)
    }

    sheet.onActionClick {
        val selected = userAdapter.getSelectedUsers()
        if (selected.isEmpty()) return@onActionClick

        val topic = sheet.extraEditText?.text?.toString()?.trim()?.ifEmpty {
            val sdf = SimpleDateFormat("dd.MM", Locale.getDefault())
            activity.getString(R.string.new_conference_format, sdf.format(Date()))
        } ?: activity.getString(R.string.new_conference_format, SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date()))

        val participants = selected + username
        GrpcClient.createGroupChat(topic, participants, username, "conference") { chatId ->
            if (chatId != null) {
                activity.runOnUiThread {
                    sheet.dismiss()
                    val intent = Intent(activity, NewChatActivity::class.java).apply {
                        putExtra("USERNAME", username)
                        putExtra("ROOM_ID", chatId)
                        putExtra("CHAT_NAME", topic)
                        putExtra("CHAT_TYPE", "conference")
                        putExtra("PARTICIPANTS", JSONArray(participants).toString())
                        putExtra("CREATOR", username)
                    }
                    activity.startActivity(intent)
                }
            } else {
                activity.runOnUiThread {
                    Toast.makeText(activity, R.string.failed_to_create_chat, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    sheet.showWithNavigation()
}

// ======= AI Bottom Sheet =======

internal fun showAIBottomSheet(activity: ChatListActivity) {
    activity.aiChats.clear()
    activity.aiChats.addAll(activity.viewModel.getChats().filter {
        it.type == "hermes" || it.type == "owl"
    }.map { chat ->
        AIChatInfo(
            id = chat.id,
            name = chat.name,
            type = chat.type
        )
    })

    activity.aiBottomSheet = AIBottomSheet(
        context = activity,
        existingChats = activity.aiChats,
        onChatClick = { aiChat ->
            if (aiChat.type == "hermes") {
                openHermesChat(activity, aiChat.id, aiChat.name)
            } else {
                openOwlChat(activity, aiChat.id, aiChat.name)
            }
        },
        onDeleteChat = { aiChat ->
            val userId = SessionManager.session.value.userId
            val username = SessionManager.session.value.username
            if (userId.isNotEmpty()) {
                GrpcClient.deleteChat(aiChat.id, userId, username) { success, _ ->
                    if (success) {
                        activity.viewModel.loadChats()
                    }
                }
            }
        },
        onSettingsClick = { aiChat ->
            if (aiChat.type == "hermes") {
                openHermesSettings(activity, aiChat.id)
            } else {
                openOwlSettings(activity, aiChat.id)
            }
        },
        onCreateHermesChat = {
            val hermesCount = activity.aiChats.count { it.type == "hermes" }
            val chatName = activity.getString(R.string.lava_ai_n, hermesCount + 1)
            openHermesChat(activity, "", chatName)
        },
        onCreateOwlChat = {
            val owlCount = activity.aiChats.count { it.type == "owl" }
            val chatName = activity.getString(R.string.owl_agent_n, owlCount + 1)
            openOwlChat(activity, "", chatName)
        },
        onOpenNotifications = {
            activity.startActivity(Intent(activity, lavender.client.android.ui.notification.NotificationActivity::class.java))
        },
        onOpenRemoteAgents = {
            activity.startActivity(Intent(activity, lavender.client.android.ui.remote.RemoteAgentActivity::class.java))
        },
        unreadNotifCount = 0
    )
    activity.aiBottomSheet?.buildAndShow()
}

internal fun openHermesChat(activity: ChatListActivity, chatId: String, chatName: String) {
    val intent = Intent(activity, lavender.client.android.ui.hermes.HermesChatActivity::class.java).apply {
        putExtra("CHAT_ID", chatId)
        putExtra("CHAT_NAME", chatName)
    }
    activity.startActivity(intent)
}

internal fun openOwlChat(activity: ChatListActivity, chatId: String, chatName: String) {
    val intent = Intent(activity, lavender.client.android.ui.owl.OwlChatActivity::class.java).apply {
        putExtra("CHAT_ID", chatId)
        putExtra("CHAT_NAME", chatName)
    }
    activity.startActivity(intent)
}

internal fun openHermesSettings(activity: ChatListActivity, chatId: String) {
    val intent = Intent(activity, lavender.client.android.ui.owl.OwlSettingsActivity::class.java).apply {
        putExtra("sessionId", chatId)
        putExtra("isHermes", true)
    }
    activity.startActivity(intent)
}

internal fun openOwlSettings(activity: ChatListActivity, chatId: String) {
    val intent = Intent(activity, lavender.client.android.ui.owl.OwlSettingsActivity::class.java).apply {
        putExtra("CHAT_ID", chatId)
    }
    activity.startActivity(intent)
}
