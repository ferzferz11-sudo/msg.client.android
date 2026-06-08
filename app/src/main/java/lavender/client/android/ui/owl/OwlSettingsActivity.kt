package lavender.client.android.ui.owl

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.grpc.getHermesSettings
import lavender.client.android.data.grpc.getOwlSettings
import lavender.client.android.data.grpc.updateHermesSettings
import lavender.client.android.data.grpc.updateOwlSettings
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi

/**
 * AI Settings screen — works for both OWL and Hermes (Lava AI) chats.
 *
 * Pass extras:
 * - chatId (String): OWL chat ID
 * - sessionId (String): Hermes session ID
 * - isHermes (Boolean true): if set, use Hermes settings RPCs
 *
 * Shows:
 * - API key input (masked) with indicator if using own key or shared server key
 * - Model dropdown (all models for own key, free-only for shared key)
 * - Save button
 */
class OwlSettingsActivity : AppCompatActivity() {

    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var apiKeyLayout: TextInputLayout
    private lateinit var modelDropdown: AutoCompleteTextView
    private lateinit var saveButton: MaterialButton
    private lateinit var statusText: TextView
    private lateinit var keySourceText: TextView
    private lateinit var rateLimitText: TextView

    private var userId: String = ""
    private var chatId: String = ""
    private var sessionId: String = ""
    private var isHermes = false
    private var isUsingCustomKey = false

    // Full model list (available with own API key)
    private val allModels = listOf(
        "openrouter/auto" to "OpenRouter Auto",
        "openai/gpt-4o" to "GPT-4o",
        "openai/gpt-4o-mini" to "GPT-4o Mini",
        "anthropic/claude-3-5-sonnet" to "Claude 3.5 Sonnet",
        "anthropic/claude-3-haiku" to "Claude 3 Haiku",
        "google/gemini-1.5-pro" to "Gemini 1.5 Pro",
        "google/gemini-1.5-flash" to "Gemini 1.5 Flash",
        "meta-llama/llama-3.1-70b-instruct" to "Llama 3.1 70B",
        "meta-llama/llama-3.1-8b-instruct" to "Llama 3.1 8B",
        "mistralai/mistral-large" to "Mistral Large",
        "mistralai/mistral-7b-instruct" to "Mistral 7B"
    )

    // Free tier models (no own key needed)
    private val freeModels = listOf(
        "openrouter/auto" to "OpenRouter Auto",
        "google/gemini-1.5-flash" to "Gemini 1.5 Flash",
        "meta-llama/llama-3.1-8b-instruct" to "Llama 3.1 8B",
        "mistralai/mistral-7b-instruct" to "Mistral 7B"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owl_settings)

        userId = SessionManager.session.value.userId
        chatId = intent.getStringExtra("chatId") ?: ""
        sessionId = intent.getStringExtra("sessionId") ?: ""
        isHermes = intent.getBooleanExtra("isHermes", false)

        initViews()
        setupToolbar()
        setupSaveButton()
        ThemeUi.bind(this, userId)

