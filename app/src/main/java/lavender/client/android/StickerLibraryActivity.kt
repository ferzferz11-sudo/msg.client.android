package lavender.client.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Sticker
import lavender.client.android.data.models.StickerPack
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.ui.sticker.StickerPackListAdapter

class StickerLibraryActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var rvPacks: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var fabCreate: FloatingActionButton
    private lateinit var packAdapter: StickerPackListAdapter

    private var myPacks = listOf<StickerPack>()
    private var publicPacks = listOf<StickerPack>()
    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sticker_library)
        @Suppress("DEPRECATION")
        try { window.decorView.systemUiVisibility = 0 } catch (_: Exception) {}
        ThemeApplier.apply(this, ThemeStore.currentTheme())

        toolbar = findViewById(R.id.toolbar)
        tabLayout = findViewById(R.id.tabLayout)
        rvPacks = findViewById(R.id.rvPacks)
        tvEmpty = findViewById(R.id.tvEmpty)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        fabCreate = findViewById(R.id.fabCreate)

        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.navigationIcon?.setTint(getColor(R.color.white))
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = getString(R.string.sticker_library)

        tabLayout.addTab(tabLayout.newTab().setText(R.string.sticker_my_packs))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.sticker_public_packs))

        packAdapter = StickerPackListAdapter(
            onPackClick = { pack ->
                val intent = Intent(this, StickerPackCreateActivity::class.java)
                intent.putExtra("PACK_ID", pack.id)
                startActivity(intent)
            },
            onPackLongClick = { pack ->
                showPackOptions(pack)
            }
        )
        rvPacks.layoutManager = LinearLayoutManager(this)
        rvPacks.adapter = packAdapter

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                updateList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        swipeRefresh.setOnRefreshListener { loadPacks() }
        fabCreate.setOnClickListener {
            startActivity(Intent(this, StickerPackCreateActivity::class.java))
        }

        loadPacks()
    }

    private fun loadPacks() {
        swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val userResponse = GrpcClient.getUserStickerPacks()
                myPacks = userResponse?.packs?.map { proto ->
                    StickerPack(
                        id = proto.id, title = proto.title, name = proto.name,
                        creatorUsername = proto.creatorUsername,
                        stickers = proto.stickers.map { s -> Sticker(s.id, s.packId, s.lottieUrl, s.thumbnailUrl, s.emoji, s.width, s.height) },
                        coverStickerId = proto.coverStickerId, status = proto.status,
                        rejectionReason = proto.rejectionReason, isFeatured = proto.isFeatured
                    )
                } ?: emptyList()

                val publicResponse = GrpcClient.getPublicStickerPacks(limit = 50)
                publicPacks = publicResponse?.packs?.map { proto ->
                    StickerPack(
                        id = proto.id, title = proto.title, name = proto.name,
                        creatorUsername = proto.creatorUsername,
                        stickers = proto.stickers.map { s -> Sticker(s.id, s.packId, s.lottieUrl, s.thumbnailUrl, s.emoji, s.width, s.height) },
                        coverStickerId = proto.coverStickerId, status = proto.status,
                        rejectionReason = proto.rejectionReason, isFeatured = proto.isFeatured
                    )
                } ?: emptyList()

                updateList()
            } catch (e: Exception) {
                Toast.makeText(this@StickerLibraryActivity, "Failed to load packs", Toast.LENGTH_SHORT).show()
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun updateList() {
        val packs = if (currentTab == 0) myPacks else publicPacks
        packAdapter.submitList(packs)
        tvEmpty.visibility = if (packs.isEmpty()) View.VISIBLE else View.GONE
        rvPacks.visibility = if (packs.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showPackOptions(pack: StickerPack) {
        val options = mutableListOf<String>()
        if (pack.status == "draft") options.add(getString(R.string.sticker_submit_for_approval))
        options.add(getString(R.string.sticker_edit_pack))
        options.add(getString(R.string.sticker_delete_pack))

        val items = options.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle(pack.title)
            .setItems(items) { _, which ->
                when (items[which]) {
                    getString(R.string.sticker_submit_for_approval) -> submitPack(pack)
                    getString(R.string.sticker_edit_pack) -> {
                        val intent = Intent(this, StickerPackCreateActivity::class.java)
                        intent.putExtra("PACK_ID", pack.id)
                        startActivity(intent)
                    }
                    getString(R.string.sticker_delete_pack) -> deletePack(pack)
                }
            }
            .show()
    }

    private fun submitPack(pack: StickerPack) {
        lifecycleScope.launch {
            val result = GrpcClient.submitStickerPackForApproval(pack.id)
            if (result?.success == true) {
                Toast.makeText(this@StickerLibraryActivity, "Submitted for approval", Toast.LENGTH_SHORT).show()
                loadPacks()
            } else {
                Toast.makeText(this@StickerLibraryActivity, result?.error ?: "Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deletePack(pack: StickerPack) {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.sticker_delete_pack)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val result = GrpcClient.deleteStickerPack(pack.id)
                    if (result?.success == true) {
                        loadPacks()
                    } else {
                        Toast.makeText(this@StickerLibraryActivity, "Failed to delete", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
