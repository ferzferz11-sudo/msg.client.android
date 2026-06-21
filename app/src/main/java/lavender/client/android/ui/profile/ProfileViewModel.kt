package lavender.client.android.ui.profile

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.AppLog
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.io.File

/**
 * ViewModel for profile/group info screen.
 *
 * Owns: profile data state, participant list, group settings, avatar upload.
 * Delegates: image picking to Activity (via callback), navigation to Activity.
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val grpcClient = GrpcClient
    private val prefs = application.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)

    // ===== Profile state =====
    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _avatarUrl = MutableStateFlow("")
    val avatarUrl: StateFlow<String> = _avatarUrl.asStateFlow()

    private val _fullAvatarUrl = MutableStateFlow("")
    val fullAvatarUrl: StateFlow<String> = _fullAvatarUrl.asStateFlow()

    private val _bio = MutableStateFlow("")
    val bio: StateFlow<String> = _bio.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _statusColor = MutableStateFlow(0)
    val statusColor: StateFlow<Int> = _statusColor.asStateFlow()

    private val _isGroup = MutableStateFlow(false)
    val isGroup: StateFlow<Boolean> = _isGroup.asStateFlow()

    private val _isMeAdmin = MutableStateFlow(false)
    val isMeAdmin: StateFlow<Boolean> = _isMeAdmin.asStateFlow()

    // ===== Group settings =====
    private val _allowMembersToAdd = MutableStateFlow(false)
    val allowMembersToAdd: StateFlow<Boolean> = _allowMembersToAdd.asStateFlow()

    private val _participants = MutableStateFlow<List<String>>(emptyList())
    val participants: StateFlow<List<String>> = _participants.asStateFlow()

    // ===== Loading / Error =====
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // ===== Room info (set by Activity) =====
    var roomId: String = ""
    var creator: String = ""
    private var profileUserId: String = ""

    // ===== Initialize from intent data =====

    fun initFromIntent(
        username: String,
        avatarUrl: String,
        fullAvatarUrl: String,
        isGroup: Boolean,
        roomId: String,
        creator: String,
        participantsJson: String
    ) {
        _username.value = username
        _avatarUrl.value = avatarUrl
        _fullAvatarUrl.value = fullAvatarUrl
        _isGroup.value = isGroup
        this.roomId = roomId
        this.creator = creator

        // Parse participants
        try {
            val jsonArray = JSONArray(participantsJson)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            _participants.value = list
        } catch (e: Exception) {
            AppLog.e("ProfileViewModel", "Error parsing participants", e)
        }

        val currentMe = grpcClient.getCurrentUsername() ?: ""
        _isMeAdmin.value = currentMe == creator && creator.isNotEmpty()
    }

    // ===== Load profile data =====

    fun loadProfileData() {
        if (_isGroup.value) {
            loadGroupProfile()
        } else {
            loadUserProfile()
        }
    }

    private fun loadGroupProfile() {
        val currentMe = grpcClient.getCurrentUsername() ?: ""
        _isMeAdmin.value = currentMe == creator && creator.isNotEmpty()
    }

    private fun loadUserProfile() {
        val uname = _username.value
        if (uname.isEmpty()) return

        viewModelScope.launch {
            try {
                val userId = withContext(Dispatchers.IO) {
                    var result: String? = null
                    var success = false
                    grpcClient.fetchUserId(uname) { id, ok ->
                        result = id
                        success = ok
                    }
                    if (success) result else null
                }

                if (userId.isNullOrEmpty()) {
                    _bio.value = ""
                    _statusText.value = ""
                    return@launch
                }

                profileUserId = userId

                val profile = withContext(Dispatchers.IO) {
                    var result: Any? = null
                    grpcClient.getUserProfile(userId) { p -> result = p }
                    result
                }

                if (profile != null) {
                    withContext(Dispatchers.Main) {
                        // Profile data would be parsed here
                        // The actual parsing depends on the profile response type
                    }
                }
            } catch (e: Exception) {
                AppLog.e("ProfileViewModel", "Error loading user profile", e)
            }
        }
    }

    // ===== Refresh participants from server =====

    fun refreshParticipantsFromServer(onComplete: (() -> Unit)? = null) {
        if (!_isGroup.value || roomId.isEmpty()) {
            onComplete?.invoke()
            return
        }

        val currentMe = grpcClient.getCurrentUsername() ?: return
        grpcClient.getChats(currentMe) { chats ->
            val chat = chats.find { it.id == roomId }
            if (chat != null) {
                try {
                    val jsonArray = JSONArray(chat.participants)
                    val newList = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        newList.add(jsonArray.getString(i))
                    }
                    _participants.value = newList
                    creator = chat.creator
                    _username.value = chat.name
                    _avatarUrl.value = chat.avatarUrl
                    _fullAvatarUrl.value = chat.fullAvatarUrl
                    _allowMembersToAdd.value = chat.allowMembersToAdd
                    onComplete?.invoke()
                } catch (e: Exception) {
                    AppLog.e("ProfileViewModel", "Error refreshing participants", e)
                    onComplete?.invoke()
                }
            } else {
                onComplete?.invoke()
            }
        }
    }

    // ===== Group actions =====

    fun updateChatName(newName: String, callback: (Boolean, String) -> Unit) {
        _isLoading.value = true
        grpcClient.updateChatName(roomId, newName) { success, msg ->
            _isLoading.value = false
            if (success) {
                _username.value = newName
            }
            callback(success, msg)
        }
    }

    fun updateChatSettings(allowAdd: Boolean, callback: (Boolean, String) -> Unit) {
        _isLoading.value = true
        grpcClient.updateChatSettings(roomId, allowAdd) { success, msg ->
            _isLoading.value = false
            if (success) {
                _allowMembersToAdd.value = allowAdd
            }
            callback(success, msg)
        }
    }

    fun deleteGroup(callback: () -> Unit) {
        callback()
    }

    // ===== Participant management =====

    fun addParticipants(users: List<String>, callback: (Boolean, String) -> Unit) {
        _isLoading.value = true
        grpcClient.addParticipants(roomId, users) { success, msg ->
            _isLoading.value = false
            if (success) {
                refreshParticipantsFromServer()
            }
            callback(success, msg)
        }
    }

    fun removeParticipant(user: String, callback: (Boolean, String) -> Unit) {
        _isLoading.value = true
        grpcClient.removeParticipant(roomId, user) { success, msg ->
            _isLoading.value = false
            if (success) {
                refreshParticipantsFromServer()
            }
            callback(success, msg)
        }
    }

    // ===== Avatar upload =====

    fun uploadGroupAvatar(uri: Uri, onResult: (Boolean, String) -> Unit) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val mimeType = context.contentResolver.getType(uri)
                val isGif = mimeType == "image/gif"

                val (thumbBytes, fullBytes, mediaType) = withContext(Dispatchers.IO) {
                    if (isGif) {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val bytes = inputStream?.readBytes() ?: byteArrayOf()
                        inputStream?.close()
                        Triple(bytes, bytes, "image/gif")
                    } else {
                        val thumbResized = resizeImageForGroup(uri)
                        val fullResized = resizeImageFull(uri)
                        if (thumbResized == null || fullResized == null) {
                            _isLoading.value = false
                            withContext(Dispatchers.Main) {
                                onResult(false, "Failed to resize image")
                            }
                            return@withContext
                        }
                        Triple(thumbResized, fullResized, "image/jpeg")
                    }
                }

                if (thumbBytes.isEmpty()) {
                    _isLoading.value = false
                    withContext(Dispatchers.Main) {
                        onResult(false, "Failed to read image")
                    }
                    return@launch
                }

                val httpUrl = lavender.client.android.data.session.CredentialStore.getHttpServerUrl(context)
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("avatar", if (isGif) "avatar.gif" else "avatar.jpg",
                        thumbBytes.toRequestBody(mediaType.toMediaTypeOrNull()))
                    .addFormDataPart("avatar_full", if (isGif) "avatar_full.gif" else "avatar_full.jpg",
                        fullBytes.toRequestBody(mediaType.toMediaTypeOrNull()))
                    .build()

                val request = Request.Builder()
                    .url("$httpUrl/upload-avatar")
                    .post(requestBody)
                    .build()

                val response = withContext(Dispatchers.IO) {
                    OkHttpClient().newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val (thumbUrl, fullUrl) = extractUrlsFromResponse(responseBody)

                    if (thumbUrl.isNotEmpty()) {
                        val currentMe = grpcClient.getCurrentUsername() ?: ""
                        grpcClient.updateChatAvatar(roomId, thumbUrl, currentMe, fullUrl) { success, message ->
                            _isLoading.value = false
                            if (success) {
                                _avatarUrl.value = thumbUrl
                                _fullAvatarUrl.value = fullUrl
                                grpcClient.updateAvatarCache(roomId, thumbUrl, fullUrl)
                            }
                            onResult(success, message)
                        }
                    } else {
                        _isLoading.value = false
                        onResult(false, "Failed to parse response")
                    }
                } else {
                    _isLoading.value = false
                    onResult(false, "Upload failed: ${response.code}")
                }
            } catch (e: Exception) {
                _isLoading.value = false
                AppLog.e("ProfileViewModel", "Error uploading avatar", e)
                onResult(false, "Error: ${e.message}")
            }
        }
    }

    // ===== Image helpers =====

    private fun resizeImageForGroup(uri: Uri): ByteArray? {
        return resizeImageWithMax(uri, 512, 512, 85)
    }

    private fun resizeImageFull(uri: Uri): ByteArray? {
        return resizeImageWithMax(uri, 1920, 1920, 90)
    }

    private fun resizeImageWithMax(uri: Uri, maxWidth: Int, maxHeight: Int, quality: Int): ByteArray? {
        return try {
            val context = getApplication<Application>()
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            val imageStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = android.graphics.BitmapFactory.decodeStream(imageStream)
            imageStream.close() ?: return null

            val width = bitmap.width
            val height = bitmap.height
            val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)

            val scaledBitmap = if (scale < 1) {
                android.graphics.Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
            } else {
                bitmap
            }

            val outputStream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()

            if (scaledBitmap != bitmap) scaledBitmap.recycle()
            bitmap.recycle()
            bytes
        } catch (e: Exception) {
            AppLog.e("ProfileViewModel", "Error resizing image", e)
            null
        }
    }

    private fun extractUrlsFromResponse(response: String): Pair<String, String> {
        val urlPattern = """\"url"\s*:\s*"([^"]+)"\s*""".toRegex()
        val fullUrlPattern = """\"full_url"\s*:\s*"([^"]+)"\s*""".toRegex()
        return Pair(
            urlPattern.find(response)?.groupValues?.get(1) ?: "",
            fullUrlPattern.find(response)?.groupValues?.get(1) ?: ""
        )
    }

    // ===== Toast =====

    fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}
