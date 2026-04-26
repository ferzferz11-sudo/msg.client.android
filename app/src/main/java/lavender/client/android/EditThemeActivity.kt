package lavender.client.android

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import lavender.client.android.ui.ThemeManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.*

class EditThemeActivity : AppCompatActivity() {

    private val grpcClient = GrpcClient
    private val okHttpClient = OkHttpClient()
    private var username: String = ""
    private var themeId: String? = null
    private var existingTheme: CustomThemeProto? = null

    private lateinit var editName: TextInputEditText
    private lateinit var editBackgroundImageUrl: TextInputEditText
    private lateinit var checkIsDark: MaterialCheckBox
    private lateinit var btnSave: MaterialButton
    private lateinit var btnDelete: MaterialButton
    private lateinit var btnUploadBackground: ImageButton
    private lateinit var uploadProgress: ProgressBar
    
    private lateinit var previewChatList: View
    private lateinit var previewChat: View

    private val colorInputs = mutableMapOf<String, EditText>()
    private val colorPreviews = mutableMapOf<String, View>()

    private val pickBackgroundImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadBackgroundImage(it) }
    }

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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_theme)

        username = intent.getStringExtra("username") ?: ""
        themeId = intent.getStringExtra("theme_id")
        
        initViews()
        setupTemplates()
        
        if (themeId != null) {
            loadTheme()
            btnDelete.isVisible = true
        } else {
            applyTemplate(templates[0])
        }

        updateLivePreviews()
    }

    private fun initViews() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        editName = findViewById(R.id.editThemeName)
        editBackgroundImageUrl = findViewById(R.id.editBackgroundImageUrl)
        checkIsDark = findViewById(R.id.checkIsDark)
        btnSave = findViewById(R.id.btnSave)
        btnDelete = findViewById(R.id.btnDelete)
        btnUploadBackground = findViewById(R.id.btnUploadBackground)
        uploadProgress = findViewById(R.id.uploadProgress)
        
        previewChatList = findViewById(R.id.previewChatList)
        previewChat = findViewById(R.id.previewChat)

        setupColorInput("primary", R.id.layoutPrimary, getString(R.string.primary_color), "#312051")
        setupColorInput("background", R.id.layoutBackground, getString(R.string.background_color), "#FFFFFF")
        setupColorInput("surface", R.id.layoutSurface, getString(R.string.surface_color), "#F8F7FC")
        setupColorInput("textPrimary", R.id.layoutTextPrimary, getString(R.string.text_primary_color), "#000000")
        setupColorInput("onPrimary", R.id.layoutOnPrimary, getString(R.string.on_primary_color), "#FFFFFF")
        setupColorInput("onSurface", R.id.layoutOnSurface, getString(R.string.on_surface_color), "#000000")

        btnSave.setOnClickListener { saveTheme() }
        btnDelete.setOnClickListener { deleteTheme() }
        btnUploadBackground.setOnClickListener { pickBackgroundImageLauncher.launch("image/*") }
        
        checkIsDark.setOnCheckedChangeListener { _, _ -> updateLivePreviews() }
        editBackgroundImageUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateLivePreviews() }
        })
    }

    private fun setupColorInput(key: String, layoutId: Int, label: String, default: String) {
        val layout = findViewById<View>(layoutId)
        val labelView = layout.findViewById<TextView>(R.id.colorLabel)
        val input = layout.findViewById<EditText>(R.id.colorInput)
        val preview = layout.findViewById<View>(R.id.colorPreview)

        labelView.text = label
        input.setText(default)
        
        colorInputs[key] = input
        colorPreviews[key] = preview

        val updateColor = { hex: String ->
            try {
                val color = hex.toColorInt()
                preview.backgroundTintList = ColorStateList.valueOf(color)
                updateLivePreviews()
            } catch (_: Exception) {}
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateColor(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        preview.setOnClickListener {
            showColorPickerDialog(input.text.toString()) { selectedHex ->
                input.setText(selectedHex)
            }
        }
    }

    private fun showColorPickerDialog(initialColor: String, onColorSelected: (String) -> Unit) {
        val colors = listOf(
            "#312051", "#967BB6", "#E6E6FA", "#F8F7FC", "#FFFFFF", "#000000",
            "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3",
            "#03A9F4", "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
            "#FFEB3B", "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E", "#607D8B"
        )

        val gridView = GridView(this).apply {
            numColumns = 5
            verticalSpacing = 16
            horizontalSpacing = 16
            setPadding(32, 32, 32, 32)
            adapter = object : BaseAdapter() {
                override fun getCount(): Int = colors.size
                override fun getItem(position: Int): Any = colors[position]
                override fun getItemId(position: Int): Long = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    return View(this@EditThemeActivity).apply {
                        layoutParams = AbsListView.LayoutParams(100, 100)
                        background = ContextCompat.getDrawable(this@EditThemeActivity, R.drawable.circle_indicator)
                        backgroundTintList = ColorStateList.valueOf(colors[position].toColorInt())
                    }
                }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.color_scheme)
            .setView(gridView)
            .create()

        gridView.setOnItemClickListener { _, _, position, _ ->
            onColorSelected(colors[position])
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadTheme() {
        grpcClient.getThemes(username) { _, themes ->
            val theme = themes.find { it.id == themeId }
            if (theme != null) {
                existingTheme = theme
                runOnUiThread {
                    editName.setText(theme.name)
                    colorInputs["primary"]?.setText(theme.primaryColor)
                    colorInputs["background"]?.setText(theme.backgroundColor)
                    colorInputs["surface"]?.setText(theme.surfaceColor)
                    colorInputs["textPrimary"]?.setText(theme.textPrimaryColor)
                    colorInputs["onPrimary"]?.setText(theme.onPrimaryColor)
                    colorInputs["onSurface"]?.setText(theme.onSurfaceColor)
                    editBackgroundImageUrl.setText(theme.backgroundImageUrl)
                    checkIsDark.isChecked = theme.isDark
                    updateLivePreviews()
                }
            }
        }
    }

    private fun getCurrentThemeFromInputs(): CustomThemeProto {
        return CustomThemeProto(
            id = themeId ?: UUID.randomUUID().toString(),
            name = editName.text.toString().trim(),
            primaryColor = colorInputs["primary"]?.text.toString(),
            onPrimaryColor = colorInputs["onPrimary"]?.text.toString(),
            surfaceColor = colorInputs["surface"]?.text.toString(),
            onSurfaceColor = colorInputs["onSurface"]?.text.toString(),
            backgroundColor = colorInputs["background"]?.text.toString(),
            textPrimaryColor = colorInputs["textPrimary"]?.text.toString(),
            textSecondaryColor = colorInputs["textPrimary"]?.text.toString(), // Default to textPrimary for now
            isDark = checkIsDark.isChecked,
            backgroundImageUrl = editBackgroundImageUrl.text.toString().trim()
        )
    }

    private fun updateLivePreviews() {
        val theme = getCurrentThemeFromInputs()
        applyThemeToPreview(previewChatList, theme)
        applyThemeToPreview(previewChat, theme)
        
        // Chat specific background image
        val bgImage = previewChat.findViewById<ImageView>(R.id.previewChatBgImage)
        if (theme.backgroundImageUrl.isNotEmpty()) {
            Glide.with(this).load(theme.backgroundImageUrl).centerCrop().into(bgImage)
            previewChat.findViewById<View>(R.id.previewChatRoot).setBackgroundColor(Color.TRANSPARENT)
        } else {
            bgImage.setImageDrawable(null)
            try {
                previewChat.findViewById<View>(R.id.previewChatRoot).setBackgroundColor(theme.backgroundColor.toColorInt())
            } catch (_: Exception) {}
        }
    }

    private fun applyThemeToPreview(root: View, theme: CustomThemeProto) {
        try {
            val primary = theme.primaryColor.toColorInt()
            val background = theme.backgroundColor.toColorInt()
            val onSurface = theme.onSurfaceColor.toColorInt()
            val surface = theme.surfaceColor.toColorInt()
            val textPrimary = theme.textPrimaryColor.toColorInt()
            val onPrimary = theme.onPrimaryColor.toColorInt()

            // Root backgrounds
            root.findViewById<View>(R.id.previewRoot)?.setBackgroundColor(background)
            root.findViewById<View>(R.id.previewChatRoot)?.setBackgroundColor(background)
            
            // Toolbars
            root.findViewById<View>(R.id.previewToolbar)?.setBackgroundColor(primary)
            root.findViewById<View>(R.id.previewChatToolbar)?.setBackgroundColor(primary)
            
            // Cards and bubbles
            root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.previewChatCard)?.setCardBackgroundColor(surface)
            root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.previewIncomingBubble)?.setCardBackgroundColor(surface)
            root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.previewOutgoingBubble)?.setCardBackgroundColor(primary)
            
            // Text colors
            root.findViewById<TextView>(R.id.previewTextName)?.setTextColor(textPrimary)
            root.findViewById<TextView>(R.id.previewTextMsg)?.setTextColor(onSurface)
            root.findViewById<TextView>(R.id.previewIncomingText)?.setTextColor(textPrimary)
            root.findViewById<TextView>(R.id.previewOutgoingText)?.setTextColor(onPrimary)
            
            // Bottom panel
            root.findViewById<View>(R.id.previewBottomPanel)?.setBackgroundColor(surface)
            
        } catch (_: Exception) {}
    }

    private fun saveTheme() {
        val name = editName.text.toString().trim()
        if (name.isEmpty()) {
            editName.error = getString(R.string.username_empty)
            return
        }

        val theme = getCurrentThemeFromInputs()
        val overlay = findViewById<View>(R.id.progressOverlay)
        overlay.isVisible = true
        
        grpcClient.saveTheme(username, theme) { success, msg ->
            runOnUiThread {
                overlay.isVisible = false
                if (success) {
                    Toast.makeText(this, R.string.message_edited, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun deleteTheme() {
        val id = themeId ?: return
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_theme_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                grpcClient.deleteTheme(username, id) { success ->
                    if (success) {
                        runOnUiThread { finish() }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun uploadBackgroundImage(uri: Uri) {
        uploadProgress.isVisible = true
        btnUploadBackground.isVisible = false
        
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: return@launch

                val fileName = "theme_bg_${System.currentTimeMillis()}.jpg"
                val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("background", fileName, bytes.toRequestBody("image/*".toMediaTypeOrNull()))
                    .build()

                val request = Request.Builder()
                    .url("http://159.195.38.145:8082/upload-background")
                    .post(requestBody)
                    .build()

                val response = withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    val fileUrl = JSONObject(responseBody).getString("url")
                    withContext(Dispatchers.Main) {
                        editBackgroundImageUrl.setText(fileUrl)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@EditThemeActivity, "Upload failed: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditThemeActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    uploadProgress.isVisible = false
                    btnUploadBackground.isVisible = true
                }
            }
        }
    }

    private data class ThemeTemplate(
        val nameRes: Int,
        val primary: String,
        val background: String,
        val surface: String,
        val text: String,
        val onPrimary: String,
        val onSurface: String,
        val isDark: Boolean
    )

    private val templates = listOf(
        ThemeTemplate(R.string.theme_template_green, "#2E7D32", "#F1F8E9", "#DCEDC8", "#1B5E20", "#FFFFFF", "#33691E", false),
        ThemeTemplate(R.string.theme_template_blue, "#1565C0", "#E3F2FD", "#BBDEFB", "#0D47A1", "#FFFFFF", "#01579B", false),
        ThemeTemplate(R.string.theme_template_purple, "#6A1B9A", "#F3E5F5", "#E1BEE7", "#4A148C", "#FFFFFF", "#4A148C", false),
        ThemeTemplate(R.string.theme_template_sunset, "#D84315", "#FFF3E0", "#FFE0B2", "#BF360C", "#FFFFFF", "#E65100", false)
    )

    private fun setupTemplates() {
        val container = findViewById<LinearLayout>(R.id.templatesContainer)
        for (tmpl in templates) {
            val chip = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 8, 0) }
                text = getString(tmpl.nameRes)
                textSize = 12f
                cornerRadius = (20 * resources.displayMetrics.density).toInt()
                setOnClickListener { applyTemplate(tmpl) }
            }
            container.addView(chip)
        }
    }

    private fun applyTemplate(tmpl: ThemeTemplate) {
        editName.setText(getString(tmpl.nameRes))
        colorInputs["primary"]?.setText(tmpl.primary)
        colorInputs["background"]?.setText(tmpl.background)
        colorInputs["surface"]?.setText(tmpl.surface)
        colorInputs["textPrimary"]?.setText(tmpl.text)
        colorInputs["onPrimary"]?.setText(tmpl.onPrimary)
        colorInputs["onSurface"]?.setText(tmpl.onSurface)
        checkIsDark.isChecked = tmpl.isDark
        updateLivePreviews()
    }
}
