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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.ui.adapter.ThemeAdapter
import androidx.core.view.updatePadding
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import java.util.*

class ThemesActivity : AppCompatActivity() {

    private lateinit var themesRecyclerView: RecyclerView
    private lateinit var adapter: ThemeAdapter
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
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
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

        // Handle window insets for edge-to-edge
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }

        themesRecyclerView = findViewById(R.id.themesRecyclerView)

        adapter = ThemeAdapter(
            onThemeClick = { theme -> 
                currentThemeId = theme.id
                adapter.setCurrentThemeId(currentThemeId)
                invalidateOptionsMenu()
            },
            onEditClick = { theme -> openEditTheme(theme.id) },
            onAddClick = { openEditTheme(null) },
            onSelectionChanged = { count -> invalidateOptionsMenu() },
            currentThemeId = currentThemeId
        )
        themesRecyclerView.layoutManager = LinearLayoutManager(this)
        themesRecyclerView.adapter = adapter

        loadThemes()
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
        val allThemes = mutableListOf<CustomThemeProto>()
        allThemes.add(CustomThemeProto(id = "light", name = getString(R.string.light_theme)))
        allThemes.add(CustomThemeProto(id = "dark", name = getString(R.string.dark_theme)))
        allThemes.addAll(customThemes)
        
        adapter.setCurrentThemeId(currentThemeId)
        adapter.setThemes(allThemes)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        val selected = adapter.getSelectedThemes()
        if (selected.isNotEmpty()) {
            val item = menu.add(0, 100, 0, R.string.delete)
            item.setIcon(R.drawable.ic_delete)
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            item.iconTintList = ColorStateList.valueOf(getOnPrimaryColor())
        } else {
            // Edit button for custom themes
            if (currentThemeId != "light" && currentThemeId != "dark") {
                val editItem = menu.add(0, 300, 0, R.string.edit_theme_button)
                editItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
            }
            
            val applyItem = menu.add(0, 200, 0, R.string.apply)
            applyItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
            applyItem.iconTintList = ColorStateList.valueOf(typedValue.data)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            100 -> {
                val selected = adapter.getSelectedThemes()
                if (selected.isNotEmpty()) {
                    confirmDeleteThemes(selected)
                }
                return true
            }
            200 -> {
                selectTheme(currentThemeId)
                return true
            }
            300 -> {
                openEditTheme(currentThemeId)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun getOnPrimaryColor(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
        return typedValue.data
    }

    private fun confirmDeleteThemes(selected: List<CustomThemeProto>) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_theme_confirm))
            .setPositiveButton(R.string.delete) { _, _ ->
                val themesToDelete = selected.toList()
                adapter.clearSelection()
                
                var deletedCount = 0
                for (theme in themesToDelete) {
                    if (currentThemeId == theme.id) {
                        selectTheme("light")
                    }
                    grpcClient.deleteTheme(username, theme.id) { success ->
                        if (success) {
                            deletedCount++
                            if (deletedCount == themesToDelete.size) {
                                runOnUiThread { loadThemes() }
                            }
                        }
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

    private fun selectTheme(themeId: String) {
        currentThemeId = themeId
        grpcClient.setCurrentTheme(username, themeId) { success ->
            runOnUiThread {
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
                    applyAndRestart()
                } else {
                    Toast.makeText(this@ThemesActivity, "Failed to apply theme", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyAndRestart() {
        lavender.client.android.ui.ThemeManager.clearAllCaches(this)
        
        // Restart the app from SplashActivity to ensure all activities are reloaded with the new theme
        val intent = Intent(this, SplashActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private fun applySavedColorScheme() {
        val theme = when (getSharedPreferences("ChatPrefs", MODE_PRIVATE).getString("color_scheme", "dark")) {
            "light" -> R.style.Theme_Lavender_Light_NoActionBar
            else -> R.style.Theme_Lavender_Dark_NoActionBar
        }
        setTheme(theme)
    }
}
