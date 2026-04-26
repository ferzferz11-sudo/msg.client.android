package lavender.client.android

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.material.button.MaterialButton
import com.google.android.material.radiobutton.MaterialRadioButton
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import java.util.*

class ThemesActivity : AppCompatActivity() {

    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var customThemesContainer: LinearLayout
    private lateinit var btnAddTheme: MaterialButton
    private var colorSchemeMenuItem: MenuItem? = null
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

        lavender.client.android.ui.ThemeManager.loadTheme(this, username) {
            runOnUiThread {
                lavender.client.android.ui.ThemeManager.applyTheme(this)
            }
        }
        
        currentThemeId = getSharedPreferences("ChatPrefs", MODE_PRIVATE).getString("current_theme_id", "dark") ?: "dark"

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        themeRadioGroup = findViewById(R.id.themeRadioGroup)
        customThemesContainer = findViewById(R.id.customThemesContainer)
        btnAddTheme = findViewById(R.id.btnAddTheme)

        loadThemes()

        btnAddTheme.setOnClickListener {
            openEditTheme(null)
        }
    }

    override fun onResume() {
        super.onResume()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false
        loadThemes()
    }

    override fun onPause() {
        super.onPause()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = true
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.themes_menu, menu)
        colorSchemeMenuItem = menu.findItem(R.id.action_color_scheme)
        updateColorSchemeIcon()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_color_scheme -> {
                toggleColorScheme()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleColorScheme() {
        val schemes = listOf("light", "dark")
        val currentScheme = getSavedColorScheme() ?: "dark"
        val currentIndex = schemes.indexOf(currentScheme)
        val nextIndex = (currentIndex + 1) % schemes.size
        val newScheme = schemes[nextIndex]

        selectTheme(newScheme)
    }

    private fun updateColorSchemeIcon() {
        val currentScheme = getSavedColorScheme() ?: "dark"
        val iconRes = if (currentScheme == "dark") {
            R.drawable.ic_light_mode
        } else {
            R.drawable.ic_theme_dark
        }
        colorSchemeMenuItem?.setIcon(iconRes)
    }

    private fun loadThemes() {
        grpcClient.getThemes(username) { currentId, list ->
            customThemes = list.toMutableList()
            
            val localThemeId = getSharedPreferences("ChatPrefs", MODE_PRIVATE).getString("current_theme_id", null)
            if (localThemeId == null) {
                currentThemeId = currentId
            } else {
                currentThemeId = localThemeId
            }

            runOnUiThread {
                updateUI()
            }
        }
    }

    private fun updateUI() {
        themeRadioGroup.clearCheck()
        
        val radioLight = findViewById<MaterialRadioButton>(R.id.radioLight)
        val radioDark = findViewById<MaterialRadioButton>(R.id.radioDark)
        
        val textColor = getOnSurfaceColor()
        radioLight.setTextColor(textColor)
        radioDark.setTextColor(textColor)
        
        radioLight.isChecked = currentThemeId == "light"
        radioDark.isChecked = currentThemeId == "dark"

        radioLight.setOnClickListener { selectTheme("light") }
        radioDark.setOnClickListener { selectTheme("dark") }

        customThemesContainer.removeAllViews()
        for (theme in customThemes) {
            val itemLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }

            val rb = MaterialRadioButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    (64 * resources.displayMetrics.density).toInt(),
                    1f
                )
                text = theme.name
                setTextColor(textColor)
                isChecked = currentThemeId == theme.id
                setOnClickListener { selectTheme(theme.id) }
                
                setOnLongClickListener {
                    openEditTheme(theme.id)
                    true
                }
            }

            val btnEdit = ImageButton(this).apply {
                val size = (64 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size)
                setImageResource(R.drawable.ic_settings_brightness)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(12, 12, 12, 12)
                
                val typedValue = android.util.TypedValue()
                this@ThemesActivity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
                setBackgroundResource(typedValue.resourceId)
                
                // Get color from ThemeManager to match the ACTUAL toolbar background
                val customTheme = lavender.client.android.ui.ThemeManager.getCurrentTheme()
                val iconColor = if (customTheme != null) {
                    try { Color.parseColor(customTheme.primaryColor) } catch (_: Exception) { getPrimaryColor() }
                } else {
                    getPrimaryColor()
                }
                
                imageTintList = ColorStateList.valueOf(iconColor)
                setOnClickListener { openEditTheme(theme.id) }
            }

            val btnDelete = ImageButton(this).apply {
                val size = (44 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (8 * resources.displayMetrics.density).toInt()
                }
                setImageResource(R.drawable.ic_delete)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(10, 10, 10, 10)
                
                val typedValue = android.util.TypedValue()
                this@ThemesActivity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
                setBackgroundResource(typedValue.resourceId)
                
                // Get same color for Delete as for Edit
                val customTheme = lavender.client.android.ui.ThemeManager.getCurrentTheme()
                val iconColor = if (customTheme != null) {
                    try { Color.parseColor(customTheme.primaryColor) } catch (_: Exception) { getPrimaryColor() }
                } else {
                    getPrimaryColor()
                }

                imageTintList = ColorStateList.valueOf(iconColor)
                setOnClickListener { confirmQuickDeleteTheme(theme) }
            }

            itemLayout.addView(rb)
            itemLayout.addView(btnEdit)
            itemLayout.addView(btnDelete)
            customThemesContainer.addView(itemLayout)
        }
    }

    private fun confirmQuickDeleteTheme(theme: CustomThemeProto) {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_theme_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (currentThemeId == theme.id) {
                    // Switch to default light theme before deleting active theme
                    selectTheme("light")
                }
                grpcClient.deleteTheme(username, theme.id) { success ->
                    if (success) {
                        runOnUiThread { loadThemes() }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openEditTheme(themeId: String?) {
        val intent = Intent(this, EditThemeActivity::class.java).apply {
            putExtra("username", username)
            if (themeId != null) putExtra("theme_id", themeId)
        }
        startActivity(intent)
    }

    private fun getOnSurfaceColor(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        return typedValue.data
    }

    private fun getPrimaryColor(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
        return typedValue.data
    }

    private fun getSavedColorScheme(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("color_scheme", null)
    }

    private fun selectTheme(themeId: String) {
        currentThemeId = themeId
        if (themeId == "light" || themeId == "dark") {
            lavender.client.android.ui.ThemeManager.clearTheme()
        }
        grpcClient.setCurrentTheme(username, themeId) { success ->
            if (success) {
                val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
                val scheme = if (themeId == "light" || themeId == "dark") themeId else {
                    val theme = customThemes.find { it.id == themeId }
                    if (theme?.isDark == true) "dark" else "light"
                }
                prefs.edit {
                    putString("color_scheme", scheme)
                    putString("current_theme_id", themeId)
                }
                runOnUiThread { recreate() }
            }
        }
    }

    private fun applySavedColorScheme() {
        val theme = when (getSharedPreferences("ChatPrefs", MODE_PRIVATE).getString("color_scheme", "dark")) {
            "light" -> R.style.Theme_Lavender_Light_NoActionBar
            else -> R.style.Theme_Lavender_Dark_NoActionBar
        }
        setTheme(theme)
    }
}