        val title = if (isHermes) "Настройки Лава ИИ" else "Настройки OWL"
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).title = title

        Log.d(TAG, "onCreate: userId=$userId chatId=$chatId sessionId=$sessionId isHermes=$isHermes")

        loadSettings()
    }

    private fun initViews() {
        apiKeyInput = findViewById(R.id.apiKeyInput)
        apiKeyLayout = findViewById(R.id.apiKeyLayout)
        modelDropdown = findViewById(R.id.modelDropdown)
        saveButton = findViewById(R.id.saveButton)
        statusText = findViewById(R.id.statusText)
        keySourceText = findViewById(R.id.keySourceText)
        rateLimitText = findViewById(R.id.rateLimitText)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupSaveButton() {
        saveButton.setOnClickListener {
            val apiKey = apiKeyInput.text?.toString()?.trim() ?: ""
            val selectedModelName = modelDropdown.text.toString()
            val modelsList = if (apiKey.isNotEmpty()) allModels else freeModels
            val selectedModel = modelsList.find { it.second == selectedModelName }
                ?.first ?: "openrouter/auto"
            saveSettings(apiKey, selectedModel)
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            try {
                var loadedApiKey = ""
                var loadedModel = ""
                var loadedIsCustom = false

                if (isHermes && sessionId.isNotEmpty()) {
                    val s = getHermesSettings(sessionId, userId)
                    loadedApiKey = s.apiKey
                    loadedModel = s.model
                    loadedIsCustom = s.isUsingCustomKey
                } else {
                    val s = getOwlSettings(chatId, userId)
                    loadedApiKey = s.apiKey
                    loadedModel = s.model
                    loadedIsCustom = s.isUsingCustomKey
                }

                isUsingCustomKey = loadedIsCustom

                if (loadedApiKey.isNotEmpty()) {
                    apiKeyInput.setText(loadedApiKey)
                }

                setupModelDropdown(loadedApiKey.isNotEmpty())

                if (loadedModel.isNotEmpty() && loadedModel != "openrouter/auto") {
                    val displayList = if (loadedApiKey.isNotEmpty()) allModels else freeModels
                    val modelDisplay = displayList.find { it.first == loadedModel }?.second
                    if (modelDisplay != null) {
                        modelDropdown.setText(modelDisplay, false)
                    }
                }

                updateKeySourceInfo(loadedApiKey.isNotEmpty())
                Log.d(TAG, "Settings loaded: model=$loadedModel customKey=$loadedIsCustom")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load settings", e)
                showStatus("Ошибка загрузки настроек: ${e.message}", false)
                setupModelDropdown(false)
            }
        }
    }

    private fun setupModelDropdown(hasCustomKey: Boolean) {
        val models = if (hasCustomKey) allModels else freeModels
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            models.map { it.second }
        )
        modelDropdown.setAdapter(adapter)
        modelDropdown.setText(models[0].second, false)

        // When API key changes, update model list
        apiKeyInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val apiKey = apiKeyInput.text?.toString()?.trim() ?: ""
                val newHasKey = apiKey.isNotEmpty()
                if (newHasKey != isUsingCustomKey) {
                    isUsingCustomKey = newHasKey
                    setupModelDropdown(newHasKey)
                    updateKeySourceInfo(newHasKey)
                }
            }
        }
    }

    private fun updateKeySourceInfo(hasCustomKey: Boolean) {
        if (hasCustomKey) {
            keySourceText.text = "Ваш ключ: все модели, без ограничений"
            keySourceText.setTextColor(getColor(android.R.color.holo_green_dark))
            rateLimitText.visibility = View.GONE
        } else {
            keySourceText.text = "Общий ключ: бесплатные модели, 20 запросов/час"
            keySourceText.setTextColor(getColor(android.R.color.holo_orange_dark))
            rateLimitText.visibility = View.VISIBLE
            rateLimitText.text = "Установите свой API ключ OpenRouter для доступа ко всем моделям и снятия ограничений"
        }
        keySourceText.visibility = View.VISIBLE
    }

    private fun saveSettings(apiKey: String, model: String) {
        saveButton.isEnabled = false
        saveButton.text = "Сохранение..."

        lifecycleScope.launch {
            try {
                var success = false
                var message = ""

                if (isHermes && sessionId.isNotEmpty()) {
                    val r = updateHermesSettings(sessionId, userId, apiKey, model)
                    success = r.success
                    message = r.message
                } else {
                    val r = updateOwlSettings(chatId, userId, apiKey, model)
                    success = r.success
                    message = r.message
                }

                if (success) {
                    showStatus("Настройки сохранены", true)
                    Toast.makeText(this@OwlSettingsActivity, "Сохранено", Toast.LENGTH_SHORT).show()
                } else {
                    showStatus("Ошибка: $message", false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save settings", e)
                showStatus("Ошибка: ${e.message}", false)
            } finally {
                saveButton.isEnabled = true
                saveButton.text = "Сохранить"
            }
        }
    }

    private fun showStatus(message: String, isSuccess: Boolean) {
        statusText.visibility = View.VISIBLE
        statusText.text = message
        statusText.setTextColor(
            if (isSuccess) getColor(android.R.color.holo_green_dark)
            else getColor(android.R.color.holo_red_dark)
        )
    }

    companion object {
        private const val TAG = "OwlSettingsActivity"
    }
}
