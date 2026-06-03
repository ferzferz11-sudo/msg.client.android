package lavender.client.android.ui.hermes

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.repository.HermesRepository

class AgentSettingsActivity : AppCompatActivity() {

    private lateinit var presetSpinner: AutoCompleteTextView
    private lateinit var presetSpinnerLayout: TextInputLayout
    private lateinit var agentNameInput: TextInputEditText
    private lateinit var systemPromptInput: TextInputEditText
    private lateinit var modelInput: TextInputEditText
    private lateinit var maxTokensInput: TextInputEditText
    private lateinit var saveBtn: MaterialButton
    private lateinit var deleteBtn: MaterialButton

    private val repository = HermesRepository()
    private var userId: String = ""
    private var agentId: String = ""
    private var mode: String = "create" // "create" or "edit"
    private var presets = listOf<lavender.client.android.data.models.AgentPreset>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agent_settings)

        userId = intent.getStringExtra("USER_ID") ?: ""
        agentId = intent.getStringExtra("AGENT_ID") ?: ""
        mode = intent.getStringExtra("MODE") ?: "create"

        setupToolbar()
        setupViews()
        loadPresets()

        if (mode == "edit" && agentId.isNotEmpty()) {
            loadAgentData()
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = if (mode == "create") "Новый агент" else "Редактирование"
    }

    private fun setupViews() {
        presetSpinner = findViewById(R.id.presetSpinner)
        presetSpinnerLayout = findViewById(R.id.presetSpinnerLayout)
        agentNameInput = findViewById(R.id.agentNameInput)
        systemPromptInput = findViewById(R.id.systemPromptInput)
        modelInput = findViewById(R.id.modelInput)
        maxTokensInput = findViewById(R.id.maxTokensInput)
        saveBtn = findViewById(R.id.saveBtn)
        deleteBtn = findViewById(R.id.deleteBtn)

        if (mode == "edit") {
            deleteBtn.visibility = View.VISIBLE
        }

        presetSpinner.setOnItemClickListener { _, _, position, _ ->
            val preset = presets.getOrNull(position)
            preset?.let {
                agentNameInput.setText(it.name)
                systemPromptInput.setText(it.description)
                maxTokensInput.setText(it.maxTokens.toString())
            }
        }

        saveBtn.setOnClickListener { saveAgent() }
        deleteBtn.setOnClickListener { confirmDelete() }
    }

    private fun loadPresets() {
        lifecycleScope.launch {
            try {
                presets = repository.getPresets()
                val adapter = ArrayAdapter(
                    this@AgentSettingsActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    presets.map { "${it.icon} ${it.name}" }
                )
                presetSpinner.setAdapter(adapter)
            } catch (e: Exception) {
                Toast.makeText(this@AgentSettingsActivity, "Ошибка загрузки пресетов", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadAgentData() {
        // FUTURE — load existing agent data for editing
    }

    private fun saveAgent() {
        val presetPosition = presets.indexOfFirst {
            presetSpinner.text.toString().contains(it.name)
        }
        if (presetPosition < 0 && mode == "create") {
            Toast.makeText(this, "Выберите пресет", Toast.LENGTH_SHORT).show()
            return
        }

        val presetId = if (presetPosition >= 0) presets[presetPosition].id else ""
        val name = agentNameInput.text.toString().trim()
        val prompt = systemPromptInput.text.toString().trim()
        val model = modelInput.text.toString().trim()
        val maxTokens = maxTokensInput.text.toString().toIntOrNull() ?: 0

        if (name.isEmpty()) {
            Toast.makeText(this, "Введите имя агента", Toast.LENGTH_SHORT).show()
            return
        }

        saveBtn.isEnabled = false

        lifecycleScope.launch {
            try {
                if (mode == "create" && presetId.isNotEmpty()) {
                    val result = repository.createAgent(userId, presetId, name, prompt, model, maxTokens)
                    result.onSuccess {
                        Toast.makeText(this@AgentSettingsActivity, "Агент создан", Toast.LENGTH_SHORT).show()
                        finish()
                    }.onFailure { e ->
                        Toast.makeText(this@AgentSettingsActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                        saveBtn.isEnabled = true
                    }
                } else if (mode == "edit" && agentId.isNotEmpty()) {
                    val result = repository.updateAgent(agentId, userId, name, prompt, model, maxTokens)
                    result.onSuccess {
                        Toast.makeText(this@AgentSettingsActivity, "Агент обновлён", Toast.LENGTH_SHORT).show()
                        finish()
                    }.onFailure { e ->
                        Toast.makeText(this@AgentSettingsActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                        saveBtn.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@AgentSettingsActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                saveBtn.isEnabled = true
            }
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Удалить агента?")
            .setMessage("Это действие необратимо.")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    val result = repository.deleteAgent(agentId, userId)
                    result.onSuccess {
                        Toast.makeText(this@AgentSettingsActivity, "Агент удалён", Toast.LENGTH_SHORT).show()
                        finish()
                    }.onFailure { e ->
                        Toast.makeText(this@AgentSettingsActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
