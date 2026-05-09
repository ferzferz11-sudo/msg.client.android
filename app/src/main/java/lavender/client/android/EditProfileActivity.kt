package lavender.client.android

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

class EditProfileActivity : AppCompatActivity() {

    private val grpcClient = GrpcClient
    private var username: String = ""
    private var password: String = ""
    private var selectedAvatarUri: Uri? = null
    private var currentAvatarImageView: CircleImageView? = null
    private var currentAvatarProgressBar: ProgressBar? = null
    private var currentFullAvatarUrl: String = ""

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedAvatarUri = uri
                uploadAvatarToServer(uri)
            }
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

    private fun setupUI() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }
        val avatarImageView = findViewById<CircleImageView>(R.id.avatarImageView)
        val editTextBio = findViewById<EditText>(R.id.editTextBio)
        val btnChangeUsername = findViewById<Button>(R.id.btnChangeUsername)
        val btnChangeBio = findViewById<Button>(R.id.btnChangeBio)
        val btnChangePassword = findViewById<Button>(R.id.btnChangePassword)
        val btnChangeAvatar = findViewById<Button>(R.id.btnChangeAvatar)
        val avatarProgressBar = findViewById<ProgressBar>(R.id.avatarProgressBar)
        val btnToggleEdit = findViewById<Button>(R.id.btnToggleEdit)
        val editFieldsContainer = findViewById<View>(R.id.editFieldsContainer)
        val btnDeleteProfile = findViewById<Button>(R.id.btnDeleteProfile)

        // username already set in onCreate
        password = intent.getStringExtra("password") ?: ""

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Store references
        currentAvatarImageView = avatarImageView
        currentAvatarProgressBar = avatarProgressBar

        // Load current profile (bio)
        Log.d("EditProfile", "Loading profile for user: $username")
        grpcClient.getUserProfile(username) { profile ->
            Log.d("EditProfile", "Profile received: bio='${profile?.bio}', status='${profile?.status}', avatarUrl='${profile?.avatarUrl}'")
            runOnUiThread {
                if (profile != null) {
                    editTextBio.setText(profile.bio)
                }
            }
        }

        // Load current avatar and full avatar URL
        grpcClient.getUserAvatar(username) { avatarUrl ->
            runOnUiThread {
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

        btnToggleEdit.setOnClickListener {
            editFieldsContainer.isVisible = !editFieldsContainer.isVisible
        }

        btnDeleteProfile.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.delete_profile)
                .setMessage(R.string.delete_profile_confirm)
                .setPositiveButton(R.string.delete_profile) { _, _ ->
                    grpcClient.deleteProfile(username) { success, message ->
                        runOnUiThread {
                            if (success) {
                                Toast.makeText(this, getString(R.string.profile_deleted), Toast.LENGTH_SHORT).show()
                                grpcClient.disconnect()
                                finish()
                            } else {
                                Toast.makeText(this, getString(R.string.failed_to_delete_profile) + ": " + message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.cancel_dialog, null)
                .show()
        }

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
            Log.d("EditProfile", "Calling updateProfile with username=$username, bio='$newBio', status=''")
            grpcClient.updateProfile(username, newBio, "") { success, message ->
                Log.d("EditProfile", "Update bio result: success=$success, message=$message")
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "Био сохранено", Toast.LENGTH_SHORT).show()
                        // Reload profile to verify
                        grpcClient.getUserProfile(username) { profile ->
                            Log.d("EditProfile", "Profile after update: bio='${profile?.bio}'")
                        }
                    } else {
                        Toast.makeText(this, "Ошибка: $message", Toast.LENGTH_LONG).show()
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
                            runOnUiThread {
                                currentAvatarProgressBar?.isVisible = false
                                Toast.makeText(this@EditProfileActivity, "Failed to resize image", Toast.LENGTH_SHORT).show()
                            }
                            return@withContext
                        }

                        thumbBytes = resizedBytes
                        fullBytes = fullResizedBytes
                        mediaType = "image/jpeg"
                    }

                    if (thumbBytes.isEmpty()) {
                        runOnUiThread {
                            currentAvatarProgressBar?.isVisible = false
                            Toast.makeText(this@EditProfileActivity, "Failed to read image", Toast.LENGTH_SHORT).show()
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
                        .url("http://159.195.38.145:8082/upload-avatar")
                        .post(requestBody)
                        .build()

                    val client = OkHttpClient()
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val (url, fullUrl) = extractUrlsFromResponse(responseBody)

                        if (url.isNotEmpty()) {
                            // Update avatar via gRPC - передаем оба URL
                            grpcClient.updateAvatar(username, url, fullUrl) { success, message ->
                                runOnUiThread {
                                    currentAvatarProgressBar?.isVisible = false
                                    if (success) {
                                        Toast.makeText(this@EditProfileActivity, "Аватар обновлен", Toast.LENGTH_SHORT).show()
                                        // Update current full avatar URL
                                        currentFullAvatarUrl = fullUrl.takeIf { it.isNotEmpty() } ?: url
                                        // Update avatarImageView (используем миниатюру)
                                        currentAvatarImageView?.let {
                                            Glide.with(this@EditProfileActivity)
                                                .load(url)
                                                .placeholder(R.drawable.ic_default_avatar_white)
                                                .error(R.drawable.ic_default_avatar_white)
                                                .into(it)
                                        }
                                        // Set result to notify NewChatActivity to refresh
                                        setResult(RESULT_OK)
                                    } else {
                                        Toast.makeText(this@EditProfileActivity, message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } else {
                            runOnUiThread {
                                currentAvatarProgressBar?.isVisible = false
                                Toast.makeText(this@EditProfileActivity, "Failed to parse server response", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        runOnUiThread {
                            currentAvatarProgressBar?.isVisible = false
                            Toast.makeText(this@EditProfileActivity, "Upload failed: ${response.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_username, null)
        val editNewUsername = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editNewUsername)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSave)

        editNewUsername.setText(username)
        editNewUsername.requestFocus()

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Ensure keyboard shows up
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newUsername = editNewUsername.text.toString().trim()
            if (newUsername.isEmpty()) {
                Toast.makeText(this, getString(R.string.username_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newUsername == username) {
                dialog.dismiss()
                return@setOnClickListener
            }

            // Show loading state if we had a progress bar, but for now just disable button
            btnSave.isEnabled = false

            grpcClient.updateUsername(username, newUsername) { success, message ->
                runOnUiThread {
                    btnSave.isEnabled = true
                    if (success) {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        
                        // Update local identity
                        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                        prefs.edit {
                            putString("username", newUsername)
                        }
                        
                        SessionManager.updateSession(username = newUsername)
                        username = newUsername
                        dialog.dismiss()
                        
                        setResult(RESULT_OK)
                        finish() 
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        
        val oldPassword = dialogView.findViewById<EditText>(R.id.editTextOldPassword)
        val newPassword = dialogView.findViewById<EditText>(R.id.editTextNewPassword)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSave)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val oldPass = oldPassword.text.toString().trim()
            val newPass = newPassword.text.toString().trim()
            if (oldPass.isNotEmpty() && newPass.isNotEmpty()) {
                grpcClient.updatePassword(username, oldPass, newPass) { success, message ->
                    runOnUiThread {
                        if (success) {
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                            password = newPass
                            dialog.dismiss()
                        } else {
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Введите оба пароля", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }
}
