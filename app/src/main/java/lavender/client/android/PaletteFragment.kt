package lavender.client.android
import android.util.Log

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import lavender.client.android.ui.widget.StandardBottomSheet

class PaletteFragment : Fragment() {

    interface PaletteCallback {
        fun onColorChanged(fieldName: String, color: String)
        fun getCurrentColors(): Map<String, String>
        fun getDefaultColors(): Map<String, String>
    }

    private var callback: PaletteCallback? = null
    private lateinit var adapter: ColorAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_palette, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.paletteRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        adapter = ColorAdapter()
        recyclerView.adapter = adapter
        adapter.notifyDataSetChanged()
    }

    fun setCallback(cb: PaletteCallback) {
        callback = cb
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
    }

    fun refresh() {
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
    }

    private fun getColorItems(): List<ColorItem> {
        val currentColors = callback?.getCurrentColors() ?: return emptyList()

        val fields = listOf(
            Triple("primaryColor", R.string.color_primary, R.string.color_primary_desc),
            Triple("backgroundColor", R.string.color_background, R.string.color_background_desc),
            Triple("surfaceColor", R.string.color_surface, R.string.color_surface_desc),
            Triple("surfaceContainer", R.string.color_surface_container, R.string.color_surface_container_desc),
            Triple("textPrimaryColor", R.string.color_text_primary, R.string.color_text_primary_desc),
            Triple("textSecondaryColor", R.string.color_text_secondary, R.string.color_text_secondary_desc),
            Triple("onPrimaryColor", R.string.color_on_primary, R.string.color_on_primary_desc),
            Triple("onSurfaceColor", R.string.color_on_surface, R.string.color_on_surface_desc),
            Triple("bottomPanelColor", R.string.color_bottom_panel, R.string.color_bottom_panel_desc),
            Triple("onBottomPanelColor", R.string.color_on_bottom_panel, R.string.color_on_bottom_panel_desc),
            Triple("outgoingBubbleColor", R.string.color_outgoing_bubble, R.string.color_outgoing_bubble_desc),
            Triple("incomingBubbleColor", R.string.color_incoming_bubble, R.string.color_incoming_bubble_desc),
            Triple("outgoingTextColor", R.string.color_outgoing_text, R.string.color_outgoing_text_desc),
            Triple("incomingTextColor", R.string.color_incoming_text, R.string.color_incoming_text_desc)
        )

        return fields.mapNotNull { (fieldName, nameRes, descRes) ->
            val hex = currentColors[fieldName] ?: return@mapNotNull null
            ColorItem(getString(nameRes), hex, getString(descRes), fieldName)
        }
    }

    private fun showColorPicker(fieldName: String, currentColor: String) {
        val sheet = StandardBottomSheet(requireContext(), R.layout.dialog_color_picker)
        sheet.setTitle(getString(R.string.edit_color))

        val colorGrid = sheet.findViewById<android.widget.GridLayout>(R.id.colorGrid)
        val hexInput = sheet.findViewById<android.widget.EditText>(R.id.hexInput)
        val colorPreview = sheet.findViewById<View>(R.id.colorPreview)
        val transparencySlider = sheet.findViewById<com.google.android.material.slider.Slider>(R.id.transparencySlider)
        val btnApply = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnApply)
        val btnCancel = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnReset = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReset)

        val presetColors = listOf(
            "#FF0000", "#FF5722", "#FF9800", "#FFC107", "#FFEB3B", "#CDDC39",
            "#8BC34A", "#4CAF50", "#009688", "#00BCD4", "#03A9F4", "#2196F3",
            "#3F51B5", "#673AB7", "#9C27B0", "#E91E63", "#F44336", "#795548",
            "#9E9E9E", "#607D8B", "#FFFFFF", "#000000", "#2D2D2D", "#5F9EA0"
        )

        presetColors.forEach { colorHex ->
            val colorView = View(requireContext()).apply {
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = (40 * resources.displayMetrics.density).toInt()
                    height = (40 * resources.displayMetrics.density).toInt()
                    setMargins(8, 8, 8, 8)
                }
                setBackgroundColor(Color.parseColor(colorHex))
                setOnClickListener {
                    val currentAlpha = transparencySlider?.value?.toInt() ?: 255
                    val newHex = if (currentAlpha < 255) {
                        String.format("#%02X%s", currentAlpha, colorHex.removePrefix("#"))
                    } else {
                        colorHex
                    }
                    hexInput?.setText(newHex.uppercase())
                }
            }
            colorGrid?.addView(colorView)
        }

        hexInput?.setText(currentColor.uppercase())
        
        val initialAlpha = try {
            val c = Color.parseColor(currentColor)
            Color.alpha(c)
        } catch (_: Exception) { 255 }
        transparencySlider?.value = initialAlpha.toFloat()

        fun updatePreviewFromInput() {
            val hex = hexInput?.text.toString().trim()
            if (isValidHexColor(hex)) {
                try {
                    val color = Color.parseColor(hex)
                    colorPreview?.setBackgroundColor(color)
                    val alpha = Color.alpha(color)
                    if (transparencySlider?.value?.toInt() != alpha) {
                        transparencySlider?.value = alpha.toFloat()
                    }
                } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }
            }
        }

        hexInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updatePreviewFromInput()
            }
        })

        transparencySlider?.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val hex = hexInput?.text.toString().trim()
                if (isValidHexColor(hex)) {
                    val alpha = value.toInt()
                    val color = Color.parseColor(hex)
                    val r = Color.red(color)
                    val g = Color.green(color)
                    val b = Color.blue(color)
                    val newHex = String.format("#%02X%02X%02X%02X", alpha, r, g, b)
                    hexInput?.setText(newHex)
                }
            }
        }

        updatePreviewFromInput()

        btnApply?.setOnClickListener {
            val hex = hexInput?.text.toString().trim().uppercase()
            if (isValidHexColor(hex)) {
                callback?.onColorChanged(fieldName, hex)
                sheet.dismiss()
            } else {
                android.widget.Toast.makeText(requireContext(), R.string.invalid_hex, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel?.setOnClickListener { sheet.dismiss() }

        btnReset?.setOnClickListener {
            val defaultColors = callback?.getDefaultColors() ?: return@setOnClickListener
            val defaultColor = defaultColors[fieldName] ?: return@setOnClickListener
            callback?.onColorChanged(fieldName, defaultColor)
            sheet.dismiss()
        }

        sheet.show()
    }

    private fun isValidHexColor(hex: String): Boolean {
        return hex.matches(Regex("^#[0-9A-Fa-f]{6}$")) || hex.matches(Regex("^#[0-9A-Fa-f]{8}$"))
    }

    inner class ColorAdapter : RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {

        inner class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val colorPreview: MaterialCardView = itemView.findViewById(R.id.colorPreview)
            val colorName: TextView = itemView.findViewById(R.id.colorName)
            val colorValue: TextView = itemView.findViewById(R.id.colorValue)
            val colorDescription: TextView = itemView.findViewById(R.id.colorDescription)
            val settingsButton: ImageButton = itemView.findViewById(R.id.colorSettingsButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_color_palette, parent, false)
            return ColorViewHolder(view)
        }

        override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
            val item = getColorItems()[position]
            try {
                val color = item.colorHex.toColorInt()
                holder.colorPreview.setCardBackgroundColor(color)
                val isLight = isColorLight(color)
                holder.colorValue.setTextColor(if (isLight) Color.DKGRAY else Color.LTGRAY)
            } catch (_: Exception) {
                holder.colorPreview.setCardBackgroundColor(Color.GRAY)
            }
            holder.colorName.text = item.name
            holder.colorValue.text = item.colorHex.uppercase()
            holder.colorDescription.text = item.description

            holder.settingsButton.setOnClickListener {
                showColorPicker(item.fieldName, item.colorHex)
            }
        }

        override fun getItemCount(): Int = 14

        private fun isColorLight(color: Int): Boolean {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
            return luminance > 0.5
        }
    }
}
