package lavender.client.android

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.core.view.updatePadding
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import lavender.client.android.ui.ThemeManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.*

class EditThemeActivity : AppCompatActivity() {

    private val grpcClient = GrpcClient
    private val okHttpClient = OkHttpClient()
    private var username: String = ""
    private var themeId: String? = null
    private var existingTheme: CustomThemeProto? = null
    private var backgroundImageUrl: String = ""
    private var chatListBackgroundImageUrl: String = ""

    private lateinit var editName: TextInputEditText
    private lateinit var bgImagePreview: ImageView
    private lateinit var bgImagePreviewContainer: View
    private lateinit var noBgImagePlaceholder: View
    private lateinit var btnSelectBackground: View
    private lateinit var btnDeleteBackground: View
    private lateinit var uploadProgress: ProgressBar

    private lateinit var chatListBgPreview: ImageView
    private lateinit var chatListBgPreviewContainer: View
    private lateinit var noChatListBgPlaceholder: View
    private lateinit var btnSelectChatListBg: View
    private lateinit var btnDeleteChatListBg: View
    private lateinit var uploadProgressChatList: ProgressBar
    private lateinit var btnSave: MaterialButton
    private lateinit var btnDelete: MaterialButton
    
    private lateinit var previewChatList: View
    private lateinit var previewChat: View
    private var userAvatarUrl: String = ""

    private val colorInputs = mutableMapOf<String, EditText>()
    private val colorPreviews = mutableMapOf<String, View>()

