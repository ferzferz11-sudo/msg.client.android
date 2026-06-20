package lavender.client.android.ui.profile

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import lavender.client.android.network.HttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    data class ProfileData(
        val username: String = "",
        val avatarUrl: String = "",
        val fullAvatarUrl: String = "",
        val bio: String = "",
        val status: String = "",
        val isOnline: Boolean = false,
        val lastSeenAt: com.google.protobuf.Timestamp? = null
    )

    data class GroupData(
        val name: String = "",
        val avatarUrl: String = "",
        val fullAvatarUrl: String = "",
        val creator: String = "",
        val participants: List<String> = emptyList(),
        val allowMembersToAdd: Boolean = false
    )

    data class AvatarUploadResult(val thumbUrl: String = "", val fullUrl: String = "", val error: String = "")

    private val _profileData = MutableStateFlow(ProfileData())
    val profileData: StateFlow<ProfileData> = _profileData.asStateFlow()

    private val _groupData = MutableStateFlow(GroupData())
    val groupData: StateFlow<GroupData> = _groupData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _onlineUsers = MutableStateFlow<Set<String>>(emptySet())
    val onlineUsers: StateFlow<Set<String>> = _onlineUsers.asStateFlow()

    fun loadUserProfile(username: String) {
        viewModelScope.launch {
            val currentUsername = GrpcClient.getCurrentUsername()
            if (username == currentUsername) {
                // v2: current user — user_id from JWT
                val profile = lavender.client.android.data.grpc.ProfileClient.getProfile(getApplication())
                val isOnline = true
                _profileData.value = ProfileData(
                    username = profile?.username ?: username,
                    avatarUrl = profile?.avatarUrl ?: "",
                    fullAvatarUrl = profile?.fullAvatarUrl?.ifEmpty { profile.avatarUrl } ?: "",
                    bio = profile?.bio ?: "",
                    status = profile?.status ?: "",
                    isOnline = isOnline,
                    lastSeenAt = null
                )
                if (profile != null && profile.avatarUrl.isNotEmpty()) {
                    GrpcClient.updateAvatarCache(username, profile.avatarUrl, profile.fullAvatarUrl.ifEmpty { profile.avatarUrl })
                }
            } else {
                // Other users — ChatService (no v2 replacement for viewing other profiles)
                GrpcClient.fetchUserId(username) { userId, success ->
                    if (!success || userId.isNullOrEmpty()) {
                        _profileData.value = _profileData.value.copy(username = username, bio = "", status = "")
                        return@fetchUserId
                    }
                    GrpcClient.getUserProfile(userId) { profile ->
                        val isOnline = username == currentUsername || GrpcClient.users.value.contains(username)
                        _profileData.value = ProfileData(
                            username = username,
                            avatarUrl = profile?.avatarUrl ?: "",
                            fullAvatarUrl = profile?.fullAvatarUrl?.ifEmpty { profile.avatarUrl } ?: "",
                            bio = profile?.bio ?: "",
                            status = profile?.status ?: "",
                            isOnline = isOnline,
                            lastSeenAt = profile?.lastSeenAt
                        )
                        if (profile != null && profile.avatarUrl.isNotEmpty()) {
                            GrpcClient.updateAvatarCache(username, profile.avatarUrl, profile.fullAvatarUrl.ifEmpty { profile.avatarUrl })
                        }
                    }
                }
            }
        }
    }

    fun loadGroupData(roomId: String) {
        viewModelScope.launch {
            val username = GrpcClient.getCurrentUsername() ?: return@launch
            GrpcClient.getChats(username) { chats ->
                val chat = chats.find { it.id == roomId }
                if (chat != null) {
                    val participants = try {
                        val arr = JSONArray(chat.participants)
                        (0 until arr.length()).map { arr.getString(it) }
                    } catch (_: Exception) { emptyList() }
                    _groupData.value = GroupData(
                        name = chat.name, avatarUrl = chat.avatarUrl,
                        fullAvatarUrl = chat.fullAvatarUrl, creator = chat.creator,
                        participants = participants, allowMembersToAdd = chat.allowMembersToAdd
                    )
                }
            }
        }
    }

    fun updateChatName(roomId: String, newName: String, onComplete: (Boolean, String) -> Unit) {
        GrpcClient.updateChatName(roomId, newName) { success, msg ->
            if (success) _groupData.value = _groupData.value.copy(name = newName)
            onComplete(success, msg)
        }
    }

    fun updateChatSettings(roomId: String, allowAdd: Boolean, onComplete: (Boolean, String) -> Unit) {
        GrpcClient.updateChatSettings(roomId, allowAdd) { success, msg ->
            if (success) _groupData.value = _groupData.value.copy(allowMembersToAdd = allowAdd)
            onComplete(success, msg)
        }
    }

    fun removeParticipant(roomId: String, user: String, onComplete: (Boolean, String) -> Unit) {
        GrpcClient.removeParticipant(roomId, user) { success, msg ->
            if (success) loadGroupData(roomId)
            onComplete(success, msg)
        }
    }

    fun addParticipants(roomId: String, users: List<String>, onComplete: (Boolean, String) -> Unit) {
        GrpcClient.addParticipants(roomId, users) { success, msg ->
            if (success) loadGroupData(roomId)
            onComplete(success, msg)
        }
    }

    fun getAvailableContacts(onComplete: (List<String>) -> Unit) {
        val username = GrpcClient.getCurrentUsername() ?: return
        GrpcClient.getContacts(username) { contacts ->
            val current = _groupData.value.participants
            onComplete(contacts.filter { it !in current })
        }
    }

    fun uploadGroupAvatar(context: Context, roomId: String, uri: Uri, onComplete: (AvatarUploadResult) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val mimeType = context.contentResolver.getType(uri)
                    val isGif = mimeType == "image/gif"
                    val thumbBytes: ByteArray
                    val fullBytes: ByteArray
                    val mediaType: String

                    if (isGif) {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        thumbBytes = inputStream?.readBytes() ?: byteArrayOf()
                        inputStream?.close()
                        fullBytes = thumbBytes
                        mediaType = "image/gif"
                    } else {
                        val thumb = resizeImage(context, uri, 512, 512)
                        val full = resizeImage(context, uri, 1920, 1920)
                        if (thumb == null || full == null) { onComplete(AvatarUploadResult(error = "Failed to resize image")); return@withContext }
                        thumbBytes = thumb; fullBytes = full; mediaType = "image/jpeg"
                    }

                    if (thumbBytes.isEmpty()) { onComplete(AvatarUploadResult(error = "Failed to read image")); return@withContext }

                    val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("avatar", if (isGif) "avatar.gif" else "avatar.jpg", thumbBytes.toRequestBody(mediaType.toMediaTypeOrNull()))
                        .addFormDataPart("avatar_full", if (isGif) "avatar_full.gif" else "avatar_full.jpg", fullBytes.toRequestBody(mediaType.toMediaTypeOrNull()))
                        .build()

                    val url = "${lavender.client.android.data.session.CredentialStore.getHttpServerUrl(context)}/upload-avatar"
                    val request = Request.Builder().url(url).post(requestBody).build()
                    val response = HttpClient.client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val body = response.body.string()
                        val thumbUrl = """"url"\s*:\s*"([^"]+)"""".toRegex().find(body)?.groupValues?.get(1) ?: ""
                        val fullUrl = """"full_url"\s*:\s*"([^"]+)"""".toRegex().find(body)?.groupValues?.get(1) ?: ""
                        if (thumbUrl.isNotEmpty()) {
                            val currentMe = GrpcClient.getCurrentUsername() ?: ""
                            GrpcClient.updateChatAvatar(roomId, thumbUrl, currentMe, fullUrl) { success, msg ->
                                if (success) {
                                    GrpcClient.updateAvatarCache(roomId, thumbUrl, fullUrl)
                                    _groupData.value = _groupData.value.copy(avatarUrl = thumbUrl, fullAvatarUrl = fullUrl)
                                }
                                onComplete(AvatarUploadResult(thumbUrl = if (success) thumbUrl else "", fullUrl = if (success) fullUrl else "", error = if (success) "" else msg))
                            }
                        } else {
                            onComplete(AvatarUploadResult(error = "Failed to parse response"))
                        }
                    } else {
                        onComplete(AvatarUploadResult(error = "Upload failed: ${response.code}"))
                    }
                } catch (e: Exception) {
                    onComplete(AvatarUploadResult(error = "Error: ${e.message}"))
                }
            }
        }
    }

    private fun resizeImage(context: Context, uri: Uri, maxWidth: Int, maxHeight: Int): ByteArray? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()
        val imageStream = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = android.graphics.BitmapFactory.decodeStream(imageStream)
        imageStream.close() ?: return null
        if (bitmap == null) return null
        val width = bitmap.width; val height = bitmap.height
        val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val scaled = if (scale < 1) bitmap.scale((width * scale).toInt(), (height * scale).toInt()) else bitmap
        val output = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, if (maxWidth > 1000) 90 else 85, output)
        val bytes = output.toByteArray()
        scaled.recycle(); bitmap.recycle()
        return bytes
    }

    fun observeOnlineUsers() {
        viewModelScope.launch {
            GrpcClient.users.collect { users -> _onlineUsers.value = users.toSet() }
        }
    }
}
