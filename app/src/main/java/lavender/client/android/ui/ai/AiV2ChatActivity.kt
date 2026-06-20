package lavender.client.android.ui.ai

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.ai.RateLimitCache
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.chat.widget.ChatMessageAdapter
import lavender.client.android.ui.chat.widget.ChatMessageItem
import lavender.client.android.ui.chat.widget.ChatWidget

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

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_v2_chat)

        sessionId = intent.getStringExtra("SESSION_ID") ?: ""
        agentId = intent.getStringExtra("AGENT_ID") ?: ""
        agentName = intent.getStringExtra("AGENT_NAME") ?: ""
        userId = SessionManager.session.value.userId

        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory).get(AiV2ChatViewModel::class.java)

        chatWidget = findViewById(R.id.chatWidget)
        progressBar = findViewById(R.id.progressBar)

        setupToolbar()
        setupRecyclerView()
        setupInput()
        observeState()
        ThemeUi.bind(this, SessionManager.session.value.username)

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
            chatWidget.setToolbarTitle(agentName.ifEmpty { getString(R.string.ai_v2_chat_title) })
        } else {
            chatWidget.setToolbarTitle(agentName.ifEmpty { getString(R.string.ai_v2_chat_title) })
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
        toolbar.setNavigationOnClickListener { finish() }

        chatWidget.setToolbarTitle(agentName.ifEmpty { getString(R.string.ai_v2_chat_title) })
        chatWidget.setToolbarAgentIcon("🤖", true)
        chatWidget.setToolbarAvatar(false)
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
                                senderId = msg.agentId,
                                senderName = msg.agentName.ifEmpty { "AI" },
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
                    viewModel.error.collect { error ->
                        error?.let {
                            if (it.contains("rate limit", ignoreCase = true)) {
                                handleRateLimit()
                            } else {
                                Toast.makeText(this@AiV2ChatActivity, it, Toast.LENGTH_SHORT).show()
                            }
                            viewModel.clearError()
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

        val userMessage = lavender.client.android.data.ai.AiV2ChatMessage(
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
