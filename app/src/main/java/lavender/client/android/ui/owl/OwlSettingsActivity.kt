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
import lavender.client.android.data.grpc.getFreeModels
import lavender.client.android.data.grpc.getHermesSettings
import lavender.client.android.data.grpc.getOwlSettings
import lavender.client.android.data.grpc.updateHermesSettings
import lavender.client.android.data.grpc.updateOwlSettings
import lavender.client.android.data.proto.FreeModelInfoProto
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
 * - Model selector: free models (from server) + paid models (if custom key) + "Own model" text input
 * - Save button
 *
 * Logic:
 * - No custom key: only free models dropdown, OWL Alpha first
 * - Has custom key: free models + "Other model" text input for any OpenRouter model
 */
class OwlSettingsActivity : AppCompatActivity() {

    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var apiKeyLayout: TextInputLayout
    private lateinit var modelDropdown: AutoCompleteTextView
    private lateinit var modelCustomInput: TextInputEditText
    private lateinit var modelCustomLayout: TextInputLayout
    private lateinit var modelCustomHint: TextView
    private lateinit var saveButton: MaterialButton
    private lateinit var statusText: TextView
    private lateinit var keySourceText: TextView
    private lateinit var rateLimitText: TextView

    private var userId: String = ""
    private var chatId: String = ""
    private var sessionId: String = ""
    private var isHermes = false
    private var isUsingCustomKey = false

    // Models loaded from server
    private var freeModels: List<FreeModelInfoProto> = emptyList()

    // "Own model" option — always available with custom key
    private val ownModelOption = "Своя модель (ввести вручную)"

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
        modelCustomInput = findViewById(R.id.modelCustomInput)
        modelCustomLayout = findViewById(R.id.modelCustomLayout)
        modelCustomHint = findViewById(R.id.modelCustomHint)
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
            val hasCustomKey = apiKey.isNotEmpty()

            val selectedModel: String
            if (hasCustomKey && modelDropdown.text?.toString() == ownModelOption) {
                // User selected "Own model" — read from text input
                selectedModel = modelCustomInput.text?.toString()?.trim() ?: ""
                if (selectedModel.isEmpty()) {
                    showStatus("Введите ID модели", false)
                    return@setOnClickListener
                }
            } else {
                // User selected a model from dropdown — find its modelId
                val displayName = modelDropdown.text.toString()
                if (hasCustomKey && displayName == ownModelOption) {
                    selectedModel = modelCustomInput.text?.toString()?.trim() ?: ""
                    if (selectedModel.isEmpty()) {
                        showStatus("Введите ID модели", false)
                        return@setOnClickListener
                    }
                } else {
                    // Find modelId by display name
                    selectedModel = freeModels.find { it.displayName == displayName }?.modelId ?: "openrouter/auto"
                }
            }
            saveSettings(apiKey, selectedModel)
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            try {
                // 1. Load free models from server
                freeModels = getFreeModels()
                if (freeModels.isEmpty()) {
                    // Fallback: hardcoded list if server returns empty
                    freeModels = listOf(
                        FreeModelInfoProto("openrouter/owl-alpha", "OWL Alpha (free)", 0),
                        FreeModelInfoProto("google/gemini-2.0-flash-001", "Gemini 2.0 Flash (free)", 1),
                        FreeModelInfoProto("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B (free)", 2),
                        FreeModelInfoProto("mistralai/mistral-7b-instruct:free", "Mistral 7B (free)", 3)
                    )
                }
                Log.d(TAG, "Loaded ${freeModels.size} free models from server")

                // 2. Load chat-specific settings
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
                    // If server returned free_models in response, prefer them
                    if (s.freeModels.isNotEmpty()) {
                        freeModels = s.freeModels
                    }
                }

                isUsingCustomKey = loadedIsCustom

                if (loadedApiKey.isNotEmpty()) {
                    apiKeyInput.setText(loadedApiKey)
                }

                setupModelDropdown(loadedApiKey.isNotEmpty())

                // Set current model
                if (loadedModel.isNotEmpty()) {
                    val displayName = freeModels.find { it.modelId == loadedModel }?.displayName
                    if (displayName != null) {
                        modelDropdown.setText(displayName, false)
                    } else if (isUsingCustomKey) {
                        // Model is not in free list — it's a custom/paid model
                        modelDropdown.setText(ownModelOption, false)
                        modelCustomInput.setText(loadedModel)
                    }
                }

                updateKeySourceInfo(loadedApiKey.isNotEmpty())
                Log.d(TAG, "Settings loaded: model=$loadedModel customKey=$loadedIsCustom freeModels=${freeModels.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load settings", e)
                showStatus("Ошибка загрузки настроек: ${e.message}", false)
                // Fallback to hardcoded free models
                freeModels = listOf(
                    FreeModelInfoProto("openrouter/owl-alpha", "OWL Alpha (free)", 0),
                    FreeModelInfoProto("google/gemini-2.0-flash-001", "Gemini 2.0 Flash (free)", 1)
                )
                setupModelDropdown(false)
            }
        }
    }

    private fun setupModelDropdown(hasCustomKey: Boolean) {
        // Build dropdown items: always show free models, add "Own model" option if custom key
        val displayItems = mutableListOf<String>()
        // Free models first, sorted by sortOrder
        freeModels.sortedBy { it.sortOrder }.forEach { displayItems.add(it.displayName) }
        if (hasCustomKey) {
            displayItems.add(ownModelOption)
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            displayItems
        )
        modelDropdown.setAdapter(adapter)

        // Default: first free model (owl-alpha)
        if (freeModels.isNotEmpty()) {
            val first = freeModels.minByOrNull { it.sortOrder }?.displayName ?: freeModels[0].displayName
            modelDropdown.setText(first, false)
        }

        // Show/hide custom model input based on dropdown selection
        modelDropdown.setOnItemClickListener { _, _, position, _ ->
            val selected = displayItems.getOrNull(position) ?: ""
            modelCustomLayout.visibility = if (selected == ownModelOption) View.VISIBLE else View.GONE
        }

        // When API key changes, update model list
        apiKeyInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val apiKey = apiKeyInput.text?.toString()?.trim() ?: ""
                val newHasKey = apiKey.isNotEmpty()
                if (newHasKey != isUsingCustomKey) {
                    isUsingCustomKey = newHasKey
                    setupModelDropdown(newHasKey)
                    updateKeySourceInfo(newHasKey)
                    // Reset custom input
                    modelCustomInput.text?.clear()
                    modelCustomLayout.visibility = View.GONE
                }
            }
        }

        // Initially hide custom input
        modelCustomLayout.visibility = View.GONE
    }

    private fun updateKeySourceInfo(hasCustomKey: Boolean) {
        if (hasCustomKey) {
            keySourceText.text = "Ваш ключ: все модели, без ограничений"
            keySourceText.setTextColor(getColor(android.R.color.holo_green_dark))
            rateLimitText.visibility = View.GONE
            modelCustomHint.visibility = View.GONE
            modelCustomLayout.hint = "Введите ID модели (например: openai/gpt-4o)"
        } else {
            keySourceText.text = "Общий ключ: бесплатные модели, 20 запросов/час"
            keySourceText.setTextColor(getColor(android.R.color.holo_orange_dark))
            rateLimitText.visibility = View.VISIBLE
            rateLimitText.text = "Установите свой API ключ OpenRouter для доступа ко всем моделям и снятия ограничений"
            // Show hint that custom model input is only available with own key
            modelCustomHint.visibility = View.VISIBLE
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
