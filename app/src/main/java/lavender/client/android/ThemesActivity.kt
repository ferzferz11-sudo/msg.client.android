package lavender.client.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import lavender.client.android.data.grpc.RealGrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.adapter.ThemeAdapter
import lavender.client.android.ui.themes.ThemesViewModel
import kotlinx.coroutines.launch
import java.util.Locale

class ThemesActivity : AppCompatActivity() {

    private lateinit var viewModel: ThemesViewModel
    private lateinit var adapter: ThemeAdapter
    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar
    private lateinit var toolbarTitle: android.widget.TextView
    private lateinit var actionApply: android.widget.ImageView
    private lateinit var actionEdit: android.widget.ImageView
    private lateinit var actionDelete: android.widget.ImageView
    private lateinit var themesRecyclerView: androidx.recyclerview.widget.RecyclerView

    private var username: String = ""

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
        setContentView(R.layout.activity_themes)

        viewModel = ViewModelProvider(this)[ThemesViewModel::class.java]
        username = intent.getStringExtra("username") ?: ""

        initViews()
        setupToolbar()
        setupRecyclerView()
        setupFab()
        setupWindowInsets()
        setupObservers()
        ThemeUi.bind(this, username)

        viewModel.loadThemes(username)
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        actionApply = findViewById(R.id.actionApply)
        actionEdit = findViewById(R.id.actionEdit)
        actionDelete = findViewById(R.id.actionDelete)
        themesRecyclerView = findViewById(R.id.themesRecyclerView)

        val switchSystemDarkMode = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchSystemDarkMode)
        switchSystemDarkMode.isChecked = ThemeStore.isFollowSystemDarkMode()
        switchSystemDarkMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setFollowSystemDarkMode(this, isChecked)
            adapter.setCurrentThemeId(ThemeStore.currentTheme().id)
        }
    }

    private fun setupToolbar() {
        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.navigationIcon?.setTint(getColorOnPrimary())
        toolbar.setNavigationOnClickListener {
            if (adapter.getSelectedThemes().isNotEmpty()) {
                adapter.clearSelection()
            } else {
                finish()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ThemeAdapter(
            onThemeClick = { },
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
                    setBackIcon(true)
                } else {
                    toolbarTitle.text = getString(R.string.themes)
                    setBackIcon(false)
                }
            },
            currentThemeId = ThemeStore.currentTheme().id
        )
        themesRecyclerView.layoutManager = LinearLayoutManager(this)
        themesRecyclerView.adapter = adapter

        actionApply.setOnClickListener {
            val selected = adapter.getSelectedThemes()
            if (selected.size == 1) {
                viewModel.applyTheme(username, selected.first().id)
            }
        }

        actionEdit.setOnClickListener {
            val selected = adapter.getSelectedThemes()
            if (selected.size == 1) {
                openThemePaletteWithTheme(selected.first())
            }
        }

        actionDelete.setOnClickListener {
            val selected = adapter.getSelectedThemes()
            if (selected.isNotEmpty()) confirmDeleteThemes(selected)
        }
    }

    private fun setupFab() {
        val addThemeFab = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.addThemeFab)
        addThemeFab.setOnClickListener { openThemePaletteForNewTheme() }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            themesRecyclerView.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.setCurrentThemeId(state.currentThemeId)
                    adapter.setThemes(state.themes)

                    if (state.themeApplied) {
                        viewModel.consumeThemeApplied()
                        ThemeUi.bind(this@ThemesActivity, username)
                        adapter.clearSelection()
                        updateToolbarAvatar()
                        Toast.makeText(this@ThemesActivity, getString(R.string.theme_applied), Toast.LENGTH_SHORT).show()
                    }

                    if (state.themesDeleted) {
                        viewModel.consumeThemesDeleted()
                        adapter.clearSelection()
                        viewModel.loadThemes(username)
                    }

                    state.error?.let { error ->
                        viewModel.consumeError()
                        Toast.makeText(this@ThemesActivity, error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        RealGrpcClient.isAppInBackground = false
        viewModel.loadThemes(username)
    }

    override fun onPause() {
        super.onPause()
        RealGrpcClient.isAppInBackground = true
    }

    private fun updateToolbarAvatar() {
        val avatarView = findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.toolbarUserAvatar) ?: return
        val avatarCache = viewModel.getAvatarCache()
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

    private fun getColorOnPrimary(): Int {
        val theme = ThemeStore.currentTheme()
        return ThemeUtils.parseSafeColor(theme.onPrimaryColor, android.graphics.Color.WHITE)
    }

    private fun setBackIcon(isClose: Boolean) {
        val iconRes = if (isClose) R.drawable.ic_close else R.drawable.ic_back_arrow
        toolbar.navigationIcon = androidx.core.content.ContextCompat.getDrawable(this, iconRes)?.apply {
            setTint(getColorOnPrimary())
        }
    }

    private fun confirmDeleteThemes(themes: List<CustomThemeProto>) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_theme_confirm))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteThemes(username, themes.map { it.id })
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openThemePaletteForNewTheme() {
        startActivity(Intent(this, ThemePaletteActivity::class.java).apply {
            putExtra("theme_id", "custom_new_${System.currentTimeMillis()}")
            putExtra("username", username)
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
        })
    }

    private fun openThemePaletteWithTheme(theme: CustomThemeProto) {
        startActivity(Intent(this, ThemePaletteActivity::class.java).apply {
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
            if (theme.chatListBackgroundImageUrl.isNotEmpty()) putExtra("chat_list_background", theme.chatListBackgroundImageUrl)
            if (theme.chatBackgroundImageUrl.isNotEmpty()) putExtra("chat_background", theme.chatBackgroundImageUrl)
        })
    }
}
