package lavender.client.android.ui.hermes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.R
import lavender.client.android.audio.AudioUploader
import lavender.client.android.data.models.AgentInfo
import lavender.client.android.data.models.HermesMessage
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.chat.widget.ChatMessageAdapter
import lavender.client.android.ui.chat.widget.ChatMessageItem
import lavender.client.android.ui.chat.widget.ChatWidget
import lavender.client.android.ui.chat.widget.MentionItem
import lavender.client.android.ui.widget.StandardBottomSheet
import lavender.client.android.ui.audio.AudioRecordingView
import java.io.File

/**
 * HermesChatActivity — чат с оркестратором агентов.
 *
 * Использует единый ChatWidget.
 * Агенты отображаются как участники группового чата.
 * Поддержка меншена: @ → popup с выбором агента.
 */
class HermesChatActivity : AppCompatActivity() {

    private lateinit var viewModel: HermesChatViewModel
    private lateinit var adapter: ChatMessageAdapter
    private lateinit var chatWidget: ChatWidget
    private lateinit var progressBar: ProgressBar

    private var userId: String = ""
    private var chatId: String = ""

    // Agent registry
    private val agents = mutableListOf<AgentInfo>()
    private var activeAgentId: String = ""

    // Mention state
    private var mentionQuery: String = ""
    private var mentionStartPos: Int = -1
    private var isMentionActive = false

    // Mention items derived from agents
    private val mentionItems: List<MentionItem>
        get() = agents.map { agent ->
            MentionItem(
                id = agent.id,
                name = agent.name,
                description = agent.description,
                emoji = agent.icon.ifEmpty { "🤖" },
                mentionTag = agent.name.lowercase().replace(" ", "_")
            )
        }

