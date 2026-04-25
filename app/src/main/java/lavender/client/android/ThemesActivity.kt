package lavender.client.android

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.TextInputEditText
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import java.util.*

class ThemesActivity : AppCompatActivity() {

    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var customThemesContainer: LinearLayout
    private lateinit var btnAddTheme: MaterialButton
    private val grpcClient = GrpcClient
    private var username: String = ""
    private var currentThemeId: String = "dark"
    private var customThemes = mutableListOf<CustomThemeProto>()

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedColorScheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_themes)

        username = intent.getStringExtra("username") ?: ""

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        themeRadioGroup = findViewById(R.id.themeRadioGroup)
        customThemesContainer = findViewById(R.id.customThemesContainer)
        btnAddTheme = findViewById(R.id.btnAddTheme)

        loadThemes()

        btnAddTheme.setOnClickListener {
            showEditThemeDialog(null)
        }
    }

    private fun loadThemes() {
        grpcClient.getThemes(username) { currentId, list ->
            currentThemeId = currentId
            customThemes = list.toMutableList()
            runOnUiThread {
                updateUI()
            }
        }
    }

    private fun updateUI() {
        themeRadioGroup.clearCheck()
        
        // Handle built-in themes
        val radioLight = findViewById<MaterialRadioButton>(R.id.radioLight)
        val radioDark = findViewById<MaterialRadioButton>(R.id.radioDark)
        
        // Ensure text is visible
        val textColor = getOnSurfaceColor()
        radioLight.setTextColor(textColor)
        radioDark.setTextColor(textColor)
        
        radioLight.isChecked = currentThemeId == "light"
        radioDark.isChecked = currentThemeId == "dark"

        radioLight.setOnClickListener { selectTheme("light") }
        radioDark.setOnClickListener { selectTheme("dark") }

        // Handle custom themes
        customThemesContainer.removeAllViews()
        for (theme in customThemes) {
            val rb = MaterialRadioButton(this).apply {
                layoutParams = RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT,
                    (64 * resources.displayMetrics.density).toInt()
                )
                text = theme.name
                setTextColor(textColor)
                isChecked = currentThemeId == theme.id
                setOnClickListener { selectTheme(theme.id) }
                
                setOnLongClickListener {
                    showEditThemeDialog(theme)
                    true
                }
            }
            customThemesContainer.addView(rb)
        }
    }

    private fun getOnSurfaceColor(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        return typedValue.data
    }

    private fun selectTheme(themeId: String) {
        currentThemeId = themeId
        grpcClient.setCurrentTheme(username, themeId) { success ->
            if (success) {
                val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
                val scheme = if (themeId == "light" || themeId == "dark") themeId else {
                    val theme = customThemes.find { it.id == themeId }
                    if (theme?.isDark == true) "dark" else "light"
                }
                prefs.edit().putString("color_scheme", scheme).apply()
                runOnUiThread { recreate() }
            }
        }
    }

    private data class ThemeTemplate(
        val nameRes: Int,
        val primary: String,
        val background: String,
        val text: String,
        val isDark: Boolean
    )

    private fun showEditThemeDialog(theme: CustomThemeProto?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_theme, null)
        val editName = dialogView.findViewById<TextInputEditText>(R.id.editThemeName)
        val editPrimary = dialogView.findViewById<TextInputEditText>(R.id.editPrimaryColor)
        val editBackground = dialogView.findViewById<TextInputEditText>(R.id.editBackgroundColor)
        val editTextPrimary = dialogView.findViewById<TextInputEditText>(R.id.editTextPrimaryColor)
        val checkIsDark = dialogView.findViewById<MaterialCheckBox>(R.id.checkIsDark)
        val previewPrimary = dialogView.findViewById<View>(R.id.previewPrimary)
        val previewBackground = dialogView.findViewById<View>(R.id.previewBackground)
        val previewText = dialogView.findViewById<View>(R.id.previewText)
        val templatesContainer = dialogView.findViewById<LinearLayout>(R.id.templatesContainer)
        
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSave)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<MaterialButton>(R.id.btnDelete)
        val title = dialogView.findViewById<TextView>(R.id.dialogTitle)

        // Setup templates
        val templates = listOf(
            ThemeTemplate(R.string.theme_template_green, "#2E7D32", "#F1F8E9", "#1B5E20", false),
            ThemeTemplate(R.string.theme_template_blue, "#1565C0", "#E3F2FD", "#0D47A1", false),
            ThemeTemplate(R.string.theme_template_purple, "#6A1B9A", "#F3E5F5", "#4A148C", false),
            ThemeTemplate(R.string.theme_template_sunset, "#D84315", "#FFF3E0", "#BF360C", false)
        )

        for (tmpl in templates) {
            val chip = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 8, 0) }
                text = getString(tmpl.nameRes)
                textSize = 12f
                cornerRadius = (20 * resources.displayMetrics.density).toInt()
                setOnClickListener {
                    editName.setText(getString(tmpl.nameRes))
                    editPrimary.setText(tmpl.primary)
                    editBackground.setText(tmpl.background)
                    editTextPrimary.setText(tmpl.text)
                    checkIsDark.isChecked = tmpl.isDark
                }
            }
            templatesContainer.addView(chip)
        }

        // Live preview logic
        fun updatePreviews() {
            try {
                previewPrimary.backgroundTintList = ColorStateList.valueOf(editPrimary.text.toString().toColorInt())
                previewBackground.backgroundTintList = ColorStateList.valueOf(editBackground.text.toString().toColorInt())
                previewText.backgroundTintList = ColorStateList.valueOf(editTextPrimary.text.toString().toColorInt())
            } catch (_: Exception) {}
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreviews() }
            override fun afterTextChanged(s: Editable?) {}
        }

        editPrimary.addTextChangedListener(watcher)
        editBackground.addTextChangedListener(watcher)
        editTextPrimary.addTextChangedListener(watcher)

        if (theme != null) {
            title.text = getString(R.string.edit_theme)
            editName.setText(theme.name)
            editPrimary.setText(theme.primaryColor)
            editBackground.setText(theme.backgroundColor)
            editTextPrimary.setText(theme.textPrimaryColor)
            checkIsDark.isChecked = theme.isDark
            btnDelete.isVisible = true
        }
        
        updatePreviews()

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        
        btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.delete_theme_confirm)
                .setPositiveButton(R.string.delete) { _, _ ->
                    grpcClient.deleteTheme(username, theme!!.id) { success ->
                        if (success) {
                            runOnUiThread {
                                dialog.dismiss()
                                loadThemes()
                            }
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        btnSave.setOnClickListener {
            val name = editName.text.toString().trim()
            if (name.isEmpty()) return@setOnClickListener

            val newTheme = CustomThemeProto(
                id = theme?.id ?: UUID.randomUUID().toString(),
                name = name,
                primaryColor = editPrimary.text.toString(),
                onPrimaryColor = "#FFFFFF", // Default logic
                surfaceColor = editBackground.text.toString(),
                onSurfaceColor = editTextPrimary.text.toString(),
                backgroundColor = editBackground.text.toString(),
                textPrimaryColor = editTextPrimary.text.toString(),
                textSecondaryColor = editTextPrimary.text.toString(),
                isDark = checkIsDark.isChecked
            )

            grpcClient.saveTheme(username, newTheme) { success, _ ->
                if (success) {
                    runOnUiThread {
                        dialog.dismiss()
                        loadThemes()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun applySavedColorScheme() {
        val theme = when (getSharedPreferences("ChatPrefs", MODE_PRIVATE).getString("color_scheme", "dark")) {
            "light" -> R.style.Theme_Lavender_Light_NoActionBar
            else -> R.style.Theme_Lavender_Dark_NoActionBar
        }
        setTheme(theme)
    }
}
