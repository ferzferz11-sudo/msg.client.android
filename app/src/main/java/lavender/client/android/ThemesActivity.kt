package lavender.client.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import lavender.client.android.theme.BuiltInThemes
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.data.ThemeMappers
import lavender.client.android.theme.ui.ThemeUi
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
        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.navigationIcon?.setTint(getColorOnPrimary())
        
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
            onThemeClick = { _ ->
                // No immediate switch
            },
            onSelectionChanged = { count ->
                val hasSelection = count > 0
                val selectedThemes = adapter.getSelectedThemes()
                
                val canDelete = selectedThemes.isNotEmpty() && selectedThemes.all { !it.id.startsWith("builtin_") && it.id != "dark" }
                val canApply = count == 1
                val canViewPalette = count == 1

                actionApply.visibility = if (canApply) android.view.View.VISIBLE else android.view.View.GONE
                actionEdit.visibility = if (canViewPalette) android.view.View.VISIBLE else android.view.View.GONE
                actionDelete.visibility = if (canDelete) android.view.View.VISIBLE else android.view.View.GONE
                
                if (hasSelection) {
                    toolbarTitle.text = getString(R.string.selected_count, count)
                    setBackIcon(toolbar, true)
                } else {
                    toolbarTitle.text = getString(R.string.themes)
                    setBackIcon(toolbar, false)
                }
            },
            currentThemeId = currentThemeId
        )
        themesRecyclerView.layoutManager = LinearLayoutManager(this)
        themesRecyclerView.adapter = adapter

        val addThemeFab = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.addThemeFab)
        addThemeFab.setOnClickListener { openThemePaletteForNewTheme() }
        
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            themesRecyclerView.updatePadding(bottom = systemBars.bottom)
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
                val theme = selected.first()
                openThemePaletteWithTheme(theme)
            }
        }
        
        actionDelete.setOnClickListener {
            val selected = adapter.getSelectedThemes()
            if (selected.isNotEmpty()) {
                confirmDeleteThemes(selected)
            }
        }

        ThemeUi.bind(this, username)
        adapter.setCurrentThemeId(ThemeStore.theme.value.id)
        updateToolbarAvatar()

        loadThemes()
    }

    private fun updateToolbarAvatar() {
        val avatarView = findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.toolbarUserAvatar) ?: return
        val avatarCache = grpcClient.getAvatarCache()
        val myAvatarUrl = avatarCache[username]
        val currentTheme = ThemeStore.currentTheme()
        
        avatarView.visibility = android.view.View.VISIBLE
        if (!myAvatarUrl.isNullOrEmpty()) {
            com.bumptech.glide.Glide.with(this).load(myAvatarUrl).placeholder(R.drawable.ic_default_avatar).circleCrop().into(avatarView)
            avatarView.clearColorFilter()
        } else {
            ThemeUtils.applyDefaultAvatar(avatarView, currentTheme)
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
            
            var remoteId = currentId
            // Handle migration from graphite to default dark
            if (remoteId == "builtin_dark_graphite") {
                remoteId = "dark"
            }

            val localThemeId = getSharedPreferences("lavender_prefs", MODE_PRIVATE).getString("current_theme_id", null)
            if (localThemeId == null || localThemeId == "builtin_dark_graphite") {
                activeThemeId = remoteId
                currentThemeId = remoteId
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

        BuiltInThemes.all.map { ThemeMappers.toProto(it) }.forEach { theme ->
            val localizedName = when (theme.id) {
                "dark"                  -> getString(R.string.dark_theme)
                "builtin_lavender_dark" -> getString(R.string.theme_lavender_night)
                "builtin_dark_graphite" -> getString(R.string.theme_dark_graphite)
                "builtin_green"         -> getString(R.string.theme_template_green)
                "builtin_blue"          -> getString(R.string.theme_template_blue)
                "builtin_graphite"      -> getString(R.string.theme_template_graphite)
                "builtin_mint"          -> getString(R.string.theme_template_mint)
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
        
        if (queryId.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_user_id_not_found), Toast.LENGTH_LONG).show()
            return
        }

        grpcClient.setCurrentTheme(queryId, themeId) { success ->
            runOnUiThread {
                if (success) {
                    val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                    prefs.edit {
                        putString("current_theme_id", themeId)
                        commit()
                    }

                    ThemeUi.bind(this, username)
                    adapter.setCurrentThemeId(themeId)
                    adapter.clearSelection()
                    updateToolbarAvatar()
                    Toast.makeText(this, getString(R.string.theme_applied), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ThemesActivity, getString(R.string.failed_to_apply_theme), Toast.LENGTH_SHORT).show()
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

    private fun getColorOnPrimary(): Int {
        val theme = lavender.client.android.theme.ThemeStore.currentTheme()
        return lavender.client.android.theme.ThemeUtils.parseSafeColor(theme.onPrimaryColor, android.graphics.Color.WHITE)
    }

    private fun setBackIcon(toolbar: com.google.android.material.appbar.MaterialToolbar, isClose: Boolean) {
        val iconRes = if (isClose) R.drawable.ic_close else R.drawable.ic_back_arrow
        toolbar.navigationIcon = androidx.core.content.ContextCompat.getDrawable(this, iconRes)?.apply {
            setTint(getColorOnPrimary())
        }
    }

    private fun openThemePaletteForNewTheme() {
        val intent = Intent(this, ThemePaletteActivity::class.java).apply {
            putExtra("theme_id", "custom_new_${System.currentTimeMillis()}")
            putExtra("username", username)
            // Use dark theme as base
            putExtra("primary_color", "#5F9EA0")
            putExtra("background_color", "#1E1E1E")
            putExtra("surface_color", "#2D2D2D")
            putExtra("surface_container", "#252525")
            putExtra("text_primary_color", "#FFFFFF")
            putExtra("on_primary_color", "#FFFFFF")
            putExtra("on_surface_color", "#E0E0E0")
            putExtra("bottom_panel_color", "#2D2D2D")
            putExtra("on_bottom_panel_color", "#5F9EA0")
            putExtra("outgoing_bubble_color", "#2A2C6D")
            putExtra("incoming_bubble_color", "#16173A")
        }
        startActivity(intent)
    }

    private fun openThemePaletteWithTheme(theme: CustomThemeProto) {
        val intent = Intent(this, ThemePaletteActivity::class.java).apply {
            putExtra("theme_id", theme.id)
            putExtra("username", username)
            putExtra("primary_color", theme.primaryColor)
            putExtra("background_color", theme.backgroundColor)
            putExtra("surface_color", theme.surfaceColor)
            putExtra("surface_container", theme.surfaceContainer)
            putExtra("text_primary_color", theme.textPrimaryColor)
            putExtra("on_primary_color", theme.onPrimaryColor)
            putExtra("on_surface_color", theme.onSurfaceColor)
            putExtra("bottom_panel_color", theme.bottomPanelColor)
            putExtra("on_bottom_panel_color", theme.onBottomPanelColor)
            putExtra("outgoing_bubble_color", theme.outgoingBubbleColor)
            putExtra("incoming_bubble_color", theme.incomingBubbleColor)
            if (theme.chatListBackgroundImageUrl.isNotEmpty()) {
                putExtra("chat_list_background", theme.chatListBackgroundImageUrl)
            }
            if (theme.chatBackgroundImageUrl.isNotEmpty()) {
                putExtra("chat_background", theme.chatBackgroundImageUrl)
            }
        }
        startActivity(intent)
    }
}
