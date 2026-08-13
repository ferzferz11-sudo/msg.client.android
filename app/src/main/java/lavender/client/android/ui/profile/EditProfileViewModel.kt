package lavender.client.android.ui.profile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.GrpcCompanyClient
import lavender.client.android.data.grpc.ProfileClient
import lavender.client.android.data.proto.GetProfileResponseProto
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.session.SessionManager
import lavender.client.android.network.HttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class ProfileUiState(
    val isLoading: Boolean = false,
    val profile: GetProfileResponseProto? = null,
    val avatarUrl: String = "",
    val fullAvatarUrl: String = "",
    val companyId: String = "",
    val companyName: String = "",
    val companyPosition: String = "",
    val companyLogoUrl: String = "",
    val hasMultipleCompanies: Boolean = false,
    val companyCount: Int = 0,
    val error: String? = null,
    val successMessage: String? = null
)

data class AvatarUploadState(
    val isUploading: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null
)

class EditProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _avatarState = MutableStateFlow(AvatarUploadState())
    val avatarState: StateFlow<AvatarUploadState> = _avatarState.asStateFlow()

    private val _initialBio = MutableStateFlow("")
    val initialBio: StateFlow<String> = _initialBio.asStateFlow()

    private val grpcClient = GrpcClient

    @Suppress("UNUSED_PARAMETER")
    fun loadProfile(username: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val profile = ProfileClient.getProfile(getApplication())
                Log.d("EditProfile", "Profile received: bio='${profile?.bio}', status='${profile?.status}', avatarUrl='${profile?.avatarUrl}'")

                if (profile != null) {
                    _initialBio.value = profile.bio
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        profile = profile,
                        companyId = profile.companyId,
                        companyName = profile.companyName,
                        companyPosition = formatCompanyPosition(profile.positionTitle, profile.positionLevel)
                    )

                    if (profile.companyId.isNotEmpty()) {
                        loadCompanyDetails(profile.companyId)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                Log.e("EditProfile", "Failed to load profile", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private suspend fun loadCompanyDetails(companyId: String) {
        try {
            val companyResp = withContext(Dispatchers.IO) {
                GrpcCompanyClient.getCompany(companyId)
            }
            val logoUrl = companyResp?.company?.avatarUrl
            _uiState.value = _uiState.value.copy(companyLogoUrl = logoUrl ?: "")

            val companiesResponse = GrpcCompanyClient.getUserCompanies()
            if (companiesResponse != null && companiesResponse.companies.size > 1) {
                _uiState.value = _uiState.value.copy(
                    hasMultipleCompanies = true,
                    companyCount = companiesResponse.companies.size,
                    companyPosition = _uiState.value.companyPosition + " (${companiesResponse.companies.size})"
                )
            }
        } catch (e: Exception) {
            Log.e("EditProfile", "Failed to load company details", e)
        }
    }

    fun loadAvatar(username: String) {
        grpcClient.getUserAvatar(username, grpcClient.getUserId() ?: "") { avatarUrl ->
            viewModelScope.launch {
                if (avatarUrl.isNotEmpty()) {
                    val fullUrl = grpcClient.getFullAvatarUrl(username) ?: avatarUrl
                    _uiState.value = _uiState.value.copy(
                        avatarUrl = avatarUrl,
                        fullAvatarUrl = fullUrl
                    )
                }
            }
        }
    }

    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            _avatarState.value = AvatarUploadState(isUploading = true)
            try {
                val context = getApplication<Application>()
                val mimeType = context.contentResolver.getType(uri)
                val isGif = mimeType == "image/gif"

                val thumbBytes: ByteArray
                val fullBytes: ByteArray?
                val mediaType: String

                if (isGif) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    thumbBytes = inputStream?.readBytes() ?: byteArrayOf()
                    fullBytes = null
                    inputStream?.close()
                    mediaType = "image/gif"
                } else {
                    val resizedBytes = resizeImage(uri, 256, 256)
                    val fullResizedBytes = resizeImage(uri, 1920, 1920)

                    if (resizedBytes == null) {
                        _avatarState.value = AvatarUploadState(error = "Failed to resize image")
                        return@launch
                    }

                    thumbBytes = resizedBytes
                    fullBytes = fullResizedBytes
                    mediaType = "image/jpeg"
                }

                if (thumbBytes.isEmpty()) {
                    _avatarState.value = AvatarUploadState(error = "Failed to read image")
                    return@launch
                }

                val requestBodyBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("avatar", if (isGif) "avatar.gif" else "avatar.jpg", thumbBytes.toRequestBody(mediaType.toMediaTypeOrNull()))

                if (fullBytes != null && fullBytes.isNotEmpty()) {
                    requestBodyBuilder.addFormDataPart("avatar_full", "avatar_full.jpg", fullBytes.toRequestBody(mediaType.toMediaTypeOrNull()))
                }

                val requestBody = requestBodyBuilder.build()
                val serverUrl = CredentialStore.getHttpServerUrl(context)
                val request = Request.Builder()
                    .url("$serverUrl/upload-avatar")
                    .post(requestBody)
                    .build()

                val response = withContext(Dispatchers.IO) {
                    HttpClient.client.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    val (url, fullUrl) = extractUrlsFromResponse(responseBody)

                    if (url.isNotEmpty()) {
                        val success = ProfileClient.updateAvatar(
                            context = context,
                            avatarUrl = url,
                            fullAvatarUrl = fullUrl
                        )

                        if (success) {
                            _uiState.value = _uiState.value.copy(
                                avatarUrl = url,
                                fullAvatarUrl = fullUrl.ifEmpty { url }
                            )
                            _avatarState.value = AvatarUploadState()
                            grpcClient.updateAvatarCache(
                                SessionManager.session.value.username,
                                url,
                                fullUrl.ifEmpty { url }
                            )
                        } else {
                            _avatarState.value = AvatarUploadState(error = "Failed to update avatar")
                        }
                    } else {
                        _avatarState.value = AvatarUploadState(error = "Failed to parse response")
                    }
                } else {
                    _avatarState.value = AvatarUploadState(error = "Upload failed: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("EditProfile", "Avatar upload failed", e)
                _avatarState.value = AvatarUploadState(error = e.message)
            }
        }
    }

    fun deleteAvatar() {
        viewModelScope.launch {
            _avatarState.value = AvatarUploadState(isUploading = true)
            try {
                val context = getApplication<Application>()
                val success = ProfileClient.updateAvatar(
                    context = context,
                    avatarUrl = "",
                    fullAvatarUrl = ""
                )
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        avatarUrl = "",
                        fullAvatarUrl = "",
                        successMessage = getApplication<Application>().getString(lavender.client.android.R.string.avatar_deleted)
                    )
                    _avatarState.value = AvatarUploadState()
                    grpcClient.updateAvatarCache(
                        SessionManager.session.value.username,
                        "",
                        ""
                    )
                } else {
                    _avatarState.value = AvatarUploadState(error = "Failed to delete avatar")
                }
            } catch (e: Exception) {
                Log.e("EditProfile", "Avatar delete failed", e)
                _avatarState.value = AvatarUploadState(error = e.message)
            }
        }
    }

    private suspend fun resizeImage(uri: Uri, maxWidth: Int, maxHeight: Int): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            val imageStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val bitmap = BitmapFactory.decodeStream(imageStream)
            imageStream.close()

            if (bitmap == null) return@withContext null

            val width = bitmap.width
            val height = bitmap.height
            val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)

            val scaledBitmap = if (scale < 1) {
                Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
            } else {
                bitmap
            }

            val outputStream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val bytes = outputStream.toByteArray()
            scaledBitmap.recycle()
            bitmap.recycle()

            bytes
        } catch (e: Exception) {
            Log.e("EditProfile", "Image resize failed", e)
            null
        }
    }

    private fun extractUrlsFromResponse(response: String): Pair<String, String> {
        val urlPattern = """"url"\s*:\s*"([^"]+)"""".toRegex()
        val fullUrlPattern = """"full_url"\s*:\s*"([^"]+)"""".toRegex()

        val urlMatch = urlPattern.find(response)
        val fullUrlMatch = fullUrlPattern.find(response)

        val url = urlMatch?.groupValues?.get(1) ?: ""
        val fullUrl = fullUrlMatch?.groupValues?.get(1) ?: ""

        if (url.isEmpty() && response.startsWith("http")) {
            return Pair(response.trim(), "")
        }

        return Pair(url, fullUrl)
    }

    fun updateBio(newBio: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val success = ProfileClient.updateProfile(
                    context = getApplication(),
                    bio = newBio,
                    status = ""
                )

                if (success) {
                    _initialBio.value = newBio
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        profile = _uiState.value.profile?.copy(bio = newBio),
                        successMessage = "Bio saved"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to update bio")
                }
            } catch (e: Exception) {
                Log.e("EditProfile", "Bio update failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updateUsername(oldUsername: String, newUsername: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                grpcClient.updateUsername(oldUsername, newUsername) { success, message ->
                    viewModelScope.launch {
                        if (success) {
                            val context = getApplication<Application>()
                            CredentialStore.setCredentials(
                                context = context,
                                username = newUsername,
                                password = password,
                                userId = SessionManager.session.value.userId,
                                email = SessionManager.session.value.email,
                                serverAddress = CredentialStore.getServerAddress(context)
                            )
                            val prefs = context.getSharedPreferences("lavender_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putString("last_logged_username", newUsername).apply()

                            SessionManager.updateSession(username = newUsername)
                            _uiState.value = _uiState.value.copy(isLoading = false, successMessage = message)
                        } else {
                            _uiState.value = _uiState.value.copy(isLoading = false, error = message)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("EditProfile", "Username update failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updatePassword(username: String, oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                grpcClient.updatePassword(username, oldPassword, newPassword) { success, message ->
                    viewModelScope.launch {
                        if (success) {
                            _uiState.value = _uiState.value.copy(isLoading = false, successMessage = message)
                        } else {
                            _uiState.value = _uiState.value.copy(isLoading = false, error = message)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("EditProfile", "Password update failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun deleteProfile(password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val success = ProfileClient.deleteProfile(getApplication(), password)
                if (success) {
                    _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Profile deleted")
                    grpcClient.disconnect()
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to delete profile")
                }
            } catch (e: Exception) {
                Log.e("EditProfile", "Profile deletion failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createCompany(companyName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = GrpcCompanyClient.createCompany(companyName)
                if (response?.success == true) {
                    val newCompanyId = response.company?.id ?: ""
                    GrpcCompanyClient.setPrimaryCompany(newCompanyId)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        companyId = newCompanyId,
                        companyName = companyName,
                        successMessage = "Company created"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to create company")
                }
            } catch (e: Exception) {
                Log.e("EditProfile", "Company creation failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun setPrimaryCompany(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = GrpcCompanyClient.setPrimaryCompany(companyId)
                if (response?.success == true) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        companyId = companyId,
                        successMessage = "Company updated"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to update company")
                }
            } catch (e: Exception) {
                Log.e("EditProfile", "Set primary company failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    private fun formatCompanyPosition(positionTitle: String, positionLevel: Int): String {
        val context = getApplication<Application>()
        val levelName = when (positionLevel) {
            0 -> context.getString(lavender.client.android.R.string.employee)
            1 -> context.getString(lavender.client.android.R.string.manager)
            2 -> context.getString(lavender.client.android.R.string.top_manager)
            3 -> context.getString(lavender.client.android.R.string.owner)
            else -> positionTitle
        }
        if (positionTitle.isEmpty()) return levelName
        val englishNames = mapOf(0 to "Employee", 1 to "Manager", 2 to "Top Manager", 3 to "Owner")
        val englishName = englishNames[positionLevel]
        return if (englishName != null && positionTitle.equals(englishName, ignoreCase = true)) {
            levelName
        } else if (positionTitle != levelName) {
            "$positionTitle ($levelName)"
        } else {
            levelName
        }
    }
}
