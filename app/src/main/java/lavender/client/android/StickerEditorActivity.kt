package lavender.client.android
import android.util.Log

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.ui.sticker.StickerEditorView
import java.io.File
import java.io.FileOutputStream

class StickerEditorActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var editorView: StickerEditorView
    private lateinit var textInputContainer: LinearLayout
    private lateinit var etTextInput: EditText
    private lateinit var btnAddText: MaterialButton
    private lateinit var colorPickerContainer: LinearLayout
    private lateinit var filterStrip: LinearLayout
    private lateinit var btnCrop: MaterialButton
    private lateinit var btnText: MaterialButton
    private lateinit var btnFilters: MaterialButton

    private var imageUri: Uri? = null
    private var currentMode = StickerEditorView.EditorMode.CROP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sticker_editor)
        ThemeApplier.apply(this, ThemeStore.currentTheme())

        toolbar = findViewById(R.id.toolbar)
        editorView = findViewById(R.id.editorView)
        textInputContainer = findViewById(R.id.textInputContainer)
        etTextInput = findViewById(R.id.etTextInput)
        btnAddText = findViewById(R.id.btnAddText)
        colorPickerContainer = findViewById(R.id.colorPickerContainer)
        filterStrip = findViewById(R.id.filterStrip)
        btnCrop = findViewById(R.id.btnCrop)
        btnText = findViewById(R.id.btnText)
        btnFilters = findViewById(R.id.btnFilters)

        applyThemeToViews()

        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.navigationIcon?.setTint(ThemeUtils.parseSafeColor(ThemeStore.currentTheme().onPrimaryColor, Color.WHITE))
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = getString(R.string.sticker_editor_title)

        toolbar.menu?.clear()
        toolbar.inflateMenu(R.menu.sticker_editor_menu)

        imageUri = lavender.client.android.data.CompatUtils.getParcelableExtra(intent, EXTRA_IMAGE_URI, Uri::class.java)
        if (imageUri != null) {
            editorView.setImageUri(imageUri!!)
        }

        btnCrop.setOnClickListener { setMode(StickerEditorView.EditorMode.CROP) }
        btnText.setOnClickListener { setMode(StickerEditorView.EditorMode.TEXT) }
        btnFilters.setOnClickListener { setMode(StickerEditorView.EditorMode.FILTER) }

        btnAddText.setOnClickListener {
            val text = etTextInput.text.toString().trim()
            if (text.isNotEmpty()) {
                editorView.addTextOverlay(text, pickedColor)
                etTextInput.text.clear()
            }
        }

        setupColorPicker()
        setupFilterStrip()

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_done -> {
                    saveAndReturn()
                    true
                }
                else -> false
            }
        }

        setMode(StickerEditorView.EditorMode.CROP)
    }

    private fun applyThemeToViews() {
        try {
            val theme = ThemeStore.currentTheme()
            val primaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
            val surfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
            val textPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
            val onSurface = ThemeUtils.parseSafeColor(theme.onSurfaceColor, Color.WHITE)

            toolbar.setBackgroundColor(primaryColor)
            toolbar.setTitleTextColor(ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE))

            listOf(btnCrop, btnText, btnFilters).forEach { btn ->
                btn.setTextColor(onSurface)
                btn.iconTint = android.content.res.ColorStateList.valueOf(onSurface)
            }

            // Theme the bottom bar background
            findViewById<View>(R.id.bottomBar)?.setBackgroundColor(surfaceColor)

            // Theme the filter strip background
            filterStrip.setBackgroundColor(surfaceColor)

            etTextInput.setTextColor(textPrimary)
            etTextInput.setHintTextColor(ThemeUtils.adjustAlpha(textPrimary, 0.5f))
            btnAddText.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            btnAddText.setTextColor(ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE))
        } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }
    }

    private var pickedColor = Color.WHITE

    private fun setupColorPicker() {
        val colorViews = mapOf(
            R.id.ivColorWhite to Color.WHITE,
            R.id.ivColorBlack to Color.BLACK,
            R.id.ivColorRed to Color.RED,
            R.id.ivColorYellow to Color.YELLOW,
            R.id.ivColorGreen to Color.parseColor("#4CAF50"),
            R.id.ivColorBlue to Color.parseColor("#2196F3")
        )

        colorViews.forEach { (viewId, color) ->
            findViewById<ImageView>(viewId)?.setOnClickListener {
                pickedColor = color
                editorView.setTextColor(color)
            }
        }
    }

    private fun setupFilterStrip() {
        val filters = listOf(
            StickerEditorView.FilterType.ORIGINAL to getString(R.string.sticker_filter_original),
            StickerEditorView.FilterType.GRAYSCALE to getString(R.string.sticker_filter_grayscale),
            StickerEditorView.FilterType.SEPIA to getString(R.string.sticker_filter_sepia),
            StickerEditorView.FilterType.WARM to getString(R.string.sticker_filter_warm),
            StickerEditorView.FilterType.COOL to getString(R.string.sticker_filter_cool),
            StickerEditorView.FilterType.BRIGHTNESS to getString(R.string.sticker_filter_bright)
        )

        filterStrip.removeAllViews()
        val theme = ThemeStore.currentTheme()
        val primaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
        val textPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)

        filters.forEach { (type, label) ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                textSize = 11f
                setTextColor(textPrimary)
                strokeWidth = 2
                strokeColor = android.content.res.ColorStateList.valueOf(ThemeUtils.adjustAlpha(textPrimary, 0.3f))
                setPadding(16, 8, 16, 8)
                minimumWidth = 0
                minimumHeight = 0
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 8 }
                layoutParams = params
                setOnClickListener {
                    editorView.currentFilter = type
                    updateFilterSelection(type)
                }
            }
            filterStrip.addView(btn)
        }
    }

    private fun updateFilterSelection(selected: StickerEditorView.FilterType) {
        val theme = ThemeStore.currentTheme()
        val primaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
        val textPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)

        for (i in 0 until filterStrip.childCount) {
            val btn = filterStrip.getChildAt(i) as? MaterialButton ?: continue
            val filters = StickerEditorView.FilterType.entries
            if (i < filters.size) {
                if (filters[i] == selected) {
                    btn.strokeColor = android.content.res.ColorStateList.valueOf(primaryColor)
                    btn.setTextColor(primaryColor)
                } else {
                    btn.strokeColor = android.content.res.ColorStateList.valueOf(ThemeUtils.adjustAlpha(textPrimary, 0.3f))
                    btn.setTextColor(textPrimary)
                }
            }
        }
    }

    private fun setMode(mode: StickerEditorView.EditorMode) {
        // Apply crop when leaving crop mode or pressing crop again
        if (currentMode == StickerEditorView.EditorMode.CROP) {
            editorView.applyCrop()
        }
        currentMode = mode
        editorView.editorMode = mode

        textInputContainer.visibility = if (mode == StickerEditorView.EditorMode.TEXT) View.VISIBLE else View.GONE
        colorPickerContainer.visibility = if (mode == StickerEditorView.EditorMode.TEXT) View.VISIBLE else View.GONE
        filterStrip.visibility = if (mode == StickerEditorView.EditorMode.FILTER) View.VISIBLE else View.GONE

        val theme = ThemeStore.currentTheme()
        val primaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
        val textPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)

        listOf(btnCrop, btnText, btnFilters).forEach { btn ->
            btn.setTextColor(textPrimary)
            btn.iconTint = android.content.res.ColorStateList.valueOf(textPrimary)
        }

        val activeBtn = when (mode) {
            StickerEditorView.EditorMode.CROP -> btnCrop
            StickerEditorView.EditorMode.TEXT -> btnText
            StickerEditorView.EditorMode.FILTER -> btnFilters
        }
        activeBtn.setTextColor(primaryColor)
        activeBtn.iconTint = android.content.res.ColorStateList.valueOf(primaryColor)
    }

    private fun saveAndReturn() {
        lifecycleScope.launch {
            // getCroppedBitmap() handles crop mapping + square enforcement when in CROP mode.
            // Don't call applyCrop() here — it would replace the bitmap without square enforcement,
            // and switching to TEXT mode would skip the square crop in getCroppedBitmap().
            val bitmap = withContext(Dispatchers.Default) {
                editorView.getCroppedBitmap()
            }
            if (bitmap == null) {
                Toast.makeText(this@StickerEditorActivity, getString(R.string.sticker_editor_save_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val maxSize = 512
            val scaledBitmap = if (bitmap.width > maxSize || bitmap.height > maxSize) {
                val scale = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
                android.graphics.Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else bitmap

            val file = File(cacheDir, "sticker_edited_${System.currentTimeMillis()}.jpg")
            withContext(Dispatchers.IO) {
                FileOutputStream(file).use { out ->
                    scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                }
            }
            if (scaledBitmap !== bitmap) scaledBitmap.recycle()
            bitmap.recycle()

            val resultIntent = Intent().apply {
                putExtra(EXTRA_RESULT_URI, Uri.fromFile(file))
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        editorView.destroy()
    }

    companion object {
        const val EXTRA_IMAGE_URI = "sticker_image_uri"
        const val EXTRA_RESULT_URI = "sticker_result_uri"

        fun createIntent(context: Context, imageUri: Uri): Intent {
            return Intent(context, StickerEditorActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_URI, imageUri)
            }
        }
    }
}
