package lavender.client.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.ThemeManager
import lavender.client.android.ui.adapter.SelectableUserAdapter
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.util.Locale
import androidx.core.graphics.toColorInt

class ProfileActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru" // Default to Russian for first launch
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
    private var fullAvatarUrl: String = ""
    private var isGroup: Boolean = false
    private var roomId: String = ""
    private var creator: String = ""
    private var currentParticipants = mutableListOf<String>()
    private var selectedAvatarUri: Uri? = null
    private var currentProfileAvatar: CircleImageView? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedAvatarUri = uri
                uploadGroupAvatar(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val currentUsername = prefs.getString("current_username", "") ?: ""
        ThemeUi.bind(this, currentUsername)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        
        // Handle window insets for edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        val profileName = findViewById<TextView>(R.id.profileName)

        username = intent.getStringExtra("username") ?: ""
        avatarUrl = intent.getStringExtra("avatar_url") ?: ""
        fullAvatarUrl = intent.getStringExtra("full_avatar_url") ?: ""
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
        
        // Store avatar reference for later updates
        val profileAvatar = findViewById<CircleImageView>(R.id.profileAvatar)
        currentProfileAvatar = profileAvatar
        val changeAvatarButton = findViewById<View>(R.id.changeAvatarButton)
        
        // Load current avatar if provided
        if (avatarUrl.isNotEmpty()) {
            Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .skipMemoryCache(true)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                .into(profileAvatar)
        }

        // Tapping on photo always opens full screen
        profileAvatar.setOnClickListener {
            val fullImageUrl = if (!isGroup) {
                // Для пользователей берем полный URL из кэша если есть, иначе из интента
                grpcClient.getFullAvatarUrl(username) ?: fullAvatarUrl.ifEmpty { avatarUrl }
            } else {
                // Для групп используем полный URL из интента, если есть, иначе thumbnail
                fullAvatarUrl.ifEmpty { avatarUrl }
            }
            if (fullImageUrl.isNotEmpty()) {
                showFullScreenImage(fullImageUrl)
            }
        }

        if (isGroup) {
            val currentMe = grpcClient.getCurrentUsername()
            val isMeAdmin = currentMe == creator && creator.isNotEmpty()
            if (isMeAdmin) {
                // Allow admin to change group name
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
                
                // Allow admin to change group avatar via separate button
                changeAvatarButton.isVisible = true
                changeAvatarButton.setOnClickListener {
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    pickImageLauncher.launch(intent)
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
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false
        refreshParticipantsFromServer(null)
    }

    override fun onPause() {
        super.onPause()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = true
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

    @SuppressLint("SetTextI18n")
    private fun loadProfileData() {
        if (isFinishing || isDestroyed) return

        val profileAvatar = findViewById<CircleImageView>(R.id.profileAvatar) ?: return
        val profileBio = findViewById<TextView>(R.id.profileBio) ?: return
        val profileStatus = findViewById<TextView>(R.id.profileStatus) ?: return
        val bioCard = findViewById<View>(R.id.bioCard)

        if (isGroup) {
            profileStatus.isVisible = false
            bioCard?.isVisible = false
            
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
                            avatarView.setOnClickListener {
                                val fullImageUrl = grpcClient.getFullAvatarUrl(user) ?: url
                                if (fullImageUrl.isNotEmpty()) {
                                    showFullScreenImage(fullImageUrl)
                                }
                            }
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

                // Apply theme to the dynamically added participant item
                ThemeManager.getCurrentTheme()?.let { theme ->
                    ThemeManager.applyThemeToView(userView, theme)
                }
            }

            // Apply theme to participants card and add button
            ThemeManager.getCurrentTheme()?.let { theme ->
                val participantsCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.participantsCard)
                participantsCard?.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(
                    theme.surfaceColor.toColorInt()))
                participantsCard?.strokeColor = ThemeManager.adjustAlpha(theme.onSurfaceColor.toColorInt(), 0.2f)

                addParticipantLayout?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    theme.surfaceContainer.toColorInt())
                val addParticipantButton = findViewById<TextView>(R.id.addParticipantButton)
                addParticipantButton?.setTextColor(theme.primaryColor.toColorInt())
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
                                val intent = Intent(this@ProfileActivity, NewChatActivity::class.java).apply {
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
                        if (profile.avatarUrl.isNotEmpty()) {
                            avatarUrl = profile.avatarUrl
                            Glide.with(this).load(avatarUrl).placeholder(R.drawable.ic_default_avatar).into(profileAvatar)
                            // Also fetch full avatar to update cache
                            grpcClient.getUserAvatar(username) { _ -> }

                            profileAvatar.setOnClickListener {
                                val fullImageUrl = if (!isGroup) {
                                    grpcClient.getFullAvatarUrl(username) ?: fullAvatarUrl.ifEmpty { avatarUrl }
                                } else {
                                    fullAvatarUrl.ifEmpty { avatarUrl }
                                }
                                showFullScreenImage(fullImageUrl)
                            }
                        }
                    }
                }
            }
        }

        if (avatarUrl.isNotEmpty()) {
            Glide.with(this).load(avatarUrl).placeholder(R.drawable.ic_default_avatar).into(profileAvatar)
            profileAvatar.setOnClickListener {
                val fullImageUrl = if (!isGroup) {
                    grpcClient.getFullAvatarUrl(username) ?: fullAvatarUrl.ifEmpty { avatarUrl }
                } else {
                    fullAvatarUrl.ifEmpty { avatarUrl }
                }
                showFullScreenImage(fullImageUrl)
            }
        } else {
            profileAvatar.setImageResource(R.drawable.ic_default_avatar)
            profileAvatar.setOnClickListener(null)
        }

        // Apply theme to bioCard for non-group profiles
        if (!isGroup) {
            ThemeManager.getCurrentTheme()?.let { theme ->
                val bioCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.bioCard)
                bioCard?.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(
                    theme.surfaceColor.toColorInt()))
                bioCard?.strokeColor = ThemeManager.adjustAlpha(theme.onSurfaceColor.toColorInt(), 0.2f)
            }
        }
    }

    private fun showAddParticipantDialog(contacts: List<String>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_contact, null)

        // Apply theme to dialog background like in ChatListActivity
        val customTheme = ThemeManager.getCurrentTheme()
        val bgColor = if (customTheme != null) {
            try {
                customTheme.surfaceColor.toColorInt() } catch (_: Exception) { getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainer) }
        } else {
            getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainer)
        }
        val shapeDrawable = android.graphics.drawable.ShapeDrawable(
            android.graphics.drawable.shapes.RoundRectShape(floatArrayOf(28f, 28f, 28f, 28f, 28f, 28f, 28f, 28f), null, null)
        )
        shapeDrawable.paint.color = bgColor
        dialogView.background = shapeDrawable

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

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

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
        val intent = Intent(this, FullScreenImageActivity::class.java).apply {
            putExtra("image_url", imageUrl)
        }
        startActivity(intent)
    }

    private fun uploadGroupAvatar(uri: Uri) {
        val progressOverlay = findViewById<View>(R.id.progressOverlay)
        progressOverlay?.isVisible = true

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val mimeType = contentResolver.getType(uri)
                    val isGif = mimeType == "image/gif"

                    val thumbBytes: ByteArray
                    val fullBytes: ByteArray
                    val mediaType: String

                    if (isGif) {
                        val inputStream = contentResolver.openInputStream(uri)
                        thumbBytes = inputStream?.readBytes() ?: byteArrayOf()
                        inputStream?.close()
                        fullBytes = thumbBytes // For GIF, use same bytes for both
                        mediaType = "image/gif"
                    } else {
                        // Resize for thumbnail (512x512 for groups)
                        val thumbResizedBytes = resizeImageForGroup(uri)
                        // Resize for full version (1920x1920 max)
                        val fullResizedBytes = resizeImageFull(uri)

                        if (thumbResizedBytes == null || fullResizedBytes == null) {
                            runOnUiThread {
                                progressOverlay?.isVisible = false
                                Toast.makeText(this@ProfileActivity, "Failed to resize image", Toast.LENGTH_SHORT).show()
                            }
                            return@withContext
                        }

                        thumbBytes = thumbResizedBytes
                        fullBytes = fullResizedBytes
                        mediaType = "image/jpeg"
                    }

                    if (thumbBytes.isEmpty()) {
                        runOnUiThread {
                            progressOverlay?.isVisible = false
                            Toast.makeText(this@ProfileActivity, "Failed to read image", Toast.LENGTH_SHORT).show()
                        }
                        return@withContext
                    }

                    // Upload to HTTP server with multipart/form-data (both thumbnail and full)
                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("avatar", if (isGif) "avatar.gif" else "avatar.jpg", thumbBytes.toRequestBody(mediaType.toMediaTypeOrNull()))
                        .addFormDataPart("avatar_full", if (isGif) "avatar_full.gif" else "avatar_full.jpg", fullBytes.toRequestBody(mediaType.toMediaTypeOrNull()))
                        .build()

                    val request = Request.Builder()
                        .url("http://159.195.38.145:8082/upload-avatar")
                        .post(requestBody)
                        .build()

                    val client = OkHttpClient()
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val (thumbUrl, fullUrl) = extractUrlsFromResponse(responseBody)

                        if (thumbUrl.isNotEmpty()) {
                            // Update group avatar via gRPC with both URLs
                            val currentMe = grpcClient.getCurrentUsername() ?: ""
                            grpcClient.updateChatAvatar(roomId, thumbUrl, currentMe, fullUrl) { success, message ->
                                runOnUiThread {
                                    progressOverlay?.isVisible = false
                                    if (success) {
                                        Toast.makeText(this@ProfileActivity, "Групповой аватар обновлен", Toast.LENGTH_SHORT).show()
                                        // Update avatar view
                                        currentProfileAvatar?.let {
                                            Glide.with(this@ProfileActivity)
                                                .load(thumbUrl)
                                                .placeholder(R.drawable.ic_default_avatar)
                                                .error(R.drawable.ic_default_avatar)
                                                .skipMemoryCache(true)
                                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                                                .into(it)
                                        }
                                        avatarUrl = thumbUrl
                                        fullAvatarUrl = fullUrl
                                    } else {
                                        Toast.makeText(this@ProfileActivity, message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } else {
                            runOnUiThread {
                                progressOverlay?.isVisible = false
                                Toast.makeText(this@ProfileActivity, "Failed to parse server response", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        runOnUiThread {
                            progressOverlay?.isVisible = false
                            Toast.makeText(this@ProfileActivity, "Upload failed: ${response.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressOverlay?.isVisible = false
                    Toast.makeText(this@ProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun resizeImageForGroup(uri: Uri): ByteArray? {
        val maxWidth = 512
        val maxHeight = 512
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        val imageStream = contentResolver.openInputStream(uri) ?: return null
        val bitmap = android.graphics.BitmapFactory.decodeStream(imageStream)
        imageStream.close()

        if (bitmap == null) return null

        val width = bitmap.width
        val height = bitmap.height
        val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)

        val scaledBitmap = if (scale < 1) {
            bitmap.scale((width * scale).toInt(), (height * scale).toInt())
        } else {
            bitmap
        }

        val outputStream = java.io.ByteArrayOutputStream()
        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        scaledBitmap.recycle()
        bitmap.recycle()

        return bytes
    }

    private fun resizeImageFull(uri: Uri): ByteArray? {
        val maxWidth = 1920
        val maxHeight = 1920
        return resizeImageWithMax(uri, maxWidth, maxHeight)
    }

    private fun resizeImageWithMax(uri: Uri, maxWidth: Int, maxHeight: Int): ByteArray? {
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        val imageStream = contentResolver.openInputStream(uri) ?: return null
        val bitmap = android.graphics.BitmapFactory.decodeStream(imageStream)
        imageStream.close()

        if (bitmap == null) return null

        val width = bitmap.width
        val height = bitmap.height
        val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)

        val scaledBitmap = if (scale < 1) {
            bitmap.scale((width * scale).toInt(), (height * scale).toInt())
        } else {
            bitmap
        }

        val outputStream = java.io.ByteArrayOutputStream()
        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
        val bytes = outputStream.toByteArray()
        scaledBitmap.recycle()
        bitmap.recycle()

        return bytes
    }

    private fun extractUrlsFromResponse(response: String): Pair<String, String> {
        val urlPattern = """"url"\s*:\s*"([^"]+)"""".toRegex()
        val fullUrlPattern = """"full_url"\s*:\s*"([^"]+)"""".toRegex()

        val urlMatch = urlPattern.find(response)
        val fullUrlMatch = fullUrlPattern.find(response)

        val url = urlMatch?.groupValues?.get(1) ?: ""
        val fullUrl = fullUrlMatch?.groupValues?.get(1) ?: ""

        return Pair(url, fullUrl)
    }

    private fun getColorFromAttr(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
}