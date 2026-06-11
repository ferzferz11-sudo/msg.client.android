package lavender.client.android.ui.remote

import android.content.Context
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import lavender.client.android.R
import lavender.client.android.theme.Theme
import lavender.client.android.theme.ThemeUtils

/**
 * Dialog for generating a new agent token.
 * Fields: Agent Name, Capabilities (multi-select), TTL (hours)
 */
class TokenDialog(
    private val context: Context,
    private val theme: Theme,
    private val onGenerate: (agentName: String, capabilities: List<String>, ttlHours: Int) -> Unit
) {
    private var dialog: AlertDialog? = null

    fun show() {
        val bgColor = ThemeUtils.parseSafeColor(theme.surfaceColor)
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
            setBackgroundColor(bgColor)
        }

        // Agent Name input
        val nameLayout = TextInputLayout(context).apply {
            setBoxBackgroundColor(bgColor)
            setBoxStrokeColor(primColor)
            defaultHintTextColor = android.content.res.ColorStateList.valueOf(txtColor)
        }
        val nameInput = TextInputEditText(context).apply {
            hint = "Имя агента"
            setTextColor(txtColor)
            setHintTextColor(txtColor and 0x80FFFFFF.toInt())
            maxLines = 1
        }
        nameLayout.addView(nameInput)
        container.addView(nameLayout)

        // Capabilities label
        val capLabel = TextView(context).apply {
            text = "Возможности:"
            setTextColor(txtColor)
            textSize = 14f
            setPadding(0, 24, 0, 8)
        }
        container.addView(capLabel)

        // Capability checkboxes
        val capabilities = listOf(
            "shell" to "Shell",
            "git" to "Git",
            "build" to "Сборка",
            "deploy" to "Деплой",
            "file" to "Файлы",
            "docker" to "Docker",
            "ai" to "AI"
        )
        val checkBoxes = mutableListOf<MaterialCheckBox>()

        capabilities.forEach { (key, label) ->
            val cb = MaterialCheckBox(context).apply {
                text = label
                setTextColor(txtColor)
                buttonTintList = android.content.res.ColorStateList.valueOf(primColor)
                isChecked = key == "shell" // default
            }
            checkBoxes.add(cb)
            container.addView(cb)
        }

        // TTL input
        val ttlLayout = TextInputLayout(context).apply {
            setBoxBackgroundColor(bgColor)
            setBoxStrokeColor(primColor)
            defaultHintTextColor = android.content.res.ColorStateList.valueOf(txtColor)
        }
        val ttlInput = TextInputEditText(context).apply {
            hint = "TTL (часы)"
            setTextColor(txtColor)
            setHintTextColor(txtColor and 0x80FFFFFF.toInt())
            maxLines = 1
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("24")
        }
        ttlLayout.addView(ttlInput)
        container.addView(ttlLayout)

        dialog = AlertDialog.Builder(context)
            .setTitle("Сгенерировать токен")
            .setView(container)
            .setPositiveButton("Сгенерировать") { _, _ ->
                val agentName = nameInput.text?.toString()?.trim() ?: ""
                if (agentName.isEmpty()) {
                    Toast.makeText(context, "Введите имя агента", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val selectedCaps = checkBoxes
                    .filter { it.isChecked }
                    .map { capabilities[checkBoxes.indexOf(it)].first }
                val ttl = ttlInput.text?.toString()?.toIntOrNull() ?: 24
                onGenerate(agentName, selectedCaps, ttl)
            }
            .setNegativeButton("Отмена", null)
            .show()

        // Apply theme to dialog buttons
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(primColor)
        dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(txtColor)
    }

    fun dismiss() {
        dialog?.dismiss()
    }
}
