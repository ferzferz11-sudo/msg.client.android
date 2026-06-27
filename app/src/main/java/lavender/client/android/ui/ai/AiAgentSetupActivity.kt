package lavender.client.android.ui.ai

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.content.ClipData
import android.content.ClipboardManager
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import lavender.client.android.R
import lavender.client.android.data.ai.AiProviderType
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi
import org.json.JSONObject

class AiAgentSetupActivity : AppCompatActivity() {

    private lateinit var viewModel: AiV2AgentCreateEditViewModel
    private lateinit var agentNameInput: TextInputEditText
    private lateinit var agentDescriptionInput: TextInputEditText
    private lateinit var providerTypeInput: AutoCompleteTextView
    private lateinit var modelInput: TextInputEditText
    private lateinit var systemPromptInput: TextInputEditText
    private lateinit var agentApiKeyInput: TextInputEditText
    private lateinit var temperatureSlider: Slider
    private lateinit var temperatureValue: android.widget.TextView
    private lateinit var maxTokensInput: TextInputEditText
    private lateinit var toolsEnabledSwitch: SwitchMaterial
    private lateinit var ragEnabledSwitch: SwitchMaterial
    private lateinit var publicSwitch: SwitchMaterial
    private var saveButton: android.widget.Button? = null

    private var editAgentId: String = ""
    private var isLoaded = false
    private var hasChanges = false

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_agent_setup)

        editAgentId = intent.getStringExtra("AGENT_ID") ?: ""

        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory).get(AiV2AgentCreateEditViewModel::class.java)

        initViews()
        setupToolbar()
        setupProviderTypeDropdown()
        setupChangeTracking()
        setupWindowInsets()
        observeState()
        ThemeUi.bind(this, SessionManager.session.value.username)

        if (editAgentId.isNotEmpty()) {
            loadAgent(editAgentId)
        } else {
            isLoaded = true
        }
    }

    private fun initViews() {
        agentNameInput = findViewById(R.id.agentNameInput)
        agentDescriptionInput = findViewById(R.id.agentDescriptionInput)
        providerTypeInput = findViewById(R.id.providerTypeInput)
        modelInput = findViewById(R.id.modelInput)
        systemPromptInput = findViewById(R.id.systemPromptInput)
        agentApiKeyInput = findViewById(R.id.agentApiKeyInput)
        agentApiKeyInput.setOnLongClickListener {
            val key = agentApiKeyInput.text?.toString()?.trim() ?: ""
            if (key.isNotEmpty()) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("api_key", key))
                Toast.makeText(this, getString(R.string.ai_api_key_copied), Toast.LENGTH_SHORT).show()
            }
            true
        }
        temperatureSlider = findViewById(R.id.temperatureSlider)
        temperatureValue = findViewById(R.id.temperatureValue)
        maxTokensInput = findViewById(R.id.maxTokensInput)
        toolsEnabledSwitch = findViewById(R.id.toolsEnabledSwitch)
        ragEnabledSwitch = findViewById(R.id.ragEnabledSwitch)
        publicSwitch = findViewById(R.id.publicSwitch)

        temperatureSlider.addOnChangeListener { _, value, _ ->
            temperatureValue.text = String.format("%.1f", value)
            if (isLoaded) markChanged()
        }
        temperatureValue.text = String.format("%.1f", temperatureSlider.value)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.title = if (editAgentId.isNotEmpty()) getString(R.string.ai_v2_edit_agent) else getString(R.string.ai_v2_create_agent)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupProviderTypeDropdown() {
        val providerTypes = AiProviderType.entries.map { it.value }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, providerTypes)
        providerTypeInput.setAdapter(adapter)
        providerTypeInput.setText(AiProviderType.OPENROUTER.value, false)
    }

    private fun setupWindowInsets() {
        val rootView = findViewById<View>(android.R.id.content)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val imeHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
            val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
            val bottomOffset = maxOf(imeHeight, navBar)
            saveButton?.let { btn ->
                (btn.layoutParams as? FrameLayout.LayoutParams)?.bottomMargin = bottomOffset + dp(8)
            }
            insets
        }
    }

    private fun showSaveButton() {
        if (saveButton != null) {
            saveButton?.visibility = View.VISIBLE
            return
        }
        val btn = android.widget.Button(this).apply {
            text = getString(R.string.save)
            setTextColor(Color.WHITE)
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
            setBackgroundColor(typedValue.data)
            setPadding(dp(24), dp(12), dp(24), dp(12))
            textSize = 15f
            visibility = View.VISIBLE
            setOnClickListener { saveAgent() }
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(8)
        }
        val rootView = findViewById<FrameLayout>(android.R.id.content)
        rootView.addView(btn, params)
        saveButton = btn
    }

    private fun setupChangeTracking() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isLoaded) markChanged()
            }
        }
        agentNameInput.addTextChangedListener(watcher)
        agentDescriptionInput.addTextChangedListener(watcher)
        modelInput.addTextChangedListener(watcher)
        systemPromptInput.addTextChangedListener(watcher)
        agentApiKeyInput.addTextChangedListener(watcher)
        maxTokensInput.addTextChangedListener(watcher)

        providerTypeInput.setOnItemClickListener { _, _, _, _ -> if (isLoaded) markChanged() }
        toolsEnabledSwitch.setOnCheckedChangeListener { _, _ -> if (isLoaded) markChanged() }
        ragEnabledSwitch.setOnCheckedChangeListener { _, _ -> if (isLoaded) markChanged() }
        publicSwitch.setOnCheckedChangeListener { _, _ -> if (isLoaded) markChanged() }
    }

    private fun markChanged() {
        if (!hasChanges) {
            hasChanges = true
            showSaveButton()
        }
    }

    private fun saveAgent() {
        val name = agentNameInput.text.toString().trim()
        if (name.isEmpty()) {
            agentNameInput.error = getString(R.string.ai_v2_agent_name_required)
            return
        }

        val providerType = providerTypeInput.text.toString()
        val description = agentDescriptionInput.text.toString().trim()
        val model = modelInput.text.toString().trim()
        val systemPrompt = systemPromptInput.text.toString().trim()
        val apiKey = agentApiKeyInput.text.toString().trim()
        val temperature = temperatureSlider.value
        val maxTokens = maxTokensInput.text.toString().trim().toIntOrNull() ?: 4096
        val toolsEnabled = toolsEnabledSwitch.isChecked
        val ragEnabled = ragEnabledSwitch.isChecked
        val isPublic = publicSwitch.isChecked

        val providerConfig = JSONObject().apply {
            if (apiKey.isNotEmpty()) put("api_key", apiKey)
        }.toString()

        if (editAgentId.isEmpty()) {
            viewModel.createAgent(
                name = name,
                description = description,
                providerType = providerType,
                model = model,
                systemPrompt = systemPrompt,
                providerConfig = providerConfig,
                toolsEnabled = toolsEnabled,
                ragEnabled = ragEnabled,
                isPublic = isPublic,
                temperature = temperature,
                maxTokens = maxTokens
            )
        } else {
            viewModel.updateAgent(
                agentId = editAgentId,
                name = name,
                description = description,
                model = model,
                systemPrompt = systemPrompt,
                providerConfig = providerConfig,
                toolsEnabled = toolsEnabled,
                ragEnabled = ragEnabled,
                isPublic = isPublic,
                temperature = temperature,
                maxTokens = maxTokens
            )
        }
    }

    private fun loadAgent(agentId: String) {
        viewModel.loadAgent(agentId)
    }

    private fun observeState() {
        viewModel.agent.observe(this) { agent ->
            agent?.let {
                agentNameInput.setText(it.name)
                agentDescriptionInput.setText(it.description)
                providerTypeInput.setText(it.providerType.value, false)
                modelInput.setText(it.model)
                systemPromptInput.setText(it.systemPrompt)
                toolsEnabledSwitch.isChecked = it.toolsEnabled
                ragEnabledSwitch.isChecked = it.ragEnabled
                publicSwitch.isChecked = it.isPublic
                temperatureSlider.value = it.temperature.coerceIn(0f, 2f)
                temperatureValue.text = String.format("%.1f", it.temperature)
                maxTokensInput.setText(it.maxTokens.toString())
                try {
                    val config = JSONObject(it.providerConfig)
                    val keySource = config.optString("api_key_source", "")
                    val key = config.optString("apiKey", "").ifEmpty { config.optString("api_key", "") }
                    if (key.isNotEmpty()) {
                        val masked = if (key.length > 8) key.take(4) + "..." + key.takeLast(4) else key
                        agentApiKeyInput.setText(key)
                        agentApiKeyInput.hint = getString(R.string.ai_toolbar_api_key_status, masked)
                    } else if (keySource == "server") {
                        agentApiKeyInput.setText("")
                        agentApiKeyInput.hint = getString(R.string.ai_toolbar_server_key)
                    } else {
                        agentApiKeyInput.setText("")
                        agentApiKeyInput.hint = getString(R.string.ai_agent_api_key)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AiAgentSetup", "Failed to parse providerConfig: ${it.providerConfig}", e)
                    agentApiKeyInput.hint = getString(R.string.ai_agent_api_key)
                }
                isLoaded = true
            }
        }

        viewModel.saveResult.observe(this) { success ->
            if (success) {
                Toast.makeText(this, getString(R.string.ai_agent_saved), Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
