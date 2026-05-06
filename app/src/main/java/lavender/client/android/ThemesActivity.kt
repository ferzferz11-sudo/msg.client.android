package lavender.client.android

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import lavender.client.android.ui.ThemeManager
import lavender.client.android.ui.adapter.ThemeAdapter
import java.util.Locale

class ThemesActivity : AppCompatActivity() {

    private lateinit var themesRecyclerView: RecyclerView
    private lateinit var adapter: ThemeAdapter
    private val grpcClient = GrpcClient
    private var username: String = ""
    private var activeThemeId: String = "dark"
    private var currentThemeId: String = "dark"
    private var customThemes = mutableListOf<CustomThemeProto>()

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Настройка Edge-to-Edge ДО создания вьюх
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_themes)

        // 2. Получаем данные из интента
        username = intent.getStringExtra("username") ?: ""

        // 3. Инициализируем текущий ID из ПРАВИЛЬНЫХ префсов (lavender_prefs)
        // Делаем это сразу, чтобы адаптер не "моргал"
        val themePrefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        currentThemeId = themePrefs.getString("current_theme_id", "dark") ?: "dark"
        activeThemeId = currentThemeId

        // 4. Настройка Тулбара
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // Отступы для Edge-to-Edge (чтобы тулбар не залезал под статус-бар)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }

        // 5. Настройка RecyclerView и Адаптера
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

        // 6. МАГИЯ ТЕМИЗАЦИИ: Загружаем и применяем цвета
        ThemeManager.loadTheme(this, username) {
            runOnUiThread {
                ThemeManager.applyTheme(this)
                val loadedId = ThemeManager.getCurrentTheme()?.id ?: "dark"
                adapter.setCurrentThemeId(loadedId)

                // После применения темы обновляем цвета текста и иконок в меню тулбара
                invalidateOptionsMenu()
            }
        }

        // 7. Загружаем список всех тем (Built-in + Custom)
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
        val queryId = grpcClient.getUserId() ?: username
        grpcClient.getThemes(queryId) { currentId, list ->
            customThemes = list.toMutableList()
            
            val localThemeId = getSharedPreferences("lavender_prefs", MODE_PRIVATE).getString("current_theme_id", null)
            if (localThemeId == null) {
                activeThemeId = currentId
                currentThemeId = currentId
            } else {
                activeThemeId = localThemeId
                currentThemeId = localThemeId
            }

            runOnUiThread {
                updateUI()
                invalidateOptionsMenu()
            }
        }
    }

    private fun updateUI() {
        val allThemes = mutableListOf<CustomThemeProto>()
        allThemes.add(CustomThemeProto(id = "dark", name = getString(R.string.dark_theme),))

        // Add built-in template themes with localized names
        ThemeManager.builtInThemes.forEach { theme ->
            val localizedName = when (theme.id) {
                "builtin_green"    -> getString(R.string.theme_template_green)
                "builtin_blue"     -> getString(R.string.theme_template_blue)
                "builtin_purple"   -> getString(R.string.theme_template_purple)
                "builtin_sunset"   -> getString(R.string.theme_template_sunset)
                "builtin_graphite" -> getString(R.string.theme_template_graphite)
                "builtin_rose"     -> getString(R.string.theme_template_rose)
                "builtin_mint"     -> getString(R.string.theme_template_mint)
                else -> theme.name
            }
            allThemes.add(theme.copy(name = localizedName))
        }

        allThemes.addAll(customThemes)
        
        adapter.setCurrentThemeId(currentThemeId)
        adapter.setThemes(allThemes)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        val selected = adapter.getSelectedThemes()
        val textColor = getOnPrimaryColor()
        
        if (selected.isNotEmpty()) {
            val item = menu.add(0, 100, 0, R.string.delete)
            item.setIcon(R.drawable.ic_delete)
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            item.iconTintList = ColorStateList.valueOf(textColor)
        } else {
            // Edit button for custom themes (not built-in ones)
            val isBuiltIn = currentThemeId == "dark" || currentThemeId.startsWith("builtin_")
            if (!isBuiltIn) {
                val editItem = menu.add(0, 300, 0, R.string.edit_theme_button)
                editItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
                val spanString = android.text.SpannableString(editItem.title.toString())
                spanString.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, spanString.length, 0)
                editItem.title = spanString
            }
            
            if (currentThemeId != activeThemeId) {
                val applyItem = menu.add(0, 200, 0, R.string.apply)
                applyItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
                val spanString = android.text.SpannableString(applyItem.title.toString())
                spanString.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, spanString.length, 0)
                applyItem.title = spanString
            }
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
        val customTheme = ThemeManager.getCurrentTheme()
        return if (customTheme != null) {
            try {
                customTheme.textPrimaryColor.toColorInt()
            } catch (_: Exception) {
                val typedValue = android.util.TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
                typedValue.data
            }
        } else {
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
            typedValue.data
        }
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
                        selectTheme("dark")
                    }
                    val queryId = grpcClient.getUserId() ?: username
                    grpcClient.deleteTheme(queryId, theme.id) { success ->
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
        val queryId = grpcClient.getUserId() ?: username
        grpcClient.setCurrentTheme(queryId, themeId) { success ->
            runOnUiThread {
                if (success) {
                    val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                    // Записываем ID темы ОБЯЗАТЕЛЬНО
                    prefs.edit {
                        putString("current_theme_id", themeId)
                        commit() // Используем commit для синхронной записи
                    }

                    // Сначала загружаем тему в память менеджера, чтобы SplashActivity её подхватила
                    ThemeManager.loadTheme(this, username) {
                        runOnUiThread { applyAndRestart() }
                    }
                } else {
                    Toast.makeText(this@ThemesActivity, "Failed to apply theme", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyAndRestart() {
        // ThemeManager.clearAllCaches(this)
        
        // Restart the app from SplashActivity to ensure all activities are reloaded with the new theme
        val intent = Intent(this, SplashActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    fun onSetBackgroundClicked(themeId: String, newUrl: String) {
        ThemeManager.saveBackgroundOverride(this, themeId, newUrl)
        ThemeManager.applyTheme(this) // Сразу перекрашиваем текущий экран
    }
}
