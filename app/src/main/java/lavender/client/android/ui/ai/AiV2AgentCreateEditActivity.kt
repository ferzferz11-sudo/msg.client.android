package lavender.client.android.ui.ai

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import lavender.client.android.R
import lavender.client.android.data.ai.AiProviderType
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi

/**
 * AiV2AgentCreateEditActivity — create or edit AI v2 agent.
 */
class AiV2AgentCreateEditActivity : AppCompatActivity() {

    private lateinit var viewModel: AiV2AgentCreateEditViewModel
    private lateinit var agentNameInput: TextInputEditText
    private lateinit var agentDescriptionInput: TextInputEditText
    private lateinit var providerTypeInput: AutoCompleteTextView
    private lateinit var modelInput: TextInputEditText
    private lateinit var systemPromptInput: TextInputEditText
    private lateinit var providerConfigInput: TextInputEditText
    private lateinit var toolsEnabledSwitch: SwitchMaterial
    private lateinit var ragEnabledSwitch: SwitchMaterial
    private lateinit var saveButton: MaterialButton

    private var editAgentId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_v2_agent_create_edit)

        editAgentId = intent.getStringExtra("AGENT_ID") ?: ""

        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory).get(AiV2AgentCreateEditViewModel::class.java)

        initViews()
        setupToolbar()
        setupProviderTypeDropdown()
        setupSaveButton()
        observeState()
        ThemeUi.bind(this, SessionManager.session.value.username)

        if (editAgentId.isNotEmpty()) {
            loadAgent(editAgentId)
        }
    }

    private fun initViews() {
        agentNameInput = findViewById(R.id.agentNameInput)
        agentDescriptionInput = findViewById(R.id.agentDescriptionInput)
        providerTypeInput = findViewById(R.id.providerTypeInput)
        modelInput = findViewById(R.id.modelInput)
        systemPromptInput = findViewById(R.id.systemPromptInput)
        providerConfigInput = findViewById(R.id.providerConfigInput)
        toolsEnabledSwitch = findViewById(R.id.toolsEnabledSwitch)
        ragEnabledSwitch = findViewById(R.id.ragEnabledSwitch)
        saveButton = findViewById(R.id.saveButton)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        if (editAgentId.isNotEmpty()) {
            toolbar.title = getString(R.string.ai_v2_edit_agent)
        }
    }

    private fun setupProviderTypeDropdown() {
        val providerTypes = AiProviderType.entries.map { it.value }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, providerTypes)
        providerTypeInput.setAdapter(adapter)
        providerTypeInput.setText(AiProviderType.OPENROUTER.value, false)
    }

    private fun setupSaveButton() {
        saveButton.setOnClickListener {
            val name = agentNameInput.text.toString().trim()
            if (name.isEmpty()) {
                agentNameInput.error = "Name is required"
                return@setOnClickListener
            }

            val providerType = providerTypeInput.text.toString()
            val description = agentDescriptionInput.text.toString().trim()
            val model = modelInput.text.toString().trim()
            val systemPrompt = systemPromptInput.text.toString().trim()
            val providerConfig = providerConfigInput.text.toString().trim()
            val toolsEnabled = toolsEnabledSwitch.isChecked
            val ragEnabled = ragEnabledSwitch.isChecked

            if (editAgentId.isEmpty()) {
                viewModel.createAgent(
                    name = name,
                    description = description,
                    providerType = providerType,
                    model = model,
                    systemPrompt = systemPrompt,
                    providerConfig = providerConfig,
                    toolsEnabled = toolsEnabled,
                    ragEnabled = ragEnabled
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
                    ragEnabled = ragEnabled
                )
            }
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
            }
        }

        viewModel.saveResult.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Agent saved", Toast.LENGTH_SHORT).show()
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
}
