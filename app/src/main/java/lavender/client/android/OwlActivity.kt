package lavender.client.android

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.owlTyping
import lavender.client.android.data.proto.OWLResponseProto
import lavender.client.android.data.session.SessionManager
import lavender.client.android.ui.adapter.OwlMessage
import lavender.client.android.ui.adapter.OwlMessageAdapter

class OwlActivity : AppCompatActivity() {

    private lateinit var adapter: OwlMessageAdapter
    private lateinit var sendButton: android.widget.ImageButton
    private lateinit var messageInput: EditText
    private var currentResponse = ""
    private var isReceiving = false

    // Chat ID — unique per OWL chat
    private var chatId: String = ""
    private var userId: String = ""

    // Available models
    private val models = listOf(
        "openrouter/anthropic/claude-sonnet-4" to "Claude Sonnet 4",
        "openrouter/anthropic/claude-opus-4" to "Claude Opus 4",
        "openrouter/google/gemini-2.5-pro" to "Gemini 2.5 Pro",
        "openrouter/openai/gpt-4o" to "GPT-4o",
        "openrouter/openai/gpt-4o-mini" to "GPT-4o Mini"
    )
    private var selectedModelIndex = 0
    private var userApiKey = ""

    // Local prefs for per-chat settings (fallback if server unreachable)
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owl)

        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        userId = SessionManager.session.value.username

        prefs = getSharedPreferences("owl_$chatId", Context.MODE_PRIVATE)
        loadLocalSettings()

        setupToolbar(hasMenu = true)
        setupRecyclerView()
        setupInput()
        observeOwlResponses()

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

        // Load history from server
        loadHistory()
    }

    private fun loadLocalSettings() {
        selectedModelIndex = prefs.getInt("model_index", 0)
        if (selectedModelIndex >= models.size) selectedModelIndex = 0
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

        lifecycleScope.launch {
            try {
                val history = GrpcClient.getOwlHistory(chatId, userId)
                runOnUiThread {
                    if (history.isEmpty()) {
                        // First time — show welcome
                        showWelcomeMessage()
                    } else {
                        for (msg in history) {
                            when (msg.role) {
                                "user" -> adapter.addMessage(OwlMessage(text = msg.content, isUser = true))
                                "assistant" -> adapter.addMessage(OwlMessage(text = msg.content, isUser = false))
                            }
                        }
                        // Scroll to bottom
                        val rv = findViewById<RecyclerView>(R.id.messagesRecyclerView)
                        rv.scrollToPosition(adapter.itemCount - 1)
                    }

                    // Load per-chat settings from server if available
                    val serverApiKey = GrpcClient.getOwlSettingApiKey(chatId)
                    val serverModel = GrpcClient.getOwlSettingModel(chatId)
                    if (serverApiKey.isNotEmpty()) userApiKey = serverApiKey
                    if (serverModel.isNotEmpty()) {
                        val idx = models.indexOfFirst { it.first == serverModel }
                        if (idx >= 0) selectedModelIndex = idx
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showWelcomeMessage()
                }
            }
        }
    }

    private fun showWelcomeMessage() {
        adapter.addMessage(
            OwlMessage(
                text = "Привет! Я — OWL, твой AI-ассистент в Lavender. Спроси меня о чём угодно!\n\n💡 Команды:\n/model — выбрать модель\n/help — помощь",
                isUser = false
            )
        )
    }

    private fun setupToolbar(hasMenu: Boolean) {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }
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
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.messagesRecyclerView)
        adapter = OwlMessageAdapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
    }

    private fun setupInput() {
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)

        sendButton.setOnClickListener {
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

    private fun handleCommand(command: String) {
        val parts = command.split(" ", limit = 2)
        when (parts[0].lowercase()) {
            "/model" -> {
                showModelPickerDialog()
            }
            "/help" -> {
                adapter.addMessage(
                    OwlMessage(
                        text = "💡 Доступные команды:\n\n" +
                                "/model — выбрать модель\n" +
                                "/help — это сообщение\n\n" +
                                "Текущая модель: ${models[selectedModelIndex].second}",
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

    // ===== Model picker dialog =====

    private fun showModelPickerDialog() {
        val modelNames = models.map { it.second }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Выберите модель")
            .setSingleChoiceItems(modelNames, selectedModelIndex) { dialog, which ->
                selectedModelIndex = which
                val (modelId, modelName) = models[which]
                saveLocalSettings()

                // Also save to server
                lifecycleScope.launch {
                    try {
                        GrpcClient.updateOwlSettings(chatId, userId, userApiKey, modelId)
                    } catch (_: Exception) {}
                }

                adapter.addMessage(
                    OwlMessage(text = "✅ Модель изменена на: $modelName", isUser = false)
                )
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
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

        val modelId = models[selectedModelIndex].first

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
                adapter.updateLastMessage(currentResponse + response.text)
            } else if (currentResponse.isEmpty()) {
                adapter.updateLastMessage("Получен пустой ответ от сервера")
            }
            currentResponse = ""
            isReceiving = false
            findViewById<RecyclerView>(R.id.messagesRecyclerView)
                .scrollToPosition(adapter.itemCount - 1)
        } else {
            currentResponse += response.text
            adapter.hideTyping()
            adapter.addMessage(OwlMessage(text = currentResponse, isUser = false))
            findViewById<RecyclerView>(R.id.messagesRecyclerView)
                .scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun observeOwlResponses() {
        lifecycleScope.launch {
            owlTyping.collect { isTyping ->
                runOnUiThread {
                    val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
                    val subtitle = toolbar.findViewById<android.widget.TextView>(R.id.toolbarSubtitle)
                    if (isTyping) {
                        subtitle?.text = "Печатает..."
                        subtitle?.visibility = View.VISIBLE
                    } else {
                        subtitle?.visibility = View.GONE
                    }
                }
            }
        }
    }

    // ===== Settings dialog =====

    private fun showSettingsDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }

        // API Key input
        val keyLabel = TextView(this).apply {
            text = "OpenRouter API Key (оставьте пустым для серверного ключа)"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        layout.addView(keyLabel)

        val keyInput = EditText(this).apply {
            hint = "sk-or-v1-..."
            setText(userApiKey)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        layout.addView(keyInput)

        // Model selector
        val modelLabel = TextView(this).apply {
            text = "Модель"
            textSize = 14f
            setPadding(0, 24, 0, 8)
        }
        layout.addView(modelLabel)

        val modelNames = models.map { it.second }.toTypedArray()
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, modelNames)
            setSelection(selectedModelIndex)
        }
        layout.addView(spinner)

        // Info
        val infoText = TextView(this).apply {
            text = "💡 Без своего ключа используется серверный.\nВаш ключ хранится на сервере и устройстве."
            textSize = 12f
            setPadding(0, 24, 0, 0)
        }
        layout.addView(infoText)

        AlertDialog.Builder(this)
            .setTitle("Настройки OWL")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                userApiKey = keyInput.text.toString().trim()
                selectedModelIndex = spinner.selectedItemPosition
                saveLocalSettings()

                // Save to server
                val modelId = models[selectedModelIndex].first
                lifecycleScope.launch {
                    try {
                        GrpcClient.updateOwlSettings(chatId, userId, userApiKey, modelId)
                    } catch (_: Exception) {}
                }

                Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ===== Delete chat =====

    private fun confirmDeleteChat() {
        AlertDialog.Builder(this)
            .setTitle("Удалить чат?")
            .setMessage("Вся история будет удалена. Это действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    try {
                        GrpcClient.deleteOwlChat(chatId, userId)
                        runOnUiThread {
                            Toast.makeText(this@OwlActivity, "Чат удалён", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@OwlActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }
}