    private val pickBackgroundImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadBackgroundImage(it, false) }
    }
    
    private val pickChatListBgLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadBackgroundImage(it, true) }
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_theme)

        username = intent.getStringExtra("username") ?: ""
        themeId = intent.getStringExtra("theme_id")
        
        initViews()
        setupTabs()
        setupTemplates()
        
        if (themeId != null) {
            loadTheme()
            btnDelete.isVisible = true
        } else {
            applyTemplate(templates[0])
        }

        loadUserProfile()
        updateLivePreviews()
    }

    private fun loadUserProfile() {
        grpcClient.getUserProfile(username) { profile ->
            if (profile != null) {
                userAvatarUrl = profile.avatarUrl
                runOnUiThread { updateLivePreviews() }
            }
        }
    }

    private fun initViews() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // Handle window insets for edge-to-edge
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        editName = findViewById(R.id.editThemeName)
        bgImagePreview = findViewById(R.id.bgImagePreview)
        bgImagePreviewContainer = findViewById(R.id.bgImagePreviewContainer)
        noBgImagePlaceholder = findViewById(R.id.noBgImagePlaceholder)
        btnSelectBackground = findViewById(R.id.btnSelectBackground)
        btnDeleteBackground = findViewById(R.id.btnDeleteBackground)
        uploadProgress = findViewById(R.id.uploadProgress)
        
        chatListBgPreview = findViewById(R.id.chatListBgPreview)
        chatListBgPreviewContainer = findViewById(R.id.chatListBgPreviewContainer)
        noChatListBgPlaceholder = findViewById(R.id.noChatListBgPlaceholder)
        btnSelectChatListBg = findViewById(R.id.btnSelectChatListBg)
        btnDeleteChatListBg = findViewById(R.id.btnDeleteChatListBg)
        uploadProgressChatList = findViewById(R.id.uploadProgressChatList)
        
        btnSave = findViewById(R.id.btnSave)
        btnDelete = findViewById(R.id.btnDelete)
        
        previewChatList = findViewById(R.id.previewChatList)
        previewChat = findViewById(R.id.previewChat)

        setupColorInput("primary", R.id.layoutPrimary, getString(R.string.primary_color) + " (Toolbar)", "#312051")
        setupColorInput("background", R.id.layoutBackground, getString(R.string.background_color), "#FFFFFF")
        setupColorInput("surface", R.id.layoutSurface, getString(R.string.surface_color), "#F8F7FC")
        setupColorInput("textPrimary", R.id.layoutTextPrimary, getString(R.string.text_primary_color), "#000000")
        setupColorInput("onPrimary", R.id.layoutOnPrimary, getString(R.string.on_primary_color) + " (Icons/Text)", "#FFFFFF")
        setupColorInput("onSurface", R.id.layoutOnSurface, getString(R.string.on_surface_color), "#000000")
        setupColorInput("bottomPanel", R.id.layoutBottomPanel, "Bottom Panel Background", "#F8F7FC")
        setupColorInput("onBottomPanel", R.id.layoutOnBottomPanel, "Bottom Panel Icons", "#000000")

        btnSave.isVisible = false
        btnSave.setOnClickListener { saveTheme() }
        btnDelete.setOnClickListener { deleteTheme() }
        btnSelectBackground.setOnClickListener { pickBackgroundImageLauncher.launch("image/*") }
        btnDeleteBackground.setOnClickListener {
            backgroundImageUrl = ""
            updateBgImageUI()
            updateLivePreviews()
            checkChanges()
        }
        
        btnSelectChatListBg.setOnClickListener { pickChatListBgLauncher.launch("image/*") }
        btnDeleteChatListBg.setOnClickListener {
            chatListBackgroundImageUrl = ""
            updateBgImageUI()
            updateLivePreviews()
            checkChanges()
        }
        
        bgImagePreview.setOnClickListener {
            if (backgroundImageUrl.isNotEmpty()) showFullScreenImage(backgroundImageUrl)
        }
        
        chatListBgPreview.setOnClickListener {
            if (chatListBackgroundImageUrl.isNotEmpty()) showFullScreenImage(chatListBackgroundImageUrl)
        }

        editName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkChanges()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupTabs() {
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val tabMain = findViewById<View>(R.id.tabContentMain)
        val tabColors = findViewById<View>(R.id.tabContentColors)
        val tabBackground = findViewById<View>(R.id.tabContentBackground)

        tabLayout.addTab(tabLayout.newTab().setText(R.string.chats)) 
        tabLayout.addTab(tabLayout.newTab().setText(R.string.color_scheme))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.background_image_url))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { tabMain.isVisible = true; tabColors.isVisible = false; tabBackground.isVisible = false }
                    1 -> { tabMain.isVisible = false; tabColors.isVisible = true; tabBackground.isVisible = false }
                    2 -> { tabMain.isVisible = false; tabColors.isVisible = false; tabBackground.isVisible = true }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateBgImageUI() {
        if (backgroundImageUrl.isNotEmpty()) {
            bgImagePreviewContainer.isVisible = true
            noBgImagePlaceholder.isVisible = false
            btnDeleteBackground.isVisible = true
            Glide.with(this).load(backgroundImageUrl).centerCrop().into(bgImagePreview)
        } else {
            bgImagePreviewContainer.isVisible = false
            noBgImagePlaceholder.isVisible = true
            btnDeleteBackground.isVisible = false
        }
        
        if (chatListBackgroundImageUrl.isNotEmpty()) {
            chatListBgPreviewContainer.isVisible = true
            noChatListBgPlaceholder.isVisible = false
            btnDeleteChatListBg.isVisible = true
            Glide.with(this).load(chatListBackgroundImageUrl).centerCrop().into(chatListBgPreview)
        } else {
            chatListBgPreviewContainer.isVisible = false
            noChatListBgPlaceholder.isVisible = true
            btnDeleteChatListBg.isVisible = false
        }
    }

    private fun setupColorInput(key: String, layoutId: Int, label: String, default: String) {
        val layout = findViewById<View>(layoutId) ?: return
        val labelView = layout.findViewById<TextView>(R.id.colorLabel)
        val input = layout.findViewById<EditText>(R.id.colorInput)
        val preview = layout.findViewById<View>(R.id.colorPreview)

        labelView.text = label
        input.setText(default)
        
        colorInputs[key] = input
        colorPreviews[key] = preview

        val updateColor = { hex: String ->
            try {
                if (hex.startsWith("#") && (hex.length == 7 || hex.length == 9)) {
                    val color = hex.toColorInt()
                    preview.backgroundTintList = ColorStateList.valueOf(color)
                    updateLivePreviews()
                }
            } catch (_: Exception) {}
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateColor(s.toString())
                checkChanges()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        preview.setOnClickListener {
            showColorPickerDialog(input.text.toString()) { selectedHex ->
                input.setText(selectedHex)
            }
        }
    }

    private fun showColorPickerDialog(initialColor: String, onColorSelected: (String) -> Unit) {
        val colors = listOf(
            "#312051", "#967BB6", "#E6E6FA", "#F8F7FC", "#FFFFFF", "#000000",
            "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3",
            "#03A9F4", "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
            "#FFEB3B", "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E", "#607D8B"
        )

        val gridView = GridView(this).apply {
            numColumns = 5
            verticalSpacing = 16
            horizontalSpacing = 16
            setPadding(32, 32, 32, 32)
            adapter = object : BaseAdapter() {
                override fun getCount(): Int = colors.size
                override fun getItem(position: Int): Any = colors[position]
                override fun getItemId(position: Int): Long = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    return View(this@EditThemeActivity).apply {
                        layoutParams = AbsListView.LayoutParams(100, 100)
                        background = ContextCompat.getDrawable(this@EditThemeActivity, R.drawable.circle_indicator)
                        backgroundTintList = ColorStateList.valueOf(colors[position].toColorInt())
                    }
                }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.color_scheme)
            .setView(gridView)
            .create()

        gridView.setOnItemClickListener { _, _, position, _ ->
            onColorSelected(colors[position])
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadTheme() {
        grpcClient.getThemes(username) { _, themes ->
            val theme = themes.find { it.id == themeId }
            if (theme != null) {
                existingTheme = theme
                runOnUiThread {
                    editName.setText(theme.name)
                    colorInputs["primary"]?.setText(theme.primaryColor)
                    colorInputs["background"]?.setText(theme.backgroundColor)
                    colorInputs["surface"]?.setText(theme.surfaceColor)
                    colorInputs["textPrimary"]?.setText(theme.textPrimaryColor)
                    colorInputs["onPrimary"]?.setText(theme.onPrimaryColor)
                    colorInputs["onSurface"]?.setText(theme.onSurfaceColor)
                    colorInputs["bottomPanel"]?.setText(theme.bottomPanelColor)
                    colorInputs["onBottomPanel"]?.setText(theme.onBottomPanelColor)
                    backgroundImageUrl = theme.backgroundImageUrl
                    chatListBackgroundImageUrl = theme.chatListBackgroundImageUrl
                    updateBgImageUI()
                    updateLivePreviews()
                }
            }
        }
    }

    private fun getCurrentThemeFromInputs(): CustomThemeProto {
        return CustomThemeProto(
            id = themeId ?: UUID.randomUUID().toString(),
            name = editName.text.toString().trim(),
            primaryColor = colorInputs["primary"]?.text.toString().ifEmpty { "#312051" },
            onPrimaryColor = colorInputs["onPrimary"]?.text.toString().ifEmpty { "#FFFFFF" },
            surfaceColor = colorInputs["surface"]?.text.toString().ifEmpty { "#F8F7FC" },
            onSurfaceColor = colorInputs["onSurface"]?.text.toString().ifEmpty { "#000000" },
            bottomPanelColor = colorInputs["bottomPanel"]?.text.toString().ifEmpty { "#F8F7FC" },
            onBottomPanelColor = colorInputs["onBottomPanel"]?.text.toString().ifEmpty { "#000000" },
            backgroundColor = colorInputs["background"]?.text.toString().ifEmpty { "#FFFFFF" },
            textPrimaryColor = colorInputs["textPrimary"]?.text.toString().ifEmpty { "#000000" },
            textSecondaryColor = colorInputs["textPrimary"]?.text.toString().ifEmpty { "#000000" }, 
            isDark = false, 
            backgroundImageUrl = backgroundImageUrl,
            chatListBackgroundImageUrl = chatListBackgroundImageUrl
        )
    }

    private fun updateLivePreviews() {
        val theme = getCurrentThemeFromInputs()
        applyThemeToPreview(previewChatList, theme)
        applyThemeToPreview(previewChat, theme)
        
        // Chat screen background image
        val bgImage = previewChat.findViewById<ImageView>(R.id.previewChatBgImage)
        if (theme.backgroundImageUrl.isNotEmpty()) {
            Glide.with(this).load(theme.backgroundImageUrl).centerCrop().into(bgImage)
            previewChat.findViewById<View>(R.id.previewChatRoot).setBackgroundColor(Color.TRANSPARENT)
        } else {
            bgImage.setImageDrawable(null)
            try {
                previewChat.findViewById<View>(R.id.previewChatRoot).setBackgroundColor(theme.backgroundColor.toColorInt())
            } catch (_: Exception) {}
        }

        // Chat List specific background image
        val chatListBg = previewChatList.findViewById<ImageView>(R.id.previewChatListBgImage)
        if (theme.chatListBackgroundImageUrl.isNotEmpty()) {
            chatListBg?.isVisible = true
            Glide.with(this).load(theme.chatListBackgroundImageUrl).centerCrop().into(chatListBg)
            previewChatList.findViewById<View>(R.id.previewRoot).setBackgroundColor(Color.TRANSPARENT)
        } else {
            chatListBg?.isVisible = false
            try {
                previewChatList.findViewById<View>(R.id.previewRoot).setBackgroundColor(theme.backgroundColor.toColorInt())
            } catch (_: Exception) {}
        }
    }

    private fun applyThemeToPreview(root: View, theme: CustomThemeProto) {
        try {
            val primary = theme.primaryColor.toColorInt()
            val background = theme.backgroundColor.toColorInt()
            val onSurface = theme.onSurfaceColor.toColorInt()
            val surface = theme.surfaceColor.toColorInt()
            val bpColor = theme.bottomPanelColor.toColorInt()
            val onBpColor = theme.onBottomPanelColor.toColorInt()
            val textPrimary = theme.textPrimaryColor.toColorInt()
            val onPrimary = theme.onPrimaryColor.toColorInt()
            
            // Root backgrounds
            root.findViewById<View>(R.id.previewRoot)?.setBackgroundColor(background)
            root.findViewById<View>(R.id.previewChatRoot)?.setBackgroundColor(background)
            
            // Toolbars
            root.findViewById<View>(R.id.previewToolbar)?.setBackgroundColor(primary)
            root.findViewById<View>(R.id.previewChatToolbar)?.setBackgroundColor(primary)
            
            // Cards and bubbles
            root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.previewChatCard)?.setCardBackgroundColor(surface)
            root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.previewIncomingBubble)?.setCardBackgroundColor(surface)
            root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.previewOutgoingBubble)?.setCardBackgroundColor(primary)
            
            // Text colors
            root.findViewById<TextView>(R.id.previewTextName)?.setTextColor(textPrimary)
            root.findViewById<TextView>(R.id.previewTextMsg)?.setTextColor(onSurface)
            root.findViewById<TextView>(R.id.previewIncomingText)?.setTextColor(textPrimary)
            root.findViewById<TextView>(R.id.previewOutgoingText)?.setTextColor(onPrimary)
            
            // Toolbar Text and Icons
            root.findViewById<TextView>(R.id.previewToolbarTitle)?.setTextColor(onPrimary)
            root.findViewById<ImageView>(R.id.previewToolbarSearch)?.setColorFilter(onPrimary)
            root.findViewById<ImageView>(R.id.previewToolbarMore)?.setColorFilter(onPrimary)
            root.findViewById<ImageView>(R.id.previewChatBack)?.setColorFilter(onPrimary)

            root.findViewById<ImageView>(R.id.previewToolbarAvatar)?.let { avatarView ->
                if (userAvatarUrl.isNotEmpty()) {
                    Glide.with(this).load(userAvatarUrl).placeholder(R.drawable.ic_default_avatar).circleCrop().into(avatarView)
                } else {
                    avatarView.setImageResource(R.drawable.ic_default_avatar)
                }
            }
            
            // FAB
            val fab = root.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.previewFab)
            fab?.backgroundTintList = android.content.res.ColorStateList.valueOf(primary)
            fab?.imageTintList = android.content.res.ColorStateList.valueOf(onPrimary)

            val chatTitle = root.findViewById<TextView>(R.id.previewChatTitle)
            if (chatTitle != null) {
                chatTitle.text = username
                chatTitle.setTextColor(onPrimary)
            }
            
            val chatAvatar = root.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.previewChatAvatar)
            if (chatAvatar != null) {
                if (userAvatarUrl.isNotEmpty()) {
                    Glide.with(this).load(userAvatarUrl).placeholder(R.drawable.ic_default_avatar).circleCrop().into(chatAvatar)
                } else {
                    chatAvatar.setImageResource(R.drawable.ic_default_avatar)
                }
            }

            root.findViewById<ImageView>(R.id.previewChatBack)?.setColorFilter(onPrimary)
            
            // Bottom Panel Preview
            root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.previewBottomPanel)?.setCardBackgroundColor(bpColor)
            root.findViewById<ImageView>(R.id.previewEmojiButton)?.setColorFilter(onBpColor)
            root.findViewById<ImageView>(R.id.previewAttachButton)?.setColorFilter(onBpColor)
            root.findViewById<ImageView>(R.id.previewAudioButton)?.setColorFilter(onBpColor)
            root.findViewById<ImageView>(R.id.previewSendButton)?.setColorFilter(primary)
            root.findViewById<TextView>(R.id.previewInputPlaceholder)?.setTextColor(onBpColor)
        } catch (_: Exception) {}
    }

    private fun isColorLight(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness < 0.5
    }

    private fun checkChanges() {
        val current = getCurrentThemeFromInputs()
        val original = existingTheme

        if (original == null) {
            // New theme: show save if name is not empty
            btnSave.isVisible = current.name.isNotEmpty()
            return
        }

        // Compare all fields
        val hasChanges = current.name != original.name ||
                current.primaryColor != original.primaryColor ||
                current.backgroundColor != original.backgroundColor ||
                current.surfaceColor != original.surfaceColor ||
                current.textPrimaryColor != original.textPrimaryColor ||
                current.onPrimaryColor != original.onPrimaryColor ||
                current.onSurfaceColor != original.onSurfaceColor ||
                current.bottomPanelColor != original.bottomPanelColor ||
                current.onBottomPanelColor != original.onBottomPanelColor ||
                current.backgroundImageUrl != original.backgroundImageUrl ||
                current.chatListBackgroundImageUrl != original.chatListBackgroundImageUrl

        btnSave.isVisible = hasChanges
    }

    private fun saveTheme() {
        val name = editName.text.toString().trim()
        if (name.isEmpty()) {
            editName.error = getString(R.string.username_empty)
            return
        }

        val theme = getCurrentThemeFromInputs()
        val overlay = findViewById<View>(R.id.progressOverlay)
        overlay.isVisible = true
        
        grpcClient.saveTheme(username, theme) { success, msg ->
            runOnUiThread {
                overlay.isVisible = false
                if (success) {
                    Toast.makeText(this, R.string.save_theme, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun deleteTheme() {
        val id = themeId ?: return
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_theme_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                val overlay = findViewById<View>(R.id.progressOverlay)
                overlay.isVisible = true
                grpcClient.deleteTheme(username, id) { success ->
                    runOnUiThread {
                        overlay.isVisible = false
                        if (success) finish()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showFullScreenImage(imageUrl: String) {
        val intent = Intent(this, FullScreenImageActivity::class.java).apply {
            putExtra("image_url", imageUrl)
        }
        startActivity(intent)
    }

    private fun uploadBackgroundImage(uri: Uri, isChatList: Boolean) {
        val progressBar = if (isChatList) uploadProgressChatList else uploadProgress
        val selectButton = if (isChatList) btnSelectChatListBg else btnSelectBackground
        
        progressBar.isVisible = true
        selectButton.isVisible = false
        
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: return@launch

                val fileName = if (isChatList) "chat_list_bg_${System.currentTimeMillis()}.jpg" else "theme_bg_${System.currentTimeMillis()}.jpg"
                val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("background", fileName, bytes.toRequestBody("image/*".toMediaTypeOrNull()))
                    .build()

                val request = Request.Builder()
                    .url("http://159.195.38.145:8082/upload-background")
                    .post(requestBody)
                    .build()

                val response = withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    val fileUrl = JSONObject(responseBody).getString("url")
                    withContext(Dispatchers.Main) {
                        if (isChatList) {
                            chatListBackgroundImageUrl = fileUrl
                        } else {
                            backgroundImageUrl = fileUrl
                        }
                        updateBgImageUI()
                        updateLivePreviews()
                        checkChanges()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@EditThemeActivity, "Upload failed: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditThemeActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    progressBar.isVisible = false
                    selectButton.isVisible = true
                }
            }
        }
    }

    private data class ThemeTemplate(
        val nameRes: Int,
        val primary: String,
        val background: String,
        val surface: String,
        val text: String,
        val onPrimary: String,
        val onSurface: String,
        val bottomPanel: String,
        val onBottomPanel: String,
        val isDark: Boolean
    )

    private val templates = listOf(
        ThemeTemplate(R.string.theme_template_green, "#2E7D32", "#F8FAF5", "#EEF7E2", "#144218", "#FFFFFF", "#33691E", "#FFFFFF", "#2E7D32", false),
        ThemeTemplate(R.string.theme_template_blue, "#007AFF", "#E3F2FD", "#FFFFFF", "#1C1C1E", "#FFFFFF", "#3A3A3C", "#E3F2FD", "#1565C0", false),
        ThemeTemplate(R.string.theme_template_purple, "#6A1B9A", "#FBF8FF", "#F0E2F5", "#2D0C54", "#FFFFFF", "#4A148C", "#FFFFFF", "#6A1B9A", false),
        ThemeTemplate(R.string.theme_template_sunset, "#D84315", "#FFF3E0", "#FFE0B2", "#BF360C", "#FFFFFF", "#E65100", "#FFF3E0", "#D84315", false)
    )

    private fun setupTemplates() {
        val container = findViewById<LinearLayout>(R.id.templatesContainer)
        for (tmpl in templates) {
            val chip = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 8, 0) }
                text = getString(tmpl.nameRes)
                textSize = 12f
                cornerRadius = (20 * resources.displayMetrics.density).toInt()
                setOnClickListener { applyTemplate(tmpl) }
            }
            container.addView(chip)
        }
    }

    private fun applyTemplate(tmpl: ThemeTemplate) {
        editName.setText(getString(tmpl.nameRes))
        colorInputs["primary"]?.setText(tmpl.primary)
        colorInputs["background"]?.setText(tmpl.background)
        colorInputs["surface"]?.setText(tmpl.surface)
        colorInputs["textPrimary"]?.setText(tmpl.text)
        colorInputs["onPrimary"]?.setText(tmpl.onPrimary)
        colorInputs["onSurface"]?.setText(tmpl.onSurface)
        colorInputs["bottomPanel"]?.setText(tmpl.bottomPanel)
        colorInputs["onBottomPanel"]?.setText(tmpl.onBottomPanel)
        updateLivePreviews()
        checkChanges()
    }
}
