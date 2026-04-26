package lavender.client.android

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
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
            val intent = Intent(this, EditThemeActivity::class.java).apply {
                putExtra("username", username)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadThemes()
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
                    val intent = Intent(this@ThemesActivity, EditThemeActivity::class.java).apply {
                        putExtra("username", username)
                        putExtra("theme_id", theme.id)
                    }
                    startActivity(intent)
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

    private fun getSavedColorScheme(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("color_scheme", null)
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
