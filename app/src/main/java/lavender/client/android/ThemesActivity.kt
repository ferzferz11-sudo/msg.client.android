package lavender.client.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.updateLayoutParams
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
        val languageCode = prefs.getString("language", "ru") ?: "ru"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_themes)

        username = intent.getStringExtra("username") ?: ""

        val themePrefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        currentThemeId = themePrefs.getString("current_theme_id", "dark") ?: "dark"
        activeThemeId = currentThemeId

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = ""
            setDisplayHomeAsUpEnabled(true)
        }
        
        val actionApply = findViewById<android.widget.ImageView>(R.id.actionApply)
        val actionEdit = findViewById<android.widget.ImageView>(R.id.actionEdit)
        val actionDelete = findViewById<android.widget.ImageView>(R.id.actionDelete)
        val toolbarTitle = findViewById<android.widget.TextView>(R.id.toolbarTitle)

        toolbar.setNavigationOnClickListener {
            if (adapter.getSelectedThemes().isNotEmpty()) {
                adapter.clearSelection()
            } else {
                finish()
            }
        }

        themesRecyclerView = findViewById(R.id.themesRecyclerView)
        adapter = ThemeAdapter(
            onThemeClick = { theme ->
                // No immediate switch
            },
            onSelectionChanged = { count ->
                val hasSelection = count > 0
                val selectedThemes = adapter.getSelectedThemes()
                
                val canDelete = selectedThemes.isNotEmpty() && selectedThemes.all { !it.id.startsWith("builtin_") && it.id != "dark" }
                val canEdit = count == 1 && selectedThemes.first().let { !it.id.startsWith("builtin_") && it.id != "dark" }
                val canApply = count == 1
                
                actionApply.visibility = if (canApply) android.view.View.VISIBLE else android.view.View.GONE
                actionEdit.visibility = if (canEdit) android.view.View.VISIBLE else android.view.View.GONE
                actionDelete.visibility = if (canDelete) android.view.View.VISIBLE else android.view.View.GONE
                
                if (hasSelection) {
                    toolbarTitle.text = getString(R.string.selected_count, count)
                    supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close)
                } else {
                    toolbarTitle.text = getString(R.string.themes)
                    supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back_arrow)
                }
            },
            currentThemeId = currentThemeId
        )
        themesRecyclerView.layoutManager = LinearLayoutManager(this)
        themesRecyclerView.adapter = adapter

        val addThemeFab = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.addThemeFab)
        addThemeFab.setOnClickListener { openEditTheme(null) }
        
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            toolbar.updatePadding(top = systemBars.top)
            themesRecyclerView.updatePadding(bottom = systemBars.bottom)
            addThemeFab.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                bottomMargin = (28 * resources.displayMetrics.density).toInt() + systemBars.bottom
                marginEnd = (16 * resources.displayMetrics.density).toInt()
            }
            insets
        }

        actionApply.setOnClickListener {
            val selected = adapter.getSelectedThemes()
            if (selected.size == 1) {
                applyThemeImmediate(selected.first().id)
            }
        }
        
        actionEdit.setOnClickListener {
            val selected = adapter.getSelectedThemes()
            if (selected.size == 1) {
                openEditTheme(selected.first().id)
            }
        }
        
        actionDelete.setOnClickListener {
            val selected = adapter.getSelectedThemes()
            if (selected.isNotEmpty()) {
                confirmDeleteThemes(selected)
            }
        }

        ThemeManager.loadTheme(this, username) {
            runOnUiThread {
                ThemeManager.applyTheme(this)
                val theme = ThemeManager.getCurrentTheme()
                val loadedId = theme?.id ?: "dark"
                adapter.setCurrentThemeId(loadedId)
                updateToolbarAvatar()
            }
        }

        loadThemes()
    }

    private fun updateToolbarAvatar() {
        val avatarView = findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.toolbarUserAvatar) ?: return
        val avatarCache = grpcClient.getAvatarCache()
        val myAvatarUrl = avatarCache[username]
        avatarView.visibility = android.view.View.VISIBLE
        if (!myAvatarUrl.isNullOrEmpty()) {
            com.bumptech.glide.Glide.with(this).load(myAvatarUrl).placeholder(R.drawable.ic_default_avatar).circleCrop().into(avatarView)
        } else {
            avatarView.setImageResource(R.drawable.ic_default_avatar_white)
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
            }
        }
    }

    private fun updateUI() {
        val allThemes = mutableListOf<CustomThemeProto>()
        allThemes.add(CustomThemeProto(id = "dark", name = getString(R.string.dark_theme),))

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

    private fun applyThemeImmediate(themeId: String) {
        currentThemeId = themeId
        val queryId = grpcClient.getUserId() ?: username
        grpcClient.setCurrentTheme(queryId, themeId) { success ->
            runOnUiThread {
                if (success) {
                    val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                    prefs.edit {
                        putString("current_theme_id", themeId)
                        commit()
                    }

                    ThemeManager.loadTheme(this, username) {
                        runOnUiThread {
                            ThemeManager.applyTheme(this)
                            adapter.setCurrentThemeId(themeId)
                            adapter.clearSelection()
                            updateToolbarAvatar()
                            Toast.makeText(this, "Theme applied", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this@ThemesActivity, "Failed to apply theme", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmDeleteThemes(themes: List<CustomThemeProto>) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_theme_confirm))
            .setPositiveButton(R.string.delete) { _, _ ->
                val themeIds = themes.map { it.id }
                val queryId = grpcClient.getUserId() ?: username
                
                var deletedCount = 0
                themeIds.forEach { id ->
                    grpcClient.deleteTheme(queryId, id) { success ->
                        if (success) {
                            deletedCount++
                            if (deletedCount == themeIds.size) {
                                runOnUiThread {
                                    adapter.clearSelection()
                                    loadThemes()
                                }
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
}
