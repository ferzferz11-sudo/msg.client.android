package lavender.client.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import lavender.client.android.ui.chatlist.ChatListActivity
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.ProtoUtils
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.adapter.SelectableUserAdapter
import lavender.client.android.ui.adapter.ParticipantAdapter
import lavender.client.android.ui.profile.ProfileViewModel
import java.util.Locale
import androidx.core.graphics.toColorInt
import androidx.appcompat.app.AlertDialog

import lavender.client.android.ui.widget.SearchableListBottomSheet

class ProfileActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    private val grpcClient = GrpcClient
    private lateinit var viewModel: ProfileViewModel
    private var isGroup: Boolean = false
    private var roomId: String = ""
    private var intentParticipants: String = ""
    private var intentCreator: String = ""
    private var intentAvatarUrl: String = ""
    private var intentFullAvatarUrl: String = ""
    private var intentChatName: String = ""
    private var selectedAvatarUri: Uri? = null
    private var currentProfileAvatar: CircleImageView? = null
    private var participantsAdapter: ParticipantAdapter? = null
    private var participantsBottomSheet: SearchableListBottomSheet? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedAvatarUri = uri
                uploadGroupAvatar(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val currentMe = prefs.getString("username", "") ?: ""
        ThemeUi.bind(this, currentMe)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]

        val username = intent.getStringExtra("username") ?: ""
        isGroup = intent.getBooleanExtra("is_group", false)
        roomId = intent.getStringExtra("room_id") ?: ""
        intentParticipants = intent.getStringExtra("participants") ?: ""
        intentCreator = intent.getStringExtra("creator") ?: ""
        intentAvatarUrl = intent.getStringExtra("avatar_url") ?: ""
        intentFullAvatarUrl = intent.getStringExtra("full_avatar_url") ?: ""
        intentChatName = intent.getStringExtra("chat_name") ?: ""

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (isGroup) getString(R.string.group_info) else getString(R.string.profile)
        toolbar.setNavigationOnClickListener { finish() }

        if (isGroup) {
            viewModel.loadGroupData(roomId, intentParticipants, intentCreator, intentAvatarUrl, intentFullAvatarUrl, intentChatName)
            setupGroupObservers()
        } else {
            viewModel.loadUserProfile(username)
            setupProfileObservers()
        }
        viewModel.observeOnlineUsers()

        lifecycleScope.launch {
            grpcClient.users.collect { runOnUiThread { refreshCurrentView() } }
        }
    }

    override fun onResume() {
        super.onResume()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false
        if (isGroup && roomId.isNotEmpty()) {
            val data = viewModel.groupData.value
            viewModel.loadGroupData(
                roomId,
                intentParticipants.ifEmpty { try { org.json.JSONArray(data.participants).toString() } catch (_: Exception) { "" } },
                intentCreator.ifEmpty { data.creator },
                intentAvatarUrl.ifEmpty { data.avatarUrl },
                intentFullAvatarUrl.ifEmpty { data.fullAvatarUrl },
                intentChatName.ifEmpty { data.name }
            )
        }
    }

    override fun onPause() {
        super.onPause()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = true
    }

    private fun setupProfileObservers() {
        lifecycleScope.launch {
            viewModel.profileData.collect { data ->
                runOnUiThread {
                    if (isFinishing || isDestroyed || data.username.isEmpty()) return@runOnUiThread
                    updateProfileUI(data)
                }
            }
        }
    }

    private fun setupGroupObservers() {
        lifecycleScope.launch {
            viewModel.groupData.collect { data ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    updateGroupUI(data)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateProfileUI(data: ProfileViewModel.ProfileData) {
        val profileAvatar = findViewById<CircleImageView>(R.id.profileAvatar) ?: return
        val profileName = findViewById<TextView>(R.id.profileName) ?: return
        val profileBio = findViewById<TextView>(R.id.profileBio) ?: return
        val profileStatus = findViewById<TextView>(R.id.profileStatus) ?: return
        val bioCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.bioCard)
        val groupSettingsCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.groupSettingsCard)
        val companyCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.companyCard)
        val tvProfileCompanyName = findViewById<TextView>(R.id.tvProfileCompanyName)
        val tvProfileCompanyPosition = findViewById<TextView>(R.id.tvProfileCompanyPosition)

        currentProfileAvatar = profileAvatar
        profileName.text = data.username
        val currentTheme = ThemeStore.currentTheme()
        val textPrimaryColor = ThemeUtils.parseSafeColor(currentTheme.textPrimaryColor, android.graphics.Color.BLACK)
        val primaryColor = ThemeUtils.parseSafeColor(currentTheme.primaryColor, android.graphics.Color.BLUE)
        profileName.setTextColor(textPrimaryColor)
        profileBio.setTextColor(textPrimaryColor)
        profileAvatar.borderColor = primaryColor
        profileAvatar.borderWidth = (2 * resources.displayMetrics.density).toInt()

        profileStatus.isVisible = false
        bioCard?.isVisible = true
        groupSettingsCard?.isVisible = false

        profileBio.text = data.bio.ifEmpty { getString(R.string.no_bio) }
        if (data.isOnline || data.username == grpcClient.getCurrentUsername()) {
            profileStatus.text = getString(R.string.connected)
            profileStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            profileStatus.text = if (data.lastSeenAt != null) ProtoUtils.formatLastSeen(data.lastSeenAt, this) else data.status.ifEmpty { getString(R.string.offline) }
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
            profileStatus.setTextColor(typedValue.data)
        }
        profileStatus.isVisible = true

        // Company section
        if (data.companyId.isNotEmpty() && companyCard != null) {
            companyCard.isVisible = true
            tvProfileCompanyName?.text = data.companyName
            tvProfileCompanyPosition?.text = getString(R.string.company_position, data.positionTitle, data.positionLevel)
            companyCard.setOnClickListener {
                val intent = Intent(this, CompanyProfileActivity::class.java).apply {
                    putExtra("COMPANY_ID", data.companyId)
                }
                startActivity(intent)
            }
        } else if (companyCard != null) {
            companyCard.isVisible = false
        }

        if (data.avatarUrl.isNotEmpty()) {
            Glide.with(this).load(data.avatarUrl).placeholder(R.drawable.ic_default_avatar).into(profileAvatar)
            profileAvatar.imageTintList = null
            setupAvatarClickListener(profileAvatar, data.fullAvatarUrl.ifEmpty { data.avatarUrl })
        } else {
            ThemeUtils.applyDefaultAvatar(profileAvatar, currentTheme)
            profileAvatar.setOnClickListener(null)
        }

        bioCard?.setCardBackgroundColor(ColorStateList.valueOf(currentTheme.surfaceColor.toColorInt()))
        bioCard?.strokeColor = ThemeUtils.adjustAlpha(currentTheme.onSurfaceColor.toColorInt(), 0.2f)
        applyThemeToView(findViewById(android.R.id.content), currentTheme)
    }

    @SuppressLint("SetTextI18n")
    private fun updateGroupUI(data: ProfileViewModel.GroupData) {
        val profileAvatar = findViewById<CircleImageView>(R.id.profileAvatar) ?: return
        val profileName = findViewById<TextView>(R.id.profileName) ?: return
        val profileStatus = findViewById<TextView>(R.id.profileStatus) ?: return
        val bioCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.bioCard)
        val groupSettingsCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.groupSettingsCard)
        val switchAllowAdd = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchAllowAdd)
        val participantsCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.participantsCard)
        val participantsCountText = findViewById<TextView>(R.id.participantsCountText)
        val deleteGroupButton = findViewById<MaterialButton>(R.id.deleteGroupButton)
        val changeAvatarButton = findViewById<View>(R.id.changeAvatarButton)

        currentProfileAvatar = profileAvatar
        val currentTheme = ThemeStore.currentTheme()
        val textPrimaryColor = ThemeUtils.parseSafeColor(currentTheme.textPrimaryColor, android.graphics.Color.BLACK)
        val primaryColor = ThemeUtils.parseSafeColor(currentTheme.primaryColor, android.graphics.Color.BLUE)

        profileName.text = data.name
        profileName.setTextColor(textPrimaryColor)
        profileAvatar.borderColor = primaryColor
        profileAvatar.borderWidth = (2 * resources.displayMetrics.density).toInt()

        profileStatus.isVisible = false
        bioCard?.isVisible = false
        groupSettingsCard?.isVisible = true

        val currentMe = grpcClient.getCurrentUsername() ?: ""
        val isMeAdmin = currentMe == data.creator && data.creator.isNotEmpty()

        if (groupSettingsCard != null && switchAllowAdd != null) {
            groupSettingsCard.isVisible = isMeAdmin
            switchAllowAdd.isEnabled = isMeAdmin
            switchAllowAdd.setOnCheckedChangeListener(null)
            switchAllowAdd.isChecked = data.allowMembersToAdd
            if (isMeAdmin) {
                switchAllowAdd.setOnCheckedChangeListener { _, isChecked ->
                    val progressOverlay = findViewById<View>(R.id.progressOverlay)
                    progressOverlay?.isVisible = true
                    viewModel.updateChatSettings(roomId, isChecked) { success, msg ->
                        runOnUiThread {
                            progressOverlay?.isVisible = false
                            if (success) Toast.makeText(this, R.string.theme_saved, Toast.LENGTH_SHORT).show()
                            else { switchAllowAdd.isChecked = !isChecked; Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
            }
            groupSettingsCard.setCardBackgroundColor(ColorStateList.valueOf(currentTheme.surfaceColor.toColorInt()))
            groupSettingsCard.strokeColor = ThemeUtils.adjustAlpha(currentTheme.onSurfaceColor.toColorInt(), 0.2f)
        }

        if (isMeAdmin) {
            profileName.setOnClickListener {
                val editName = EditText(this).apply { setText(data.name); setSelection(data.name.length) }
                AlertDialog.Builder(this).setTitle(R.string.edit_message).setView(editName)
                    .setPositiveButton(R.string.change) { _, _ ->
                        val newName = editName.text.toString().trim()
                        if (newName.isNotEmpty() && newName != data.name) {
                            val progressOverlay = findViewById<View>(R.id.progressOverlay)
                            progressOverlay.isVisible = true
                            viewModel.updateChatName(roomId, newName) { success, msg ->
                                runOnUiThread { progressOverlay.isVisible = false; if (!success) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                            }
                        }
                    }.setNegativeButton(R.string.cancel, null).show()
            }
            changeAvatarButton?.isVisible = true
            changeAvatarButton?.setOnClickListener { pickImageLauncher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)) }
        } else {
            profileName.setOnClickListener(null)
            changeAvatarButton?.isVisible = false
        }

        participantsCard?.isVisible = true
        participantsCountText?.text = data.participants.size.toString()
        participantsCard?.setOnClickListener { showParticipantsBottomSheet(data) }

        deleteGroupButton?.apply {
            isVisible = isMeAdmin
            if (isMeAdmin) {
                setOnClickListener {
                    AlertDialog.Builder(this@ProfileActivity).setTitle(R.string.delete_group).setMessage(R.string.delete_group_confirm)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            val intent = Intent(this@ProfileActivity, ChatListActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP; putExtra("START_DELETION_ID", roomId)
                            }; startActivity(intent); finish()
                        }.setNegativeButton(R.string.cancel, null).show()
                }
            }
        }

        participantsCard?.setCardBackgroundColor(ColorStateList.valueOf(currentTheme.surfaceColor.toColorInt()))
        participantsCard?.strokeColor = ThemeUtils.adjustAlpha(currentTheme.onSurfaceColor.toColorInt(), 0.2f)
        findViewById<TextView>(R.id.participantsTitle)?.setTextColor(currentTheme.textPrimaryColor.toColorInt())
        findViewById<android.widget.ImageView>(R.id.participantsArrow)?.imageTintList = ColorStateList.valueOf(currentTheme.onSurfaceColor.toColorInt())

        if (data.avatarUrl.isNotEmpty()) {
            Glide.with(this).load(data.avatarUrl).placeholder(R.drawable.ic_default_avatar).into(profileAvatar)
            profileAvatar.imageTintList = null
            setupAvatarClickListener(profileAvatar, data.fullAvatarUrl.ifEmpty { data.avatarUrl })
        } else {
            ThemeUtils.applyDefaultAvatar(profileAvatar, currentTheme)
            profileAvatar.setOnClickListener(null)
        }

        applyThemeToView(findViewById(android.R.id.content), currentTheme)
    }

    private fun setupAvatarClickListener(profileAvatar: CircleImageView, fullImageUrl: String) {
        profileAvatar.setOnClickListener {
            if (fullImageUrl.isNotEmpty()) {
                val intent = Intent(this, FullScreenImageActivity::class.java).apply { putExtra("image_url", fullImageUrl) }
                startActivity(intent)
            }
        }
    }

    private fun showParticipantsBottomSheet(data: ProfileViewModel.GroupData) {
        val currentTheme = ThemeStore.currentTheme()
        val currentMe = grpcClient.getCurrentUsername() ?: ""
        val isMeAdmin = currentMe == data.creator && data.creator.isNotEmpty()

        val sheet = SearchableListBottomSheet(this, currentTheme)
            .setTitle(getString(R.string.participants))
            .setActionButtonText(getString(R.string.add))
            .setExtraInputVisible(false)

        if (participantsAdapter == null) {
            participantsAdapter = ParticipantAdapter(
                theme = currentTheme, isAdmin = isMeAdmin, creator = data.creator,
                onRemoveClick = { user -> showRemoveParticipantDialog(user) },
                onAvatarClick = { user, url -> showFullScreenImage(grpcClient.getFullAvatarUrl(user) ?: url) },
                onLongClick = { user -> if (isMeAdmin && user != data.creator) showRemoveParticipantDialog(user) }
            )
        }
        sheet.setAdapter(participantsAdapter!!)
        participantsAdapter?.updateData(data.participants, grpcClient.users.value.toSet(), grpcClient.getAvatarCache())

        sheet.onSearchTextChanged { query ->
            val q = query.lowercase()
            participantsAdapter?.updateData(data.participants.filter { it.lowercase().contains(q) }, grpcClient.users.value.toSet(), grpcClient.getAvatarCache())
        }

        val canAdd = isMeAdmin || data.allowMembersToAdd
        sheet.setActionButtonEnabled(canAdd)
        sheet.actionButton?.isVisible = canAdd

        sheet.onActionClick {
            viewModel.getAvailableContacts { available ->
                runOnUiThread {
                    if (available.isEmpty()) Toast.makeText(this, R.string.no_users_available, Toast.LENGTH_SHORT).show()
                    else showAddParticipantDialog(available)
                }
            }
        }

        participantsBottomSheet = sheet
        sheet.setOnDismissListener {
            participantsBottomSheet = null
            participantsAdapter?.updateData(data.participants, grpcClient.users.value.toSet(), grpcClient.getAvatarCache())
        }
        sheet.show()
    }

    private fun showRemoveParticipantDialog(user: String) {
        AlertDialog.Builder(this).setTitle(R.string.remove).setMessage(getString(R.string.remove_participant_confirm, user))
            .setPositiveButton(R.string.remove) { _, _ ->
                val progressOverlay = findViewById<View>(R.id.progressOverlay)
                progressOverlay?.isVisible = true
                viewModel.removeParticipant(roomId, user) { success, msg ->
                    runOnUiThread { progressOverlay?.isVisible = false; if (!success) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showAddParticipantDialog(contacts: List<String>) {
        val sheet = SearchableListBottomSheet(this).setTitle(getString(R.string.add_participants)).setActionButtonText(getString(R.string.add)).setExtraInputVisible(false)
        val selectableAdapter = SelectableUserAdapter(lifecycleScope, avatarCache = grpcClient.getAvatarCache(),
            onSelectionChanged = { count -> sheet.setActionButtonEnabled(count > 0); sheet.setActionButtonText(if (count > 0) "${getString(R.string.add)} ($count)" else getString(R.string.add)) })
        sheet.setAdapter(selectableAdapter)
        selectableAdapter.setUsers(contacts)
        sheet.onSearchTextChanged { query -> selectableAdapter.setUsers(contacts.filter { it.lowercase().contains(query.lowercase()) }) }
        sheet.onActionClick {
            val selected = selectableAdapter.getSelectedUsers()
            if (selected.isEmpty()) return@onActionClick
            val progressOverlay = findViewById<View>(R.id.progressOverlay)
            sheet.dismiss(); progressOverlay.isVisible = true
            viewModel.addParticipants(roomId, selected) { success, msg ->
                runOnUiThread { progressOverlay.isVisible = false; if (!success) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
            }
        }
        sheet.show()
    }

    private fun showFullScreenImage(imageUrl: String) {
        if (imageUrl.isNotEmpty()) startActivity(Intent(this, FullScreenImageActivity::class.java).apply { putExtra("image_url", imageUrl) })
    }

    private fun uploadGroupAvatar(uri: Uri) {
        val progressOverlay = findViewById<View>(R.id.progressOverlay)
        progressOverlay?.isVisible = true
        viewModel.uploadGroupAvatar(this, roomId, uri) { result ->
            runOnUiThread {
                progressOverlay?.isVisible = false
                if (result.thumbUrl.isNotEmpty()) Toast.makeText(this, R.string.theme_saved, Toast.LENGTH_SHORT).show()
                else if (result.error.isNotEmpty()) Toast.makeText(this, result.error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshCurrentView() {
        if (isGroup) {
            val data = viewModel.groupData.value
            if (data.participants.isNotEmpty()) {
                participantsAdapter?.updateData(data.participants, grpcClient.users.value.toSet(), grpcClient.getAvatarCache())
            }
        }
    }

    private fun applyThemeToView(view: View, theme: lavender.client.android.theme.Theme) {
        val textPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, android.graphics.Color.BLACK)
        val onSurface = ThemeUtils.parseSafeColor(theme.onSurfaceColor, android.graphics.Color.GRAY)
        val primary = ThemeUtils.parseSafeColor(theme.primaryColor, android.graphics.Color.BLUE)

        when (view) {
            is MaterialButton -> {
                view.isAllCaps = false; view.transformationMethod = null
                view.cornerRadius = (28 * resources.displayMetrics.density).toInt()
                view.minimumHeight = (56 * resources.displayMetrics.density).toInt()
                val isCancelType = view.id == R.id.deleteGroupButton
                if (isCancelType) {
                    view.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                    view.strokeColor = ColorStateList.valueOf(primary); view.strokeWidth = (1 * resources.displayMetrics.density).toInt()
                    view.setTextColor(primary); view.rippleColor = ColorStateList.valueOf(ThemeUtils.adjustAlpha(primary, 0.1f))
                } else {
                    view.backgroundTintList = ColorStateList.valueOf(ThemeUtils.adjustAlpha(primary, 0.7f))
                    val onP = ThemeUtils.parseSafeColor(theme.onPrimaryColor, android.graphics.Color.WHITE)
                    view.setTextColor(onP); view.rippleColor = ColorStateList.valueOf(ThemeUtils.adjustAlpha(onP, 0.2f)); view.strokeWidth = 0
                }
            }
            is com.google.android.material.switchmaterial.SwitchMaterial -> {
                view.setTextColor(textPrimary)
                val thumbStates = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(primary, android.graphics.Color.LTGRAY))
                val trackStates = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(ThemeUtils.adjustAlpha(primary, 0.5f), ThemeUtils.adjustAlpha(android.graphics.Color.GRAY, 0.3f)))
                view.thumbTintList = thumbStates; view.trackTintList = trackStates
            }
            is CircleImageView -> { view.borderColor = primary; view.borderWidth = (2 * resources.displayMetrics.density).toInt() }
            is android.widget.CheckBox -> view.buttonTintList = ColorStateList.valueOf(primary)
            is TextView -> {
                if (view.id == R.id.participantsTitle || view.id == R.id.profileName || view.id == R.id.bioTitle || view.id == R.id.settingsTitle) view.setTextColor(primary)
                else view.setTextColor(textPrimary)
            }
            is com.google.android.material.card.MaterialCardView -> {
                view.setCardBackgroundColor(ColorStateList.valueOf(ThemeUtils.parseSafeColor(theme.surfaceColor, android.graphics.Color.WHITE)))
                view.strokeColor = ThemeUtils.adjustAlpha(onSurface, 0.2f)
            }
            is android.view.ViewGroup -> { for (i in 0 until view.childCount) applyThemeToView(view.getChildAt(i), theme) }
        }
    }
}