    // Activity result launchers
    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            // Photo taken successfully - handled by createImageUri()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handleImageSelection(result.data)
        }
    }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handleFileSelection(result.data)
        }
    }

    private val pickLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handleLocationSelection(result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hermes_chat)

        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        userId = SessionManager.session.value.username

        viewModel = androidx.lifecycle.ViewModelProvider(this)[HermesChatViewModel::class.java]

        chatWidget = findViewById(R.id.chatWidget)
        progressBar = findViewById(R.id.progressBar)

        setupToolbar()
        setupRecyclerView()
        setupInput()
        setupMentionListener()
        observeState()
        ThemeUi.bind(this, userId)

        // Handle window insets
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

        android.util.Log.d("HermesChatActivity", "onCreate: chatId=$chatId userId=$userId")

        if (viewModel.agents.value.isEmpty()) {
            viewModel.initPresetAgents()
        }
        agents.clear()
        agents.addAll(viewModel.agents.value)
        updateAgentParticipants()

        if (chatId.isEmpty()) {
            // New session — create on server
            viewModel.createSession(userId)
        } else {
            // Existing session from chat list
            val agentId = intent.getStringExtra("ACTIVE_AGENT_ID") ?: ""
            val mode = intent.getStringExtra("AGENT_MODE") ?: "single"
            viewModel.setExistingSession(chatId, userId, agentId, mode)
            viewModel.loadHistory()
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        chatWidget.setToolbarTitle(intent.getStringExtra("CHAT_NAME") ?: "Lava AI")
        chatWidget.setToolbarAgentIcon("🎼", true)
        chatWidget.setToolbarAvatar(false)

        updateAgentParticipants()
    }

    private fun updateAgentParticipants() {
        chatWidget.clearParticipantChips()

        agents.forEach { agent ->
            val chip = createAgentChip(agent, agent.id == activeAgentId)
            chatWidget.addParticipantChip(chip)
        }
    }

    private fun createAgentChip(agent: AgentInfo, isActive: Boolean): com.google.android.material.chip.Chip {
        val chip = com.google.android.material.chip.Chip(this).apply {
            text = "${agent.icon.ifEmpty { "🤖" }} ${agent.name}"
            textSize = 11f
            isClickable = true
            isCheckable = false

            if (isActive) {
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.chip_background_active, null)
                )
                setTextColor(resources.getColor(R.color.chip_text_active, null))
                chipStrokeWidth = 2f
                chipStrokeColor = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.chip_stroke_active, null)
                )
            } else {
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.chip_background, null)
                )
                setTextColor(resources.getColor(R.color.chip_text, null))
                chipStrokeWidth = 0f
            }

            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 4.dpToPx()
            }
            setOnClickListener {
                switchToAgent(agent)
            }
        }
        return chip
    }

    private fun switchToAgent(agent: AgentInfo) {
        activeAgentId = agent.id
        viewModel.switchAgent(agent.id, agents)
        chatWidget.setToolbarTitle(agent.name)
        chatWidget.setToolbarAgentIcon(agent.icon.ifEmpty { "🤖" }, true)
        chatWidget.setToolbarAvatar(false)
        updateAgentParticipants()
        Toast.makeText(this, "Переключение на ${agent.name}", Toast.LENGTH_SHORT).show()
    }

    private fun setupRecyclerView() {
        adapter = ChatMessageAdapter(
            currentUserId = userId,
            showAvatars = false,
            showNames = true,
            onMessageClick = { item -> showReactionsDialog(item) },
            onMessageLongClick = { item -> showMessageMenu(item) }
        )
        chatWidget.setAdapter(adapter)
    }

    private fun setupInput() {
        // Show attach/audio buttons for Hermes chat
        chatWidget.attachButton.visibility = View.VISIBLE
        chatWidget.audioButton.visibility = View.VISIBLE

        chatWidget.setOnSendMessageListener { text ->
            val session = viewModel.currentSession.value
            if (session == null) {
                Toast.makeText(this, "Сессия не создана", Toast.LENGTH_SHORT).show()
                return@setOnSendMessageListener
            }

            // Add user message immediately
            val userMessage = ChatMessageItem(
                id = java.util.UUID.randomUUID().toString(),
                content = text,
                senderId = userId,
                senderName = "Вы",
                isCurrentUser = true,
                timestamp = System.currentTimeMillis()
            )
            adapter.submitList(adapter.currentList + userMessage)
            chatWidget.scrollToBottom()

            // Send via ViewModel
            val currentAgent = viewModel.currentAgent.value
            viewModel.sendMessage(
                text = text,
                agentId = currentAgent?.id ?: ""
            )
        }

        chatWidget.setOnEmojiClickListener {
            chatWidget.showEmojiPicker()
        }

        chatWidget.attachButton.setOnClickListener {
            showAttachmentSheet()
        }

        chatWidget.audioButton.setOnClickListener {
            showVoiceRecorder()
        }
    }

    private fun showAttachmentSheet() {
        val sheet = StandardBottomSheet(this, R.layout.dialog_attachment_picker)
        sheet.contentContainer?.let { container ->
            // Camera
            container.findViewById<android.widget.LinearLayout>(R.id.attachCamera)?.setOnClickListener {
                takePhotoLauncher.launch(createImageUri() ?: run {
                    Toast.makeText(this, "Не удалось создать URI для фото", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                })
                sheet.dismiss()
            }

            // Gallery
            container.findViewById<android.widget.LinearLayout>(R.id.attachGallery)?.setOnClickListener {
                pickImageLauncher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                })
                sheet.dismiss()
            }

            // File
            container.findViewById<android.widget.LinearLayout>(R.id.attachFile)?.setOnClickListener {
                pickFileLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                })
                sheet.dismiss()
            }

            // Location
            container.findViewById<android.widget.LinearLayout>(R.id.attachLocation)?.setOnClickListener {
                pickLocationLauncher.launch(Intent(this, MapPickerActivity::class.java))
                sheet.dismiss()
            }
        }
        sheet.show()
    }

    private fun showVoiceRecorder() {
        val sheet = StandardBottomSheet(this, R.layout.audio_recording_view)
        sheet.contentContainer?.let { container ->
            val audioView = container.findViewById<AudioRecordingView>(R.id.audioRecordingView) ?: 
                AudioRecordingView(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

            // Clear any existing views and add audio view
            container.removeAllViews()
            container.addView(audioView)

            audioView.setOnRecordingStarted {
                // Show upload progress when recording starts
                chatWidget.showUploadProgress("Запись аудио...")
            }

            audioView.setOnRecordingFinished { file, duration ->
                sheet.dismiss()
                if (file != null) {
                    // Show upload progress
                    chatWidget.showUploadProgress("Отправка аудио...")
                    lifecycleScope.launch {
                        val result = AudioUploader(this@HermesChatActivity).uploadAudio(file, duration)
                        runOnUiThread {
                            chatWidget.hideUploadProgress()
                            if (result.success && result.url.isNotEmpty() && !result.url.contains("404")) {
                                // Send voice message
                                val session = viewModel.currentSession.value
                                val currentAgent = viewModel.currentAgent.value
                                viewModel.sendMessage(
                                    text = "Голосовое сообщение",
                                    agentId = currentAgent?.id ?: "",
                                    voiceUrl = result.url,
                                    duration = result.duration
                                )
                            } else {
                                Toast.makeText(
                                    this@HermesChatActivity,
                                    "Не удалось загрузить аудио: ${if (result.url.contains("404")) "Ошибка сервера 404" else result.error}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this@HermesChatActivity, "Не удалось записать аудио", Toast.LENGTH_SHORT).show()
                }
            }

            audioView.setOnRecordingCancelled {
                sheet.dismiss()
            }
        }
        sheet.show()
    }

    private fun createImageUri(): Uri? {
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "temp_photo_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    }

    private fun handleImageSelection(data: Intent?) {
        val uris = mutableSetOf<Uri>()
        data?.data?.let { uris.add(it) }
        data?.clipData?.let { clipData ->
            for (i in 0 until clipData.itemCount) uris.add(clipData.getItemAt(i).uri)
        }
        if (uris.isNotEmpty()) {
            // For now, just send as regular message - could enhance to show preview
            val fileList = uris.joinToString(", ") { it.toString() }
            val session = viewModel.currentSession.value
            val currentAgent = viewModel.currentAgent.value
            viewModel.sendMessage(
                text = "Изображение: $fileList",
                agentId = currentAgent?.id ?: ""
            )
        }
    }

    private fun handleFileSelection(data: Intent?) {
        val uris = mutableSetOf<Uri>()
        data?.data?.let { uris.add(it) }
        data?.clipData?.let { clipData ->
            for (i in 0 until clipData.itemCount) uris.add(clipData.getItemAt(i).uri)
        }
        if (uris.isNotEmpty()) {
            val fileList = uris.joinToString(", ") { it.toString() }
            val session = viewModel.currentSession.value
            val currentAgent = viewModel.currentAgent.value
            viewModel.sendMessage(
                text = "Файл: $fileList",
                agentId = currentAgent?.id ?: ""
            )
        }
    }

    private fun handleLocationSelection(data: Intent?) {
        data?.let {
            val lat = it.getDoubleExtra("lat", 0.0)
            val lng = it.getDoubleExtra("lng", 0.0)
            if (lat != 0.0 || lng != 0.0) {
                val session = viewModel.currentSession.value
                val currentAgent = viewModel.currentAgent.value
                viewModel.sendMessage(
                    text = "geo:$lat,$lng",
                    agentId = currentAgent?.id ?: ""
                )
            }
        }
    }

    // ===== Reactions & Message Menu =====

    private fun showReactionsDialog(item: ChatMessageItem) {
        val sheet = StandardBottomSheet(this, R.layout.dialog_reactions)
        val container = sheet.findViewById<android.widget.LinearLayout>(R.id.reactionsContainer)
        listOf("👍", "💯", "🔥", "✅", "❤️", "😂", "😮", "😢", "🙏").forEach { e ->
            val tv = android.widget.TextView(this).apply {
                text = e
                textSize = 30f
                setPadding(16, 8, 16, 8)
                val v2 = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, v2, true)
                setBackgroundResource(v2.resourceId)
                setOnClickListener {
                    // Send reaction via gRPC
                    viewModel.setReaction(item.id, userId, e)
                    sheet.dismiss()
                }
            }
            container?.addView(tv)
        }
        sheet.show()
    }

    private fun showMessageMenu(item: ChatMessageItem) {
        val actions = listOf(
            SheetAction(R.id.actionCopy, R.drawable.ic_copy, "Копировать") {
                copyToClipboard(item.content)
            },
            SheetAction(R.id.actionReply, R.drawable.ic_reply, "Ответить") {
                chatWidget.showReplyPreview(item.senderName, item.content)
            }
        )
        lavender.client.android.ui.widget.ActionBottomSheet(this, actions).show()
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("message", text))
        Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
    }

    // ===== Mention logic =====

    private fun setupMentionListener() {
        // Track @ in input
        chatWidget.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (s == null) return
                detectMention(s, start + count)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Handle mention selection
        chatWidget.setOnMentionSelectedListener { item ->
            insertMention(item)
        }
    }

    private fun detectMention(text: CharSequence, cursorPos: Int) {
        // Safety: toString() bypasses SpannableBuilder issues
        val textStr = text.toString()
        val len = textStr.length

        if (cursorPos <= 0 || cursorPos > len) {
            hideMention()
            return
        }

        // Find the last @ before cursor
        val beforeCursor = textStr.substring(0, cursorPos)
        val atPos = beforeCursor.lastIndexOf('@')

        if (atPos == -1) {
            hideMention()
            return
        }

        // Check that @ is at word boundary or start of text
        if (atPos > 0 && !beforeCursor[atPos - 1].isWhitespace()) {
            hideMention()
            return
        }

        // Extract query after @
        val query = beforeCursor.substring(atPos + 1)

        // If space typed after @, close mention
        if (query.contains(" ")) {
            hideMention()
            return
        }

        // Show mention popup
        mentionStartPos = atPos
        mentionQuery = query
        isMentionActive = true

        if (agents.isNotEmpty()) {
            chatWidget.showMentionList(mentionItems, query)
        }
    }

    private fun hideMention() {
        isMentionActive = false
        mentionQuery = ""
        mentionStartPos = -1
        chatWidget.hideMentionList()
    }

    private fun insertMention(item: MentionItem) {
        val input = chatWidget.messageInput
        val text = input.text.toString() // toString() for safety

        // Replace @query with @mentionTag
        if (mentionStartPos >= 0) {
            val before = text.substring(0, mentionStartPos)
            val after = if (mentionStartPos + mentionQuery.length + 1 < text.length) {
                text.substring(mentionStartPos + mentionQuery.length + 1)
            } else ""

            val mentionTag = "@${item.mentionTag} "
            val newText = before + mentionTag + after

            input.setText(newText)
            input.setSelection(before.length + mentionTag.length)
        } else {
            // Fallback: append
            val mentionTag = "@${item.mentionTag} "
            input.append(mentionTag)
        }

        hideMention()
    }

    // ===== State observation =====

    private fun observeState() {
        // Messages
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { hermesMessages ->
                    val items = hermesMessages.map { it.toChatMessageItem() }
                    adapter.submitList(items)
                    if (items.isNotEmpty()) {
                        chatWidget.scrollToBottom()
                    }
                }
            }
        }

        // Session
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentSession.collect { session ->
                    session?.let {
                        if (chatId.isNotEmpty() && it.id.isNotEmpty()) {
                            viewModel.loadHistory()
                        }
                    }
                }
            }
        }

        // Typing
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isTyping.collect { isTyping ->
                    val typingText = if (isTyping) {
                        "${viewModel.currentAgent.value?.name ?: "Агент"} печатает..."
                    } else ""
                    chatWidget.setToolbarSubtitle(typingText, isTyping)

                    if (isTyping) {
                        val typingItem = ChatMessageItem(
                            id = "typing",
                            content = "",
                            senderId = "typing",
                            senderName = viewModel.currentAgent.value?.name ?: "Агент",
                            isTyping = true,
                            timestamp = System.currentTimeMillis()
                        )
                        adapter.submitList(adapter.currentList + typingItem)
                        chatWidget.scrollToBottom()
                    } else {
                        val filtered = adapter.currentList.filter { !it.isTyping }
                        adapter.submitList(filtered)
                    }
                }
            }
        }

        // Current agent → update toolbar
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentAgent.collect { agent ->
                    if (agent != null) {
                        activeAgentId = agent.id
                        chatWidget.setToolbarTitle(agent.name)
                        chatWidget.setToolbarAgentIcon(agent.icon.ifEmpty { "🤖" }, true)
                        updateAgentParticipants()
                    } else {
                        activeAgentId = ""
                        chatWidget.setToolbarTitle("Lava AI")
                        chatWidget.setToolbarAgentIcon("🎼", true)
                        updateAgentParticipants()
                    }
                }
            }
        }

        // Loading state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { isLoading ->
                    progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                }
            }
        }

        // Error
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.error.collect { error ->
                    error?.let {
                        Toast.makeText(this@HermesChatActivity, it, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    // ===== Public API =====

    fun addAgent(agent: AgentInfo) {
        if (agents.none { it.id == agent.id }) {
            agents.add(agent)
            updateAgentParticipants()
        }
    }

    fun removeAgent(agentId: String) {
        agents.removeAll { it.id == agentId }
        updateAgentParticipants()
    }

    fun getAgents(): List<AgentInfo> = agents.toList()

    // ===== Helpers =====

    private fun HermesMessage.toChatMessageItem(): ChatMessageItem {
        val agent = viewModel.getAgent(this.agentId)
        return ChatMessageItem(
            id = this.id,
            content = this.content,
            senderId = this.agentId.ifEmpty { "hermes" },
            senderName = this.agentName.ifEmpty { agent?.name ?: "Lava AI" },
            senderEmoji = this.agentIcon.ifEmpty { agent?.icon ?: "🤖" },
            timestamp = this.timestamp,
            isCurrentUser = this.role == "user",
            isRead = true
        )
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}