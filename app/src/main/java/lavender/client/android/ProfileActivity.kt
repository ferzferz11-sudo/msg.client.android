package lavender.client.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
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
import lavender.client.android.data.proto.ProtoUtils
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.adapter.SelectableUserAdapter
import lavender.client.android.ui.adapter.ParticipantAdapter
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.util.Locale
import androidx.core.graphics.toColorInt
import androidx.appcompat.app.AlertDialog

import lavender.client.android.ui.widget.SearchableListBottomSheet
import lavender.client.android.ui.widget.WidgetManager

class ProfileActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration)
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
    private var allowMembersToAdd: Boolean = false
    private var selectedAvatarUri: Uri? = null
    private var currentProfileAvatar: CircleImageView? = null
    private var participantsAdapter: ParticipantAdapter? = null

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
        val currentMe = prefs.getString("username", "") ?: ""
        ThemeUi.bind(this, currentMe)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

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

        loadProfileData()

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
                        username = chat.name
                        avatarUrl = chat.avatarUrl
                        fullAvatarUrl = chat.fullAvatarUrl
                        allowMembersToAdd = chat.allowMembersToAdd
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
                runOnUiThread {
                    if (isGroup && !isFinishing) {
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
        val profileName = findViewById<TextView>(R.id.profileName) ?: return
        val profileBio = findViewById<TextView>(R.id.profileBio) ?: return
        val profileStatus = findViewById<TextView>(R.id.profileStatus) ?: return
        val bioCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.bioCard)
        val changeAvatarButton = findViewById<View>(R.id.changeAvatarButton)
        val groupSettingsCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.groupSettingsCard)
        val switchAllowAdd = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchAllowAdd)

        currentProfileAvatar = profileAvatar
        profileName.text = username

        val currentTheme = ThemeStore.currentTheme()
        val textPrimaryColor = ThemeUtils.parseSafeColor(currentTheme.textPrimaryColor, android.graphics.Color.BLACK)
        val primaryColor = ThemeUtils.parseSafeColor(currentTheme.primaryColor, android.graphics.Color.BLUE)
        
        profileName.setTextColor(textPrimaryColor)
        profileBio.setTextColor(textPrimaryColor)
        profileAvatar.borderColor = primaryColor
        profileAvatar.borderWidth = (2 * resources.displayMetrics.density).toInt()

        if (isGroup) {
            profileStatus.isVisible = false
            bioCard?.isVisible = false
            groupSettingsCard?.isVisible = true

            val currentMe = grpcClient.getCurrentUsername() ?: ""
            val isMeAdmin = currentMe == creator && creator.isNotEmpty()

            if (groupSettingsCard != null && switchAllowAdd != null) {
                groupSettingsCard.isVisible = isMeAdmin
                setupAllowAddSwitch(switchAllowAdd, roomId, isMeAdmin)
                
                groupSettingsCard.setCardBackgroundColor(ColorStateList.valueOf(currentTheme.surfaceColor.toColorInt()))
                groupSettingsCard.strokeColor = ThemeUtils.adjustAlpha(currentTheme.onSurfaceColor.toColorInt(), 0.2f)
            }

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

                changeAvatarButton?.isVisible = true
                changeAvatarButton?.setOnClickListener {
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    pickImageLauncher.launch(intent)
                }
            } else {
                profileName.setOnClickListener(null)
                changeAvatarButton?.isVisible = false
            }

            val participantsCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.participantsCard)
            val participantsRecyclerView = findViewById<RecyclerView>(R.id.participantsRecyclerView)
            val addParticipantLayout = findViewById<LinearLayout>(R.id.addParticipantLayout)
            val addParticipantProgress = findViewById<ProgressBar>(R.id.addParticipantProgress)
            val deleteGroupButton = findViewById<MaterialButton>(R.id.deleteGroupButton)

            participantsCard?.isVisible = true
            
            if (participantsRecyclerView != null) {
                if (participantsAdapter == null) {
                    participantsAdapter = ParticipantAdapter(
                        theme = currentTheme,
                        isAdmin = isMeAdmin,
                        creator = creator,
                        onRemoveClick = { user ->
                            showRemoveParticipantDialog(user)
                        },
                        onAvatarClick = { user, url ->
                            val fullImageUrl = grpcClient.getFullAvatarUrl(user) ?: url
                            if (fullImageUrl.isNotEmpty()) showFullScreenImage(fullImageUrl)
                        },
                        onLongClick = { user ->
                            if (isMeAdmin && user != creator) showRemoveParticipantDialog(user)
                        }
                    )
                    participantsRecyclerView.layoutManager = LinearLayoutManager(this)
                    participantsRecyclerView.adapter = participantsAdapter
                }
                
                participantsAdapter?.updateData(
                    currentParticipants, 
                    grpcClient.users.value.toSet(), 
                    grpcClient.getAvatarCache()
                )

                // Prefetch avatars for participants
                currentParticipants.forEach { user ->
                    grpcClient.getUserAvatar(user) { /* cached */ }
                }
            }

            deleteGroupButton?.apply {
                isVisible = isMeAdmin
                if (isMeAdmin) {
                    setOnClickListener {
                        AlertDialog.Builder(this@ProfileActivity)
                            .setTitle(R.string.delete_group)
                            .setMessage(R.string.delete_group_confirm)
                            .setPositiveButton(R.string.delete) { _, _ ->
                                val intent = Intent(this@ProfileActivity, ChatListActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra("START_DELETION_ID", roomId)
                                }
                                startActivity(intent)
                                finish()
                            }
                            .setNegativeButton(R.string.cancel, null).show()
                    }
                }
            }

            ThemeStore.currentTheme().let { theme ->
                participantsCard?.setCardBackgroundColor(ColorStateList.valueOf(theme.surfaceColor.toColorInt()))
                participantsCard?.strokeColor = ThemeUtils.adjustAlpha(theme.onSurfaceColor.toColorInt(), 0.2f)
                addParticipantLayout?.backgroundTintList = ColorStateList.valueOf(theme.surfaceContainer.toColorInt())
                findViewById<TextView>(R.id.addParticipantButton)?.setTextColor(theme.primaryColor.toColorInt())
            }

            if (isMeAdmin || allowMembersToAdd) {
                addParticipantLayout?.isVisible = true
                addParticipantLayout?.setOnClickListener {
                    addParticipantLayout.isEnabled = false
                    addParticipantProgress?.isVisible = true
                    grpcClient.getContacts(currentMe) { allContacts ->
                        val availableContacts = allContacts.filter { it !in currentParticipants }
                        runOnUiThread {
                            addParticipantLayout.isEnabled = true
                            addParticipantProgress?.isVisible = false
                            if (availableContacts.isEmpty()) Toast.makeText(this, R.string.no_users_available, Toast.LENGTH_SHORT).show()
                            else showAddParticipantDialog(availableContacts)
                        }
                    }
                }
            } else {
                addParticipantLayout?.isVisible = false
            }
        } else {
            // Fetch userId first, then get profile
            grpcClient.fetchUserId(username) { userId, success ->
                if (!success || userId.isNullOrEmpty()) {
                    runOnUiThread {
                        profileBio.text = getString(R.string.no_bio)
                        profileStatus.text = getString(R.string.offline)
                    }
                    return@fetchUserId
                }
                grpcClient.getUserProfile(userId) { profile ->
                    runOnUiThread {
                        if (!isFinishing && profile != null) {
                            profileBio.text = profile.bio.ifEmpty { getString(R.string.no_bio) }
                            val isOnline = username == grpcClient.getCurrentUsername() || grpcClient.users.value.contains(username)
                            if (isOnline) {
                                profileStatus.text = getString(R.string.connected)
                                profileStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                            } else {
                                // Use lastSeenAt from profile response
                                val lastSeenText = if (profile.lastSeenAt != null) {
                                    ProtoUtils.formatLastSeen(profile.lastSeenAt, this)
                                } else {
                                    profile.status.ifEmpty { getString(R.string.offline) }
                                }
                                profileStatus.text = lastSeenText
                                val typedValue = android.util.TypedValue()
                                theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                                profileStatus.setTextColor(typedValue.data)
                            }
                        }
                        if (profile != null && profile.avatarUrl.isNotEmpty()) {
                            avatarUrl = profile.avatarUrl
                            fullAvatarUrl = profile.fullAvatarUrl.ifEmpty { avatarUrl }
                            // Force update cache with fresh data from profile
                            grpcClient.updateAvatarCache(username, avatarUrl, fullAvatarUrl)

                            Glide.with(this).load(avatarUrl).placeholder(R.drawable.ic_default_avatar).into(profileAvatar)
                            grpcClient.getUserAvatar(username, userId) { _ -> }
                            
                            // Re-bind avatar click listener because avatarUrl might have been empty initially
                            setupAvatarClickListener(profileAvatar)
                        }
                    }
                }
            }
        }

        if (avatarUrl.isNotEmpty()) {
            Glide.with(this).load(avatarUrl).placeholder(R.drawable.ic_default_avatar).into(profileAvatar)
            profileAvatar.imageTintList = null
            setupAvatarClickListener(profileAvatar)
        } else {
            ThemeUtils.applyDefaultAvatar(profileAvatar, currentTheme)
            profileAvatar.setOnClickListener(null)
        }

        if (!isGroup) {
            ThemeStore.currentTheme().let { theme ->
                bioCard?.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(theme.surfaceColor.toColorInt()))
                bioCard?.strokeColor = ThemeUtils.adjustAlpha(theme.onSurfaceColor.toColorInt(), 0.2f)
            }
        }

        // Apply global theme to all views
        applyThemeToView(findViewById(android.R.id.content), ThemeStore.currentTheme())
    }

    private fun showRemoveParticipantDialog(user: String) {
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
    }

    private fun setupAllowAddSwitch(switch: com.google.android.material.switchmaterial.SwitchMaterial, roomId: String, isMeAdmin: Boolean) {
        switch.isEnabled = isMeAdmin
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = allowMembersToAdd
        
        if (isMeAdmin) {
            switch.setOnCheckedChangeListener { _, isChecked ->
                val progressOverlay = findViewById<View>(R.id.progressOverlay)
                progressOverlay?.isVisible = true
                grpcClient.updateChatSettings(roomId, isChecked) { success, msg ->
                    runOnUiThread {
                        progressOverlay?.isVisible = false
                        if (success) {
                            allowMembersToAdd = isChecked
                            Toast.makeText(this@ProfileActivity, R.string.theme_saved, Toast.LENGTH_SHORT).show()
                            loadProfileData() 
                        } else {
                            switch.setOnCheckedChangeListener(null)
                            switch.isChecked = !isChecked
                            setupAllowAddSwitch(switch, roomId, isMeAdmin)
                            Toast.makeText(this@ProfileActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun setupAvatarClickListener(profileAvatar: CircleImageView) {
        profileAvatar.setOnClickListener {
            val fullImageUrl = if (!isGroup) {
                grpcClient.getFullAvatarUrl(username) ?: fullAvatarUrl.ifEmpty { avatarUrl }
            } else {
                fullAvatarUrl.ifEmpty { avatarUrl }
            }
            if (fullImageUrl.isNotEmpty()) showFullScreenImage(fullImageUrl)
        }
    }

    private fun showAddParticipantDialog(contacts: List<String>) {
        val sheet = SearchableListBottomSheet(this)
            .setTitle(getString(R.string.add_participants))
            .setActionButtonText(getString(R.string.add))
            .setExtraInputVisible(false)

        val selectableAdapter = SelectableUserAdapter(
            lifecycleScope,
            avatarCache = grpcClient.getAvatarCache(),
            onSelectionChanged = { count ->
                sheet.setActionButtonEnabled(count > 0)
                sheet.setActionButtonText(if (count > 0) "${getString(R.string.add)} ($count)" else getString(R.string.add))
            }
        )
        sheet.setAdapter(selectableAdapter)
        selectableAdapter.setUsers(contacts)

        sheet.onSearchTextChanged { query ->
            val q = query.lowercase()
            selectableAdapter.setUsers(contacts.filter { it.lowercase().contains(q) })
        }

        sheet.onActionClick {
            val selected = selectableAdapter.getSelectedUsers()
            if (selected.isEmpty()) return@onActionClick
            val progressOverlay = findViewById<View>(R.id.progressOverlay)
            sheet.dismiss()
            progressOverlay.isVisible = true
            grpcClient.addParticipants(roomId, selected) { success, msg ->
                if (success) refreshParticipantsFromServer { runOnUiThread { progressOverlay.isVisible = false } }
                else runOnUiThread { progressOverlay.isVisible = false; Toast.makeText(this@ProfileActivity, msg, Toast.LENGTH_SHORT).show() }
            }
        }
        sheet.show()
    }

    private fun showFullScreenImage(imageUrl: String) {
        val intent = Intent(this, FullScreenImageActivity::class.java).apply { putExtra("image_url", imageUrl) }
        startActivity(intent)
    }

    private fun uploadGroupAvatar(uri: Uri) {
        val progressOverlay = findViewById<View>(R.id.progressOverlay)
        progressOverlay?.isVisible = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val mimeType = contentResolver.getType(uri); val isGif = mimeType == "image/gif"
                    val thumbBytes: ByteArray; val fullBytes: ByteArray; val mediaType: String
                    if (isGif) {
                        val inputStream = contentResolver.openInputStream(uri); thumbBytes = inputStream?.readBytes() ?: byteArrayOf(); inputStream?.close(); fullBytes = thumbBytes; mediaType = "image/gif"
                    } else {
                        val thumbResizedBytes = resizeImageForGroup(uri); val fullResizedBytes = resizeImageFull(uri)
                        if (thumbResizedBytes == null || fullResizedBytes == null) {
                            runOnUiThread { progressOverlay?.isVisible = false; Toast.makeText(this@ProfileActivity, "Failed to resize image", Toast.LENGTH_SHORT).show() }; return@withContext
                        }
                        thumbBytes = thumbResizedBytes; fullBytes = fullResizedBytes; mediaType = "image/jpeg"
                    }
                    if (thumbBytes.isEmpty()) {
                        runOnUiThread { progressOverlay?.isVisible = false; Toast.makeText(this@ProfileActivity, "Failed to read image", Toast.LENGTH_SHORT).show() }; return@withContext
                    }
                    val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("avatar", if (isGif) "avatar.gif" else "avatar.jpg", thumbBytes.toRequestBody(mediaType.toMediaTypeOrNull())).addFormDataPart("avatar_full", if (isGif) "avatar_full.gif" else "avatar_full.jpg", fullBytes.toRequestBody(mediaType.toMediaTypeOrNull())).build()
                    val request = Request.Builder().url("http://159.195.38.145:8082/upload-avatar").post(requestBody).build()
                    val client = OkHttpClient(); val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val responseBody = response.body.string(); val (thumbUrl, fullUrl) = extractUrlsFromResponse(responseBody)
                        if (thumbUrl.isNotEmpty()) {
                            val currentMe = grpcClient.getCurrentUsername() ?: ""
                            grpcClient.updateChatAvatar(roomId, thumbUrl, currentMe, fullUrl) { success, message ->
                                runOnUiThread {
                                    progressOverlay?.isVisible = false
                                    if (success) {
                                        avatarUrl = thumbUrl
                                        fullAvatarUrl = fullUrl
                                        // Update local cache to ensure it stays updated across the app
                                        grpcClient.updateAvatarCache(roomId, thumbUrl, fullUrl)

                                        // Force refresh avatar without cache
                                        currentProfileAvatar?.let {
                                            Glide.with(this@ProfileActivity)
                                                .load(thumbUrl)
                                                .placeholder(R.drawable.ic_default_avatar)
                                                .skipMemoryCache(true)
                                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                                                .into(it)
                                        }

                                        loadProfileData()
                                        Toast.makeText(this@ProfileActivity, R.string.theme_saved, Toast.LENGTH_SHORT).show()
                                    } else Toast.makeText(this@ProfileActivity, message, Toast.LENGTH_LONG).show()
                                }
                            }
                        } else runOnUiThread { progressOverlay?.isVisible = false; Toast.makeText(this@ProfileActivity, "Failed to parse server response", Toast.LENGTH_SHORT).show() }
                    } else runOnUiThread { progressOverlay?.isVisible = false; Toast.makeText(this@ProfileActivity, "Upload failed: ${response.code}", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) { runOnUiThread { progressOverlay?.isVisible = false; Toast.makeText(this@ProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() } }
        }
    }

    private fun resizeImageForGroup(uri: Uri): ByteArray? {
        val maxWidth = 512; val maxHeight = 512; val inputStream = contentResolver.openInputStream(uri) ?: return null
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }; android.graphics.BitmapFactory.decodeStream(inputStream, null, options); inputStream.close()
        val imageStream = contentResolver.openInputStream(uri) ?: return null; val bitmap = android.graphics.BitmapFactory.decodeStream(imageStream); imageStream.close(); if (bitmap == null) return null
        val width = bitmap.width; val height = bitmap.height; val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val scaledBitmap = if (scale < 1) bitmap.scale((width * scale).toInt(), (height * scale).toInt()) else bitmap
        val outputStream = java.io.ByteArrayOutputStream(); scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream); val bytes = outputStream.toByteArray(); scaledBitmap.recycle(); bitmap.recycle(); return bytes
    }

    private fun resizeImageFull(uri: Uri): ByteArray? = resizeImageWithMax(uri, 1920, 1920)

    private fun resizeImageWithMax(uri: Uri, maxWidth: Int, maxHeight: Int): ByteArray? {
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }; android.graphics.BitmapFactory.decodeStream(inputStream, null, options); inputStream.close()
        val imageStream = contentResolver.openInputStream(uri) ?: return null; val bitmap = android.graphics.BitmapFactory.decodeStream(imageStream); imageStream.close(); if (bitmap == null) return null
        val width = bitmap.width; val height = bitmap.height; val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val scaledBitmap = if (scale < 1) bitmap.scale((width * scale).toInt(), (height * scale).toInt()) else bitmap
        val outputStream = java.io.ByteArrayOutputStream(); scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream); val bytes = outputStream.toByteArray(); scaledBitmap.recycle(); bitmap.recycle(); return bytes
    }

    private fun extractUrlsFromResponse(response: String): Pair<String, String> {
        val urlPattern = """"url"\s*:\s*"([^"]+)"""".toRegex(); val fullUrlPattern = """"full_url"\s*:\s*"([^"]+)"""".toRegex()
        return Pair(urlPattern.find(response)?.groupValues?.get(1) ?: "", fullUrlPattern.find(response)?.groupValues?.get(1) ?: "")
    }

    private fun getColorFromAttr(attr: Int): Int {
        val typedValue = android.util.TypedValue(); theme.resolveAttribute(attr, typedValue, true); return typedValue.data
    }

    private fun applyThemeToView(view: View, theme: lavender.client.android.theme.Theme) {
        val textPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, android.graphics.Color.BLACK)
        val onSurface = ThemeUtils.parseSafeColor(theme.onSurfaceColor, android.graphics.Color.GRAY)
        val primary = ThemeUtils.parseSafeColor(theme.primaryColor, android.graphics.Color.BLUE)
        
        when (view) {
            is MaterialButton -> {
                view.setTextColor(primary)
                view.iconTint = ColorStateList.valueOf(primary)
                // Remove custom background tint for delete button
                if (view.id == R.id.deleteGroupButton) {
                    view.backgroundTintList = ColorStateList.valueOf(ThemeUtils.parseSafeColor(theme.surfaceContainer, android.graphics.Color.LTGRAY))
                }
            }
            is com.google.android.material.switchmaterial.SwitchMaterial -> {
                view.setTextColor(textPrimary)
                val thumbStates = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(primary, android.graphics.Color.LTGRAY)
                )
                val trackStates = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(ThemeUtils.adjustAlpha(primary, 0.5f), ThemeUtils.adjustAlpha(android.graphics.Color.GRAY, 0.3f))
                )
                view.thumbTintList = thumbStates
                view.trackTintList = trackStates
            }
            is CircleImageView -> {
                view.borderColor = primary
                view.borderWidth = (2 * resources.displayMetrics.density).toInt()
            }
            is android.widget.CheckBox -> view.buttonTintList = ColorStateList.valueOf(primary)
            is TextView -> {
                if (view.id == R.id.participantsTitle || view.id == R.id.addParticipantButton || 
                    view.id == R.id.profileName || view.id == R.id.bioTitle || view.id == R.id.settingsTitle) {
                    view.setTextColor(primary)
                } else {
                    view.setTextColor(textPrimary)
                }
            }
            is com.google.android.material.card.MaterialCardView -> { 
                view.setCardBackgroundColor(ColorStateList.valueOf(ThemeUtils.parseSafeColor(theme.surfaceColor, android.graphics.Color.WHITE)))
                view.strokeColor = ThemeUtils.adjustAlpha(onSurface, 0.2f) 
            }
            is android.view.ViewGroup -> { for (i in 0 until view.childCount) applyThemeToView(view.getChildAt(i), theme) }
        }
    }
}
