package lavender.client.android

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.imageview.ShapeableImageView
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

    // Available models
    private val models = listOf(
        "openrouter/anthropic/claude-sonnet-4" to "Claude Sonnet 4",
        "openrouter/anthropic/claude-opus-4" to "Claude Opus 4",
        "openrouter/google/gemini-2.5-pro" to "Gemini 2.5 Pro",
        "openrouter/openai/gpt-4o" to "GPT-4o",
        "openrouter/openai/gpt-4o-mini" to "GPT-4o Mini"
    )
    private var selectedModelIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owl)

        setupToolbar(binding = false)
        setupRecyclerView()
        setupInput()
        observeOwlResponses()

        // Add welcome message
        adapter.addMessage(
            OwlMessage(
                text = "Привет! Я — OWL, твой AI-ассистент в Lavender. Спроси меня о чём угодно!\n\n💡 Команды:\n/model — сменить модель\n/help — помощь",
                isUser = false
            )
        )
    }

    private fun setupToolbar(binding: Boolean) {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = ""

        // Load avatar
        val avatarView = findViewById<CircleImageView>(R.id.toolbarAvatar)
        avatarView?.setImageResource(R.drawable.ic_notification_logo)
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

            // Handle commands
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
                selectedModelIndex = (selectedModelIndex + 1) % models.size
                val (modelId, modelName) = models[selectedModelIndex]
                adapter.addMessage(
                    OwlMessage(text = "✅ Модель изменена на: $modelName\n(ID: $modelId)", isUser = false)
                )
            }
            "/help" -> {
                adapter.addMessage(
                    OwlMessage(
                        text = "💡 Доступные команды:\n\n" +
                                "/model — сменить модель (циклически)\n" +
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

    private fun sendMessage(text: String) {
        // Add user message
        adapter.addMessage(OwlMessage(text = text, isUser = true))
        messageInput.setText("")
        currentResponse = ""
        isReceiving = true

        // Show typing
        adapter.showTyping()

        // Scroll to bottom
        findViewById<RecyclerView>(R.id.messagesRecyclerView)
            .scrollToPosition(adapter.itemCount - 1)

        // Send to OWL
        val userId = SessionManager.session.value.username
        if (userId.isEmpty()) {
            adapter.hideTyping()
            adapter.addMessage(
                OwlMessage(text = "Ошибка: необходимо войти в аккаунт", isUser = false)
            )
            isReceiving = false
            return
        }

        try {
            val modelId = models[selectedModelIndex].first
            GrpcClient.chatWithOWL(userId, text, modelId, lifecycleScope) { response ->
                runOnUiThread {
                    if (response.error.isNotEmpty()) {
                        adapter.hideTyping()
                        adapter.addMessage(
                            OwlMessage(text = "Ошибка: ${response.error}", isUser = false)
                        )
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
                        adapter.addMessage(
                            OwlMessage(text = currentResponse, isUser = false)
                        )
                        findViewById<RecyclerView>(R.id.messagesRecyclerView)
                            .scrollToPosition(adapter.itemCount - 1)
                    }
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

    private fun observeOwlResponses() {
        lifecycleScope.launch {
            owlTyping.collect { isTyping ->
                runOnUiThread {
                    val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
                    val subtitle = toolbar.findViewById<android.widget.TextView>(R.id.toolbarSubtitle)
                    if (isTyping) {
                        subtitle?.text = "Печатает..."
                        subtitle?.visibility = android.view.View.VISIBLE
                    } else {
                        subtitle?.visibility = android.view.View.GONE
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }
}
