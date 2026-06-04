package lavender.client.android.ui.hermes

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.models.HermesMessage
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi

class HermesChatActivity : AppCompatActivity() {

    private lateinit var viewModel: HermesChatViewModel
    private lateinit var adapter: HermesChatAdapter
    private lateinit var sendButton: ImageButton
    private lateinit var messageInput: EditText
    private lateinit var toolbarSubtitle: TextView
    private lateinit var toolbarTitle: TextView
    private lateinit var toolbarAgentIcon: TextView

    private var userId: String = ""
    private var chatId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hermes_chat)

        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        userId = SessionManager.session.value.username

        viewModel = androidx.lifecycle.ViewModelProvider(this)[HermesChatViewModel::class.java]

        setupToolbar()
        setupRecyclerView()
        setupInput()
        observeState()
        ThemeUi.bind(this, userId)

        // Handle window insets
        val bottomPanel = findViewById<MaterialCardView>(R.id.bottomPanel)
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            bottomPanel.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = if (isImeVisible) imeInsets.bottom else systemBars.bottom
            }
            insets
        }

        // Create session and load history
        android.util.Log.d("HermesChatActivity", "onCreate: chatId=$chatId userId=$userId")
        if (chatId.isEmpty()) {
            android.util.Log.d("HermesChatActivity", "onCreate: creating new session for userId=$userId")
            viewModel.createSession(userId)
        } else {
            android.util.Log.d("HermesChatActivity", "onCreate: loading history for chatId=$chatId")
            viewModel.loadHistory()
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        toolbarTitle = findViewById(R.id.toolbarTitle)
        toolbarSubtitle = findViewById(R.id.toolbarSubtitle)
        toolbarAgentIcon = findViewById(R.id.toolbarAgentIcon)

        toolbarTitle.text = "Hermes"
        toolbarAgentIcon.text = "🎼"
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.messagesRecyclerView)
        adapter = HermesChatAdapter()
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
            if (text.isEmpty()) return@setOnClickListener

            val session = viewModel.currentSession.value
            if (session == null) {
                Toast.makeText(this, "Сессия не создана", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.sendMessage(text)
            messageInput.setText("")
        }
    }

    private fun observeState() {
        // Messages
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { messages ->
                    adapter.setMessages(messages)
                    if (messages.isNotEmpty()) {
                        findViewById<RecyclerView>(R.id.messagesRecyclerView)
                            .scrollToPosition(adapter.itemCount - 1)
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
                    toolbarSubtitle.visibility = if (isTyping) View.VISIBLE else View.GONE
                    toolbarSubtitle.text = "печатает..."
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

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
