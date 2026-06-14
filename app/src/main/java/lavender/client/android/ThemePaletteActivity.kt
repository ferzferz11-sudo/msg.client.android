package lavender.client.android

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import lavender.client.android.theme.BuiltInThemes
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.data.ThemeMappers
import lavender.client.android.theme.ui.ThemeUi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

data class ColorItem(
    val name: String,
    val colorHex: String,
    val description: String,
    val fieldName: String
)

class ThemePaletteActivity : AppCompatActivity(),
    PaletteFragment.PaletteCallback,
    BackgroundsFragment.BackgroundsCallback {

    private lateinit var originalTheme: CustomThemeProto
    private lateinit var currentColors: MutableMap<String, String>
    private lateinit var defaultColors: MutableMap<String, String>
    private var themeId: String = ""
    private var username: String = ""
    private var hasChanges = false
    private lateinit var saveButton: Button
    private lateinit var uploadProgress: ProgressBar

    private var chatListBackgroundUri: Uri? = null
    private var chatBackgroundUri: Uri? = null
    private var originalChatListBackgroundUri: Uri? = null
    private var originalChatBackgroundUri: Uri? = null

    private lateinit var viewPager: ViewPager2
    private lateinit var paletteFragment: PaletteFragment
    private lateinit var backgroundsFragment: BackgroundsFragment

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme_palette)

        themeId = intent.getStringExtra("theme_id") ?: return finish()
        username = intent.getStringExtra("username") ?: ""

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationOnClickListener { checkUnsavedChangesAndFinish() }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val currentThemeId = prefs.getString("current_theme_id", "dark") ?: "dark"
        if (currentThemeId != themeId) {
            prefs.edit { putString("current_theme_id", themeId) }
        }
        ThemeUi.bind(this, username)

        val isCustomThemeWithExtras = intent.hasExtra("primary_color")

        if (isCustomThemeWithExtras) {
            val customTheme = CustomThemeProto(
                id = themeId,
                name = intent.getStringExtra("theme_name") ?: themeId,
                primaryColor = intent.getStringExtra("primary_color") ?: "#5F9EA0",
                backgroundColor = intent.getStringExtra("background_color") ?: "#1E1E1E",
                surfaceColor = intent.getStringExtra("surface_color") ?: "#2D2D2D",
                surfaceContainer = intent.getStringExtra("surface_container") ?: "#252525",
                textPrimaryColor = intent.getStringExtra("text_primary_color") ?: "#FFFFFF",
                textSecondaryColor = intent.getStringExtra("text_secondary_color") ?: "#E0E0E0",
                onPrimaryColor = intent.getStringExtra("on_primary_color") ?: "#FFFFFF",
                onSurfaceColor = intent.getStringExtra("on_surface_color") ?: "#E0E0E0",
                bottomPanelColor = intent.getStringExtra("bottom_panel_color") ?: "#2D2D2D",
                onBottomPanelColor = intent.getStringExtra("on_bottom_panel_color") ?: "#5F9EA0",
                outgoingBubbleColor = intent.getStringExtra("outgoing_bubble_color") ?: "#2A2C6D",
                incomingBubbleColor = intent.getStringExtra("incoming_bubble_color") ?: "#16173A",
                outgoingTextColor = intent.getStringExtra("outgoing_text_color") ?: "",
                incomingTextColor = intent.getStringExtra("incoming_text_color") ?: "",
                chatListBackgroundImageUrl = intent.getStringExtra("chat_list_background") ?: "",
                chatBackgroundImageUrl = intent.getStringExtra("chat_background") ?: ""
            )
            originalTheme = customTheme
        } else {
            originalTheme = getThemeById(themeId) ?: return finish()
        }

        supportActionBar?.title = when (originalTheme.id) {
            "builtin_green" -> getString(R.string.theme_template_green)
            "builtin_blue" -> getString(R.string.theme_template_blue)
            "builtin_graphite" -> getString(R.string.theme_template_graphite)
            "builtin_mint" -> getString(R.string.theme_template_mint)
            else -> originalTheme.name
        }

        currentColors = mutableMapOf()
        defaultColors = mutableMapOf()

        initDefaultColors(originalTheme)
        currentColors.putAll(defaultColors)

        chatListBackgroundUri = originalTheme.chatListBackgroundImageUrl.takeIf { it.isNotEmpty() }?.toUri()
        chatBackgroundUri = originalTheme.chatBackgroundImageUrl.takeIf { it.isNotEmpty() }?.toUri()
        originalChatListBackgroundUri = chatListBackgroundUri
        originalChatBackgroundUri = chatBackgroundUri

        saveButton = findViewById(R.id.saveButton)
        uploadProgress = findViewById(R.id.uploadProgress)
        saveButton.setOnClickListener { showSaveThemeDialog() }
        saveButton.isVisible = false

        setupViewPager()
    }

    private fun setupViewPager() {
        viewPager = findViewById(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        paletteFragment = PaletteFragment()
        backgroundsFragment = BackgroundsFragment()

        paletteFragment.setCallback(this)
        backgroundsFragment.setCallback(this)

        viewPager.adapter = ViewPagerAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_palette)
                1 -> getString(R.string.tab_backgrounds)
                else -> ""
            }
        }.attach()
    }

    inner class ViewPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 2
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> paletteFragment
                1 -> backgroundsFragment
                else -> paletteFragment
            }
        }
    }

    private fun getThemeById(id: String): CustomThemeProto? {
        return BuiltInThemes.findById(id)?.let { ThemeMappers.toProto(it) }
            ?: if (id == "dark") ThemeMappers.toProto(BuiltInThemes.dark) else null
    }

    private fun initDefaultColors(theme: CustomThemeProto) {
        defaultColors["primaryColor"] = theme.primaryColor
        defaultColors["backgroundColor"] = theme.backgroundColor
        defaultColors["surfaceColor"] = theme.surfaceColor
        defaultColors["surfaceContainer"] = theme.surfaceContainer
        defaultColors["textPrimaryColor"] = theme.textPrimaryColor
        defaultColors["textSecondaryColor"] = theme.textSecondaryColor
        defaultColors["onPrimaryColor"] = theme.onPrimaryColor
        defaultColors["onSurfaceColor"] = theme.onSurfaceColor
        defaultColors["bottomPanelColor"] = theme.bottomPanelColor
        defaultColors["onBottomPanelColor"] = theme.onBottomPanelColor
        defaultColors["outgoingBubbleColor"] = theme.outgoingBubbleColor
        defaultColors["incomingBubbleColor"] = theme.incomingBubbleColor
        defaultColors["outgoingTextColor"] = theme.outgoingTextColor
        defaultColors["incomingTextColor"] = theme.incomingTextColor
    }

    override fun onColorChanged(fieldName: String, color: String) {
        currentColors[fieldName] = color
        checkChanges()
        paletteFragment.refresh()
        previewTheme()
    }

    override fun getCurrentColors(): Map<String, String> = currentColors
    override fun getDefaultColors(): Map<String, String> = defaultColors

    override fun onChatListBackgroundChanged(uri: Uri?) {
        chatListBackgroundUri = uri
        checkChanges()
        previewTheme()
    }

    override fun onChatBackgroundChanged(uri: Uri?) {
        chatBackgroundUri = uri
        checkChanges()
        previewTheme()
    }

    private fun previewTheme() {
        val theme = lavender.client.android.theme.Theme(
            id = "preview",
            name = "Preview",
            primaryColor = currentColors["primaryColor"]!!,
            onPrimaryColor = currentColors["onPrimaryColor"]!!,
            surfaceColor = currentColors["surfaceColor"]!!,
            onSurfaceColor = currentColors["onSurfaceColor"]!!,
            backgroundColor = currentColors["backgroundColor"]!!,
            textPrimaryColor = currentColors["textPrimaryColor"]!!,
            textSecondaryColor = currentColors["textSecondaryColor"]!!,
            surfaceContainer = currentColors["surfaceContainer"]!!,
            bottomPanelColor = currentColors["bottomPanelColor"]!!,
            onBottomPanelColor = currentColors["onBottomPanelColor"]!!,
            outgoingBubbleColor = currentColors["outgoingBubbleColor"]!!,
            incomingBubbleColor = currentColors["incomingBubbleColor"]!!,
            outgoingTextColor = currentColors["outgoingTextColor"]!!,
            incomingTextColor = currentColors["incomingTextColor"]!!,
            chatListBackgroundImageUrl = chatListBackgroundUri?.toString() ?: "",
            chatBackgroundImageUrl = chatBackgroundUri?.toString() ?: ""
        )
        lavender.client.android.theme.ui.ThemeApplier.apply(this, theme)
    }

    override fun getChatListBackgroundUri(): Uri? = chatListBackgroundUri
    override fun getChatBackgroundUri(): Uri? = chatBackgroundUri

    private fun checkChanges() {
        val colorChanges = currentColors.any { (key, value) -> value != defaultColors[key] }
        val backgroundChanges = chatListBackgroundUri?.toString() != originalChatListBackgroundUri?.toString() || 
                                chatBackgroundUri?.toString() != originalChatBackgroundUri?.toString()
        hasChanges = colorChanges || backgroundChanges
        saveButton.isVisible = hasChanges
    }

    private fun checkUnsavedChangesAndFinish() {
        if (hasChanges) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.unsaved_changes))
                .setMessage(getString(R.string.discard_changes))
                .setPositiveButton(R.string.yes) { _, _ -> finish() }
                .setNegativeButton(R.string.no, null)
                .show()
        } else {
            finish()
        }
    }

    private fun showSaveThemeDialog() {
        if (themeId.startsWith("custom_") && !themeId.startsWith("custom_new_")) {
            saveCustomThemeWithUploads(originalTheme.name)
            return
        }

        val defaultName = "$username's ${originalTheme.name}"
        val input = EditText(this).apply {
            setText(defaultName)
            setSelection(defaultName.length)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.enter_theme_name))
            .setView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 20, 50, 0)
                addView(input)
            })
            .setPositiveButton(R.string.yes) { _, _ ->
                val themeName = input.text.toString().trim()
                if (themeName.isNotEmpty()) {
                    saveCustomThemeWithUploads(themeName)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveCustomThemeWithUploads(themeName: String) {
        saveButton.isVisible = false
        uploadProgress.isVisible = true

        lifecycleScope.launch {
            try {
                val listBgUrl = uploadIfLocal(chatListBackgroundUri)
                val chatBgUrl = uploadIfLocal(chatBackgroundUri)
                
                withContext(Dispatchers.Main) {
                    saveCustomTheme(themeName, listBgUrl, chatBgUrl)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uploadProgress.isVisible = false
                    saveButton.isVisible = true
                    Toast.makeText(this@ThemePaletteActivity, "Error uploading backgrounds: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun uploadIfLocal(uri: Uri?): String {
        if (uri == null) return ""
        val uriString = uri.toString()
        if (uriString.startsWith("http")) return uriString
        
        return withContext(Dispatchers.IO) {
            val stream = contentResolver.openInputStream(uri)
            val bytes = stream?.readBytes()
            stream?.close()
            
            if (bytes == null) return@withContext ""
            
            val fileName = getFileName(uri) ?: "background.jpg"
            val body = MultipartBody.Part.createFormData("image", fileName, bytes.toRequestBody("image/jpeg".toMediaTypeOrNull()))
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(body)
                .build()
                
            val request = Request.Builder()
                .url("${lavender.client.android.data.session.CredentialStore.getHttpServerUrl(this@ThemePaletteActivity)}/upload-image")
                .post(requestBody)
                .build()
                
            val response = OkHttpClient().newCall(request).execute()
            val responseBody = response.body.string()
            
            if (response.isSuccessful && !responseBody.contains("404")) {
                if (responseBody.contains("\"url\":")) {
                    try { org.json.JSONObject(responseBody).getString("url") } catch (_: Exception) { "" }
                } else if (responseBody.startsWith("http")) responseBody else ""
            } else {
                throw Exception("Upload failed: ${response.code}")
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    private fun saveCustomTheme(themeName: String, listBgUrl: String, chatBgUrl: String) {
        val finalId = if (themeId.startsWith("custom_") && !themeId.startsWith("custom_new_")) {
            themeId
        } else {
            "custom_${System.currentTimeMillis()}"
        }

        val newTheme = CustomThemeProto(
            id = finalId,
            name = themeName,
            primaryColor = currentColors["primaryColor"]!!,
            backgroundColor = currentColors["backgroundColor"]!!,
            surfaceColor = currentColors["surfaceColor"]!!,
            surfaceContainer = currentColors["surfaceContainer"]!!,
            textPrimaryColor = currentColors["textPrimaryColor"]!!,
            textSecondaryColor = currentColors["textSecondaryColor"]!!,
            onPrimaryColor = currentColors["onPrimaryColor"]!!,
            onSurfaceColor = currentColors["onSurfaceColor"]!!,
            bottomPanelColor = currentColors["bottomPanelColor"]!!,
            onBottomPanelColor = currentColors["onBottomPanelColor"]!!,
            outgoingBubbleColor = currentColors["outgoingBubbleColor"]!!,
            incomingBubbleColor = currentColors["incomingBubbleColor"]!!,
            outgoingTextColor = currentColors["outgoingTextColor"]!!,
            incomingTextColor = currentColors["incomingTextColor"]!!,
            chatListBackgroundImageUrl = listBgUrl,
            chatBackgroundImageUrl = chatBgUrl
        )

        val queryId = GrpcClient.getUserId() ?: username
        GrpcClient.saveTheme(queryId, newTheme) { success, error ->
            runOnUiThread {
                if (success) {
                    // Switch to the new theme on the server
                    GrpcClient.setCurrentTheme(queryId, finalId) { setSuccess ->
                        runOnUiThread {
                            uploadProgress.isVisible = false
                            if (setSuccess) {
                                val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                                prefs.edit { putString("current_theme_id", finalId) }
                                
                                Toast.makeText(this, R.string.theme_saved, Toast.LENGTH_SHORT).show()
                                hasChanges = false
                                themeId = finalId
                                originalChatListBackgroundUri = listBgUrl.takeIf { it.isNotEmpty() }?.toUri()
                                originalChatBackgroundUri = chatBgUrl.takeIf { it.isNotEmpty() }?.toUri()
                                chatListBackgroundUri = originalChatListBackgroundUri
                                chatBackgroundUri = originalChatBackgroundUri
                                
                                // Refresh current theme in app with force bypass
                                ThemeStore.refresh(this@ThemePaletteActivity, username, force = true)
                                setResult(RESULT_OK)
                            } else {
                                saveButton.isVisible = true
                                Toast.makeText(this, getString(R.string.theme_saved_failed_to_set), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    uploadProgress.isVisible = false
                    saveButton.isVisible = true
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
