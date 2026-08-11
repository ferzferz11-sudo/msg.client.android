package lavender.client.android

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import androidx.core.graphics.scale

class StickerPackCreateActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var etTitle: EditText
    private lateinit var rvStickers: RecyclerView
    private lateinit var btnAddSticker: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var btnSubmit: MaterialButton

    private val currentStickers = mutableListOf<Sticker>()
    private var originalStickerIds = mutableSetOf<String>()
    private var packId: String? = null
    private var isDraft = true
    private var coverStickerId: String? = null
    private var isSaving = false

    private val stickerGridAdapter = StickerGridAdapter(
        onStickerClick = { sticker ->
            val intent = android.content.Intent(this, FullScreenImageActivity::class.java).apply {
                putExtra("image_url", sticker.thumbnailUrl.ifEmpty { sticker.lottieUrl })
                putExtra("sticker_url", sticker.lottieUrl)
            }
            startActivity(intent)
        },
        onStickerLongClick = { sticker -> showStickerOptions(sticker) }
    )

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
            val uri = result.data?.let { lavender.client.android.data.CompatUtils.getParcelableExtra(it, StickerEditorActivity.EXTRA_RESULT_URI, android.net.Uri::class.java) }
            uri?.let { uri ->
                uploadSticker(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sticker_pack_create)
        ThemeApplier.apply(this, ThemeStore.currentTheme())

        toolbar = findViewById(R.id.toolbar)
        etTitle = findViewById(R.id.etTitle)
        rvStickers = findViewById(R.id.rvStickers)
        btnAddSticker = findViewById(R.id.btnAddSticker)
        btnSave = findViewById(R.id.btnSave)
        btnSubmit = findViewById(R.id.btnSubmit)

        applyThemeToFields()
        updateSaveButtonState()

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
            val username = GrpcClient.getCurrentUsername() ?: ""
            etTitle.setText(getString(R.string.sticker_default_pack_name, username))
            etTitle.selectAll()
        }
    }

    private fun applyThemeToFields() {
        try {
            val theme = ThemeStore.currentTheme()
            val primaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
            val textPrimaryColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
            val surfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
            val onSurfaceColor = ThemeUtils.parseSafeColor(theme.onSurfaceColor, Color.WHITE)

            etTitle.setTextColor(textPrimaryColor)
            etTitle.setHintTextColor(ThemeUtils.adjustAlpha(textPrimaryColor, 0.5f))
            etTitle.highlightColor = ThemeUtils.adjustAlpha(primaryColor, 0.3f)

            findViewById<android.view.View>(android.R.id.content)?.let { contentView ->
                themeInputLayoutsIn(contentView, primaryColor, onSurfaceColor, surfaceColor, textPrimaryColor)
            }

            btnAddSticker.strokeColor = android.content.res.ColorStateList.valueOf(primaryColor)
            btnAddSticker.setTextColor(primaryColor)
            btnAddSticker.iconTint = android.content.res.ColorStateList.valueOf(primaryColor)

            val onPrimaryColor = ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE)
            btnSave.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            btnSave.setTextColor(onPrimaryColor)
        } catch (e: Exception) { Log.w(TAG, "Caught: " + e.message) }
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

    private fun updateSaveButtonState() {
        btnSave.isEnabled = currentStickers.isNotEmpty()
        val alpha = if (currentStickers.isNotEmpty()) 1.0f else 0.5f
        btnSave.alpha = alpha
    }

    private fun showStickerOptions(sticker: Sticker) {
        val options = arrayOf(
            getString(R.string.sticker_set_cover),
            getString(R.string.sticker_remove)
        )
        android.app.AlertDialog.Builder(this)
            .setTitle(sticker.emoji)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showCoverDialog(sticker)
                    1 -> removeSticker(sticker)
                }
            }
            .show()
    }

    private fun removeSticker(sticker: Sticker) {
        currentStickers.removeAll { it.id == sticker.id }
        stickerGridAdapter.submitList(currentStickers.toList())
        updateSaveButtonState()
        Toast.makeText(this, getString(R.string.sticker_removed), Toast.LENGTH_SHORT).show()
    }

    private fun showCoverDialog(sticker: Sticker) {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.sticker_set_cover)
            .setPositiveButton(R.string.save) { _, _ ->
                coverStickerId = sticker.id
                Toast.makeText(this, getString(R.string.sticker_cover_set), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun loadPack() {
        val id = packId ?: return
        lifecycleScope.launch {
            val response = GrpcClient.getStickerPack(id)
            val pack = response?.pack ?: return@launch
            etTitle.setText(pack.title)
            isDraft = pack.status == "draft"
            btnSubmit.visibility = if (isDraft) android.view.View.VISIBLE else android.view.View.GONE
            coverStickerId = pack.coverStickerId.ifEmpty { null }

            currentStickers.clear()
            currentStickers.addAll(pack.stickers.map { s ->
                Sticker(s.id, s.packId, s.lottieUrl, s.thumbnailUrl, s.emoji, s.width, s.height)
            })
            originalStickerIds.clear()
            originalStickerIds.addAll(pack.stickers.map { it.id })
            stickerGridAdapter.submitList(currentStickers.toList())
            updateSaveButtonState()
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
                var mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                // Fallback: detect by extension if contentResolver returns generic type
                if (mimeType == "application/octet-stream") {
                    val path = uri.path ?: ""
                    mimeType = when {
                        path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
                        path.endsWith(".png", true) -> "image/png"
                        path.endsWith(".webp", true) -> "image/webp"
                        path.endsWith(".json", true) -> "application/json"
                        else -> mimeType
                    }
                }
                val isImage = mimeType.startsWith("image/")
                val maxBytes = 2 * 1024 * 1024
                android.util.Log.d("StickerPack", "uploadSticker: uri=$uri, mimeType=$mimeType, isImage=$isImage")

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

                    Triple(bytes, if (isImage) "sticker.jpg" else "sticker.json", "")
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
                    val endpoint = if (isImage) "/upload-sticker-thumbnail" else "/upload-sticker"
                    val request = Request.Builder()
                        .url("${lavender.client.android.data.session.CredentialStore.getHttpServerUrl(this@StickerPackCreateActivity)}$endpoint")
                        .post(MultipartBody.Builder().setType(MultipartBody.FORM).addPart(part).build())
                        .build()
                    val response = HttpClient.client.newCall(request).execute()
                    val code = response.code
                    val responseBody = response.body?.string() ?: ""
                    response.close()

                    if (code == 200) {
                        try {
                            val serverUrl = lavender.client.android.data.session.CredentialStore.getHttpServerUrl(this@StickerPackCreateActivity)
                            val rawUrl = org.json.JSONObject(responseBody).getString("url")
                            // Rewrite host:port to match client's configured server (PUBLIC_IP may differ from device's route)
                            val url = try {
                                val uri = java.net.URI(rawUrl)
                                val serverUri = java.net.URI(serverUrl)
                                java.net.URI(uri.scheme, null, serverUri.host, serverUri.port, uri.path, uri.query, uri.fragment).toString()
                            } catch (_: Exception) { rawUrl }
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
                android.util.Log.d("StickerPack", "Upload result: url=$url, error=$error")

                if (url.isNotEmpty()) {
                    val newSticker = Sticker(
                        id = UUID.randomUUID().toString(),
                        packId = packId ?: "",
                        lottieUrl = url,
                        thumbnailUrl = if (isImage) url else ""
                    )
                    currentStickers.add(newSticker)
                    android.util.Log.d("StickerPack", "Sticker added locally: total=${currentStickers.size}, id=${newSticker.id}")
                    stickerGridAdapter.submitList(currentStickers.toList())
                    updateSaveButtonState()
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
        var bitmap: android.graphics.Bitmap? = null
        var scaledBitmap: android.graphics.Bitmap? = null
        return try {
            bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
            val maxDim = 512
            val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1f)
            scaledBitmap = if (scale < 1f) {
                bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
            } else bitmap
            val outputStream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.toByteArray()
        } catch (_: Throwable) {
            bytes
        } finally {
            scaledBitmap?.let { if (it !== bitmap) it.recycle() }
            bitmap?.recycle()
        }
    }

    private fun savePack() {
        val title = etTitle.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, getString(R.string.sticker_title_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (currentStickers.isEmpty()) {
            Toast.makeText(this, getString(R.string.sticker_save_disabled), Toast.LENGTH_SHORT).show()
            return
        }
        if (isSaving) return
        isSaving = true
        val name = title.lowercase().replace(" ", "_")

        btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                if (packId != null) {
                    Log.d("StickerPack", "Updating pack $packId, title=$title, cover=$coverStickerId")
                    val updateResult = GrpcClient.updateStickerPack(packId!!, title = title, coverStickerId = coverStickerId ?: "")
                    Log.d("StickerPack", "Update result: success=${updateResult?.success}")
                    if (updateResult == null || !updateResult.success) {
                        Log.e("StickerPack", "updateStickerPack failed: success=${updateResult?.success}")
                        Toast.makeText(this@StickerPackCreateActivity, getString(R.string.failed), Toast.LENGTH_SHORT).show()
                        btnSave.isEnabled = true
                        isSaving = false
                        return@launch
                    }
                    val newStickers = currentStickers.filter { it.id !in originalStickerIds }
                    if (newStickers.isNotEmpty()) {
                        Log.d("StickerPack", "Adding ${newStickers.size} new stickers to pack $packId")
                        var addFailed = 0
                        for (sticker in newStickers) {
                            if (sticker.lottieUrl.isEmpty()) { addFailed++; continue }
                            val addResult = GrpcClient.addSticker(
                                packId!!, sticker.lottieUrl, sticker.thumbnailUrl,
                                sticker.emoji, sticker.width, sticker.height
                            )
                            if (addResult?.success != true) addFailed++
                        }
                        if (addFailed > 0) Log.e("StickerPack", "$addFailed new stickers failed to add")
                    }
                    Toast.makeText(this@StickerPackCreateActivity, getString(R.string.sticker_pack_updated), Toast.LENGTH_SHORT).show()
                    isSaving = false
                    finish()
                } else {
                    Log.d("StickerPack", "Creating pack: title=$title, name=$name, stickers=${currentStickers.size}")
                    val result = GrpcClient.createStickerPack(title, name)
                    Log.d("StickerPack", "Create result: success=${result?.success}, error=${result?.error}, packId=${result?.pack?.id}")
                    if (result == null) {
                        Log.e("StickerPack", "createStickerPack returned null — channel issue or auth failure")
                        Toast.makeText(this@StickerPackCreateActivity, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show()
                        btnSave.isEnabled = true
                        isSaving = false
                        return@launch
                    }
                    if (!result.success) {
                        Log.e("StickerPack", "createStickerPack failed: ${result.error}")
                        Toast.makeText(this@StickerPackCreateActivity, result.error.ifEmpty { getString(R.string.failed) }, Toast.LENGTH_SHORT).show()
                        btnSave.isEnabled = true
                        isSaving = false
                        return@launch
                    }
                    val newPackId = result.pack?.id
                    if (newPackId.isNullOrEmpty()) {
                        Log.e("StickerPack", "createStickerPack success but pack.id is null/empty")
                        Toast.makeText(this@StickerPackCreateActivity, getString(R.string.sticker_upload_failed), Toast.LENGTH_SHORT).show()
                        btnSave.isEnabled = true
                        isSaving = false
                        return@launch
                    }
                    packId = newPackId
                    Log.d("StickerPack", "Pack created: $packId, adding ${currentStickers.size} stickers")
                    toolbar.title = getString(R.string.sticker_edit_pack)
                    var addFailed = 0
                    for ((idx, sticker) in currentStickers.withIndex()) {
                        if (sticker.lottieUrl.isEmpty()) {
                            Log.w("StickerPack", "Skipping sticker ${idx+1}: empty lottieUrl")
                            addFailed++
                            continue
                        }
                        Log.d("StickerPack", "Adding sticker ${idx+1}/${currentStickers.size}: url=${sticker.lottieUrl}")
                        val addResult = GrpcClient.addSticker(
                            packId!!, sticker.lottieUrl, sticker.thumbnailUrl,
                            sticker.emoji, sticker.width, sticker.height
                        )
                        Log.d("StickerPack", "addSticker result: success=${addResult?.success}, error=${addResult?.error}")
                        if (addResult?.success != true) addFailed++
                    }
                    if (addFailed > 0) {
                        Log.e("StickerPack", "$addFailed stickers failed to add")
                    }
                    val currentCover = coverStickerId
                    if (currentCover != null) {
                        Log.d("StickerPack", "Setting cover: $currentCover")
                        val coverResult = GrpcClient.updateStickerPack(packId!!, coverStickerId = currentCover)
                        if (coverResult == null || !coverResult.success) {
                            Log.e("StickerPack", "Setting cover failed: success=${coverResult?.success}")
                        }
                    }
                    Toast.makeText(this@StickerPackCreateActivity, getString(R.string.sticker_pack_created), Toast.LENGTH_SHORT).show()
                    isSaving = false
                    finish()
                }
            } catch (e: Exception) {
                Log.e("StickerPack", "savePack exception: ${e.javaClass.simpleName}: ${e.message}", e)
                Toast.makeText(this@StickerPackCreateActivity, getString(R.string.error_colon, e.message ?: ""), Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
                isSaving = false
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
                Toast.makeText(this@StickerPackCreateActivity, result?.error ?: getString(R.string.failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val TAG = "StickerPackCreateActivity"
    }
}
