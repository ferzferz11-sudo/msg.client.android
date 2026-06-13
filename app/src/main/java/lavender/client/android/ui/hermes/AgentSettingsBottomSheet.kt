package lavender.client.android.ui.hermes

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import lavender.client.android.R
import lavender.client.android.data.models.AgentInfo
import lavender.client.android.data.repository.HermesRepository
import lavender.client.android.ui.widget.StandardBottomSheet

class AgentSettingsBottomSheet(
    context: Context,
    private val agent: AgentInfo,
    private val userId: String,
    private val onSaved: () -> Unit
) : StandardBottomSheet(context) {

    private val repository = HermesRepository()
    private lateinit var agentNameInput: TextInputEditText
    private lateinit var systemPromptInput: TextInputEditText
    private lateinit var modelInput: TextInputEditText
    private lateinit var maxTokensInput: TextInputEditText
    private lateinit var saveBtn: MaterialButton
    private lateinit var deleteBtn: MaterialButton
    private lateinit var cancelBtn: MaterialButton

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        setupViews()
        populateFields()
    }

    private fun setupViews() {
        val contentView = LayoutInflater.from(context)
            .inflate(R.layout.bottom_sheet_agent_settings, contentContainer, true)

        agentNameInput = contentView.findViewById(R.id.agentNameInput)
        systemPromptInput = contentView.findViewById(R.id.systemPromptInput)
        modelInput = contentView.findViewById(R.id.modelInput)
        maxTokensInput = contentView.findViewById(R.id.maxTokensInput)
        saveBtn = contentView.findViewById(R.id.saveBtn)
        deleteBtn = contentView.findViewById(R.id.deleteBtn)
        cancelBtn = contentView.findViewById(R.id.cancelBtn)

        // Only show delete for custom agents (not presets)
        if (!agent.isPreset) {
            deleteBtn.visibility = View.VISIBLE
        }

        setTitle(agent.name)
        setCancelable(true)

        saveBtn.setOnClickListener { saveAgent() }
        deleteBtn.setOnClickListener { confirmDelete() }
        cancelBtn.setOnClickListener { dismiss() }
    }

    private fun populateFields() {
        agentNameInput.setText(agent.name)
        systemPromptInput.setText(agent.description)
        // model and maxTokens not available from AgentInfo — leave empty for user to fill
        modelInput.setText("")
        maxTokensInput.setText("")
    }

    private fun saveAgent() {
        val name = agentNameInput.text.toString().trim()
        val prompt = systemPromptInput.text.toString().trim()
        val model = modelInput.text.toString().trim()
        val maxTokens = maxTokensInput.text.toString().toIntOrNull() ?: 0

        if (name.isEmpty()) {
            Toast.makeText(context, getString(R.string.enter_agent_name), Toast.LENGTH_SHORT).show()
            return
        }

        saveBtn.isEnabled = false

        scope.launch {
            try {
                val result = repository.updateAgent(
                    agentId = agent.id,
                    userId = userId,
                    name = name,
                    systemPrompt = prompt,
                    model = model,
                    maxTokens = maxTokens
                )
                result.onSuccess {
                    Toast.makeText(context, getString(R.string.agent_updated), Toast.LENGTH_SHORT).show()
                    dismiss()
                    onSaved()
                }.onFailure { e ->
                    Toast.makeText(context, getString(R.string.error_colon, e.message), Toast.LENGTH_LONG).show()
                    saveBtn.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(context, getString(R.string.error_colon, e.message), Toast.LENGTH_LONG).show()
                saveBtn.isEnabled = true
            }
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.delete_agent_title))
            .setMessage(getString(R.string.delete_agent_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                scope.launch {
                    try {
                        val result = repository.deleteAgent(agent.id, userId)
                        result.onSuccess {
                            Toast.makeText(context, getString(R.string.agent_deleted), Toast.LENGTH_SHORT).show()
                            dismiss()
                            onSaved()
                        }.onFailure { e ->
                            Toast.makeText(context, getString(R.string.error_colon, e.message), Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, getString(R.string.error_colon, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    fun closeSheet() {
        scope.cancel()
        super.dismiss()
    }
}
