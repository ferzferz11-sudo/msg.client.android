package lavender.client.android.ui.chat.message

import android.app.Activity
import android.graphics.Color
import android.util.TypedValue
import android.view.ViewGroup
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Sticker
import lavender.client.android.data.models.StickerPack
import lavender.client.android.theme.Theme
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.ui.sticker.StickerGridAdapter
import lavender.client.android.ui.sticker.StickerPackAdapter
import lavender.client.android.ui.widget.StandardBottomSheet

class MediaPickerSheet(
    private val activity: Activity,
    private val onEmojiSelected: (String) -> Unit,
    private val onStickerSelected: (Sticker) -> Unit,
    private val onCreateStickerPack: (() -> Unit)? = null
) : StandardBottomSheet(activity, R.layout.sheet_media_picker) {

    private val stickerGridAdapter = StickerGridAdapter { sticker ->
        onStickerSelected(sticker)
        dialog?.dismiss()
    }

    private val stickerPackAdapter = StickerPackAdapter { pack ->
        loadPackStickers(pack)
    }

    private var allPacks = listOf<StickerPack>()
    private var currentPacks = listOf<StickerPack>()
    private var emojiInitialized = false

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
        } catch (_: Exception) {}
    }

    fun showPicker() {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            setupStickerTab()
            show()
            root?.post { setupEmojiTab() }
        } catch (e: Exception) {
            android.util.Log.e("MediaPickerSheet", "showPicker failed", e)
            try { android.widget.Toast.makeText(activity, android.R.string.cancel, android.widget.Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
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
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout) ?: return
        val emojiContainer = findViewById<android.view.View>(R.id.emojiContainer)
        val stickerContainer = findViewById<LinearLayout>(R.id.stickerContainer)
        val rvPacks = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvStickerPacks)
        val rvStickers = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvStickers)
        val emptyState = findViewById<LinearLayout>(R.id.stickerEmptyState)
        val btnCreate = findViewById<android.view.View>(R.id.btnCreatePack)

        rvPacks?.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        rvPacks?.adapter = stickerPackAdapter

        rvStickers?.layoutManager = GridLayoutManager(activity, 4)
        rvStickers?.adapter = stickerGridAdapter

        btnCreate?.setOnClickListener {
            dismiss()
            onCreateStickerPack?.invoke()
        }

        tabLayout.addTab(tabLayout.newTab().setText("😀"))
        tabLayout.addTab(tabLayout.newTab().setText("🎨"))

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
                        loadStickerPacks()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
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

                val combinedPacks = (userPacks + publicPacks).filter { it.stickers.isNotEmpty() }.distinctBy { it.id }

                allPacks = combinedPacks
                currentPacks = combinedPacks

                if (combinedPacks.isNotEmpty()) {
                    stickerPackAdapter.submitList(combinedPacks)
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
}
