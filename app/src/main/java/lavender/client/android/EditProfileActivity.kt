package lavender.client.android

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.profile.EditProfileViewModel
import java.util.Locale

import lavender.client.android.ui.widget.StandardBottomSheet

class EditProfileActivity : AppCompatActivity() {

    private lateinit var viewModel: EditProfileViewModel
    private var username: String = ""
    private var password: String = ""
    private var selectedAvatarUri: Uri? = null
    private var currentAvatarImageView: CircleImageView? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedAvatarUri = uri
                viewModel.uploadAvatar(uri)
            }
        }
    }

    private val companyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            reloadProfile()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru"
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

        viewModel = ViewModelProvider(this)[EditProfileViewModel::class.java]

        username = intent.getStringExtra("USERNAME") ?: ""
        password = intent.getStringExtra("PASSWORD") ?: ""

        setContentView(R.layout.activity_edit_profile)
        ThemeUi.bind(this, username)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }
        val avatarImageView = findViewById<CircleImageView>(R.id.ivProfileAvatar)
        val editTextBio = findViewById<EditText>(R.id.editTextBio)
        val usernameCard = findViewById<android.view.View>(R.id.usernameCard)
        val tvInlineUsername = findViewById<android.widget.TextView>(R.id.tvInlineUsername)
        val btnChangeBio = findViewById<Button>(R.id.btnChangeBio)
        val btnChangePassword = findViewById<Button>(R.id.btnChangePassword)
        val btnChangeAvatar = findViewById<Button>(R.id.btnChangeAvatar)
        val btnDeleteProfile = findViewById<Button>(R.id.btnDeleteProfile)
        val companyCard = findViewById<android.view.View>(R.id.companyCard)
        val btnCompanyAction = findViewById<android.widget.ImageButton>(R.id.btnCompanyAction)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        currentAvatarImageView = avatarImageView
        tvInlineUsername.text = getString(R.string.username_at, username)

        // Load profile
        viewModel.loadProfile(username)
        viewModel.loadAvatar(username)

        // Open full screen avatar on click
        avatarImageView.setOnClickListener {
            val fullUrl = viewModel.uiState.value.fullAvatarUrl.takeIf { it.isNotEmpty() }
                ?: return@setOnClickListener
            val intent = Intent(this, FullScreenImageActivity::class.java).apply {
                putExtra("image_url", fullUrl)
            }
            startActivity(intent)
        }

        btnDeleteProfile.setOnClickListener {
            val passwordInput = EditText(this).apply {
                hint = getString(R.string.enter_password)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.delete_profile)
                .setMessage(R.string.delete_profile_confirm)
                .setView(passwordInput)
                .setPositiveButton(R.string.delete_profile) { _, _ ->
                    val pwd = passwordInput.text.toString().trim()
                    if (pwd.isEmpty()) {
                        Toast.makeText(this, getString(R.string.enter_both_passwords), Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    viewModel.deleteProfile(pwd)
                }
                .setNegativeButton(R.string.cancel_dialog, null)
                .show()
        }

        editTextBio.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newBio = s?.toString()?.trim() ?: ""
                btnChangeBio.isVisible = newBio != viewModel.initialBio.value.trim()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnChangeAvatar.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        val btnDeleteAvatar = findViewById<Button>(R.id.btnDeleteAvatar)
        btnDeleteAvatar.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.delete_avatar)
                .setMessage(R.string.delete_avatar_confirm)
                .setPositiveButton(R.string.delete_avatar) { _, _ ->
                    viewModel.deleteAvatar()
                }
                .setNegativeButton(R.string.cancel_dialog, null)
                .show()
        }

        usernameCard.setOnClickListener {
            showChangeUsernameDialog()
        }

        btnChangeBio.setOnClickListener {
            val newBio = editTextBio.text.toString().trim()
            viewModel.updateBio(newBio)
        }

        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        companyCard.setOnClickListener {
            if (viewModel.uiState.value.companyId.isNotEmpty()) {
                val intent = Intent(this, CompanyProfileActivity::class.java).apply {
                    putExtra("COMPANY_ID", viewModel.uiState.value.companyId)
                }
                companyLauncher.launch(intent)
            } else {
                showCreateCompanyDialog()
            }
        }

        btnCompanyAction.setOnClickListener {
            if (viewModel.uiState.value.hasMultipleCompanies) {
                showCompanySwitcher()
            } else if (viewModel.uiState.value.companyId.isNotEmpty()) {
                val intent = Intent(this, CompanyProfileActivity::class.java).apply {
                    putExtra("COMPANY_ID", viewModel.uiState.value.companyId)
                }
                companyLauncher.launch(intent)
            } else {
                showCreateCompanyDialog()
            }
        }

        btnCompanyAction.setOnLongClickListener {
            if (viewModel.uiState.value.hasMultipleCompanies) {
                showCompanySwitcher()
                true
            } else {
                false
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUI(state)
            }
        }

        lifecycleScope.launch {
            viewModel.avatarState.collect { state ->
                val avatarProgressBar = findViewById<android.widget.ProgressBar>(R.id.avatarProgressBar)
                avatarProgressBar?.isVisible = state.isUploading
                state.error?.let { error ->
                    Toast.makeText(this@EditProfileActivity, getString(R.string.error_colon, error), Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.initialBio.collect { bio ->
                val editTextBio = findViewById<EditText>(R.id.editTextBio)
                if (editTextBio.text.toString().trim() != bio) {
                    editTextBio.setText(bio)
                }
                findViewById<Button>(R.id.btnChangeBio)?.isVisible = false
            }
        }
    }

    private fun updateUI(state: lavender.client.android.ui.profile.ProfileUiState) {
        val tvInlineUsername = findViewById<android.widget.TextView>(R.id.tvInlineUsername)
        val companyCard = findViewById<android.view.View>(R.id.companyCard)
        val tvCompanyName = findViewById<android.widget.TextView>(R.id.tvCompanyName)
        val tvCompanyPosition = findViewById<android.widget.TextView>(R.id.tvCompanyPosition)
        val ivCompanyLogo = findViewById<CircleImageView>(R.id.ivCompanyLogo)
        val avatarImageView = findViewById<CircleImageView>(R.id.ivProfileAvatar)

        state.profile?.let { profile ->
            tvInlineUsername.text = getString(R.string.username_at, profile.username.ifEmpty { this@EditProfileActivity.username })
        }

        // Update avatar
        if (state.avatarUrl.isNotEmpty()) {
            Glide.with(this)
                .load(state.avatarUrl)
                .placeholder(R.drawable.ic_default_avatar_white)
                .error(R.drawable.ic_default_avatar_white)
                .into(avatarImageView)
            avatarImageView.imageTintList = null
        } else {
            Glide.with(this).clear(avatarImageView)
            avatarImageView.setImageResource(R.drawable.ic_default_avatar_white)
            avatarImageView.imageTintList = null
        }

        // Update company section
        if (state.companyId.isNotEmpty()) {
            companyCard.isVisible = true
            tvCompanyName.text = state.companyName
            tvCompanyPosition.text = state.companyPosition
            tvCompanyPosition.isVisible = true

            val posBubble = findViewById<com.google.android.material.card.MaterialCardView>(R.id.positionBubble)
            val currentTheme = ThemeStore.currentTheme()
            val primaryColor = ThemeUtils.parseSafeColor(currentTheme.primaryColor, android.graphics.Color.BLUE)
            val primaryContainerBg = ThemeUtils.adjustAlpha(primaryColor, 0.15f)
            posBubble?.setCardBackgroundColor(ColorStateList.valueOf(primaryContainerBg))
            val textPrimary = ThemeUtils.parseSafeColor(currentTheme.textPrimaryColor, android.graphics.Color.BLACK)
            tvCompanyPosition.setTextColor(textPrimary)

            if (state.companyLogoUrl.isNotEmpty()) {
                ivCompanyLogo.isVisible = true
                Glide.with(this)
                    .load(state.companyLogoUrl)
                    .placeholder(R.drawable.ic_default_avatar)
                    .into(ivCompanyLogo)
            } else {
                ivCompanyLogo.isVisible = false
            }

            if (state.hasMultipleCompanies) {
                tvCompanyPosition.text = getString(R.string.company_position_with_count, state.companyPosition, state.companyCount)
            }
        } else {
            companyCard.isVisible = true
            ivCompanyLogo.isVisible = false
            tvCompanyName.text = getString(R.string.create_company)
            tvCompanyPosition.isVisible = false
        }

        // Handle messages
        state.successMessage?.let { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            viewModel.clearSuccess()
            if (message.contains("deleted")) {
                finish()
            }
        }

        state.error?.let { error ->
            Toast.makeText(this, getString(R.string.error_colon, error), Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    override fun onResume() {
        super.onResume()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false
    }

    override fun onPause() {
        super.onPause()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = true
    }

    private fun showChangeUsernameDialog() {
        val sheet = StandardBottomSheet(this, R.layout.dialog_edit_username)
        val editNewUsername = sheet.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editNewUsername)
        val btnCancel = sheet.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSave = sheet.findViewById<MaterialButton>(R.id.btnSave)

        sheet.setTitle(getString(R.string.change_username))

        editNewUsername?.setText(username)
        editNewUsername?.requestFocus()

        btnCancel?.setOnClickListener { sheet.dismiss() }

        btnSave?.setOnClickListener {
            val newUsername = editNewUsername?.text.toString().trim()
            if (newUsername.isEmpty()) {
                Toast.makeText(this, getString(R.string.username_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newUsername == username) {
                sheet.dismiss()
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            viewModel.updateUsername(username, newUsername, password)
            sheet.dismiss()
        }

        sheet.show()
    }

    private fun showChangePasswordDialog() {
        val sheet = StandardBottomSheet(this, R.layout.dialog_change_password)
        sheet.setTitle(getString(R.string.change_password))

        val oldPassword = sheet.findViewById<EditText>(R.id.editTextOldPassword)
        val newPassword = sheet.findViewById<EditText>(R.id.editTextNewPassword)
        val btnCancel = sheet.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSave = sheet.findViewById<MaterialButton>(R.id.btnSave)

        btnCancel?.setOnClickListener { sheet.dismiss() }

        btnSave?.setOnClickListener {
            val oldPass = oldPassword?.text.toString().trim()
            val newPass = newPassword?.text.toString().trim()

            if (oldPass.isNotEmpty() && newPass.isNotEmpty()) {
                btnSave.isEnabled = false
                viewModel.updatePassword(username, oldPass, newPass)
                sheet.dismiss()
            } else {
                Toast.makeText(this, getString(R.string.enter_both_passwords), Toast.LENGTH_SHORT).show()
            }
        }

        sheet.show()
    }

    private fun showCreateCompanyDialog() {
        val sheet = StandardBottomSheet(this, R.layout.dialog_edit_username)
        val inputLayout = sheet.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.usernameInputLayout)
        val editNewUsername = sheet.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editNewUsername)
        val btnCancel = sheet.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSave = sheet.findViewById<MaterialButton>(R.id.btnSave)

        sheet.setTitle(getString(R.string.create_company))
        inputLayout?.hint = getString(R.string.create_company_name_hint)
        inputLayout?.startIconDrawable = null
        editNewUsername?.hint = getString(R.string.create_company_name_hint)
        editNewUsername?.text?.clear()
        editNewUsername?.requestFocus()

        btnCancel?.setOnClickListener { sheet.dismiss() }

        btnSave?.setOnClickListener {
            val companyName = editNewUsername?.text.toString().trim()
            if (companyName.isEmpty()) {
                Toast.makeText(this, getString(R.string.create_company_name_hint), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            viewModel.createCompany(companyName)
            sheet.dismiss()
        }

        sheet.show()
    }

    private fun showCompanySwitcher() {
        lifecycleScope.launch {
            val companiesResponse = lavender.client.android.data.grpc.GrpcCompanyClient.getUserCompanies() ?: return@launch
            val companies = companiesResponse.companies

            val titles = companies.map { company ->
                val member = company.member
                val position = member?.position?.title ?: ""
                val primary = if (company.isPrimary) " ★" else ""
                "${company.company?.name ?: "?"} — $position$primary"
            }.toTypedArray()

            val currentCompanyId = SessionManager.session.value.companyId
            val currentIndex = companies.indexOfFirst { it.company?.id == currentCompanyId }.coerceAtLeast(0)

            AlertDialog.Builder(this@EditProfileActivity)
                .setTitle(R.string.company_badge)
                .setSingleChoiceItems(titles, currentIndex) { dialog, which ->
                    val selected = companies[which]
                    viewModel.setPrimaryCompany(selected.company?.id ?: "")
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel_dialog, null)
                .show()
        }
    }

    private fun reloadProfile() {
        viewModel.loadProfile(username)
    }
}
