package lavender.client.android

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.owlResponses
import lavender.client.android.data.grpc.owlTyping
import lavender.client.android.data.proto.OWLResponseProto
import lavender.client.android.data.session.SessionManager
import lavender.client.android.ui.adapter.OwlMessage
import lavender.client.android.ui.adapter.OwlMessageAdapter
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

class OwlActivity : AppCompatActivity() {

    private lateinit var adapter: OwlMessageAdapter
    private var currentResponse = ""
    private var isReceiving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owl)

        setupToolbar()
        setupRecyclerView()
        setupInput()
        observeOwlResponses()

        // Add welcome message
        adapter.addMessage(
            OwlMessage(
                text = "Привет! Я — OWL, твой AI-ассистент в Lavender. Спроси меня о чём угодно о приложении или просто поболтай!",
                isUser = false
            )
        )
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = "OWL"
        ThemeUtils.applyToolbarTheme(toolbar)

        val subtitle = toolbar.findViewById<android.widget.TextView>(R.id.toolbarSubtitle)
        subtitle?.visibility = View.GONE
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
        val messageInput = findViewById<EditText>(R.id.messageInput)
        val sendButton = findViewById<MaterialButton>(R.id.sendButton)

        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isEmpty() || isReceiving) return@setOnClickListener

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
                return@setOnClickListener
            }

            try {
                GrpcClient.chatWithOWL(userId, text, lifecycleScope) { response ->
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

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
