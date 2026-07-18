package lavender.client.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Sticker
import lavender.client.android.data.models.StickerPack
import lavender.client.android.network.HttpClient
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.ui.sticker.StickerGridAdapter
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class StickerPackCreateActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var etTitle: EditText
    private lateinit var etName: EditText
    private lateinit var rvStickers: RecyclerView
    private lateinit var btnAddSticker: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var btnSubmit: MaterialButton

    private val stickerGridAdapter = StickerGridAdapter { }
    private val currentStickers = mutableListOf<Sticker>()
    private var packId: String? = null
    private var isDraft = true

    private val pickLottieLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> uploadSticker(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sticker_pack_create)
        @Suppress("DEPRECATION")
        try { window.decorView.systemUiVisibility = 0 } catch (_: Exception) {}
        ThemeApplier.apply(this, ThemeStore.currentTheme())

        toolbar = findViewById(R.id.toolbar)
        etTitle = findViewById(R.id.etTitle)
        etName = findViewById(R.id.etName)
        rvStickers = findViewById(R.id.rvStickers)
        btnAddSticker = findViewById(R.id.btnAddSticker)
        btnSave = findViewById(R.id.btnSave)
        btnSubmit = findViewById(R.id.btnSubmit)

        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.navigationIcon?.setTint(getColor(R.color.white))
        toolbar.setNavigationOnClickListener { finish() }

        rvStickers.layoutManager = GridLayoutManager(this, 4)
        rvStickers.adapter = stickerGridAdapter

        btnAddSticker.setOnClickListener { pickLottieFile() }
        btnSave.setOnClickListener { savePack() }
        btnSubmit.setOnClickListener { submitPack() }

        packId = intent.getStringExtra("PACK_ID")
        if (packId != null) {
            toolbar.title = getString(R.string.sticker_edit_pack)
            loadPack()
        } else {
            toolbar.title = getString(R.string.sticker_create_pack)
            btnSubmit.visibility = android.view.View.GONE
        }
    }

    private fun loadPack() {
        val id = packId ?: return
        lifecycleScope.launch {
            val response = GrpcClient.getStickerPack(id)
            val pack = response?.pack ?: return@launch
            etTitle.setText(pack.title)
            etName.setText(pack.name)
            isDraft = pack.status == "draft"
            btnSubmit.visibility = if (isDraft) android.view.View.VISIBLE else android.view.View.GONE

            currentStickers.clear()
            currentStickers.addAll(pack.stickers.map { s ->
                Sticker(s.id, s.packId, s.lottieUrl, s.thumbnailUrl, s.emoji, s.width, s.height)
            })
            stickerGridAdapter.submitList(currentStickers.toList())
        }
    }

    private fun pickLottieFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        pickLottieLauncher.launch(intent)
    }

    private fun uploadSticker(uri: Uri) {
        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                } ?: return@launch

                val url = withContext(Dispatchers.IO) {
                    val body = json.toRequestBody("application/json".toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("sticker", "sticker.json", body)
                    val request = Request.Builder()
                        .url("${lavender.client.android.data.session.CredentialStore.getHttpServerUrl(this@StickerPackCreateActivity)}/upload-sticker")
                        .post(MultipartBody.Builder().setType(MultipartBody.FORM).addPart(part).build())
                        .build()
                    val response = HttpClient.client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""
                    response.close()
                    try { org.json.JSONObject(responseBody).getString("url") } catch (_: Exception) { "" }
                }

                if (url.isNotEmpty()) {
                    val newSticker = Sticker(
                        id = UUID.randomUUID().toString(),
                        packId = packId ?: "",
                        lottieUrl = url,
                        emoji = "\uD83C\uDFB5"
                    )
                    currentStickers.add(newSticker)
                    stickerGridAdapter.submitList(currentStickers.toList())
                    Toast.makeText(this@StickerPackCreateActivity, "Sticker added", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@StickerPackCreateActivity, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun savePack() {
        val title = etTitle.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show()
            return
        }
        val name = etName.text.toString().trim().ifEmpty { title.lowercase().replace(" ", "_") }

        lifecycleScope.launch {
            try {
                if (packId != null) {
                    GrpcClient.updateStickerPack(packId!!, title = title)
                    Toast.makeText(this@StickerPackCreateActivity, "Pack updated", Toast.LENGTH_SHORT).show()
                } else {
                    val result = GrpcClient.createStickerPack(title, name)
                    if (result?.success == true) {
                        packId = result.pack?.id
                        toolbar.title = getString(R.string.sticker_edit_pack)
                        for (sticker in currentStickers) {
                            GrpcClient.addSticker(
                                packId!!, sticker.lottieUrl, sticker.thumbnailUrl,
                                sticker.emoji, sticker.width, sticker.height
                            )
                        }
                        Toast.makeText(this@StickerPackCreateActivity, "Pack created", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@StickerPackCreateActivity, result?.error ?: "Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@StickerPackCreateActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submitPack() {
        val id = packId ?: return
        lifecycleScope.launch {
            val result = GrpcClient.submitStickerPackForApproval(id)
            if (result?.success == true) {
                Toast.makeText(this@StickerPackCreateActivity, "Submitted for approval", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@StickerPackCreateActivity, result?.error ?: "Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
