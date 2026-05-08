package lavender.client.android

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import lavender.client.android.theme.BuiltInThemes
import lavender.client.android.theme.data.ThemeMappers
import lavender.client.android.theme.ui.ThemeUi
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

    private var chatListBackgroundUri: Uri? = null
    private var chatBackgroundUri: Uri? = null

    private lateinit var viewPager: ViewPager2
    private lateinit var paletteFragment: PaletteFragment
    private lateinit var backgroundsFragment: BackgroundsFragment

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
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
            // Make toolbar physically taller so the next view (tabs) starts below it.
            val baseHeight = android.util.TypedValue().let { tv ->
                theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)
                android.util.TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
            }
            toolbar.layoutParams = toolbar.layoutParams.apply {
                height = baseHeight + systemBars.top
            }
            toolbar.setPadding(0, systemBars.top, 0, 0)
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        // Ensure selected theme is applied when opening this screen.
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val currentThemeId = prefs.getString("current_theme_id", "dark") ?: "dark"
        if (currentThemeId != themeId) {
            prefs.edit { putString("current_theme_id", themeId) }
        }
        ThemeUi.bind(this, username)

        // Check if this is a custom theme with full data passed via extras
        val isCustomThemeWithExtras = intent.hasExtra("primary_color")

        if (isCustomThemeWithExtras) {
            // Custom theme - use passed colors as defaults
            val customTheme = CustomThemeProto(
                id = themeId,
                name = intent.getStringExtra("theme_name") ?: themeId,
                primaryColor = intent.getStringExtra("primary_color") ?: "#967BB6",
                backgroundColor = intent.getStringExtra("background_color") ?: "#04052E",
                surfaceColor = intent.getStringExtra("surface_color") ?: "#1A1B46",
                surfaceContainer = intent.getStringExtra("surface_container") ?: "#1A1B46",
                textPrimaryColor = intent.getStringExtra("text_primary_color") ?: "#FFFFFF",
                onPrimaryColor = intent.getStringExtra("on_primary_color") ?: "#FFFFFF",
                onSurfaceColor = intent.getStringExtra("on_surface_color") ?: "#E0E0E0",
                bottomPanelColor = intent.getStringExtra("bottom_panel_color") ?: "#1A1B46",
                onBottomPanelColor = intent.getStringExtra("on_bottom_panel_color") ?: "#967BB6",
                outgoingBubbleColor = intent.getStringExtra("outgoing_bubble_color") ?: "#2A2C6D",
                incomingBubbleColor = intent.getStringExtra("incoming_bubble_color") ?: "#16173A",
                chatListBackgroundImageUrl = intent.getStringExtra("chat_list_background") ?: "",
                chatBackgroundImageUrl = intent.getStringExtra("chat_background") ?: ""
            )
            originalTheme = customTheme
        } else {
            // Built-in theme - get by ID
            originalTheme = getThemeById(themeId) ?: return finish()
        }

        supportActionBar?.title = originalTheme.name
        // Localize built-in theme names (same mapping as ThemesActivity).
        supportActionBar?.title = when (originalTheme.id) {
            "builtin_green" -> getString(R.string.theme_template_green)
            "builtin_blue" -> getString(R.string.theme_template_blue)
            "builtin_purple" -> getString(R.string.theme_template_purple)
            "builtin_sunset" -> getString(R.string.theme_template_sunset)
            "builtin_graphite" -> getString(R.string.theme_template_graphite)
            "builtin_rose" -> getString(R.string.theme_template_rose)
            "builtin_mint" -> getString(R.string.theme_template_mint)
            else -> originalTheme.name
        }

        currentColors = mutableMapOf()
        defaultColors = mutableMapOf()

        initDefaultColors(originalTheme)
        currentColors.putAll(defaultColors)

        chatListBackgroundUri = intent.getStringExtra("chat_list_background")?.toUri()
        chatBackgroundUri = intent.getStringExtra("chat_background")?.toUri()

        saveButton = findViewById(R.id.saveButton)
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
        defaultColors["onPrimaryColor"] = theme.onPrimaryColor
        defaultColors["onSurfaceColor"] = theme.onSurfaceColor
        defaultColors["bottomPanelColor"] = theme.bottomPanelColor
        defaultColors["onBottomPanelColor"] = theme.onBottomPanelColor
        defaultColors["outgoingBubbleColor"] = theme.outgoingBubbleColor
        defaultColors["incomingBubbleColor"] = theme.incomingBubbleColor
    }

    override fun onColorChanged(fieldName: String, color: String) {
        currentColors[fieldName] = color
        checkChanges()
        paletteFragment.refresh()
    }

    override fun getCurrentColors(): Map<String, String> = currentColors
    override fun getDefaultColors(): Map<String, String> = defaultColors

    override fun onChatListBackgroundChanged(uri: Uri?) {
        chatListBackgroundUri = uri
        checkChanges()
    }

    override fun onChatBackgroundChanged(uri: Uri?) {
        chatBackgroundUri = uri
        checkChanges()
    }

    override fun getChatListBackgroundUri(): Uri? = chatListBackgroundUri
    override fun getChatBackgroundUri(): Uri? = chatBackgroundUri

    private fun checkChanges() {
        val colorChanges = currentColors.any { (key, value) -> value != defaultColors[key] }
        val backgroundChanges = chatListBackgroundUri != null || chatBackgroundUri != null
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
                    saveCustomTheme(themeName)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveCustomTheme(themeName: String) {
        val newTheme = CustomThemeProto(
            id = "custom_${System.currentTimeMillis()}",
            name = themeName,
            primaryColor = currentColors["primaryColor"]!!,
            backgroundColor = currentColors["backgroundColor"]!!,
            surfaceColor = currentColors["surfaceColor"]!!,
            surfaceContainer = currentColors["surfaceContainer"]!!,
            textPrimaryColor = currentColors["textPrimaryColor"]!!,
            onPrimaryColor = currentColors["onPrimaryColor"]!!,
            onSurfaceColor = currentColors["onSurfaceColor"]!!,
            bottomPanelColor = currentColors["bottomPanelColor"]!!,
            onBottomPanelColor = currentColors["onBottomPanelColor"]!!,
            outgoingBubbleColor = currentColors["outgoingBubbleColor"]!!,
            incomingBubbleColor = currentColors["incomingBubbleColor"]!!,
            chatListBackgroundImageUrl = chatListBackgroundUri?.toString() ?: "",
            chatBackgroundImageUrl = chatBackgroundUri?.toString() ?: ""
        )

        val queryId = GrpcClient.getUserId() ?: username
        GrpcClient.saveTheme(queryId, newTheme) { success, error ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, R.string.theme_saved, Toast.LENGTH_SHORT).show()
                    hasChanges = false
                    saveButton.isVisible = false
                    setResult(RESULT_OK)
                } else {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
