package lavender.client.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.OWLResponseProto
import lavender.client.android.data.session.SessionManager
import lavender.client.android.ui.adapter.OwlMessage
import lavender.client.android.ui.adapter.OwlMessageAdapter
import lavender.client.android.theme.ui.ThemeUi

class OwlActivity : AppCompatActivity() {

    private lateinit var adapter: OwlMessageAdapter
    private lateinit var sendButton: ImageButton
    private lateinit var messageInput: EditText
    private var currentResponse = ""
    private var isReceiving = false

    // Chat ID — unique per OWL chat
    private var chatId: String = ""
    private var userId: String = ""

    // Free-only models (when using server API key)
    private val freeModels = listOf(
        "openrouter/owl-alpha" to "OWL Alpha 🆓",
        "openai/gpt-oss-20b:free" to "GPT OSS 20B 🆓",
        "openai/gpt-oss-120b:free" to "GPT OSS 120B 🆓",
        "z-ai/glm-4.5-air:free" to "GLM 4.5 Air 🆓",
        "meta-llama/llama-3.3-70b-instruct:free" to "Llama 3.3 70B 🆓",
        "meta-llama/llama-3.2-3b-instruct:free" to "Llama 3.2 3B 🆓",
        "deepseek/deepseek-v4-flash:free" to "DeepSeek V4 Flash 🆓",
        "qwen/qwen3-coder:free" to "Qwen 3 Coder 🆓",
        "qwen/qwen3-next-80b-a3b-instruct:free" to "Qwen 3 Next 80B 🆓",
        "nvidia/nemotron-3-super-120b-a12b:free" to "Nemotron Super 120B 🆓",
        "moonshotai/kimi-k2.6:free" to "Kimi K2.6 🆓",
        "google/gemma-4-26b-a4b-it:free" to "Gemma 4 26B 🆓",
    )

    // All models (free + paid) — shown when user provides their own API key
    private val allModels = freeModels + listOf(
        "anthropic/claude-sonnet-4-20250514" to "Claude Sonnet 4",
        "anthropic/claude-opus-4-20250514" to "Claude Opus 4",
        "openai/gpt-4o" to "GPT-4o",
        "openai/gpt-4o-mini" to "GPT-4o Mini",
        "google/gemini-2.5-pro" to "Gemini 2.5 Pro",
        "google/gemini-2.5-flash" to "Gemini 2.5 Flash",
        "deepseek/deepseek-r1" to "DeepSeek R1",
        "deepseek/deepseek-v3" to "DeepSeek V3",
        "qwen/qwen-2.5-72b-instruct" to "Qwen 2.5 72B",
        "mistralai/mistral-large-2407" to "Mistral Large 2",
    )

    /** Returns the model list to show: free-only if using server key, all if user key is set */
    private fun getModelsForDisplay(): List<Pair<String, String>> {
        return if (userApiKey.isEmpty()) freeModels else allModels
    }
    private var selectedModelIndex = 0
    private var userApiKey = ""

    // Local prefs for per-chat settings (fallback if server unreachable)
    private lateinit var prefs: SharedPreferences

