package lavender.client.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message
import lavender.client.android.ui.adapter.MessageAdapter
import lavender.client.android.ui.chat.ChatViewModel
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class NewChatActivity : AppCompatActivity() {

    private val okHttpClient = OkHttpClient()
    private lateinit var toolbar: Toolbar
    private lateinit var toolbarTitle: TextView
    private lateinit var toolbarSubtitle: TextView
    private lateinit var toolbarAvatar: CircleImageView
    private lateinit var groupParticipantsContainer: LinearLayout
    private lateinit var selectionToolbar: LinearLayout
    private lateinit var selectionCountText: TextView
    private lateinit var closeSelection: ImageButton
    private lateinit var copyMessages: ImageButton
    private lateinit var replyMessage: ImageButton
    private lateinit var deleteMessages: ImageButton
    private lateinit var toolbarContent: View
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var attachButton: ImageButton
    private lateinit var uploadProgressBar: ProgressBar
    
    private lateinit var replyPreview: View
    private lateinit var replyUser: TextView
    private lateinit var replyText: TextView
    private lateinit var cancelReply: ImageButton

    private var selectionMode = false
    
    private var replyingTo: Message? = null
    
    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: MessageAdapter
    
    private var typingJob: kotlinx.coroutines.Job? = null
    private var isTypingSignalSent = false

    private var colorSchemeMenuItem: MenuItem? = null
    private val grpcClient = GrpcClient

    private var username: String = ""
    private var password: String = ""
    private var roomId: String = ""
    private var chatName: String = ""
    private var isDirect: Boolean = false
    private var participantsJson: String = ""

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadImageMessage(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedColorScheme()
        applySavedLanguage()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_chat)

        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        initViews()
        loadDataFromIntent()
        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupListeners()

        viewModel.switchRoom(roomId)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        toolbarContent = findViewById(R.id.toolbarContent)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        toolbarSubtitle = findViewById(R.id.toolbarSubtitle)
        toolbarAvatar = findViewById(R.id.toolbarAvatar)
        groupParticipantsContainer = findViewById(R.id.groupParticipantsContainer)
        
        selectionToolbar = findViewById(R.id.selectionToolbar)
        selectionCountText = findViewById(R.id.selectionCountText)
        closeSelection = findViewById(R.id.closeSelection)
        copyMessages = findViewById(R.id.copyMessages)
        replyMessage = findViewById(R.id.replyMessage)
        deleteMessages = findViewById(R.id.deleteMessages)

        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        attachButton = findViewById(R.id.attachButton)
        uploadProgressBar = findViewById(R.id.uploadProgressBar)
        
        replyPreview = findViewById(R.id.replyPreview)
        replyUser = findViewById(R.id.replyUser)
        replyText = findViewById(R.id.replyText)
        cancelReply = findViewById(R.id.cancelReply)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadDataFromIntent() {
        username = intent.getStringExtra("username") ?: ""
        password = intent.getStringExtra("password") ?: ""
        roomId = intent.getStringExtra("roomId") ?: ""
        chatName = intent.getStringExtra("roomName") ?: ""
        participantsJson = intent.getStringExtra("participants") ?: "[]"
        
        isDirect = roomId.endsWith("_direct")
    }

    private fun setupToolbar() {
        if (isDirect) {
            toolbarAvatar.visibility = View.VISIBLE
            groupParticipantsContainer.visibility = View.GONE
            
            val otherUser = if (chatName.startsWith("Chat with ")) {
                chatName.removePrefix("Chat with ")
            } else {
                val parts = roomId.removeSuffix("_direct").split("_")
                if (parts.size >= 2) {
                    if (parts[0] == username) parts[1] else parts[0]
                } else {
                    chatName
                }
            }
            toolbarTitle.text = otherUser
            toolbarSubtitle.visibility = View.VISIBLE
            toolbarSubtitle.text = getString(R.string.connected_status)
        } else {
            toolbarAvatar.visibility = View.GONE
            toolbarTitle.text = chatName.ifEmpty { roomId }
            toolbarSubtitle.visibility = View.GONE
            setupGroupAvatars()
        }
    }

    private fun setupGroupAvatars() {
        groupParticipantsContainer.removeAllViews()
        try {
            val participants = JSONArray(participantsJson)
            if (participants.length() > 0) {
                groupParticipantsContainer.visibility = View.VISIBLE
                val displayCount = minOf(participants.length(), 5)
                for (i in 0 until displayCount) {
                    val avatarView = CircleImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(24.dpToPx(), 24.dpToPx()).apply {
                            marginStart = if (i > 0) (-8).dpToPx() else 0
                        }
                        setImageResource(R.drawable.ic_default_avatar)
                        borderWidth = 1.dpToPx()
                        borderColor = getColor(R.color.white)
                    }
                    groupParticipantsContainer.addView(avatarView)
                }
            }
        } catch (e: Exception) {
            groupParticipantsContainer.visibility = View.GONE
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (selectionMode) return false
        menuInflater.inflate(R.menu.main_menu, menu)
        val languageItem = menu?.findItem(R.id.action_language)
        val languageView = languageItem?.actionView
        val languageText = languageView?.findViewById<TextView>(R.id.languageText)
        val currentLang = getSavedLanguage() ?: "en"
        languageText?.text = if (currentLang == "en") "EN" else "RU"
        
        languageView?.setOnClickListener {
            val newLang = if (currentLang == "en") "ru" else "en"
            updateLocale(newLang)
        }

        colorSchemeMenuItem = menu?.findItem(R.id.action_color_scheme)
        updateThemeIcon()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_color_scheme -> {
                toggleColorScheme()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateThemeIcon() {
        val currentScheme = getSavedColorScheme() ?: "dark"
        val iconRes = if (currentScheme == "dark") {
            R.drawable.ic_theme_dark
        } else {
            R.drawable.ic_theme_toggle
        }
        colorSchemeMenuItem?.setIcon(iconRes)
    }

    private fun toggleColorScheme() {
        val schemes = listOf("light", "dark")
        val currentScheme = getSavedColorScheme() ?: "dark"
        val nextIndex = (schemes.indexOf(currentScheme) + 1) % schemes.size
        saveColorScheme(schemes[nextIndex])
        recreate()
    }

    private fun updateLocale(langCode: String) {
        saveLanguage(langCode)
        setLocale(langCode)
        recreate()
    }

    private fun getSavedColorScheme(): String? = 
        getSharedPreferences("ChatPrefs", MODE_PRIVATE).getString("color_scheme", null)

    private fun saveColorScheme(scheme: String) = 
        getSharedPreferences("ChatPrefs", MODE_PRIVATE).edit { putString("color_scheme", scheme) }

    private fun getSavedLanguage(): String? = 
        getSharedPreferences("ChatPrefs", MODE_PRIVATE).getString("language", null)

    private fun saveLanguage(lang: String) = 
        getSharedPreferences("ChatPrefs", MODE_PRIVATE).edit { putString("language", lang) }

    private fun setLocale(lang: String) {
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
    
    private fun setupRecyclerView() {
        adapter = MessageAdapter(
            currentUsername = username,
            isGroupChat = !isDirect,
            onSelectionChanged = { count ->
                if (count > 0) showSelectionToolbar(count) else hideSelectionToolbar()
            },
            onMessageClick = { message ->
                if (selectionMode) return@MessageAdapter
                if (message.imageUrl.isNotEmpty()) {
                    showFullScreenImage(message.imageUrl)
                } else {
                    showReactionsDialog(message)
                }
            }
        )
        messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        messagesRecyclerView.adapter = adapter
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                val filteredMessages = messages.filter { it.roomId == roomId }
                adapter.submitList(filteredMessages) {
                    if (filteredMessages.isNotEmpty()) {
                        messagesRecyclerView.scrollToPosition(filteredMessages.size - 1)
                    }
                }
                val hasUnread = filteredMessages.any { !it.isRead && it.user != username }
                if (hasUnread) viewModel.markRead(username)
            }
        }
        
        lifecycleScope.launch {
            viewModel.users.collect { onlineUsers ->
                if (isDirect) {
                    val otherUser = toolbarTitle.text.toString()
                    val isOnline = onlineUsers.contains(otherUser)
                    val isTyping = viewModel.typingUsers.value[roomId]?.contains(otherUser) == true
                    if (!isTyping) {
                        toolbarSubtitle.text = if (isOnline) getString(R.string.connected_status) else getString(R.string.disconnected_status)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.typingUsers.collect { roomTypingMap ->
                val typingUsersInRoom = roomTypingMap[roomId] ?: emptySet()
                if (isDirect) {
                    val otherUser = toolbarTitle.text.toString()
                    if (typingUsersInRoom.contains(otherUser)) {
                        toolbarSubtitle.text = getString(R.string.is_typing)
                    } else {
                        val isOnline = viewModel.users.value.contains(otherUser)
                        toolbarSubtitle.text = if (isOnline) getString(R.string.connected_status) else getString(R.string.disconnected_status)
                    }
                } else {
                    if (typingUsersInRoom.isNotEmpty()) {
                        toolbarSubtitle.visibility = View.VISIBLE
                        toolbarSubtitle.text = if (typingUsersInRoom.size == 1) {
                            "${typingUsersInRoom.first()} ${getString(R.string.is_typing)}"
                        } else {
                            "${typingUsersInRoom.size} ${getString(R.string.are_typing)}"
                        }
                    } else {
                        toolbarSubtitle.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isTypingSignalSent && s?.isNotEmpty() == true) {
                    viewModel.sendTypingSignal(username, true)
                    isTypingSignalSent = true
                }
                typingJob?.cancel()
                typingJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(3000)
                    if (isTypingSignalSent) {
                        viewModel.sendTypingSignal(username, false)
                        isTypingSignalSent = false
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty() || replyingTo != null) {
                sendMessage(text)
                messageInput.text.clear()
            }
        }

        attachButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        cancelReply.setOnClickListener { hideReplyPreview() }
        closeSelection.setOnClickListener {
            adapter.toggleSelectionMode(false)
            hideSelectionToolbar()
        }

        copyMessages.setOnClickListener {
            val selected = adapter.getSelectedMessages()
            if (selected.isNotEmpty()) {
                val text = selected.joinToString("\n") { "[${it.user}]: ${it.text}" }
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Messages", text))
                Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                adapter.toggleSelectionMode(false)
                hideSelectionToolbar()
            }
        }

        replyMessage.setOnClickListener {
            val selected = adapter.getSelectedMessages()
            if (selected.size == 1) {
                showReplyPreview(selected[0])
                adapter.toggleSelectionMode(false)
                hideSelectionToolbar()
            }
        }

        deleteMessages.setOnClickListener {
            val selected = adapter.getSelectedMessages()
            if (selected.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.delete_messages_title))
                    .setMessage(getString(R.string.delete_messages_confirm))
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        selected.forEach { viewModel.deleteMessage(it) }
                        adapter.toggleSelectionMode(false)
                        hideSelectionToolbar()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        }
    }

    private fun showFullScreenImage(imageUrl: String) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val frameLayout = android.widget.FrameLayout(this)
        frameLayout.setBackgroundColor(android.graphics.Color.BLACK)
        
        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        val progressBar = android.widget.ProgressBar(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
        }
        
        frameLayout.addView(imageView)
        frameLayout.addView(progressBar)
        
        dialog.setContentView(frameLayout)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        
        imageView.setOnClickListener { dialog.dismiss() }
        
        com.bumptech.glide.Glide.with(this)
            .load(imageUrl)
            .placeholder(null)
            .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(e: com.bumptech.glide.load.engine.GlideException?, model: Any?, target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean): Boolean {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@NewChatActivity, "Failed to load image", Toast.LENGTH_SHORT).show()
                    return false
                }
                override fun onResourceReady(resource: android.graphics.drawable.Drawable, model: Any, target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>, dataSource: com.bumptech.glide.load.DataSource, isFirstResource: Boolean): Boolean {
                    progressBar.visibility = View.GONE
                    return false
                }
            })
            .into(imageView)
            
        dialog.show()
    }

    private fun showSelectionToolbar(count: Int) {
        selectionMode = true
        invalidateOptionsMenu()
        toolbarContent.visibility = View.GONE
        selectionToolbar.visibility = View.VISIBLE
        selectionCountText.text = count.toString()
        toolbar.navigationIcon = null
        replyMessage.visibility = if (count == 1) View.VISIBLE else View.GONE
    }

    private fun hideSelectionToolbar() {
        selectionMode = false
        invalidateOptionsMenu()
        selectionToolbar.visibility = View.GONE
        toolbarContent.visibility = View.VISIBLE
        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.navigationIcon?.let {
            val wrapped = DrawableCompat.wrap(it)
            DrawableCompat.setTint(wrapped, getColor(R.color.white))
            toolbar.navigationIcon = wrapped
        }
    }

    private fun showReplyPreview(message: Message) {
        replyingTo = message
        replyUser.text = message.user
        replyText.text = message.text
        replyPreview.visibility = View.VISIBLE
        messageInput.requestFocus()
    }

    private fun hideReplyPreview() {
        replyingTo = null
        replyPreview.visibility = View.GONE
    }

    private fun sendMessage(text: String, imageUrl: String = "") {
        val message = Message(
            user = username,
            text = text,
            timestamp = System.currentTimeMillis(),
            roomId = roomId,
            imageUrl = imageUrl,
            repliedToMessageId = replyingTo?.id ?: "",
            repliedToUser = replyingTo?.user ?: "",
            repliedToText = replyingTo?.text ?: ""
        )
        viewModel.sendMessage(message)
        hideReplyPreview()
    }

    private fun showReactionsDialog(message: Message) {
        val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")
        val dialogView = layoutInflater.inflate(R.layout.dialog_reactions, null)
        val reactionsContainer = dialogView.findViewById<LinearLayout>(R.id.reactionsContainer)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        emojis.forEach { emoji ->
            val textView = TextView(this).apply {
                text = emoji
                textSize = 30f
                setPadding(16, 16, 16, 16)
                setOnClickListener {
                    viewModel.setReaction(message.id, username, emoji)
                    dialog.dismiss()
                }
            }
            reactionsContainer.addView(textView)
        }

        dialogView.findViewById<View>(R.id.menuReply).setOnClickListener {
            showReplyPreview(message)
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.menuCopy).setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("message", message.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.menuDelete).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_messages_title))
                .setMessage(getString(R.string.delete_messages_confirm))
                .setPositiveButton(getString(R.string.delete)) { _, _ ->
                    viewModel.deleteMessage(message)
                    dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        dialog.show()
    }

    private fun uploadImageMessage(uri: Uri) {
        attachButton.visibility = View.INVISIBLE
        uploadProgressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.readBytes() } ?: return@launch
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("image", "image.jpg", bytes.toRequestBody("image/jpeg".toMediaTypeOrNull()))
                    .build()
                val request = Request.Builder().url("http://159.195.38.145:8082/upload-image").post(requestBody).build()
                val response = withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val url = JSONObject(responseBody).optString("url", "")
                    if (url.isNotEmpty()) sendMessage("", url)
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(this@NewChatActivity, "Failed to upload image", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@NewChatActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            } finally {
                withContext(Dispatchers.Main) {
                    attachButton.visibility = View.VISIBLE
                    uploadProgressBar.visibility = View.GONE
                }
            }
        }
    }


    private fun applySavedColorScheme() {
        val themeId = when (getSavedColorScheme()) {
            "light" -> R.style.Base_Theme_MsgClientAndroid
            else -> R.style.Theme_MsgClientAndroid_Dark
        }
        setTheme(themeId)
    }

    private fun applySavedLanguage() {
        getSavedLanguage()?.let { setLocale(it) }
    }
}
