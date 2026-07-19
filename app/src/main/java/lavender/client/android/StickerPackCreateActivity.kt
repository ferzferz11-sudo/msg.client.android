package lavender.client.android

import android.app.Activity
import android.content.Intent
import android.graphics.Color
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
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Sticker
import lavender.client.android.data.models.StickerPack
import lavender.client.android.network.HttpClient
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
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

    private val stickerGridAdapter = StickerGridAdapter(onStickerClick = { })
    private val currentStickers = mutableListOf<Sticker>()
    private var packId: String? = null
    private var isDraft = true

    private val pickStickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val mimeType = contentResolver.getType(uri) ?: ""
                if (mimeType.startsWith("image/")) {
                    val editorIntent = StickerEditorActivity.createIntent(this, uri)
                    stickerEditorLauncher.launch(editorIntent)
                } else {
                    uploadSticker(uri)
                }
            }
        }
    }

    private val stickerEditorLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra<android.net.Uri>(StickerEditorActivity.EXTRA_RESULT_URI)?.let { uri ->
                uploadSticker(uri)
            }
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

        applyThemeToFields()

        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.navigationIcon?.setTint(getColor(R.color.white))
        toolbar.setNavigationOnClickListener { finish() }

        rvStickers.layoutManager = GridLayoutManager(this, 4)
        rvStickers.adapter = stickerGridAdapter

        btnAddSticker.setOnClickListener { pickStickerFile() }
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

    private fun applyThemeToFields() {
        try {
            val theme = ThemeStore.currentTheme()
            val primaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
            val textPrimaryColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
            val surfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
            val onSurfaceColor = ThemeUtils.parseSafeColor(theme.onSurfaceColor, Color.WHITE)

            listOf(etTitle, etName).forEach { et ->
                et.setTextColor(textPrimaryColor)
                et.setHintTextColor(ThemeUtils.adjustAlpha(textPrimaryColor, 0.5f))
                et.highlightColor = ThemeUtils.adjustAlpha(primaryColor, 0.3f)
            }

            findViewById<android.view.View>(android.R.id.content)?.let { contentView ->
                themeInputLayoutsIn(contentView, primaryColor, onSurfaceColor, surfaceColor, textPrimaryColor)
            }

            btnAddSticker.strokeColor = android.content.res.ColorStateList.valueOf(primaryColor)
            btnAddSticker.setTextColor(primaryColor)
            btnAddSticker.iconTint = android.content.res.ColorStateList.valueOf(primaryColor)
        } catch (_: Exception) {}
    }

    private fun themeInputLayoutsIn(view: android.view.View, primaryColor: Int, onSurfaceColor: Int, surfaceColor: Int, textPrimaryColor: Int) {
        if (view is TextInputLayout) {
            themeInputLayout(view, primaryColor, onSurfaceColor, surfaceColor)
        }
        if (view is EditText) {
            view.setTextColor(textPrimaryColor)
            view.setHintTextColor(ThemeUtils.adjustAlpha(textPrimaryColor, 0.5f))
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                themeInputLayoutsIn(view.getChildAt(i), primaryColor, onSurfaceColor, surfaceColor, textPrimaryColor)
            }
        }
    }

    private fun themeInputLayout(layout: TextInputLayout, primaryColor: Int, onSurfaceColor: Int, surfaceColor: Int) {
        val strokeColorStateList = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(primaryColor, ThemeUtils.adjustAlpha(onSurfaceColor, 0.3f))
        )
        layout.boxBackgroundColor = surfaceColor
        layout.setBoxStrokeColorStateList(strokeColorStateList)
        layout.hintTextColor = android.content.res.ColorStateList.valueOf(primaryColor)
        layout.defaultHintTextColor = android.content.res.ColorStateList.valueOf(ThemeUtils.adjustAlpha(onSurfaceColor, 0.7f))
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

    private fun pickStickerFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/json",
                "image/jpeg",
                "image/png",
                "image/webp"
            ))
        }
        pickStickerLauncher.launch(intent)
    }

    private fun uploadSticker(uri: Uri) {
        lifecycleScope.launch {
            try {
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val isImage = mimeType.startsWith("image/")
                val maxBytes = 2 * 1024 * 1024

                val prepareResult = withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri) ?: return@withContext Triple(null as ByteArray?, "", "")
                    var bytes = inputStream.readBytes()
                    inputStream.close()

                    if (isImage && bytes.size > maxBytes) {
                        bytes = compressImage(uri, bytes)
                    }

                    if (bytes.size > maxBytes) {
                        return@withContext Triple(null as ByteArray?, "", getString(R.string.sticker_file_too_large))
                    }

                    Triple(bytes, if (isImage) "sticker.png" else "sticker.json", "")
                }

                val bytes = prepareResult.first
                val fileName = prepareResult.second
                val preError = prepareResult.third

                if (preError.isNotEmpty()) {
                    Toast.makeText(this@StickerPackCreateActivity, preError, Toast.LENGTH_LONG).show()
                    return@launch
                }

                if (bytes == null || bytes.isEmpty()) {
                    Toast.makeText(this@StickerPackCreateActivity, getString(R.string.sticker_upload_failed), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val uploadResult = withContext(Dispatchers.IO) {
                    val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("sticker", fileName, body)
                    val request = Request.Builder()
                        .url("${lavender.client.android.data.session.CredentialStore.getHttpServerUrl(this@StickerPackCreateActivity)}/upload-sticker")
                        .post(MultipartBody.Builder().setType(MultipartBody.FORM).addPart(part).build())
                        .build()
                    val response = HttpClient.client.newCall(request).execute()
                    val code = response.code
                    val responseBody = response.body?.string() ?: ""
                    response.close()

                    if (code == 200) {
                        try {
                            val url = org.json.JSONObject(responseBody).getString("url")
                            Pair(url, "")
                        } catch (_: Exception) {
                            Pair("", getString(R.string.sticker_upload_failed))
                        }
                    } else {
                        val errorMsg = try {
                            org.json.JSONObject(responseBody).optString("error", responseBody)
                        } catch (_: Exception) {
                            responseBody.ifEmpty { "HTTP $code" }
                        }
                        Pair("", errorMsg)
                    }
                }

                val url = uploadResult.first
                val error = uploadResult.second

                if (url.isNotEmpty()) {
                    val newSticker = Sticker(
                        id = UUID.randomUUID().toString(),
                        packId = packId ?: "",
                        lottieUrl = url,
                        emoji = "\uD83C\uDFB5"
                    )
                    currentStickers.add(newSticker)
                    stickerGridAdapter.submitList(currentStickers.toList())
                    Toast.makeText(this@StickerPackCreateActivity, getString(R.string.sticker_added), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@StickerPackCreateActivity, "${getString(R.string.sticker_upload_failed)}: $error", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@StickerPackCreateActivity, "${getString(R.string.sticker_upload_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun compressImage(uri: Uri, bytes: ByteArray): ByteArray {
        return try {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
            val maxDim = 1024
            val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1f)
            val scaledBitmap = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else bitmap
            val outputStream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, outputStream)
            if (scaledBitmap !== bitmap) scaledBitmap.recycle()
            bitmap.recycle()
            outputStream.toByteArray()
        } catch (_: Exception) {
            bytes
        }
    }

    private fun savePack() {
        val title = etTitle.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, getString(R.string.sticker_title_required), Toast.LENGTH_SHORT).show()
            return
        }
        val name = etName.text.toString().trim().ifEmpty { title.lowercase().replace(" ", "_") }

        lifecycleScope.launch {
            try {
                if (packId != null) {
                    GrpcClient.updateStickerPack(packId!!, title = title)
                    Toast.makeText(this@StickerPackCreateActivity, getString(R.string.sticker_pack_updated), Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@StickerPackCreateActivity, getString(R.string.sticker_pack_created), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this@StickerPackCreateActivity, getString(R.string.sticker_submitted), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@StickerPackCreateActivity, result?.error ?: "Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
