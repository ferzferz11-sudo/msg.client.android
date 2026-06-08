package lavender.client.android.ui.owl

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.grpc.getOwlSettings
import lavender.client.android.data.grpc.updateOwlSettings
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi

/**
 * OwlSettingsActivity — экран настроек OWL AI.
 *
 * Позволяет задать per-chat API key и модель для OWL.
 * Настройки сохраняются на сервере в owl_chat_settings через gRPC.
 */
class OwlSettingsActivity : AppCompatActivity() {

    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var apiKeyLayout: TextInputLayout
    private lateinit var modelDropdown: AutoCompleteTextView
    private lateinit var saveButton: MaterialButton
    private lateinit var statusText: View

    private var userId: String = ""
    private var chatId: String = ""

    // Available models — matches server-side defaults
    private val availableModels = listOf(
        "default" to "По умолчанию",
        "gpt-4o" to "GPT-4o",
        "gpt-4o-mini" to "GPT-4o Mini",
        "claude-3-5-sonnet" to "Claude 3.5 Sonnet",
        "claude-3-haiku" to "Claude 3 Haiku",
        "gemini-1.5-pro" to "Gemini 1.5 Pro",
        "gemini-1.5-flash" to "Gemini 1.5 Flash"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owl_settings)

        userId = SessionManager.session.value.userId

        // Get chatId from intent if passed from chat screen, otherwise use first existing OWL chat
        chatId = intent.getStringExtra("chatId") ?: ""

        initViews()
        setupToolbar()
        setupModelDropdown()
        setupSaveButton()
        ThemeUi.bind(this, userId)

        // If no chatId passed, we need to find or create one
        if (chatId.isEmpty()) {
            // Try to get existing OWL chat from intent extra (CHAT_ID)
            chatId = intent.getStringExtra("CHAT_ID") ?: ""
        }

        Log.d(TAG, "onCreate: userId=$userId chatId=$chatId")

        // Load current settings from server
        loadSettings()
    }

    private fun initViews() {
        apiKeyInput = findViewById(R.id.apiKeyInput)
        apiKeyLayout = findViewById(R.id.apiKeyLayout)
        modelDropdown = findViewById(R.id.modelDropdown)
        saveButton = findViewById(R.id.saveButton)
        statusText = findViewById(R.id.statusText)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupModelDropdown() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            availableModels.map { it.second }
        )
        modelDropdown.setAdapter(adapter)
        modelDropdown.setText(availableModels[0].second, false)
    }

    private fun setupSaveButton() {
        saveButton.setOnClickListener {
            val apiKey = apiKeyInput.text?.toString()?.trim() ?: ""
            val selectedModelName = modelDropdown.text.toString()
            val selectedModel = availableModels.find { it.second == selectedModelName }?.first ?: "default"

            saveSettings(apiKey, selectedModel)
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            try {
                val settings = getOwlSettings(chatId, userId)
                if (settings.apiKey.isNotEmpty()) {
                    apiKeyInput.setText(settings.apiKey)
                }
                if (settings.model.isNotEmpty() && settings.model != "default") {
                    val modelDisplay = availableModels.find { it.first == settings.model }?.second
                    if (modelDisplay != null) {
                        modelDropdown.setText(modelDisplay, false)
                    }
                }
                Log.d(TAG, "Settings loaded: model=${settings.model}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load OWL settings", e)
                showStatus("Ошибка загрузки настроек: ${e.message}", false)
            }
        }
    }

    private fun saveSettings(apiKey: String, model: String) {
        saveButton.isEnabled = false
        saveButton.text = "Сохранение..."

        lifecycleScope.launch {
            try {
                val result = updateOwlSettings(chatId, userId, apiKey, model)
                if (result.success) {
                    showStatus("Настройки сохранены", true)
                    Toast.makeText(this@OwlSettingsActivity, "Сохранено", Toast.LENGTH_SHORT).show()
                } else {
                    showStatus("Ошибка: ${result.message}", false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save OWL settings", e)
                showStatus("Ошибка: ${e.message}", false)
            } finally {
                saveButton.isEnabled = true
                saveButton.text = "Сохранить"
            }
        }
    }

    private fun showStatus(message: String, isSuccess: Boolean) {
        statusText.visibility = View.VISIBLE
        (statusText as? android.widget.TextView)?.text = message
    }

    companion object {
        private const val TAG = "OwlSettingsActivity"
    }
}
