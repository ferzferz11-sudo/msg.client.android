package lavender.client.android.ui.remote

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import lavender.client.android.R
import lavender.client.android.theme.Theme
import lavender.client.android.theme.ThemeUtils

/**
 * Dialog for generating a new agent token.
 * Fields: Agent Name, Capabilities (multi-select with "Select All"), TTL (hours)
 */
class TokenDialog(
    private val context: Context,
    private val theme: Theme,
    private val onGenerate: (agentName: String, capabilities: List<String>, ttlHours: Int) -> Unit
) {
    private var dialog: AlertDialog? = null
    private val checkBoxes = mutableListOf<MaterialCheckBox>()

    fun show() {
        val bgColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
            setBackgroundColor(bgColor)
            id = View.generateViewId()
        }

        // Agent Name input
        val nameLayout = TextInputLayout(context).apply {
            setBoxBackgroundColor(bgColor)
            setBoxStrokeColor(primColor)
            defaultHintTextColor = android.content.res.ColorStateList.valueOf(txtColor)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            id = View.generateViewId()
        }
        val nameInput = TextInputEditText(context).apply {
            hint = context.getString(R.string.agent_name_hint)
            setText(context.getString(R.string.agent_name_default))
            setTextColor(txtColor)
            setHintTextColor(txtColor and 0x80FFFFFF.toInt())
            maxLines = 1
            id = View.generateViewId()
        }
        nameLayout.addView(nameInput)
        container.addView(nameLayout)

        // Capabilities label + "Select All" button
        val capHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 8)
            id = View.generateViewId()
        }
        val capLabel = TextView(context).apply {
            text = context.getString(R.string.capabilities_label)
            setTextColor(txtColor)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            id = View.generateViewId()
        }
        val selectAllBtn = TextView(context).apply {
            text = context.getString(R.string.select_all)
            setTextColor(primColor)
            textSize = 13f
            setPadding(16, 0, 0, 0)
            setOnClickListener { toggleAllCapabilities() }
            id = View.generateViewId()
        }
        capHeaderRow.addView(capLabel)
        capHeaderRow.addView(selectAllBtn)
        container.addView(capHeaderRow)

        // Capability checkboxes
        val capabilities = listOf(
            "shell" to context.getString(R.string.cap_shell),
            "git" to context.getString(R.string.cap_git),
            "build" to context.getString(R.string.cap_build),
            "deploy" to context.getString(R.string.cap_deploy),
            "file" to context.getString(R.string.cap_file),
            "docker" to context.getString(R.string.cap_docker),
            "ai" to context.getString(R.string.cap_ai)
        )
        checkBoxes.clear()

        capabilities.forEach { (key, label) ->
            val cb = MaterialCheckBox(context).apply {
                text = label
                setTextColor(txtColor)
                buttonTintList = android.content.res.ColorStateList.valueOf(primColor)
                isChecked = key == "shell" // default
                id = View.generateViewId()
            }
            checkBoxes.add(cb)
            container.addView(cb)
        }

        // TTL input
        val ttlLayout = TextInputLayout(context).apply {
            setBoxBackgroundColor(bgColor)
            setBoxStrokeColor(primColor)
            defaultHintTextColor = android.content.res.ColorStateList.valueOf(txtColor)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            id = View.generateViewId()
        }
        val ttlInput = TextInputEditText(context).apply {
            hint = context.getString(R.string.ttl_hours_hint)
            setTextColor(txtColor)
            setHintTextColor(txtColor and 0x80FFFFFF.toInt())
            maxLines = 1
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("24")
            id = View.generateViewId()
        }
        ttlLayout.addView(ttlInput)
        container.addView(ttlLayout)

        dialog = MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle(context.getString(R.string.generate_token_title))
            .setView(container)
            .setPositiveButton(context.getString(R.string.generate)) { _, _ ->
                val agentName = nameInput.text?.toString()?.trim() ?: ""
                if (agentName.isEmpty()) {
                    Toast.makeText(context, R.string.agent_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val selectedCaps = checkBoxes
                    .filter { it.isChecked }
                    .map { capabilities[checkBoxes.indexOf(it)].first }
                val ttl = ttlInput.text?.toString()?.toIntOrNull() ?: 24
                onGenerate(agentName, selectedCaps, ttl)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .create()

        dialog?.show()

        // Apply theme to dialog buttons and background
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(primColor)
        dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(txtColor)

        // Fix title color
        val titleId = context.resources.getIdentifier("alertTitle", "id", "android")
        dialog?.findViewById<TextView>(titleId)?.setTextColor(txtColor)

        // Set dialog window background
        dialog?.window?.setBackgroundDrawable(ColorDrawable(bgColor))
    }

    private fun toggleAllCapabilities() {
        val allChecked = checkBoxes.all { it.isChecked }
        checkBoxes.forEach { it.isChecked = !allChecked }
    }

    fun dismiss() {
        dialog?.dismiss()
    }
}
