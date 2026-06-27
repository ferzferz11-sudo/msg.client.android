package lavender.client.android.ui.ai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.R
import lavender.client.android.data.ai.AgentStatus
import lavender.client.android.data.ai.AiV2ChatMessage
import lavender.client.android.data.ai.AiV2ChatUseCase
import lavender.client.android.data.ai.RateLimitCache
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.session.SessionManager
import lavender.client.android.network.HttpClient
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.chat.widget.ChatMessageAdapter
import lavender.client.android.ui.chat.widget.ChatMessageItem
import lavender.client.android.ui.chat.widget.ChatWidget
import lavender.client.android.ui.widget.CommandBottomSheet
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AiV2ChatActivity : AppCompatActivity() {

    private lateinit var viewModel: AiV2ChatViewModel
    private lateinit var adapter: ChatMessageAdapter
    private lateinit var chatWidget: ChatWidget
    private lateinit var progressBar: ProgressBar
    private val rateLimitCache = RateLimitCache()
    private val handler = Handler(Looper.getMainLooper())
    private var rateLimitRunnable: Runnable? = null

    private var userId: String = ""
    private var sessionId: String = ""
    private var agentId: String = ""
    private var agentName: String = ""
    private var agentIds: List<String> = emptyList()
    private var agentNames: List<String> = emptyList()
    private var pendingImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageSelected(it) }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            pendingImageUri?.let { handleImageSelected(it) }
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleFileSelected(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_v2_chat)

        sessionId = intent.getStringExtra("SESSION_ID") ?: ""
        agentId = intent.getStringExtra("AGENT_ID") ?: ""
        agentName = intent.getStringExtra("AGENT_NAME") ?: ""
        userId = SessionManager.session.value.userId

        // Multi-agent support
        agentIds = intent.getStringArrayListExtra("AGENT_IDS") ?: emptyList()
        agentNames = intent.getStringArrayListExtra("AGENT_NAMES") ?: emptyList()

        // If single agent passed as extras
        if (agentIds.isEmpty() && agentId.isNotEmpty()) {
            agentIds = listOf(agentId)
            agentNames = listOf(agentName)
        }

        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory).get(AiV2ChatViewModel::class.java)

        chatWidget = findViewById(R.id.chatWidget)
        progressBar = findViewById(R.id.progressBar)

        setupToolbar()
        setupRecyclerView()
        setupInput()
        setupAttachButton()
        observeState()
        ThemeUi.bind(this, SessionManager.session.value.username)
        loadAgentStatus()

        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            chatWidget.bottomPanel.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = if (isImeVisible) imeInsets.bottom else systemBars.bottom
            }
            insets
        }

        if (sessionId.isEmpty()) {
            val title = when {
                agentNames.size > 1 -> "${getString(R.string.ai_chat_with)} ${agentNames.joinToString(", ")}"
                agentNames.isNotEmpty() -> agentNames.first()
                else -> getString(R.string.ai_v2_chat_title)
            }
            chatWidget.setToolbarTitle(title)
        } else {
            val title = when {
                agentNames.size > 1 -> "${getString(R.string.ai_chat_with)} ${agentNames.joinToString(", ")}"
                agentNames.isNotEmpty() -> agentNames.first()
                else -> agentName.ifEmpty { getString(R.string.ai_v2_chat_title) }
            }
            chatWidget.setToolbarTitle(title)
            viewModel.loadHistory(sessionId)
        }

        intent.getStringExtra("PREFILL_MESSAGE")?.let {
            chatWidget.messageInput.setText(it)
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.inflateMenu(R.menu.ai_chat_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    val intent = Intent(this, AiChatSettingsActivity::class.java)
                    intent.putExtra("SESSION_ID", sessionId)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
        toolbar.setNavigationOnClickListener { finish() }

        val primaryAgentId = agentIds.firstOrNull() ?: agentId
        chatWidget.setToolbarTitle(
            when {
                agentNames.size > 1 -> "${getString(R.string.ai_chat_with)} ${agentNames.size} ${getString(R.string.ai_agents)}"
                agentNames.isNotEmpty() -> agentNames.first()
                else -> getString(R.string.ai_v2_chat_title)
            }
        )
        chatWidget.setToolbarAgentIcon(getAgentEmoji(primaryAgentId), true)
        chatWidget.setToolbarAvatar(false)
    }

    private fun loadAgentStatus() {
        val primaryAgentId = agentIds.firstOrNull() ?: agentId
        if (primaryAgentId.isEmpty()) return

        lifecycleScope.launch {
            val agent = withContext(Dispatchers.IO) {
                try { AiV2ChatUseCase.getAgent(primaryAgentId) } catch (_: Exception) { null }
            }
            agent?.let {
                val status = AgentStatus.fromProviderConfig(it.providerConfig)
                val statusText = when (status) {
                    AgentStatus.AVAILABLE -> getString(R.string.ai_status_available)
                    AgentStatus.SERVER_KEY -> getString(R.string.ai_status_server_key)
                    AgentStatus.NEEDS_KEY -> getString(R.string.ai_status_needs_key)
                }
                val dot = when (status) {
                    AgentStatus.AVAILABLE -> "\u2022"
                    AgentStatus.SERVER_KEY -> "\u2022"
                    AgentStatus.NEEDS_KEY -> "\u2022"
                }
                val color = when (status) {
                    AgentStatus.AVAILABLE -> 0xFF4CAF50.toInt()
                    AgentStatus.SERVER_KEY -> 0xFFFFC107.toInt()
                    AgentStatus.NEEDS_KEY -> 0xFFF44336.toInt()
                }
                chatWidget.setToolbarInfo("$dot $statusText")
                chatWidget.toolbarInfo.setTextColor(color)
            }
        }
    }

    private fun getAgentEmoji(agentId: String): String {
        return when (agentId) {
            "reve" -> "\uD83C\uDFA8"
            "vision" -> "\uD83D\uDC41"
            "mimo" -> "\uD83E\uDD16"
            "assistant" -> "\uD83E\uDDE0"
            "developer" -> "\uD83D\uDCBB"
            "devops" -> "\u2699\uFE0F"
            "architect" -> "\uD83C\uDFD7\uFE0F"
            "writer" -> "\u270D\uFE0F"
            "analyst" -> "\uD83D\uDCCA"
            "translator" -> "\uD83C\uDF10"
            "hermes" -> "\uD83D\uDD2C"
            else -> "\uD83E\uDD16"
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatMessageAdapter(
            currentUserId = userId,
            showAvatars = true,
            showNames = true
        )
        chatWidget.setAdapter(adapter)
    }

    private fun setupInput() {
        chatWidget.setOnSendMessageListener { message ->
            if (message.isNotEmpty()) {
                sendMessage(message)
            }
        }

        chatWidget.sendButton.visibility = View.GONE

        chatWidget.messageInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val hasText = s?.toString()?.trim()?.isNotEmpty() == true
                chatWidget.sendButton.visibility = if (hasText) View.VISIBLE else View.GONE
                chatWidget.attachButton.visibility = if (hasText) View.GONE else View.VISIBLE
            }
        })

        chatWidget.attachButton.visibility = View.VISIBLE

        chatWidget.commandButton.setOnClickListener { showCommandMenu() }
    }

    private fun showCommandMenu() {
        val commands = listOf(
            CommandBottomSheet.CommandInfo("/new", getString(R.string.ai_cmd_new)),
            CommandBottomSheet.CommandInfo("/clear", getString(R.string.ai_cmd_clear)),
            CommandBottomSheet.CommandInfo("/history", getString(R.string.ai_cmd_history)),
            CommandBottomSheet.CommandInfo("/settings", getString(R.string.ai_cmd_settings)),
            CommandBottomSheet.CommandInfo("/model", getString(R.string.ai_cmd_model)),
            CommandBottomSheet.CommandInfo("/system", getString(R.string.ai_cmd_system_prompt)),
            CommandBottomSheet.CommandInfo("/tools", getString(R.string.ai_cmd_tools))
        )
        CommandBottomSheet(
            context = this,
            commands = commands,
            onCommandSelected = { cmd ->
                if (cmd.command == "/settings") {
                    val intent = Intent(this, AiChatSettingsActivity::class.java)
                    intent.putExtra("SESSION_ID", sessionId)
                    startActivity(intent)
                } else if (cmd.command == "/new") {
                    sessionId = ""
                    agentId = agentIds.firstOrNull() ?: ""
                    viewModel.clearMessages()
                    chatWidget.setToolbarTitle(agentNames.firstOrNull() ?: getString(R.string.ai_v2_chat_title))
                } else {
                    chatWidget.messageInput.setText(cmd.command + " ")
                    chatWidget.messageInput.setSelection(cmd.command.length + 1)
                }
            }
        ).buildAndShow()
    }

    private fun setupAttachButton() {
        chatWidget.setOnAttachClickListener {
            showImagePickerDialog()
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf(
            getString(R.string.ai_pick_from_gallery),
            getString(R.string.ai_take_photo),
            getString(R.string.ai_pick_file)
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.attach_file))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> imagePickerLauncher.launch("image/*")
                    1 -> openCamera()
                    2 -> filePickerLauncher.launch("*/*")
                }
            }
            .show()
    }

    private fun openCamera() {
        val imageFile = java.io.File(cacheDir, "ai_chat_image_${System.currentTimeMillis()}.jpg")
        pendingImageUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            imageFile
        )
        cameraLauncher.launch(pendingImageUri!!)
    }

    private fun handleImageSelected(uri: Uri) {
        val message = chatWidget.messageInput.text.toString().trim()
        sendMessageWithImage(message, uri)
    }

    private fun sendMessageWithImage(message: String, imageUri: Uri) {
        if (!canSendRequest()) {
            val waitMs = rateLimitCache.getTimeUntilReset(agentId)
            showRateLimitUI(waitMs)
            return
        }

        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val imageBytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                }
                if (imageBytes == null || imageBytes.isEmpty()) {
                    Toast.makeText(this@AiV2ChatActivity, getString(R.string.ai_upload_failed), Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    return@launch
                }

                rateLimitCache.recordRequest(agentId)

                val userMessage = AiV2ChatMessage(
                    sessionId = sessionId,
                    role = "user",
                    content = message,
                    imageUrl = imageUri.toString(),
                    timestamp = System.currentTimeMillis()
                )
                viewModel.addMessage(userMessage)

                viewModel.sendMessage(
                    userId = userId,
                    sessionId = sessionId,
                    message = message,
                    agentId = agentId,
                    images = listOf(imageBytes)
                )

                chatWidget.messageInput.setText("")
            } catch (e: Exception) {
                Toast.makeText(this@AiV2ChatActivity, getString(R.string.ai_upload_failed), Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun handleFileSelected(uri: Uri) {
        val message = chatWidget.messageInput.text.toString().trim()
        uploadAndSendFile(message, uri)
    }

    private fun uploadAndSendFile(message: String, fileUri: Uri) {
        if (!canSendRequest()) {
            val waitMs = rateLimitCache.getTimeUntilReset(agentId)
            showRateLimitUI(waitMs)
            return
        }

        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val url = withContext(Dispatchers.IO) {
                    uploadFile(fileUri)
                }
                if (url.isNotEmpty()) {
                    val fileName = getFileName(fileUri) ?: "file"
                    val text = if (message.isNotEmpty()) "$message\n\nFile: $fileName\n$url" else "File: $fileName\n$url"
                    rateLimitCache.recordRequest(agentId)

                    val userMessage = AiV2ChatMessage(
                        sessionId = sessionId,
                        role = "user",
                        content = text,
                        timestamp = System.currentTimeMillis()
                    )
                    viewModel.addMessage(userMessage)

                    viewModel.sendMessage(
                        userId = userId,
                        sessionId = sessionId,
                        message = text,
                        agentId = agentId
                    )

                    chatWidget.messageInput.setText("")
                } else {
                    Toast.makeText(this@AiV2ChatActivity, getString(R.string.ai_upload_failed), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AiV2ChatActivity, getString(R.string.ai_upload_failed), Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun uploadFile(uri: Uri): String {
        val contentResolver = contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: return ""
        val bytes = inputStream.use { it.readBytes() }
        val fileName = getFileName(uri) ?: "file"

        val body = MultipartBody.Part.createFormData("file", fileName,
            bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))

        val serverUrl = CredentialStore.getHttpServerUrl(this)
        val request = Request.Builder()
            .url("$serverUrl/upload-file")
            .post(MultipartBody.Builder().setType(MultipartBody.FORM).addPart(body).build())
            .build()

        val response = HttpClient.client.newCall(request).execute()
        val responseBody = response.body.string()

        return if (response.isSuccessful) {
            try {
                JSONObject(responseBody).getString("url")
            } catch (_: Exception) {
                if (responseBody.startsWith("http")) responseBody else ""
            }
        } else ""
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) name = it.getString(idx)
                }
            }
        }
        if (name == null) {
            name = uri.path
            val c = name?.lastIndexOf('/') ?: -1
            if (c != -1) name = name?.substring(c + 1)
        }
        return name
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messages ->
                        val items = messages.map { msg ->
                            ChatMessageItem(
                                id = msg.id,
                                content = msg.content,
                                imageUrl = msg.imageUrl,
                                senderId = msg.agentId,
                                senderName = msg.agentName.ifEmpty { "AI" },
                                senderEmoji = getAgentEmoji(msg.agentId),
                                timestamp = msg.timestamp,
                                isCurrentUser = msg.role == "user",
                                isTyping = msg.isStreaming && msg.content.isEmpty()
                            )
                        }
                        adapter.submitList(items)
                        if (items.isNotEmpty()) {
                            chatWidget.messagesRecyclerView.scrollToPosition(items.size - 1)
                        }
                    }
                }

                launch {
                    viewModel.streamState.collect { state ->
                        progressBar.visibility = if (state.isTyping) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.rateLimitEvent.collect { triggered ->
                        if (triggered) {
                            handleRateLimit()
                            viewModel.clearRateLimitEvent()
                        }
                    }
                }
            }
        }
    }

    private fun sendMessage(message: String) {
        if (!canSendRequest()) {
            val waitMs = rateLimitCache.getTimeUntilReset(agentId)
            showRateLimitUI(waitMs)
            return
        }

        rateLimitCache.recordRequest(agentId)

        val userMessage = AiV2ChatMessage(
            sessionId = sessionId,
            role = "user",
            content = message,
            timestamp = System.currentTimeMillis()
        )
        viewModel.addMessage(userMessage)

        viewModel.sendMessage(
            userId = userId,
            sessionId = sessionId,
            message = message,
            agentId = agentId
        )
    }

    private fun canSendRequest(): Boolean {
        return rateLimitCache.getRemaining(agentId) > 0
    }

    private fun handleRateLimit() {
        rateLimitCache.undoLastRecord(agentId)
        val waitMs = rateLimitCache.getTimeUntilReset(agentId)
        showRateLimitUI(waitMs)
    }

    private fun showRateLimitUI(waitMs: Long) {
        val seconds = (waitMs / 1000).coerceAtLeast(1)
        Toast.makeText(this, "Rate limit exceeded. Wait ${seconds}s", Toast.LENGTH_SHORT).show()

        chatWidget.messageInput.isEnabled = false
        rateLimitRunnable?.let { handler.removeCallbacks(it) }
        rateLimitRunnable = Runnable {
            chatWidget.messageInput.isEnabled = true
        }
        handler.postDelayed(rateLimitRunnable!!, waitMs)
    }

    override fun onDestroy() {
        super.onDestroy()
        rateLimitRunnable?.let { handler.removeCallbacks(it) }
    }

    companion object {
    }
}
