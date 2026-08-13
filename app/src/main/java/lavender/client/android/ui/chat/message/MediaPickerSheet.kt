package lavender.client.android.ui.chat.message
import android.util.Log

import android.app.Activity
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Sticker
import lavender.client.android.data.models.StickerPack
import lavender.client.android.data.sticker.StickerPreferencesManager
import lavender.client.android.theme.Theme
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.ui.sticker.StickerGridAdapter
import lavender.client.android.ui.sticker.StickerPackAdapter
import lavender.client.android.ui.widget.StandardBottomSheet

class MediaPickerSheet(
    private val activity: Activity,
    private val onEmojiSelected: (String) -> Unit,
    private val onStickerSelected: (Sticker) -> Unit,
    private val onCreateStickerPack: (() -> Unit)? = null,
    private val onEditStickerPack: ((String) -> Unit)? = null
) : StandardBottomSheet(activity, R.layout.sheet_media_picker) {

    init {
        try { StickerPreferencesManager.init(activity) } catch (e: Exception) { Log.w(TAG, "Caught: " + e.message) }
    }

    private val stickerGridAdapter = StickerGridAdapter(
        onStickerClick = { sticker ->
            onStickerSelected(sticker)
            try { StickerPreferencesManager.addRecent(sticker) } catch (e: Exception) { Log.w(TAG, "Caught: " + e.message) }
            dialog?.dismiss()
        },
        onStickerLongClick = { sticker ->
            try {
                val isFavorite = StickerPreferencesManager.toggleFavorite(sticker)
                android.widget.Toast.makeText(
                    activity,
                    if (isFavorite) R.string.sticker_added_to_favorites else R.string.sticker_removed_from_favorites,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) { Log.w(TAG, "Caught: " + e.message) }
        }
    )

    private val stickerPackAdapter = StickerPackAdapter(
        onPackClick = { pack -> loadPackStickers(pack) },
        onPackLongClick = { pack ->
            dismiss()
            onEditStickerPack?.invoke(pack.id)
        }
    )

    private var allPacks = listOf<StickerPack>()
    private var currentPacks = listOf<StickerPack>()
    private var emojiInitialized = false
    private var stickerTabInitialized = false
    private var searchJob: Job? = null
    private var isSearchActive = false

    override fun applyTheme(theme: Theme) {
        super.applyTheme(theme)
        try {
            val primaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
            val textPrimaryColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
            val surfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)

            findViewById<TabLayout>(R.id.tabLayout)?.apply {
                setBackgroundColor(surfaceColor)
                setTabTextColors(ThemeUtils.adjustAlpha(textPrimaryColor, 0.6f), textPrimaryColor)
                setSelectedTabIndicatorColor(primaryColor)
            }
        } catch (e: Exception) { Log.w(TAG, "Caught: " + e.message) }
    }

    fun showPicker() {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            setupStickerTab()
            show()
            backButton?.isVisible = false
            root?.post { setupEmojiTab() }
        } catch (e: Exception) {
            android.util.Log.e("MediaPickerSheet", "showPicker failed", e)
            try { android.widget.Toast.makeText(activity, android.R.string.cancel, android.widget.Toast.LENGTH_SHORT).show() } catch (e: Exception) { Log.w(TAG, "Caught: " + e.message) }
        }
    }

    private fun setupEmojiTab() {
        if (emojiInitialized) return
        emojiInitialized = true
        val emojiGrid = findViewById<GridLayout>(R.id.emojiGrid) ?: return
        val emojis = listOf(
            "😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃","😉","😌","😍","🥰","😘","😗","😙","😚",
            "😋","😛","😝","😜","🤪","🤨","🧐","🤓","😎","🤩","🥳","😏","😒","😞","😔","😟","😕","🙁","☹️","😣",
            "😖","😫","😩","🥺","😢","😭","😤","😠","😡","🤬","🤯","😳","🥵","🥶","😱","😨","😰","😥","😓","🤔",
            "🤭","🤫","🤥","😶","😐","😑","😬","🙄","😯","😦","😧","😮","😲","🥱","😴","🤤","😪","😵","🤐","🥴",
            "🤢","🤮","🤧","🥵","🥶","😷","🤒","🤕","🤑","🤠","😈","👿","👹","👺","🤡","💩","👻","💀","☠️","👽",
            "👾","🤖","🎃","😺","😸","😹","😻","😼","😽","🙀","😿","😾","👋","🤚","🖐","✋","🖖","👌","🤏","✌️",
            "🤞","🤟","🤘","🤙","👈","👉","👆","🖕","👇","☝️","👍","👎","✊","👊","🤛","🤜","👏","🙌","👐","🤲",
            "🤝","🙏","✍️","💅","🤳","💪","🦾","🦵","🦿","🦶"
        )
        val size = (48 * activity.resources.displayMetrics.density).toInt()
        for (emoji in emojis) {
            val tv = TextView(activity).apply {
                text = emoji
                textSize = 24f
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(size, size)
                val v = TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, v, true)
                setBackgroundResource(v.resourceId)
                setOnClickListener {
                    onEmojiSelected(emoji)
                    dismiss()
                }
            }
            emojiGrid.addView(tv)
        }
    }

    private fun setupStickerTab() {
        if (stickerTabInitialized) return
        stickerTabInitialized = true
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout) ?: return
        val emojiContainer = findViewById<android.view.View>(R.id.emojiContainer)
        val stickerContainer = findViewById<LinearLayout>(R.id.stickerContainer)
        val rvPacks = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvStickerPacks)
        val rvStickers = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvStickers)
        val emptyState = findViewById<LinearLayout>(R.id.stickerEmptyState)
        val btnCreate = findViewById<android.view.View>(R.id.btnCreatePack)

        rvPacks?.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
        rvPacks?.adapter = stickerPackAdapter

        rvStickers?.layoutManager = GridLayoutManager(activity, 4)
        rvStickers?.adapter = stickerGridAdapter

        btnCreate?.setOnClickListener {
            dismiss()
            onCreateStickerPack?.invoke()
        }

        tabLayout.addTab(tabLayout.newTab().setText("😀"))
        tabLayout.addTab(tabLayout.newTab().setText("\u2B50"))
        tabLayout.addTab(tabLayout.newTab().setText("\uD83C\uDFA8"))

        val etSearch = findViewById<EditText>(R.id.etStickerSearch)
        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                searchJob?.cancel()
                if (query.length >= 2) {
                    searchJob = (activity as? LifecycleOwner)?.lifecycleScope?.launch {
                        delay(300)
                        performSearch(query)
                    }
                } else if (query.isEmpty()) {
                    isSearchActive = false
                    rvPacks?.isVisible = true
                    stickerGridAdapter.submitList(currentPacks.flatMap { it.stickers })
                }
            }
        })

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        emojiContainer?.isVisible = true
                        stickerContainer?.isVisible = false
                    }
                    1 -> {
                        emojiContainer?.isVisible = false
                        stickerContainer?.isVisible = true
                        btnCreate?.isVisible = false
                        showFavoritesTab()
                    }
                    2 -> {
                        emojiContainer?.isVisible = false
                        stickerContainer?.isVisible = true
                        btnCreate?.isVisible = true
                        if (!isSearchActive) loadStickerPacks()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    1 -> showFavoritesTab()
                    2 -> {
                        isSearchActive = false
                        loadStickerPacks()
                    }
                }
            }
        })
    }

    private fun loadStickerPacks() {
        val owner = activity as? LifecycleOwner ?: return
        owner.lifecycleScope.launch {
            try {
                val publicResponse = withContext(Dispatchers.IO) {
                    GrpcClient.getPublicStickerPacks(limit = 50)
                }
                val userResponse = withContext(Dispatchers.IO) {
                    GrpcClient.getUserStickerPacks()
                }

                val publicPacks = publicResponse?.packs?.map { proto ->
                    StickerPack(
                        id = proto.id,
                        title = proto.title,
                        name = proto.name,
                        creatorUsername = proto.creatorUsername,
                        stickers = proto.stickers.map { s ->
                            Sticker(s.id, s.packId, s.lottieUrl, s.thumbnailUrl, s.emoji, s.width, s.height)
                        },
                        coverStickerId = proto.coverStickerId,
                        status = proto.status,
                        rejectionReason = proto.rejectionReason,
                        isFeatured = proto.isFeatured
                    )
                } ?: emptyList()

                val userPacks = userResponse?.packs?.map { proto ->
                    StickerPack(
                        id = proto.id,
                        title = proto.title,
                        name = proto.name,
                        creatorUsername = proto.creatorUsername,
                        stickers = proto.stickers.map { s ->
                            Sticker(s.id, s.packId, s.lottieUrl, s.thumbnailUrl, s.emoji, s.width, s.height)
                        },
                        coverStickerId = proto.coverStickerId,
                        status = proto.status,
                        rejectionReason = proto.rejectionReason,
                        isFeatured = proto.isFeatured
                    )
                } ?: emptyList()

                val combinedPacks = (userPacks + publicPacks).distinctBy { it.id }

                allPacks = combinedPacks
                currentPacks = combinedPacks

                // Clean up favorites from deleted packs
                if (combinedPacks.isNotEmpty()) {
                    val validPackIds = combinedPacks.map { it.id }.toSet()
                    try { StickerPreferencesManager.removeFavoritesByPackIds(validPackIds) } catch (_: Exception) {}
                }

                if (combinedPacks.isNotEmpty()) {
                    stickerPackAdapter.submitList(combinedPacks)
                    stickerPackAdapter.selectPack(combinedPacks[0].id)
                    loadPackStickers(combinedPacks[0])
                } else {
                    showEmptyState()
                }
            } catch (e: Exception) {
                showEmptyState()
            }
        }
    }

    private fun loadPackStickers(pack: StickerPack) {
        val rvStickers = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvStickers) ?: return
        val emptyState = findViewById<LinearLayout>(R.id.stickerEmptyState) ?: return

        if (pack.stickers.isNotEmpty()) {
            rvStickers.isVisible = true
            emptyState.isVisible = false
            val favorites = try { StickerPreferencesManager.getFavoriteStickers() } catch (_: Exception) { emptyList() }
            stickerGridAdapter.setFavoriteIds(favorites.map { it.id }.toSet())
            stickerGridAdapter.submitList(pack.stickers)
        } else {
            rvStickers.isVisible = false
            emptyState.isVisible = true
        }
    }

    private fun showEmptyState() {
        val rvPacks = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvStickerPacks)
        val rvStickers = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvStickers)
        val emptyState = findViewById<LinearLayout>(R.id.stickerEmptyState)

        rvPacks?.isVisible = false
        rvStickers?.isVisible = false
        emptyState?.isVisible = true
    }

    private fun showFavoritesTab() {
        val rvPacks = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvStickerPacks)
        val rvStickers = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvStickers) ?: return
        val emptyState = findViewById<LinearLayout>(R.id.stickerEmptyState) ?: return
        val sectionHeader = findViewById<TextView>(R.id.tvSectionHeader)

        rvPacks?.isVisible = false

        val favorites = try { StickerPreferencesManager.getFavoriteStickers() } catch (_: Exception) { emptyList() }
        val recent = try { StickerPreferencesManager.getRecentStickers() } catch (_: Exception) { emptyList() }

        // Show favorites first, then recent (without duplicating)
        val favoriteIds = favorites.map { it.id }.toSet()
        val recentOnly = recent.filter { it.id !in favoriteIds }
        val combined = favorites + recentOnly

        stickerGridAdapter.setFavoriteIds(favoriteIds)

        if (combined.isNotEmpty()) {
            rvStickers.isVisible = true
            emptyState.isVisible = false
            sectionHeader?.isVisible = true
            sectionHeader?.text = if (favorites.isNotEmpty()) {
                activity.getString(R.string.sticker_favorites_header, favorites.size)
            } else {
                activity.getString(R.string.sticker_recent_header)
            }
            stickerGridAdapter.submitList(combined)
        } else {
            rvStickers.isVisible = false
            emptyState.isVisible = true
            sectionHeader?.isVisible = false
            // Update empty state text for favorites context
            val emptyText = emptyState.findViewById<TextView>(R.id.tvEmptyText)
            emptyText?.text = activity.getString(R.string.sticker_no_favorites)
        }
    }

    private suspend fun performSearch(query: String) {
        val rvPacks = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvStickerPacks)
        val rvStickers = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvStickers) ?: return
        val emptyState = findViewById<LinearLayout>(R.id.stickerEmptyState) ?: return

        try {
            val response = withContext(Dispatchers.IO) {
                GrpcClient.searchStickerPacks(query, limit = 20)
            }

            val results = response?.packs?.map { proto ->
                StickerPack(
                    id = proto.id,
                    title = proto.title,
                    name = proto.name,
                    creatorUsername = proto.creatorUsername,
                    stickers = proto.stickers.map { s ->
                        Sticker(s.id, s.packId, s.lottieUrl, s.thumbnailUrl, s.emoji, s.width, s.height)
                    },
                    coverStickerId = proto.coverStickerId,
                    status = proto.status,
                    rejectionReason = proto.rejectionReason,
                    isFeatured = proto.isFeatured
                )
            }?.filter { it.stickers.isNotEmpty() } ?: emptyList()

            isSearchActive = true
            rvPacks?.isVisible = false

            val allStickers = results.flatMap { it.stickers }
            if (allStickers.isNotEmpty()) {
                rvStickers.isVisible = true
                emptyState.isVisible = false
                stickerGridAdapter.submitList(allStickers)
            } else {
                rvStickers.isVisible = false
                emptyState.isVisible = true
            }
        } catch (e: Exception) {
            rvStickers.isVisible = false
            emptyState.isVisible = true
            android.widget.Toast.makeText(activity, R.string.sticker_search_failed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "MediaPickerSheet"
    }
}
