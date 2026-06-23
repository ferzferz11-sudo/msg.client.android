package lavender.client.android.ui.ai

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.ai.AiV2ChatUseCase
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi

class AiChatSettingsActivity : AppCompatActivity() {

    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var modelInput: TextInputEditText
    private lateinit var saveButton: MaterialButton
    private var sessionId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat_settings)

        sessionId = intent.getStringExtra("SESSION_ID") ?: ""

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        apiKeyInput = findViewById(R.id.apiKeyInput)
        modelInput = findViewById(R.id.modelInput)
        saveButton = findViewById(R.id.saveButton)

        saveButton.setOnClickListener { saveSettings() }

        ThemeUi.bind(this, SessionManager.session.value.username)
        loadSettings()
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            try {
                val settings = AiV2ChatUseCase.getChatSettings(sessionId)
                if (settings != null) {
                    apiKeyInput.setText(settings.userApiKey)
                    modelInput.setText(settings.model)
                }
            } catch (e: Exception) {
                Toast.makeText(this@AiChatSettingsActivity, e.message ?: "Failed to load settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveSettings() {
        val apiKey = apiKeyInput.text.toString().trim()
        val model = modelInput.text.toString().trim()

        lifecycleScope.launch {
            try {
                val result = AiV2ChatUseCase.updateChatSettings(sessionId, apiKey, model)
                if (result.success) {
                    Toast.makeText(this@AiChatSettingsActivity, getString(R.string.ai_settings_saved), Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@AiChatSettingsActivity, result.message.ifEmpty { "Failed to save" }, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AiChatSettingsActivity, e.message ?: "Failed to save", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
