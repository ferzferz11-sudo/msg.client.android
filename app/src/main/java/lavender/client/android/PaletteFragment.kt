package lavender.client.android

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

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

        return listOf(
            ColorItem(getString(R.string.color_primary), currentColors["primaryColor"]!!, getString(R.string.color_primary_desc), "primaryColor"),
            ColorItem(getString(R.string.color_background), currentColors["backgroundColor"]!!, getString(R.string.color_background_desc), "backgroundColor"),
            ColorItem(getString(R.string.color_surface), currentColors["surfaceColor"]!!, getString(R.string.color_surface_desc), "surfaceColor"),
            ColorItem(getString(R.string.color_surface_container), currentColors["surfaceContainer"]!!, getString(R.string.color_surface_container_desc), "surfaceContainer"),
            ColorItem(getString(R.string.color_text_primary), currentColors["textPrimaryColor"]!!, getString(R.string.color_text_primary_desc), "textPrimaryColor"),
            ColorItem(getString(R.string.color_on_primary), currentColors["onPrimaryColor"]!!, getString(R.string.color_on_primary_desc), "onPrimaryColor"),
            ColorItem(getString(R.string.color_on_surface), currentColors["onSurfaceColor"]!!, getString(R.string.color_on_surface_desc), "onSurfaceColor"),
            ColorItem(getString(R.string.color_bottom_panel), currentColors["bottomPanelColor"]!!, getString(R.string.color_bottom_panel_desc), "bottomPanelColor"),
            ColorItem(getString(R.string.color_on_bottom_panel), currentColors["onBottomPanelColor"]!!, getString(R.string.color_on_bottom_panel_desc), "onBottomPanelColor"),
            ColorItem(getString(R.string.color_outgoing_bubble), currentColors["outgoingBubbleColor"]!!, getString(R.string.color_outgoing_bubble_desc), "outgoingBubbleColor"),
            ColorItem(getString(R.string.color_incoming_bubble), currentColors["incomingBubbleColor"]!!, getString(R.string.color_incoming_bubble_desc), "incomingBubbleColor")
        )
    }

    private fun showColorPicker(fieldName: String, currentColor: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val colorGrid = dialogView.findViewById<android.widget.GridLayout>(R.id.colorGrid)
        val hexInput = dialogView.findViewById<android.widget.EditText>(R.id.hexInput)
        val colorPreview = dialogView.findViewById<View>(R.id.colorPreview)

        val presetColors = listOf(
            "#FF0000", "#FF5722", "#FF9800", "#FFC107", "#FFEB3B", "#CDDC39",
            "#8BC34A", "#4CAF50", "#009688", "#00BCD4", "#03A9F4", "#2196F3",
            "#3F51B5", "#673AB7", "#9C27B0", "#E91E63", "#F44336", "#795548",
            "#9E9E9E", "#607D8B", "#FFFFFF", "#000000", "#1A1B46", "#967BB6"
        )

        presetColors.forEach { colorHex ->
            val colorView = View(requireContext()).apply {
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 60
                    height = 60
                    setMargins(8, 8, 8, 8)
                }
                setBackgroundColor(Color.parseColor(colorHex))
                setOnClickListener {
                    hexInput.setText(colorHex)
                    colorPreview.setBackgroundColor(Color.parseColor(colorHex))
                }
            }
            colorGrid.addView(colorView)
        }

        hexInput.setText(currentColor.uppercase())
        colorPreview.setBackgroundColor(Color.parseColor(currentColor))

        hexInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val hex = s?.toString()?.trim() ?: return
                if (isValidHexColor(hex)) {
                    try {
                        colorPreview.setBackgroundColor(Color.parseColor(hex))
                    } catch (_: Exception) {}
                }
            }
        })

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.edit_color))
            .setView(dialogView)
            .setPositiveButton(R.string.yes) { _, _ ->
                val hex = hexInput.text.toString().trim().uppercase()
                if (isValidHexColor(hex)) {
                    callback?.onColorChanged(fieldName, hex)
                } else {
                    android.widget.Toast.makeText(requireContext(), R.string.invalid_hex, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .setNeutralButton(getString(R.string.reset_color)) { _, _ ->
                val defaultColors = callback?.getDefaultColors() ?: return@setNeutralButton
                val defaultColor = defaultColors[fieldName] ?: return@setNeutralButton
                callback?.onColorChanged(fieldName, defaultColor)
            }
            .show()
    }

    private fun isValidHexColor(hex: String): Boolean {
        return hex.matches(Regex("^#[0-9A-Fa-f]{6}$"))
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

        override fun getItemCount(): Int = 11

        private fun isColorLight(color: Int): Boolean {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
            return luminance > 0.5
        }
    }
}
