package lavender.client.android

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import de.hdodenhof.circleimageview.CircleImageView
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.RealGrpcClient
import lavender.client.android.ui.adapter.SelectableUserAdapter
import lavender.client.android.ui.adapter.UserAdapter
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }
    private val grpcClient = GrpcClient
    private var username: String = ""
    private var avatarUrl: String = ""
    private var isGroup: Boolean = false
    private var roomId: String = ""
    private var creator: String = ""
    private var currentParticipants = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedColorScheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val profileName = findViewById<TextView>(R.id.profileName)

        username = intent.getStringExtra("username") ?: ""
        avatarUrl = intent.getStringExtra("avatar_url") ?: ""
        isGroup = intent.getBooleanExtra("is_group", false)
        roomId = intent.getStringExtra("room_id") ?: ""
        creator = intent.getStringExtra("creator") ?: ""
        val participantsJson = intent.getStringExtra("participants") ?: "[]"
        
        try {
            val jsonArray = JSONArray(participantsJson)
            currentParticipants.clear()
            for (i in 0 until jsonArray.length()) {
                currentParticipants.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            android.util.Log.e("ProfileActivity", "Error parsing participants", e)
        }

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (isGroup) getString(R.string.group_info) else getString(R.string.profile)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        profileName.text = username
        if (isGroup) {
            val currentMe = grpcClient.getCurrentUsername()
            val isMeAdmin = currentMe == creator && creator.isNotEmpty()
            if (isMeAdmin) {
                profileName.setOnClickListener {
                    val editName = EditText(this).apply {
                        setText(username)
                        setSelection(username.length)
                    }
                    AlertDialog.Builder(this)
                        .setTitle(R.string.edit_message)
                        .setView(editName)
                        .setPositiveButton(R.string.change) { _, _ ->
                            val newName = editName.text.toString().trim()
                            if (newName.isNotEmpty() && newName != username) {
                                val progressOverlay = findViewById<View>(R.id.progressOverlay)
                                progressOverlay.isVisible = true
                                grpcClient.updateChatName(roomId, newName) { success, msg ->
                                    runOnUiThread {
                                        progressOverlay.isVisible = false
                                        if (success) {
                                            username = newName
                                            profileName.text = newName
                                            Toast.makeText(this@ProfileActivity, R.string.message_edited, Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(this@ProfileActivity, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
        }

        lifecycleScope.launch {
            grpcClient.users.collect {
                runOnUiThread { loadProfileData() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshParticipantsFromServer(null)
    }

    private fun refreshParticipantsFromServer(onComplete: (() -> Unit)? = null) {
        if (!isGroup || roomId.isEmpty()) {
            onComplete?.invoke()
            return
        }
        
        val currentMe = grpcClient.getCurrentUsername() ?: return
        grpcClient.getChats(currentMe) { chats ->
            val chat = chats.find { it.id == roomId }
            if (chat != null) {
                try {
                    val jsonArray = JSONArray(chat.participants)
                    val newList = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        newList.add(jsonArray.getString(i))
                    }
                    runOnUiThread {
                        creator = chat.creator
                        currentParticipants.clear()
                        currentParticipants.addAll(newList)
                        loadProfileData()
                        onComplete?.invoke()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ProfileActivity", "Error refreshing participants", e)
                    runOnUiThread { onComplete?.invoke() }
                }
            } else {
                // Room no longer exists (deleted)
                runOnUiThread {
                    if (isGroup && !isFinishing) {
                        Toast.makeText(this@ProfileActivity, R.string.failed_to_delete_chats, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    onComplete?.invoke()
                }
            }
        }
    }

    private fun loadProfileData() {
        if (isFinishing || isDestroyed) return

        val profileAvatar = findViewById<CircleImageView>(R.id.profileAvatar) ?: return
        val profileBio = findViewById<TextView>(R.id.profileBio) ?: return
        val profileStatus = findViewById<TextView>(R.id.profileStatus) ?: return

        if (isGroup) {
            profileStatus.text = getString(R.string.group_chat)
            profileBio.text = getString(R.string.chat_id_format, roomId)
            
            val participantsCard = findViewById<View>(R.id.participantsCard)
            val participantsContainer = findViewById<LinearLayout>(R.id.participantsContainer)
            val addParticipantLayout = findViewById<LinearLayout>(R.id.addParticipantLayout)
            val addParticipantProgress = findViewById<ProgressBar>(R.id.addParticipantProgress)
            
            participantsCard?.isVisible = true
            participantsContainer?.removeAllViews()

            val currentMe = grpcClient.getCurrentUsername() ?: ""
            val isMeAdmin = currentMe == creator && creator.isNotEmpty()

            for (user in currentParticipants) {
                val userView = layoutInflater.inflate(R.layout.item_participant, participantsContainer, false)
                val nameText = userView.findViewById<TextView>(R.id.participantName)
                val avatarView = userView.findViewById<CircleImageView>(R.id.participantAvatar)
                val statusDot = userView.findViewById<View>(R.id.statusIndicator)
                
                val trimmedUser = user.trim()
                val isAdminLabel = if (trimmedUser == creator.trim() && creator.isNotEmpty()) " ${getString(R.string.admin_label)}" else ""
                nameText?.text = "$trimmedUser$isAdminLabel"
                
                val isOnline = grpcClient.users.value.contains(user)
                statusDot?.isVisible = true
                statusDot?.setBackgroundResource(if (isOnline) R.drawable.status_online_dot else R.drawable.status_offline_dot)

                grpcClient.getUserAvatar(user) { url ->
                    runOnUiThread {
                        if (!isFinishing && avatarView != null) {
                            Glide.with(this).load(url).placeholder(R.drawable.ic_default_avatar).into(avatarView)
                        }
                    }
                }

                if (isMeAdmin && user != creator) {
                    userView.setOnLongClickListener {
                        AlertDialog.Builder(this)
                            .setTitle(R.string.remove)
                            .setMessage(getString(R.string.remove_participant_confirm, user))
                            .setPositiveButton(R.string.remove) { _, _ ->
                                val progressOverlay = findViewById<View>(R.id.progressOverlay)
                                progressOverlay?.isVisible = true
                                grpcClient.removeParticipant(roomId, user) { success, msg ->
                                    if (success) {
                                        refreshParticipantsFromServer {
                                            runOnUiThread { progressOverlay?.isVisible = false }
                                        }
                                    } else {
                                        runOnUiThread {
                                            progressOverlay?.isVisible = false
                                            Toast.makeText(this@ProfileActivity, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                            .setNegativeButton(R.string.cancel, null).show()
                        true
                    }
                }
                participantsContainer?.addView(userView)
            }

            if (isMeAdmin) {
                addParticipantLayout?.isVisible = true
                addParticipantLayout?.setOnClickListener {
                    addParticipantLayout.isEnabled = false
                    addParticipantProgress?.isVisible = true
                    
                    grpcClient.getContacts(currentMe) { allContacts ->
                        val availableContacts = allContacts.filter { it !in currentParticipants }
                        runOnUiThread {
                            addParticipantLayout.isEnabled = true
                            addParticipantProgress?.isVisible = false
                            if (availableContacts.isEmpty()) {
                                Toast.makeText(this, R.string.no_users_available, Toast.LENGTH_SHORT).show()
                            } else {
                                showAddParticipantDialog(availableContacts)
                            }
                        }
                    }
                }
            } else {
                addParticipantLayout?.isVisible = false
            }

            findViewById<Button>(R.id.editProfileButton)?.apply {
                text = getString(R.string.delete_group)
                isVisible = isMeAdmin
                if (isMeAdmin) {
                    setOnClickListener {
                        AlertDialog.Builder(this@ProfileActivity)
                            .setTitle(R.string.delete_group)
                            .setMessage(R.string.delete_group_confirm)
                            .setPositiveButton(R.string.delete) { _, _ ->
                                val intent = Intent(this@ProfileActivity, ChatListActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    putExtra("ACTION_DELETE_CHAT_ID", roomId)
                                    putExtra("ACTION_DELETE_CHAT_NAME", username)
                                }
                                startActivity(intent)
                                finish()
                            }
                            .setNegativeButton(R.string.cancel, null).show()
                    }
                }
            }
        } else {
            // User profile logic
            grpcClient.getUserProfile(username) { profile ->
                runOnUiThread {
                    if (!isFinishing && profile != null) {
                        profileBio.text = profile.bio.ifEmpty { getString(R.string.no_bio) }
                        val isOnline = username == grpcClient.getCurrentUsername() || grpcClient.users.value.contains(username)
                        if (isOnline) {
                            profileStatus.text = getString(R.string.connected)
                            profileStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                        } else {
                            profileStatus.text = profile.status.ifEmpty { getString(R.string.offline) }
                            val typedValue = android.util.TypedValue()
                            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                            profileStatus.setTextColor(typedValue.data)
                        }
                        if (profile.avatarUrl.isNotEmpty() && avatarUrl.isEmpty()) {
                            avatarUrl = profile.avatarUrl
                            Glide.with(this).load(avatarUrl).placeholder(R.drawable.ic_default_avatar).into(profileAvatar)
                        }
                    }
                }
            }
        }

        if (avatarUrl.isNotEmpty()) {
            Glide.with(this).load(avatarUrl).placeholder(R.drawable.ic_default_avatar).into(profileAvatar)
            profileAvatar.setOnClickListener { showFullScreenImage(avatarUrl) }
        } else {
            profileAvatar.setImageResource(R.drawable.ic_default_avatar)
        }
    }

    private fun showAddParticipantDialog(contacts: List<String>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_contact, null)
        
        val titleView = dialogView.findViewById<TextView>(R.id.dialogTitle)
        titleView?.text = getString(R.string.add)

        val searchEditText = dialogView.findViewById<EditText>(R.id.searchEditText)
        val usersRecyclerView = dialogView.findViewById<RecyclerView>(R.id.usersRecyclerView)
        val createChatCheckbox = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.createChatCheckbox)
        val btnAdd = dialogView.findViewById<MaterialButton>(R.id.btnAdd)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)

        createChatCheckbox.isVisible = false

        val filteredUsers = mutableListOf<String>()
        val selectableAdapter = SelectableUserAdapter(
            avatarCache = grpcClient.getAvatarCache(),
            onSelectionChanged = { count ->
                btnAdd.isEnabled = count > 0
                btnAdd.text = if (count > 0) "${getString(R.string.add)} ($count)" else getString(R.string.add)
            }
        )

        usersRecyclerView.adapter = selectableAdapter
        usersRecyclerView.layoutManager = LinearLayoutManager(this)

        filteredUsers.addAll(contacts)
        selectableAdapter.setUsers(filteredUsers)

        lifecycleScope.launch {
            grpcClient.users.collect { online ->
                runOnUiThread { selectableAdapter.setOnlineUsers(online) }
            }
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                filteredUsers.clear()
                filteredUsers.addAll(contacts.filter { it.lowercase().contains(query) })
                selectableAdapter.setUsers(filteredUsers)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnAdd.setOnClickListener {
            val selected = selectableAdapter.getSelectedUsers()
            if (selected.isEmpty()) return@setOnClickListener
            
            val progressOverlay = findViewById<View>(R.id.progressOverlay)
            dialog.dismiss()
            progressOverlay.isVisible = true

            grpcClient.addParticipants(roomId, selected) { success, msg ->
                if (success) {
                    refreshParticipantsFromServer {
                        runOnUiThread { progressOverlay.isVisible = false }
                    }
                } else {
                    runOnUiThread {
                        progressOverlay.isVisible = false
                        Toast.makeText(this@ProfileActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showFullScreenImage(imageUrl: String) {
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen).create()
        val layout = RelativeLayout(this)
        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleLarge)
        
        layout.addView(imageView, RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        layout.addView(progressBar, RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            addRule(RelativeLayout.CENTER_IN_PARENT)
        })

        dialog.setView(layout)
        imageView.setOnClickListener { dialog.dismiss() }
        
        Glide.with(this)
            .load(imageUrl)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    progressBar.isVisible = false
                    return false
                }
                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                    progressBar.isVisible = false
                    return false
                }
            })
            .into(imageView)
            
        dialog.show()
    }

    private fun applySavedColorScheme() {
        val theme = when (getSavedColorScheme()) {
            "light" -> R.style.Theme_Lavender_Light_NoActionBar
            else -> R.style.Theme_Lavender_Dark_NoActionBar
        }
        setTheme(theme)
    }

    private fun getSavedColorScheme(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("color_scheme", null)
    }
}
