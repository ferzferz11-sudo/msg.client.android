package lavender.client.android

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.scale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeUi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import lavender.client.android.network.HttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

import lavender.client.android.ui.widget.StandardBottomSheet

class EditProfileActivity : AppCompatActivity() {

    private val grpcClient = GrpcClient
    private var username: String = ""
    private var password: String = ""
    private var selectedAvatarUri: Uri? = null
    private var currentAvatarImageView: CircleImageView? = null
    private var currentAvatarProgressBar: ProgressBar? = null
    private var currentFullAvatarUrl: String = ""
    private var initialBio: String = ""
    private var currentCompanyId: String = ""

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedAvatarUri = uri
                uploadAvatarToServer(uri)
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
        val languageCode = prefs.getString("language", "ru") ?: "ru" // Default to Russian for first launch
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

        username = intent.getStringExtra("USERNAME") ?: ""
        password = intent.getStringExtra("PASSWORD") ?: ""

        setContentView(R.layout.activity_edit_profile)
        ThemeUi.bind(this, username)
        setupUI()
    }

    private fun safeRunOnUiThread(block: () -> Unit) {
        if (!isFinishing && !isDestroyed) runOnUiThread(block)
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
        val btnChangeUsername = findViewById<Button>(R.id.btnChangeUsername)
        val btnChangeBio = findViewById<Button>(R.id.btnChangeBio)
        val btnChangePassword = findViewById<Button>(R.id.btnChangePassword)
        val btnChangeAvatar = findViewById<Button>(R.id.btnChangeAvatar)
        val avatarProgressBar = findViewById<ProgressBar>(R.id.avatarProgressBar)
        val btnDeleteProfile = findViewById<Button>(R.id.btnDeleteProfile)
        val companyCard = findViewById<android.view.View>(R.id.companyCard)
        val tvCompanyName = findViewById<android.widget.TextView>(R.id.tvCompanyName)
        val tvCompanyPosition = findViewById<android.widget.TextView>(R.id.tvCompanyPosition)
        val btnCompanyAction = findViewById<android.widget.ImageButton>(R.id.btnCompanyAction)
        val ivCompanyLogo = findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.ivCompanyLogo)
        val tvCompanyLabel = findViewById<android.widget.TextView>(R.id.tvCompanyLabel)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Store references
        currentAvatarImageView = avatarImageView
        currentAvatarProgressBar = avatarProgressBar

        // Load current profile (bio) — v2: user_id from JWT
        Log.d("EditProfile", "Loading profile for user: $username")
        lifecycleScope.launch {
            val profile = lavender.client.android.data.grpc.ProfileClient.getProfile(this@EditProfileActivity)
            Log.d("EditProfile", "Profile received: bio='${profile?.bio}', status='${profile?.status}', avatarUrl='${profile?.avatarUrl}'")
            safeRunOnUiThread {
                if (profile != null) {
                    initialBio = profile.bio
                    editTextBio.setText(profile.bio)
                    btnChangeBio.isVisible = false

                    // Company section
                    if (profile.companyId.isNotEmpty()) {
                        currentCompanyId = profile.companyId
                        companyCard.isVisible = true
                        tvCompanyName.text = profile.companyName
                        tvCompanyPosition.text = formatCompanyPosition(profile.positionTitle, profile.positionLevel)
                        ivCompanyLogo.isVisible = false
                        btnCompanyAction.setOnClickListener {
                            val intent = android.content.Intent(this@EditProfileActivity, CompanyProfileActivity::class.java).apply {
                                putExtra("COMPANY_ID", profile.companyId)
                            }
                            companyLauncher.launch(intent)
                        }
                        companyCard.setOnClickListener {
                            val intent = android.content.Intent(this@EditProfileActivity, CompanyProfileActivity::class.java).apply {
                                putExtra("COMPANY_ID", profile.companyId)
                            }
                            companyLauncher.launch(intent)
                        }
                        // Load company logo
                        lifecycleScope.launch {
                            val companyResp = withContext(Dispatchers.IO) {
                                lavender.client.android.data.grpc.GrpcCompanyClient.getCompany(profile.companyId)
                            }
                            val logoUrl = companyResp?.company?.avatarUrl
                            if (!logoUrl.isNullOrEmpty()) {
                                    safeRunOnUiThread {
                                        ivCompanyLogo.isVisible = true
                                    Glide.with(this@EditProfileActivity)
                                        .load(logoUrl)
                                        .placeholder(R.drawable.ic_default_avatar)
                                        .into(ivCompanyLogo)
                                }
                            }
                        }
                        // Check for multi-company
                        lifecycleScope.launch {
                            val companiesResponse = lavender.client.android.data.grpc.GrpcCompanyClient.getUserCompanies()
                            if (companiesResponse != null && companiesResponse.companies.size > 1) {
                                safeRunOnUiThread {
                                    tvCompanyPosition.text = formatCompanyPosition(profile.positionTitle, profile.positionLevel) +
                                        " (${companiesResponse.companies.size} ${getString(R.string.company_badge).lowercase()})"
                                    // Add long-press to switch company
                                    btnCompanyAction.setOnLongClickListener {
                                        showCompanySwitcher(companiesResponse.companies)
                                        true
                                    }
                                }
                            }
                        }
                    } else {
                        currentCompanyId = ""
                        companyCard.isVisible = true
                        ivCompanyLogo.isVisible = false
                        tvCompanyName.text = getString(R.string.create_company)
                        tvCompanyPosition.isVisible = false
                        btnCompanyAction.setOnClickListener {
                            showCreateCompanyDialog()
                        }
                        companyCard.setOnClickListener {
                            showCreateCompanyDialog()
                        }
                    }
                }
            }
        }

        // Load current avatar and full avatar URL
        grpcClient.getUserAvatar(username, grpcClient.getUserId() ?: "") { avatarUrl ->
            safeRunOnUiThread {
                val currentTheme = ThemeStore.currentTheme()
                if (avatarUrl.isNotEmpty()) {
                    Glide.with(this)
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_default_avatar_white)
                        .error(R.drawable.ic_default_avatar_white)
                        .into(avatarImageView)
                    avatarImageView.imageTintList = null
                    // Get full avatar URL from cache
                    currentFullAvatarUrl = grpcClient.getFullAvatarUrl(username) ?: avatarUrl
                } else {
                    ThemeUtils.applyDefaultAvatar(avatarImageView, currentTheme)
                }
            }
        }

        // Open full screen avatar on click
        avatarImageView.setOnClickListener {
            val fullUrl = currentFullAvatarUrl.takeIf { it.isNotEmpty() }
                ?: grpcClient.getAvatarCache()[username]
                ?: return@setOnClickListener
            val intent = Intent(this, FullScreenImageActivity::class.java).apply {
                putExtra("image_url", fullUrl)
            }
            startActivity(intent)
        }


        btnDeleteProfile.setOnClickListener {
            val passwordInput = EditText(this).apply {
                hint = getString(R.string.enter_password)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
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
                    lifecycleScope.launch {
                        val success = lavender.client.android.data.grpc.ProfileClient.deleteProfile(this@EditProfileActivity, pwd)
                        safeRunOnUiThread {
                            if (success) {
                                Toast.makeText(this@EditProfileActivity, getString(R.string.profile_deleted), Toast.LENGTH_SHORT).show()
                                grpcClient.disconnect()
                                finish()
                            } else {
                                Toast.makeText(this@EditProfileActivity, getString(R.string.failed_to_delete_profile), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.cancel_dialog, null)
                .show()
        }

        editTextBio.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newBio = s?.toString()?.trim() ?: ""
                // Show save button if bio is different from initial (or if initial was empty and user typed something)
                btnChangeBio.isVisible = newBio != initialBio.trim()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnChangeAvatar.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        btnChangeUsername.setOnClickListener {
            showChangeUsernameDialog()
        }

        btnChangeBio.setOnClickListener {
            val newBio = editTextBio.text.toString().trim()
            Log.d("EditProfile", "Updating bio: '$newBio' for user: $username")
            lifecycleScope.launch {
                val success = lavender.client.android.data.grpc.ProfileClient.updateProfile(
                    context = this@EditProfileActivity,
                    bio = newBio,
                    status = ""
                )
                Log.d("EditProfile", "Update bio result: success=$success")
                safeRunOnUiThread {
                    if (success) {
                        Toast.makeText(this@EditProfileActivity, getString(R.string.bio_saved), Toast.LENGTH_SHORT).show()
                        initialBio = newBio
                        btnChangeBio.isVisible = false
                    } else {
                        Toast.makeText(this@EditProfileActivity, getString(R.string.error_colon, "Failed"), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
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

    private fun uploadAvatarToServer(uri: Uri) {
        currentAvatarProgressBar?.isVisible = true

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val mimeType = contentResolver.getType(uri)
                    val isGif = mimeType == "image/gif"

                    val thumbBytes: ByteArray
                    val fullBytes: ByteArray?
                    val mediaType: String

                    if (isGif) {
                        // Для GIF загружаем оригинал без изменений
                        val inputStream = contentResolver.openInputStream(uri)
                        thumbBytes = inputStream?.readBytes() ?: byteArrayOf()
                        fullBytes = null // GIF не нужна отдельная полная версия
                        inputStream?.close()
                        mediaType = "image/gif"
                    } else {
                        // Создаем миниатюру 256x256
                        val resizedBytes = resizeImage(uri)
                        // Создаем полную версию 1920x1920
                        val fullResizedBytes = resizeImageFull(uri)

                        if (resizedBytes == null) {
                            safeRunOnUiThread {
                                currentAvatarProgressBar?.isVisible = false
                                Toast.makeText(this@EditProfileActivity, getString(R.string.failed_to_resize_image), Toast.LENGTH_SHORT).show()
                            }
                            return@withContext
                        }

                        thumbBytes = resizedBytes
                        fullBytes = fullResizedBytes
                        mediaType = "image/jpeg"
                    }

                    if (thumbBytes.isEmpty()) {
                        safeRunOnUiThread {
                            currentAvatarProgressBar?.isVisible = false
                            Toast.makeText(this@EditProfileActivity, getString(R.string.failed_to_read_image), Toast.LENGTH_SHORT).show()
                        }
                        return@withContext
                    }

                    // Upload to HTTP server with multipart/form-data
                    val requestBodyBuilder = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("avatar", if (isGif) "avatar.gif" else "avatar.jpg", thumbBytes.toRequestBody(mediaType.toMediaTypeOrNull()))

                    // Добавляем полную версию если есть
                    if ((fullBytes != null) && fullBytes.isNotEmpty()) {
                        requestBodyBuilder.addFormDataPart("avatar_full", "avatar_full.jpg", fullBytes.toRequestBody(mediaType.toMediaTypeOrNull()))
                    }

                    val requestBody = requestBodyBuilder.build()

                    val request = Request.Builder()
                        .url("${lavender.client.android.data.session.CredentialStore.getHttpServerUrl(this@EditProfileActivity)}/upload-avatar")
                        .post(requestBody)
                        .build()

                    val response = HttpClient.client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val (url, fullUrl) = extractUrlsFromResponse(responseBody)

                        if (url.isNotEmpty()) {
                            // Update avatar via ProfileService v2
                            lifecycleScope.launch {
                                val success = lavender.client.android.data.grpc.ProfileClient.updateAvatar(
                                    context = this@EditProfileActivity,
                                    avatarUrl = url,
                                    fullAvatarUrl = fullUrl
                                )
                                safeRunOnUiThread {
                                    currentAvatarProgressBar?.isVisible = false
                                    if (success) {
                                        Toast.makeText(this@EditProfileActivity, getString(R.string.avatar_updated), Toast.LENGTH_SHORT).show()
                                        currentFullAvatarUrl = fullUrl.ifEmpty { url }
                                        grpcClient.updateAvatarCache(username, url, currentFullAvatarUrl)
                                        currentAvatarImageView?.let {
                                            Glide.with(this@EditProfileActivity)
                                                .load(url)
                                                .placeholder(R.drawable.ic_default_avatar_white)
                                                .error(R.drawable.ic_default_avatar_white)
                                                .into(it)
                                        }
                                        setResult(RESULT_OK)
                                    } else {
                                        Toast.makeText(this@EditProfileActivity, getString(R.string.failed_to_parse_response), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } else {
                            safeRunOnUiThread {
                                currentAvatarProgressBar?.isVisible = false
                                Toast.makeText(this@EditProfileActivity, getString(R.string.failed_to_parse_response), Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        safeRunOnUiThread {
                            currentAvatarProgressBar?.isVisible = false
                            Toast.makeText(this@EditProfileActivity, "Upload failed: ${response.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                safeRunOnUiThread {
                    currentAvatarProgressBar?.isVisible = false
                    Toast.makeText(this@EditProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun extractUrlsFromResponse(response: String): Pair<String, String> {
        // Try to extract both URLs from JSON response
        val urlPattern = """"url"\s*:\s*"([^"]+)"""".toRegex()
        val fullUrlPattern = """"full_url"\s*:\s*"([^"]+)"""".toRegex()

        val urlMatch = urlPattern.find(response)
        val fullUrlMatch = fullUrlPattern.find(response)

        val url = urlMatch?.groupValues?.get(1) ?: ""
        val fullUrl = fullUrlMatch?.groupValues?.get(1) ?: ""

        // Fallback: если сервер вернул только один URL
        if (url.isEmpty() && response.startsWith("http")) {
            return Pair(response.trim(), "")
        }

        return Pair(url, fullUrl)
    }

    private fun resizeImage(uri: Uri): ByteArray? {
        return resizeImageWithMax(uri, 256, 256)
    }

    private fun resizeImageFull(uri: Uri): ByteArray? {
        return resizeImageWithMax(uri, 1920, 1920)
    }

    private fun resizeImageWithMax(uri: Uri, maxWidth: Int, maxHeight: Int): ByteArray? {
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        val imageStream = contentResolver.openInputStream(uri) ?: return null
        val bitmap = BitmapFactory.decodeStream(imageStream)
        imageStream.close()

        if (bitmap == null) return null

        val width = bitmap.width
        val height = bitmap.height
        val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)

        val scaledBitmap = if (scale < 1) {
            bitmap.scale((width * scale).toInt(), (height * scale).toInt())
        } else {
            bitmap
        }

        val outputStream = java.io.ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        scaledBitmap.recycle()
        bitmap.recycle()

        return bytes
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

            grpcClient.updateUsername(username, newUsername) { success, message ->
                safeRunOnUiThread {
                    btnSave.isEnabled = true
                    if (success) {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        
                        // Update username in secure storage
                        lavender.client.android.data.session.CredentialStore.setCredentials(
                            context = this,
                            username = newUsername,
                            password = SessionManager.session.value.password,
                            userId = SessionManager.session.value.userId,
                            email = SessionManager.session.value.email,
                            serverAddress = lavender.client.android.data.session.CredentialStore.getServerAddress(this)
                        )
                        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                        prefs.edit {
                            putString("last_logged_username", newUsername)
                        }
                        
                        SessionManager.updateSession(username = newUsername)
                        username = newUsername
                        sheet.dismiss()
                        
                        setResult(RESULT_OK)
                        finish() 
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
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
                grpcClient.updatePassword(username, oldPass, newPass) { success, message ->
                    safeRunOnUiThread {
                        btnSave.isEnabled = true
                        if (success) {
                            Toast.makeText(this@EditProfileActivity, message, Toast.LENGTH_SHORT).show()
                            password = newPass
                            sheet.dismiss()
                        } else {
                            Toast.makeText(this@EditProfileActivity, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this@EditProfileActivity, getString(R.string.enter_both_passwords), Toast.LENGTH_SHORT).show()
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

            lifecycleScope.launch {
                val response = lavender.client.android.data.grpc.GrpcCompanyClient.createCompany(companyName)
                safeRunOnUiThread {
                    btnSave.isEnabled = true
                    if (response?.success == true) {
                        Toast.makeText(this@EditProfileActivity, getString(R.string.company_created), Toast.LENGTH_SHORT).show()
                        val newCompanyId = response.company?.id ?: ""
                        lifecycleScope.launch {
                            lavender.client.android.data.grpc.GrpcCompanyClient.setPrimaryCompany(newCompanyId)
                        }
                        sheet.dismiss()
                        val intent = android.content.Intent(this@EditProfileActivity, CompanyProfileActivity::class.java).apply {
                            putExtra("COMPANY_ID", newCompanyId)
                        }
                        companyLauncher.launch(intent)
                    } else {
                        Toast.makeText(this@EditProfileActivity, getString(R.string.error_colon, "Failed to create company"), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        sheet.show()
    }

    private fun formatCompanyPosition(positionTitle: String, positionLevel: Int): String {
        val englishNames = mapOf(
            0 to "Employee",
            1 to "Manager",
            2 to "Top Manager",
            3 to "Owner"
        )
        val levelName = when (positionLevel) {
            0 -> getString(R.string.employee)
            1 -> getString(R.string.manager)
            2 -> getString(R.string.top_manager)
            3 -> getString(R.string.owner)
            else -> positionTitle
        }
        if (positionTitle.isEmpty()) return levelName
        val englishName = englishNames[positionLevel]
        return if (englishName != null && positionTitle.equals(englishName, ignoreCase = true)) {
            levelName
        } else if (positionTitle != levelName) {
            "$positionTitle ($levelName)"
        } else {
            levelName
        }
    }

    private fun reloadProfile() {
        lifecycleScope.launch {
            val profile = lavender.client.android.data.grpc.ProfileClient.getProfile(this@EditProfileActivity)
            safeRunOnUiThread {
                if (profile != null) {
                    currentCompanyId = profile.companyId
                    val companyCard = findViewById<android.view.View>(R.id.companyCard)
                    val tvCompanyName = findViewById<android.widget.TextView>(R.id.tvCompanyName)
                    val tvCompanyPosition = findViewById<android.widget.TextView>(R.id.tvCompanyPosition)
                    val ivCompanyLogo = findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.ivCompanyLogo)

                    if (profile.companyId.isNotEmpty()) {
                        companyCard.isVisible = true
                        tvCompanyName.text = profile.companyName
                        tvCompanyPosition.isVisible = true
                        tvCompanyPosition.text = formatCompanyPosition(profile.positionTitle, profile.positionLevel)
                        lifecycleScope.launch {
                            val companyResp = withContext(Dispatchers.IO) {
                                lavender.client.android.data.grpc.GrpcCompanyClient.getCompany(profile.companyId)
                            }
                            val logoUrl = companyResp?.company?.avatarUrl
                            safeRunOnUiThread {
                                if (!logoUrl.isNullOrEmpty()) {
                                    ivCompanyLogo.isVisible = true
                                    Glide.with(this@EditProfileActivity)
                                        .load(logoUrl)
                                        .placeholder(R.drawable.ic_default_avatar)
                                        .into(ivCompanyLogo)
                                } else {
                                    ivCompanyLogo.isVisible = false
                                }
                            }
                        }
                    } else {
                        currentCompanyId = ""
                        companyCard.isVisible = true
                        ivCompanyLogo.isVisible = false
                        tvCompanyName.text = getString(R.string.create_company)
                        tvCompanyPosition.isVisible = false
                        companyCard.setOnClickListener {
                            showCreateCompanyDialog()
                        }
                    }
                }
            }
        }
    }

    private fun showCompanySwitcher(companies: List<lavender.client.android.data.proto.CompanyCompanyMemberProto>) {
        val titles = companies.map { company ->
            val member = company.member
            val position = member?.position?.title ?: ""
            val primary = if (company.isPrimary) " ★" else ""
            "${company.company?.name ?: "?"} — $position$primary"
        }.toTypedArray()

        val currentCompanyId = lavender.client.android.data.session.SessionManager.session.value.companyId
        val currentIndex = companies.indexOfFirst { it.company?.id == currentCompanyId }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.company_badge)
            .setSingleChoiceItems(titles, currentIndex) { dialog, which ->
                val selected = companies[which]
                lifecycleScope.launch {
                    val response = lavender.client.android.data.grpc.GrpcCompanyClient.setPrimaryCompany(selected.company?.id ?: "")
                    safeRunOnUiThread {
                        if (response?.success == true) {
                            Toast.makeText(this@EditProfileActivity, getString(R.string.company_updated), Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        } else {
                            Toast.makeText(this@EditProfileActivity, getString(R.string.error_colon, "Failed"), Toast.LENGTH_LONG).show()
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel_dialog, null)
            .show()
    }
}
