package lavender.client.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

class EditProfileActivity : AppCompatActivity() {

    private val grpcClient = GrpcClient
    private var username: String = ""
    private var password: String = ""
    private var selectedAvatarUri: Uri? = null
    private var currentAvatarImageView: CircleImageView? = null
    private var currentAvatarProgressBar: ProgressBar? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedAvatarUri = uri
                uploadAvatarToServer(uri)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
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

        username = intent.getStringExtra("username") ?: ""
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
        android.util.Log.d("EditProfile", "Loading profile for user: $username")
        grpcClient.getUserProfile(username) { profile ->
            android.util.Log.d("EditProfile", "Profile received: bio='${profile?.bio}', status='${profile?.status}', avatarUrl='${profile?.avatarUrl}'")
            runOnUiThread {
                if (profile != null) {
                    editTextBio.setText(profile.bio)
                }
            }
        }

        // Load current avatar
        grpcClient.getUserAvatar(username) { avatarUrl ->
            runOnUiThread {
                if (avatarUrl.isNotEmpty()) {
                    Glide.with(this)
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_default_avatar)
                        .error(R.drawable.ic_default_avatar)
                        .into(avatarImageView)
                }
            }
        }

        btnToggleEdit.setOnClickListener {
            if (editFieldsContainer.visibility == View.VISIBLE) {
                editFieldsContainer.visibility = View.GONE
            } else {
                editFieldsContainer.visibility = View.VISIBLE
            }
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
            android.util.Log.d("EditProfile", "Updating bio: '$newBio' for user: $username")
            android.util.Log.d("EditProfile", "Calling updateProfile with username=$username, bio='$newBio', status=''")
            grpcClient.updateProfile(username, newBio, "") { success, message ->
                android.util.Log.d("EditProfile", "Update bio result: success=$success, message=$message")
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "Био сохранено", Toast.LENGTH_SHORT).show()
                        // Reload profile to verify
                        grpcClient.getUserProfile(username) { profile ->
                            android.util.Log.d("EditProfile", "Profile after update: bio='${profile?.bio}'")
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

    private fun uploadAvatarToServer(uri: Uri) {
        currentAvatarProgressBar?.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val mimeType = contentResolver.getType(uri)
                    val isGif = mimeType == "image/gif"

                    val bytes: ByteArray
                    val mediaType: String

                    if (isGif) {
                        val inputStream = contentResolver.openInputStream(uri)
                        bytes = inputStream?.readBytes() ?: byteArrayOf()
                        inputStream?.close()
                        mediaType = "image/gif"
                    } else {
                        val resizedBytes = resizeImage(uri, 256, 256)

                        if (resizedBytes == null) {
                            runOnUiThread {
                                currentAvatarProgressBar?.visibility = View.GONE
                                Toast.makeText(this@EditProfileActivity, "Failed to resize image", Toast.LENGTH_SHORT).show()
                            }
                            return@withContext
                        }

                        bytes = resizedBytes
                        mediaType = "image/jpeg"
                    }

                    if (bytes.isEmpty()) {
                        runOnUiThread {
                            currentAvatarProgressBar?.visibility = View.GONE
                            Toast.makeText(this@EditProfileActivity, "Failed to read image", Toast.LENGTH_SHORT).show()
                        }
                        return@withContext
                    }

                    // Upload to HTTP server with multipart/form-data
                    val requestBody = okhttp3.MultipartBody.Builder()
                        .setType(okhttp3.MultipartBody.FORM)
                        .addFormDataPart("avatar", if (isGif) "avatar.gif" else "avatar.jpg", bytes.toRequestBody(mediaType.toMediaTypeOrNull()))
                        .build()

                    val request = okhttp3.Request.Builder()
                        .url("http://159.195.38.145:8082/upload-avatar")
                        .post(requestBody)
                        .build()

                    val client = okhttp3.OkHttpClient()
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val url = extractUrlFromResponse(responseBody ?: "")

                        if (url.isNotEmpty()) {
                            // Update avatar via gRPC
                            grpcClient.updateAvatar(username, url) { success, message ->
                                runOnUiThread {
                                    currentAvatarProgressBar?.visibility = View.GONE
                                    if (success) {
                                        Toast.makeText(this@EditProfileActivity, "Аватар обновлен", Toast.LENGTH_SHORT).show()
                                        // Update avatarImageView
                                        currentAvatarImageView?.let {
                                            Glide.with(this@EditProfileActivity)
                                                .load(url)
                                                .placeholder(R.drawable.ic_default_avatar)
                                                .error(R.drawable.ic_default_avatar)
                                                .into(it)
                                        }
                                        // Set result to notify ChatListActivity to refresh
                                        setResult(RESULT_OK)
                                    } else {
                                        Toast.makeText(this@EditProfileActivity, message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } else {
                            runOnUiThread {
                                currentAvatarProgressBar?.visibility = View.GONE
                                Toast.makeText(this@EditProfileActivity, "Failed to parse server response", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        runOnUiThread {
                            currentAvatarProgressBar?.visibility = View.GONE
                            Toast.makeText(this@EditProfileActivity, "Upload failed: ${response.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    currentAvatarProgressBar?.visibility = View.GONE
                    Toast.makeText(this@EditProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun extractUrlFromResponse(response: String): String {
        // Try to extract URL from JSON response
        val jsonPattern = """"url"\s*:\s*"([^"]+)"""".toRegex()
        val match = jsonPattern.find(response)
        if (match != null) {
            return match.groupValues[1]
        }
        // Fallback: return the whole response if it looks like a URL
        if (response.startsWith("http")) {
            return response.trim()
        }
        return ""
    }

    private fun resizeImage(uri: Uri, maxWidth: Int, maxHeight: Int): ByteArray? {
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        val imageStream = contentResolver.openInputStream(uri) ?: return null
        val bitmap = android.graphics.BitmapFactory.decodeStream(imageStream)
        imageStream.close()

        if (bitmap == null) return null

        val width = bitmap.width
        val height = bitmap.height
        val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)

        val scaledBitmap = if (scale < 1) {
            android.graphics.Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
        } else {
            bitmap
        }

        val outputStream = java.io.ByteArrayOutputStream()
        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        scaledBitmap.recycle()
        bitmap.recycle()

        return bytes
    }

    private fun showChangeUsernameDialog() {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(48, 24, 48, 24)

        val editText = EditText(this)
        editText.hint = "Новое имя"
        editText.setText(username)
        editText.setPadding(16, 16, 16, 16)

        container.addView(editText)

        AlertDialog.Builder(this)
            .setTitle("Изменить имя")
            .setView(container)
            .setPositiveButton("Сохранить") { _, _ ->
                val newUsername = editText.text.toString().trim()
                android.util.Log.d("EditProfile", "Updating username: $username -> $newUsername")
                if (newUsername.isNotEmpty() && newUsername != username) {
                    grpcClient.updateUsername(username, newUsername) { success, message ->
                        android.util.Log.d("EditProfile", "Update username result: success=$success, message=$message")
                        runOnUiThread {
                            if (success) {
                                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                                username = newUsername
                                finish()
                            } else {
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, "Имя не может быть пустым", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showChangePasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val oldPassword = view.findViewById<EditText>(R.id.editTextOldPassword)
        val newPassword = view.findViewById<EditText>(R.id.editTextNewPassword)

        AlertDialog.Builder(this)
            .setTitle("Изменить пароль")
            .setView(view)
            .setPositiveButton("Сохранить") { _, _ ->
                val oldPass = oldPassword.text.toString().trim()
                val newPass = newPassword.text.toString().trim()
                if (oldPass.isNotEmpty() && newPass.isNotEmpty()) {
                    grpcClient.updatePassword(username, oldPass, newPass) { success, message ->
                        runOnUiThread {
                            if (success) {
                                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                                password = newPass
                            } else {
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, "Введите оба пароля", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