    // Selection mode
    private var selectionMode = false
    private lateinit var selectionToolbar: LinearLayout
    private lateinit var selectionCountText: TextView
    private lateinit var copyMessagesBtn: ImageButton
    private lateinit var deleteMessagesBtn: ImageButton
    private lateinit var toolbarContent: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owl)

        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        userId = SessionManager.session.value.username

        prefs = getSharedPreferences("owl_$chatId", Context.MODE_PRIVATE)
        loadLocalSettings()
        loadCustomModel()

        setupToolbar(hasMenu = true)
        setupRecyclerView()
        setupSelectionToolbar()
        setupInput()
        observeOwlResponses()

        // Apply theme (colors, background image)
        ThemeUi.bind(this, userId)

        // Handle window insets: navigation bar + keyboard
        val bottomPanel = findViewById<com.google.android.material.card.MaterialCardView>(R.id.bottomPanel)
        val rootView = findViewById<View>(android.R.id.content)
        val bottomPanelContent = bottomPanel.findViewById<LinearLayout>(R.id.bottomPanelContent)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            bottomPanel.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = if (isImeVisible) imeInsets.bottom else systemBars.bottom
            }
            bottomPanelContent.setPadding(
                bottomPanelContent.paddingLeft,
                bottomPanelContent.paddingTop,
                bottomPanelContent.paddingRight,
                4.dpToPx()
            )
            insets
        }
        if (chatId.isEmpty()) {
            // No chat ID — show error
            adapter.addMessage(
                OwlMessage(
                    text = "Ошибка: chat_id не указан",
                    isUser = false
                )
            )
            return
        }

        // Handle back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectionMode) {
                    exitSelectionMode()
                } else {
                    finish()
                }
            }
        })

        // Load history from server
        loadHistory()
    }

    private fun loadLocalSettings() {
        selectedModelIndex = prefs.getInt("model_index", 0)
        if (selectedModelIndex >= getModelsForDisplay().size) selectedModelIndex = 0
        userApiKey = prefs.getString("api_key", "") ?: ""
    }

    private fun saveLocalSettings() {
        prefs.edit()
            .putInt("model_index", selectedModelIndex)
            .putString("api_key", userApiKey)
            .apply()
    }

    private fun loadHistory() {
        if (chatId.isEmpty() || userId.isEmpty()) return

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.Job()).launch {
            try {
                val history = GrpcClient.getOwlHistory(chatId, userId)
                runOnUiThread {
                    if (history.isEmpty()) {
                        showWelcomeMessage()
                    } else {
                        for (msg in history) {
                            when (msg.role) {
                                "user" -> adapter.addMessage(OwlMessage(text = msg.content, isUser = true))
                                "assistant" -> adapter.addMessage(OwlMessage(text = msg.content, isUser = false))
                            }
                        }
                        findViewById<RecyclerView>(R.id.messagesRecyclerView).scrollToPosition(adapter.itemCount - 1)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { showWelcomeMessage() }
            }
        }
    }

    private fun showWelcomeMessage() {
        adapter.addMessage(
            OwlMessage(
                text = "Привет! Я — OWL, твой AI-ассистент в Lavender. Спроси меня о чём угодно!\n\n💡 Команды:\n/model — выбрать модель\n/key — задать свой API ключ\n/help — помощь",
                isUser = false
            )
        )
    }

    private fun setupToolbar(hasMenu: Boolean) {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener {
            if (selectionMode) exitSelectionMode()
            else finish()
        }
        toolbar.title = ""

        val avatarView = findViewById<CircleImageView>(R.id.toolbarAvatar)
        avatarView?.setImageResource(R.drawable.ic_notification_logo)

        if (hasMenu) {
            toolbar.inflateMenu(R.menu.owl_menu)
            toolbar.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_owl_settings -> {
                        showSettingsDialog()
                        true
                    }
                    R.id.action_owl_delete -> {
                        confirmDeleteChat()
                        true
                    }
                    else -> false
                }
            }
        }

        toolbarContent = findViewById(R.id.toolbarContent)
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.messagesRecyclerView)
        adapter = OwlMessageAdapter(
            onMessageClick = { position -> showMessageActionsDialog(position) },
            onMessageLongClick = { position -> enterSelectionMode(position) },
            onSelectionChanged = { count -> updateSelectionToolbar(count) },
            onReactionClick = { position, emoji -> adapter.addReaction(position, emoji) }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
    }

    private fun setupSelectionToolbar() {
        selectionToolbar = findViewById(R.id.selectionToolbar)
        selectionCountText = findViewById(R.id.selectionCountText)
        copyMessagesBtn = findViewById(R.id.copyMessages)
        deleteMessagesBtn = findViewById(R.id.deleteMessages)

        copyMessagesBtn.setOnClickListener { copySelectedMessages() }
        deleteMessagesBtn.setOnClickListener { deleteSelectedMessages() }
    }

    private fun showMessageActionsDialog(position: Int) {
        val msg = adapter.getMessageAt(position) ?: return
        if (msg.isTyping) return

        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_message_actions, null)
        sheet.setContentView(view)

        // Quick reactions
        view.findViewById<View>(R.id.reactionThumbsUp)?.setOnClickListener {
            sheet.dismiss()
            adapter.addReaction(position, "👍")
        }
        view.findViewById<View>(R.id.reactionHeart)?.setOnClickListener {
            sheet.dismiss()
            adapter.addReaction(position, "❤️")
        }
        view.findViewById<View>(R.id.reactionLaugh)?.setOnClickListener {
            sheet.dismiss()
            adapter.addReaction(position, "😂")
        }
        view.findViewById<View>(R.id.reactionWow)?.setOnClickListener {
            sheet.dismiss()
            adapter.addReaction(position, "😮")
        }
        view.findViewById<View>(R.id.reactionSad)?.setOnClickListener {
            sheet.dismiss()
            adapter.addReaction(position, "😢")
        }
        view.findViewById<View>(R.id.reactionFire)?.setOnClickListener {
            sheet.dismiss()
            adapter.addReaction(position, "🔥")
        }

        // Copy
        view.findViewById<View>(R.id.menuCopy)?.setOnClickListener {
            sheet.dismiss()
            copyMessage(msg)
        }

        // Delete
        view.findViewById<View>(R.id.menuDelete)?.setOnClickListener {
            sheet.dismiss()
            deleteMessage(position)
        }

        sheet.show()
    }

    private fun copyMessage(msg: OwlMessage) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("message", msg.text))
        Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    private fun deleteMessage(position: Int) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_message))
            .setMessage(getString(R.string.delete_message_confirmation))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                adapter.removeMessages(listOf(position))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun enterSelectionMode(position: Int) {
        selectionMode = true
        adapter.toggleSelectionMode(true)
        adapter.toggleSelection(position)
        showSelectionToolbar(1)
    }

    private fun exitSelectionMode() {
        selectionMode = false
        adapter.exitSelectionMode()
        hideSelectionToolbar()
    }

    private fun copySelectedMessages() {
        val selectedPositions = adapter.getSelectedPositions().sorted()
        val messages = selectedPositions.mapNotNull { adapter.getMessageAt(it) }
        val text = messages.joinToString("\n\n") { "${if (it.isUser) userId else "OWL"}: ${it.text}" }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("messages", text))
        Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        exitSelectionMode()
    }

    private fun deleteSelectedMessages() {
        val count = adapter.getSelectedPositions().size
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_messages_title))
            .setMessage(getString(R.string.delete_messages_confirmation, count))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                val positions = adapter.getSelectedPositions().toList()
                adapter.removeMessages(positions)
                exitSelectionMode()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showSelectionToolbar(count: Int) {
        selectionMode = true
        toolbarContent.isVisible = false
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close)
        selectionToolbar.isVisible = true
        selectionCountText.text = count.toString()
    }

    private fun updateSelectionToolbar(count: Int) {
        selectionCountText.text = count.toString()
        if (count == 0) {
            hideSelectionToolbar()
        }
    }

    private fun hideSelectionToolbar() {
        selectionMode = false
        toolbarContent.isVisible = true
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        selectionToolbar.isVisible = false
    }

    private fun setupInput() {
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)

        sendButton.setOnClickListener {
            if (selectionMode) {
                exitSelectionMode()
                return@setOnClickListener
            }
            val text = messageInput.text.toString().trim()
            if (text.isEmpty() || isReceiving) return@setOnClickListener

            if (chatId.isEmpty()) {
                Toast.makeText(this, "Ошибка: chat не создан", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (text.startsWith("/")) {
                handleCommand(text)
                messageInput.setText("")
                return@setOnClickListener
            }

            sendMessage(text)
        }
    }

    private fun observeOwlResponses() {
        // OWL responses are handled via callback in sendMessage()
        // This method is kept for compatibility with onCreate() call
    }

    // ===== Settings dialog =====

    private fun showSettingsDialog() {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_owl_settings, null)
        sheet.setContentView(view)

        // Model picker
        view.findViewById<View>(R.id.menuModelPicker)?.setOnClickListener {
            sheet.dismiss()
            showModelPickerDialog()
        }

        // API Key
        view.findViewById<View>(R.id.menuApiKey)?.setOnClickListener {
            sheet.dismiss()
            showApiKeyDialog()
        }

        sheet.show()
    }

    private fun handleCommand(command: String) {
        val parts = command.split(" ", limit = 2)
        when (parts[0].lowercase()) {
            "/model" -> {
                showModelPickerDialog()
            }
            "/key" -> {
                showApiKeyDialog()
            }
            "/help" -> {
                val keyStatus = if (userApiKey.isEmpty()) "🔒 Серверный ключ" else "🔑 Ваш ключ"
                adapter.addMessage(
                    OwlMessage(
                        text = "💡 Доступные команды:\n\n" +
                                "/model — выбрать модель\n" +
                                "/key — ввести свой API ключ\n" +
                                "/help — это сообщение\n\n" +
                                "Текущая модель: ${getModelsForDisplay()[selectedModelIndex].second}\n" +
                                "API ключ: $keyStatus",
                        isUser = false
                    )
                )
            }
            else -> {
                adapter.addMessage(
                    OwlMessage(text = "❌ Неизвестная команда: ${parts[0]}\nВведите /help для списка команд.", isUser = false)
                )
            }
        }
    }

    // ===== API Key dialog =====

    private fun showApiKeyDialog() {
        val builder = AlertDialog.Builder(this)
            .setTitle("API ключ OpenRouter")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        val input = EditText(this).apply {
            hint = "sk-or-v1-..."
            setText(userApiKey)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        layout.addView(input)

        val infoText = TextView(this).apply {
            text = if (userApiKey.isEmpty())
                "💡 Без своего ключа используется серверный (только бесплатные модели)."
            else
                "✅ Ваш ключ активен. Доступны все модели."
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#888888"))
            setPadding(0, 8, 0, 0)
        }
        layout.addView(infoText)

        builder.setView(layout)

        builder.setPositiveButton("Сохранить") { dialog, _ ->
            val newKey = input.text.toString().trim()
            userApiKey = newKey
            saveLocalSettings()
            if (newKey.isNotEmpty()) {
                lifecycleScope.launch {
                    try {
                        GrpcClient.updateOwlSettings(chatId, userId, newKey, getEffectiveModelId())
                    } catch (_: Exception) {}
                }
            }
            adapter.addMessage(
                OwlMessage(
                    text = if (newKey.isEmpty()) "🔒 API ключ очищен. Используется серверный (только бесплатные модели)."
                    else "🔑 API ключ сохранён. Доступны все модели.",
                    isUser = false
                )
            )
            dialog.dismiss()
        }

        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

    // ===== Model picker dialog =====

    private fun showModelPickerDialog() {
        val displayModels = getModelsForDisplay()
        val modelNames = displayModels.map { it.second }.toTypedArray()
        if (selectedModelIndex >= displayModels.size) selectedModelIndex = 0

        val builder = AlertDialog.Builder(this)
            .setTitle(if (userApiKey.isEmpty()) "Выберите модель (бесплатные)" else "Выберите модель")

        // Custom view with list + custom input
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }

        // List of models
        val listView = android.widget.ListView(this).apply {
            adapter = android.widget.ArrayAdapter(context, android.R.layout.simple_list_item_single_choice, modelNames)
            choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE
            setSelection(selectedModelIndex)
        }
        layout.addView(listView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        // Custom model input
        val customLabel = TextView(this).apply {
            text = "Или введите ID модели:"
            textSize = 12f
            setPadding(32, 24, 0, 4)
        }
        layout.addView(customLabel)

        val customInput = EditText(this).apply {
            hint = "provider/model-name"
            textSize = 14f
            setPadding(32, 8, 32, 8)
        }
        layout.addView(customInput)

        builder.setView(layout)

        builder.setPositiveButton("Выбрать") { dialog, _ ->
            val checkedPos = listView.checkedItemPosition
            if (checkedPos >= 0) {
                selectModel(checkedPos)
            }
            dialog.dismiss()
        }

        builder.setNeutralButton("Своя") { dialog, _ ->
            val customModel = customInput.text.toString().trim()
            if (customModel.isNotEmpty()) {
                selectCustomModel(customModel)
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

    private fun selectModel(index: Int) {
        val displayModels = getModelsForDisplay()
        if (index < 0 || index >= displayModels.size) return
        selectedModelIndex = index
        val (modelId, modelName) = displayModels[index]
        saveLocalSettings()
        lifecycleScope.launch {
            try {
                GrpcClient.updateOwlSettings(chatId, userId, userApiKey, modelId)
            } catch (_: Exception) {}
        }
        adapter.addMessage(
            OwlMessage(text = "✅ Модель изменена на: $modelName", isUser = false)
        )
    }

    private fun selectCustomModel(modelId: String) {
        selectedModelIndex = getModelsForDisplay().size // sentinel: custom model is "after" the last in list
        saveCustomModel(modelId)
        saveLocalSettings()
        lifecycleScope.launch {
            try {
                GrpcClient.updateOwlSettings(chatId, userId, userApiKey, modelId)
            } catch (_: Exception) {}
        }
        adapter.addMessage(
            OwlMessage(text = "✅ Модель изменена на: $modelId", isUser = false)
        )
    }

    // Custom model storage (outside the hardcoded list)
    private var customModelId: String = ""

    private fun saveCustomModel(modelId: String) {
        customModelId = modelId
        prefs.edit().putString("custom_model", modelId).apply()
    }

    private fun loadCustomModel() {
        customModelId = prefs.getString("custom_model", "") ?: ""
    }

    // Get effective model ID (handles custom model selection)
    private fun getEffectiveModelId(): String {
        val displayModels = getModelsForDisplay()
        if (selectedModelIndex == displayModels.size && customModelId.isNotEmpty()) {
            return customModelId
        }
        if (selectedModelIndex < displayModels.size) {
            return displayModels[selectedModelIndex].first
        }
        return displayModels[0].first
    }

    // ===== Send message =====

    private fun sendMessage(text: String) {
        adapter.addMessage(OwlMessage(text = text, isUser = true))
        messageInput.setText("")
        currentResponse = ""
        isReceiving = true

        adapter.showTyping()

        findViewById<RecyclerView>(R.id.messagesRecyclerView)
            .scrollToPosition(adapter.itemCount - 1)

        val modelId = getEffectiveModelId()

        try {
            GrpcClient.chatWithOWL(
                userId = userId,
                message = text,
                chatId = chatId,
                modelId = modelId,
                apiKey = userApiKey,
                scope = lifecycleScope
            ) { response ->
                runOnUiThread {
                    onOwlResponse(response)
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                adapter.hideTyping()
                adapter.addMessage(
                    OwlMessage(text = "Ошибка отправки: ${e.message}", isUser = false)
                )
                isReceiving = false
            }
        }
    }

    private fun onOwlResponse(response: OWLResponseProto) {
        if (response.error.isNotEmpty()) {
            adapter.hideTyping()
            adapter.addMessage(OwlMessage(text = "Ошибка: ${response.error}", isUser = false))
            isReceiving = false
        } else if (response.finished) {
            adapter.hideTyping()
            if (response.text.isNotEmpty()) {
                adapter.updateLastAssistantMessage(currentResponse + response.text)
            } else if (currentResponse.isNotEmpty()) {
                adapter.updateLastAssistantMessage(currentResponse)
            }
            isReceiving = false
        } else {
            currentResponse += response.text
            adapter.updateLastAssistantMessage(currentResponse)
        }
    }

    private fun confirmDeleteChat() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_chats))
            .setMessage(getString(R.string.delete_chats_confirmation, 1))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                lifecycleScope.launch {
                    try {
                        GrpcClient.deleteOwlChat(chatId, userId)
                    } catch (_: Exception) {}
                    runOnUiThread { finish() }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // Extension for dp to px
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
