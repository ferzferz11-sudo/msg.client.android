package lavender.client.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import lavender.client.android.ui.chatlist.ChatListActivity
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.ProtoUtils
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.adapter.SelectableUserAdapter
import lavender.client.android.ui.adapter.ParticipantAdapter
import lavender.client.android.ui.profile.ProfileViewModel
import lavender.client.android.ui.widget.SearchableListBottomSheet
import lavender.client.android.ui.widget.WidgetManager
import org.json.JSONArray
import java.util.Locale
import androidx.core.graphics.toColorInt
import androidx.appcompat.app.AlertDialog

/**
 * Profile/Group info screen — thin Activity delegating to ProfileViewModel.
 *
 * Architecture:
 * - ProfileViewModel: profile data state, participant list, group settings, avatar upload
 * - Activity: UI binding, image picking, bottom sheets, navigation
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var viewModel: ProfileViewModel
    private val grpcClient = GrpcClient

    private var isGroup: Boolean = false
    private var roomId: String = ""
    private var creator: String = ""
    private var currentParticipants = mutableListOf<String>()
    private var allowMembersToAdd: Boolean = false
    private var selectedAvatarUri: Uri? = null
    private var currentProfileAvatar: CircleImageView? = null
    private var participantsAdapter: ParticipantAdapter? = null
    private var participantsBottomSheet: SearchableListBottomSheet? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedAvatarUri = uri
                viewModel.uploadGroupAvatar(uri) { success, msg ->
                    runOnUiThread {
                        if (!success) Toast.makeText(this@ProfileActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

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

        // Parse intent
        val username = intent.getStringExtra("username") ?: ""
        val avatarUrl = intent.getStringExtra("avatar_url") ?: ""
        val fullAvatarUrl = intent.getStringExtra("full_avatar_url") ?: ""
        isGroup = intent.getBooleanExtra("is_group", false)
        roomId = intent.getStringExtra("room_id") ?: ""
        creator = intent.getStringExtra("creator") ?: ""
        val participantsJson = intent.getStringExtra("participants") ?: "[]"

        try {
            val jsonArray = JSONArray(participantsJson)
            currentParticipants.clear()
            for (i in 0 until jsonArray.length()) {
                currentParticipants.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            android.util.Log.e("ProfileActivity", "Error parsing participants", e)
        }

        // Init ViewModel
        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
        viewModel.initFromIntent(username, avatarUrl, fullAvatarUrl, isGroup, roomId, creator, participantsJson)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (isGroup) getString(R.string.group_info) else getString(R.string.profile)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Observe ViewModel state
        observeViewModel()

        // Load initial data
        viewModel.loadProfileData()

        lifecycleScope.launch {
            grpcClient.users.collect {
                runOnUiThread { viewModel.loadProfileData() }
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.username.collectLatest { name ->
                findViewById<TextView>(R.id.profileName)?.text = name
            }
        }
        lifecycleScope.launch {
            viewModel.avatarUrl.collectLatest { url ->
                val profileAvatar = findViewById<CircleImageView>(R.id.profileAvatar) ?: return@collectLatest
                if (url.isNotEmpty()) {
                    Glide.with(this@ProfileActivity).load(url).placeholder(R.drawable.ic_default_avatar).into(profileAvatar)
                    profileAvatar.imageTintList = null
                    setupAvatarClickListener(profileAvatar)
                }
            }
        }
        lifecycleScope.launch {
            viewModel.bio.collectLatest { bio ->
                val profileBio = findViewById<TextView>(R.id.profileBio) ?: return@collectLatest
                profileBio.text = bio.ifEmpty { getString(R.string.no_bio) }
            }
        }
        lifecycleScope.launch {
            viewModel.statusText.collectLatest { status ->
                val profileStatus = findViewById<TextView>(R.id.profileStatus) ?: return@collectLatest
                profileStatus.text = status
            }
        }
        lifecycleScope.launch {
            viewModel.isGroup.collectLatest { group ->
                // Update UI visibility based on group flag
                findViewById<TextView>(R.id.profileStatus)?.isVisible = !group
                findViewById<com.google.android.material.card.MaterialCardView>(R.id.bioCard)?.isVisible = !group
                findViewById<com.google.android.material.card.MaterialCardView>(R.id.groupSettingsCard)?.isVisible = group
                findViewById<com.google.android.material.card.MaterialCardView>(R.id.participantsCard)?.isVisible = group
            }
        }
        lifecycleScope.launch {
            viewModel.isLoading.collectLatest { loading ->
                findViewById<View>(R.id.progressOverlay)?.isVisible = loading
            }
        }
        lifecycleScope.launch {
            viewModel.toastMessage.collectLatest { msg ->
                if (msg != null) {
                    Toast.makeText(this@ProfileActivity, msg, Toast.LENGTH_SHORT).show()
                    viewModel.clearToast()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.participants.collectLatest { participants ->
                findViewById<TextView>(R.id.participantsCountText)?.text = participants.size.toString()
            }
        }
        lifecycleScope.launch {
            viewModel.isMeAdmin.collectLatest { isAdmin ->
                // Update admin-only UI
                val changeAvatarButton = findViewById<View>(R.id.changeAvatarButton)
                val profileName = findViewById<TextView>(R.id.profileName)
                val deleteGroupButton = findViewById<MaterialButton>(R.id.deleteGroupButton)
                val switchAllowAdd = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchAllowAdd)
                val groupSettingsCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.groupSettingsCard)

                if (isGroup) {
                    groupSettingsCard?.isVisible = isAdmin
                    switchAllowAdd?.isEnabled = isAdmin
                    changeAvatarButton?.isVisible = isAdmin
                    deleteGroupButton?.isVisible = isAdmin

                    if (isAdmin) {
                        profileName.setOnClickListener {
                            showEditGroupNameDialog()
                        }
                        changeAvatarButton?.setOnClickListener {
                            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                            pickImageLauncher.launch(intent)
                        }
                        deleteGroupButton?.setOnClickListener {
                            showDeleteGroupDialog()
                        }
                    } else {
                        profileName.setOnClickListener(null)
                    }
                }
            }
        }
        lifecycleScope.launch {
            viewModel.allowMembersToAdd.collectLatest { allowed ->
                val switchAllowAdd = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchAllowAdd) ?: return@collectLatest
                switchAllowAdd.setOnCheckedChangeListener(null)
                switchAllowAdd.isChecked = allowed
                setupAllowAddSwitch(switchAllowAdd, roomId, viewModel.isMeAdmin.value)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false
        viewModel.refreshParticipantsFromServer()
    }

    override fun onPause() {
        super.onPause()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = true
    }

    private fun showEditGroupNameDialog() {
        val editName = EditText(this).apply {
            setText(viewModel.username.value)
            setSelection(viewModel.username.value.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.edit_message)
            .setView(editName)
            .setPositiveButton(R.string.change) { _, _ ->
                val newName = editName.text.toString().trim()
                if (newName.isNotEmpty() && newName != viewModel.username.value) {
                    viewModel.updateChatName(newName) { success, msg ->
                        runOnUiThread {
                            if (!success) Toast.makeText(this@ProfileActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteGroupDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_group)
            .setMessage(R.string.delete_group_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                val intent = Intent(this@ProfileActivity, ChatListActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("START_DELETION_ID", roomId)
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun setupAllowAddSwitch(switch: com.google.android.material.switchmaterial.SwitchMaterial, roomId: String, isMeAdmin: Boolean) {
        switch.isEnabled = isMeAdmin
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = viewModel.allowMembersToAdd.value

        if (isMeAdmin) {
            switch.setOnCheckedChangeListener { _, isChecked ->
                viewModel.updateChatSettings(isChecked) { success, msg ->
                    runOnUiThread {
                        if (success) {
                            Toast.makeText(this@ProfileActivity, R.string.theme_saved, Toast.LENGTH_SHORT).show()
                        } else {
                            switch.setOnCheckedChangeListener(null)
                            switch.isChecked = !isChecked
                            setupAllowAddSwitch(switch, roomId, isMeAdmin)
                            Toast.makeText(this@ProfileActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun setupAvatarClickListener(profileAvatar: CircleImageView) {
        profileAvatar.setOnClickListener {
            val fullImageUrl = if (!isGroup) {
                grpcClient.getFullAvatarUrl(viewModel.username.value) ?: viewModel.fullAvatarUrl.value.ifEmpty { viewModel.avatarUrl.value }
            } else {
                viewModel.fullAvatarUrl.value.ifEmpty { viewModel.avatarUrl.value }
            }
            if (fullImageUrl.isNotEmpty()) showFullScreenImage(fullImageUrl)
        }
    }

    private fun showParticipantsBottomSheet() {
        val currentTheme = ThemeStore.currentTheme()
        val currentMe = grpcClient.getCurrentUsername() ?: ""
        val isMeAdmin = viewModel.isMeAdmin.value

        val sheet = SearchableListBottomSheet(this, currentTheme)
            .setTitle(getString(R.string.participants))
            .setActionButtonText(getString(R.string.add))
            .setExtraInputVisible(false)

        if (participantsAdapter == null) {
            participantsAdapter = ParticipantAdapter(
                theme = currentTheme,
                isAdmin = isMeAdmin,
                creator = creator,
                onRemoveClick = { user -> showRemoveParticipantDialog(user) },
                onAvatarClick = { user, url ->
                    val fullImageUrl = grpcClient.getFullAvatarUrl(user) ?: url
                    if (fullImageUrl.isNotEmpty()) showFullScreenImage(fullImageUrl)
                },
                onLongClick = { user ->
                    if (isMeAdmin && user != creator) showRemoveParticipantDialog(user)
                }
            )
        }

        sheet.setAdapter(participantsAdapter!!)
        participantsAdapter?.updateData(
            viewModel.participants.value,
            grpcClient.users.value.toSet(),
            grpcClient.getAvatarCache()
        )

        sheet.onSearchTextChanged { query ->
            val q = query.lowercase()
            val filtered = viewModel.participants.value.filter { it.lowercase().contains(q) }
            participantsAdapter?.updateData(
                filtered,
                grpcClient.users.value.toSet(),
                grpcClient.getAvatarCache()
            )
        }

        val canAdd = isMeAdmin || viewModel.allowMembersToAdd.value
        sheet.setActionButtonEnabled(canAdd)
        sheet.actionButton?.isVisible = canAdd

        sheet.onActionClick {
            grpcClient.getContacts(currentMe) { allContacts ->
                val availableContacts = allContacts.filter { it !in viewModel.participants.value }
                runOnUiThread {
                    if (availableContacts.isEmpty()) {
                        Toast.makeText(this, R.string.no_users_available, Toast.LENGTH_SHORT).show()
                    } else {
                        showAddParticipantDialog(availableContacts)
                    }
                }
            }
        }

        participantsBottomSheet = sheet
        sheet.setOnDismissListener {
            participantsBottomSheet = null
            participantsAdapter?.updateData(
                viewModel.participants.value,
                grpcClient.users.value.toSet(),
                grpcClient.getAvatarCache()
            )
        }
        sheet.show()
    }

    private fun showRemoveParticipantDialog(user: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.remove)
            .setMessage(getString(R.string.remove_participant_confirm, user))
            .setPositiveButton(R.string.remove) { _, _ ->
                viewModel.removeParticipant(user) { success, msg ->
                    runOnUiThread {
                        if (!success) Toast.makeText(this@ProfileActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun showAddParticipantDialog(contacts: List<String>) {
        val sheet = SearchableListBottomSheet(this)
            .setTitle(getString(R.string.add_participants))
            .setActionButtonText(getString(R.string.add))
            .setExtraInputVisible(false)

        val selectableAdapter = SelectableUserAdapter(
            lifecycleScope,
            avatarCache = grpcClient.getAvatarCache(),
            onSelectionChanged = { count ->
                sheet.setActionButtonEnabled(count > 0)
                sheet.setActionButtonText(if (count > 0) "${getString(R.string.add)} ($count)" else getString(R.string.add))
            }
        )
        sheet.setAdapter(selectableAdapter)
        selectableAdapter.setUsers(contacts)

        sheet.onSearchTextChanged { query ->
            val q = query.lowercase()
            selectableAdapter.setUsers(contacts.filter { it.lowercase().contains(q) })
        }

        sheet.onActionClick {
            val selected = selectableAdapter.getSelectedUsers()
            if (selected.isEmpty()) return@onActionClick
            val progressOverlay = findViewById<View>(R.id.progressOverlay)
            sheet.dismiss()
            progressOverlay.isVisible = true
            viewModel.addParticipants(selected) { success, msg ->
                runOnUiThread {
                    progressOverlay.isVisible = false
                    if (!success) Toast.makeText(this@ProfileActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
        sheet.show()
    }

    private fun showFullScreenImage(imageUrl: String) {
        val intent = Intent(this, FullScreenImageActivity::class.java).apply { putExtra("image_url", imageUrl) }
        startActivity(intent)
    }

    @SuppressLint("SetTextI18n")
    private fun applyThemeToView(view: View, theme: lavender.client.android.theme.Theme) {
        val textPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, android.graphics.Color.BLACK)
        val onSurface = ThemeUtils.parseSafeColor(theme.onSurfaceColor, android.graphics.Color.GRAY)
        val primary = ThemeUtils.parseSafeColor(theme.primaryColor, android.graphics.Color.BLUE)

        when (view) {
            is MaterialButton -> {
                view.isAllCaps = false
                view.transformationMethod = null
                view.cornerRadius = (28 * resources.displayMetrics.density).toInt()
                view.minimumHeight = (56 * resources.displayMetrics.density).toInt()

                val isCancelType = view.id == R.id.deleteGroupButton
                val isActionType = view.id == R.id.changeAvatarButton

                if (isCancelType) {
                    view.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                    view.strokeColor = ColorStateList.valueOf(primary)
                    view.strokeWidth = (1 * resources.displayMetrics.density).toInt()
                    view.setTextColor(primary)
                    view.rippleColor = ColorStateList.valueOf(ThemeUtils.adjustAlpha(primary, 0.1f))
                } else {
                    val alpha = if (isActionType) 0.7f else 1.0f
                    view.backgroundTintList = ColorStateList.valueOf(ThemeUtils.adjustAlpha(primary, alpha))
                    val onP = ThemeUtils.parseSafeColor(theme.onPrimaryColor, android.graphics.Color.WHITE)
                    view.setTextColor(onP)
                    view.rippleColor = ColorStateList.valueOf(ThemeUtils.adjustAlpha(onP, 0.2f))
                    view.strokeWidth = 0
                }
            }
            is com.google.android.material.switchmaterial.SwitchMaterial -> {
                view.setTextColor(textPrimary)
                val thumbStates = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(primary, android.graphics.Color.LTGRAY)
                )
                val trackStates = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(ThemeUtils.adjustAlpha(primary, 0.5f), ThemeUtils.adjustAlpha(android.graphics.Color.GRAY, 0.3f))
                )
                view.thumbTintList = thumbStates
                view.trackTintList = trackStates
            }
            is CircleImageView -> {
                view.borderColor = primary
                view.borderWidth = (2 * resources.displayMetrics.density).toInt()
            }
            is android.widget.CheckBox -> view.buttonTintList = ColorStateList.valueOf(primary)
            is TextView -> {
                if (view.id == R.id.participantsTitle ||
                    view.id == R.id.profileName || view.id == R.id.bioTitle || view.id == R.id.settingsTitle) {
                    view.setTextColor(primary)
                } else {
                    view.setTextColor(textPrimary)
                }
            }
            is com.google.android.material.card.MaterialCardView -> {
                view.setCardBackgroundColor(ColorStateList.valueOf(ThemeUtils.parseSafeColor(theme.surfaceColor, android.graphics.Color.WHITE)))
                view.strokeColor = ThemeUtils.adjustAlpha(onSurface, 0.2f)
            }
            is android.view.ViewGroup -> { for (i in 0 until view.childCount) applyThemeToView(view.getChildAt(i), theme) }
        }
    }
}
