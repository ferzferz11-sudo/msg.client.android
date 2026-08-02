package lavender.client.android.ui.share

import android.app.Application
import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.db.AppDatabase
import lavender.client.android.data.db.toDomain
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.ProfileClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.models.Message
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.session.SessionManager
import lavender.client.android.network.HttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URL
import java.util.regex.Pattern

data class VideoInfo(
    val title: String,
    val thumbnailUrl: String,
    val videoUrl: String,
    val platform: String
)

data class LinkPreview(
    val url: String,
    val title: String,
    val description: String,
    val imageUrl: String
)

data class ShareReceiverUiState(
    val isLoading: Boolean = false,
    val chats: List<ChatInfo> = emptyList(),
    val selectedChat: ChatInfo? = null,
    val sharedText: String = "",
    val videoInfo: VideoInfo? = null,
    val linkPreview: LinkPreview? = null,
    val error: String? = null,
    val successMessage: String? = null,
    val isSending: Boolean = false
)

class ShareReceiverViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ShareReceiverUiState())
    val uiState: StateFlow<ShareReceiverUiState> = _uiState.asStateFlow()

    private val username: String = SessionManager.session.value.username

    fun loadChats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val chatInfos = withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(getApplication())
                    val list = db.chatDao().getAllChats().map { it.toDomain() }.toMutableList()

                    val favoritesId = "favorites_$username"
                    val favoritesIndex = list.indexOfFirst { it.id == favoritesId }

                    if (favoritesIndex != -1) {
                        val fav = list.removeAt(favoritesIndex)
                        list.add(0, fav)
                    } else if (username.isNotEmpty()) {
                        list.add(0, ChatInfo(
                            id = favoritesId,
                            name = getApplication<Application>().getString(lavender.client.android.R.string.favorites),
                            type = "direct",
                            lastMessageText = getApplication<Application>().getString(lavender.client.android.R.string.favorites_description)
                        ))
                    }

                    list
                }

                _uiState.value = _uiState.value.copy(isLoading = false, chats = chatInfos)
            } catch (e: Exception) {
                Log.e("ShareReceiver", "Failed to load chats", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun ensureConnection() {
        viewModelScope.launch {
            if (GrpcClient.connectionStatus.value == ConnectionStatus.READY) return@launch
            val serverAddress = CredentialStore.getServerAddress(getApplication())
            if (serverAddress.isEmpty()) return@launch
            withContext(Dispatchers.IO) {
                try {
                    val parts = serverAddress.split(":")
                    val host = parts.firstOrNull() ?: serverAddress
                    val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
                    GrpcClient.connect(host, false, port, getApplication())
                } catch (e: Exception) {
                    Log.w("ShareReceiver", "Connection failed: ${e.message}")
                }
            }
        }
    }

    fun selectChat(chat: ChatInfo) {
        _uiState.value = _uiState.value.copy(selectedChat = chat)
    }

    fun setSharedText(text: String) {
        _uiState.value = _uiState.value.copy(sharedText = text)

        val videoInfo = extractVideoInfo(text)
        if (videoInfo != null) {
            _uiState.value = _uiState.value.copy(videoInfo = videoInfo)
        } else {
            val url = extractUrl(text)
            if (url != null) {
                fetchLinkPreview(url)
            }
        }
    }

    fun setSharedUri(uri: Uri, mimeType: String) {
        _uiState.value = _uiState.value.copy(sharedText = _uiState.value.sharedText)
    }

    fun sendMessageToChat(chat: ChatInfo, sharedText: String, sharedUri: Uri?, videoInfo: VideoInfo?, linkPreview: LinkPreview?) {
        viewModelScope.launch {
            try {
                val session = SessionManager.session.value
                if (session.username.isEmpty()) {
                    _uiState.value = _uiState.value.copy(error = "Username is empty")
                    return@launch
                }

                _uiState.value = _uiState.value.copy(isSending = true)

                var imageUrl = ""
                if (sharedUri != null) {
                    imageUrl = uploadFile(sharedUri) ?: ""
                    if (imageUrl.isEmpty()) {
                        _uiState.value = _uiState.value.copy(isSending = false, error = "Failed to upload file")
                        return@launch
                    }
                }

                if (imageUrl.isEmpty() && linkPreview?.imageUrl?.isNotEmpty() == true) {
                    imageUrl = downloadAndUploadImage(linkPreview.imageUrl) ?: ""
                }

                val messageText = if (videoInfo != null) {
                    if (sharedText.isNotEmpty()) {
                        "$sharedText\n\n${getApplication<Application>().getString(lavender.client.android.R.string.video_preview)}: ${videoInfo.videoUrl}"
                    } else {
                        "${getApplication<Application>().getString(lavender.client.android.R.string.video_preview)}: ${videoInfo.videoUrl}"
                    }
                } else {
                    sharedText
                }

                if (GrpcClient.connectionStatus.value != ConnectionStatus.READY) {
                    GrpcClient.startChatV2(chat.id) { /* ignore */ }
                    var retries = 0
                    while (GrpcClient.connectionStatus.value != ConnectionStatus.READY && retries < 10) {
                        kotlinx.coroutines.delay(500)
                        retries++
                    }
                    if (GrpcClient.connectionStatus.value != ConnectionStatus.READY) {
                        _uiState.value = _uiState.value.copy(isSending = false, error = "Connection failed. Please try again.")
                        return@launch
                    }
                }

                val message = Message(
                    user = session.username,
                    text = messageText,
                    timestamp = System.currentTimeMillis(),
                    roomId = chat.id,
                    imageUrl = imageUrl,
                    userId = GrpcClient.getUserId() ?: ""
                )

                GrpcClient.sendMessageV2(message)

                _uiState.value = _uiState.value.copy(isSending = false, successMessage = "Message sent")
            } catch (e: Exception) {
                Log.e("ShareReceiver", "Failed to send message", e)
                _uiState.value = _uiState.value.copy(isSending = false, error = e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun uploadFile(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val bytes = try {
                stream.use { it.readBytes() }
            } catch (e: OutOfMemoryError) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(error = "File too large")
                }
                return@withContext null
            }
            if (bytes.size > ProfileClient.maxUploadSize) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(error = "File too large")
                }
                return@withContext null
            }

            val type = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val isImage = type.startsWith("image/")
            val endpoint = if (isImage) "upload-image" else "upload-file"
            val formKey = if (isImage) "image" else "file"
            val fileName = getFileName(uri) ?: (if (isImage) "image.jpg" else "file")

            val body = MultipartBody.Part.createFormData(
                formKey,
                fileName,
                bytes.toRequestBody(type.toMediaTypeOrNull())
            )

            val serverUrl = CredentialStore.getHttpServerUrl(context)
            val request = Request.Builder()
                .url("$serverUrl/$endpoint")
                .post(MultipartBody.Builder().setType(MultipartBody.FORM).addPart(body).build())
                .build()

            val response = HttpClient.client.newCall(request).execute()
            val responseBody = response.body.string()

            if (response.isSuccessful && !responseBody.contains("404")) {
                if (responseBody.contains("\"url\":")) {
                    JSONObject(responseBody).getString("url")
                } else if (responseBody.startsWith("http")) {
                    responseBody
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ShareReceiver", "File upload failed", e)
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = getApplication<Application>().contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private suspend fun downloadAndUploadImage(imageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(imageUrl).build()
            val response = HttpClient.client.newCall(request).execute()

            if (!response.isSuccessful) return@withContext null

            val bytes = response.body.bytes()
            val contentType = response.header("Content-Type") ?: "image/jpeg"

            val body = MultipartBody.Part.createFormData(
                "image",
                "preview.jpg",
                bytes.toRequestBody(contentType.toMediaTypeOrNull())
            )

            val serverUrl = CredentialStore.getHttpServerUrl(getApplication())
            val uploadRequest = Request.Builder()
                .url("$serverUrl/upload-image")
                .post(MultipartBody.Builder().setType(MultipartBody.FORM).addPart(body).build())
                .build()

            val uploadResponse = HttpClient.client.newCall(uploadRequest).execute()
            val responseBody = uploadResponse.body.string()

            if (uploadResponse.isSuccessful && !responseBody.contains("404")) {
                if (responseBody.contains("\"url\":")) {
                    JSONObject(responseBody).getString("url")
                } else if (responseBody.startsWith("http")) {
                    responseBody
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ShareReceiver", "Image download/upload failed", e)
            null
        }
    }

    private fun extractUrl(text: String): String? {
        val words = text.split("\\s+".toRegex())
        for (word in words) {
            if (Patterns.WEB_URL.matcher(word).matches()) {
                return word
            }
        }
        return null
    }

    fun fetchLinkPreview(url: String) {
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()

                    val response = HttpClient.client.newCall(request).execute()
                    val html = response.body.string()

                    val title = extractMetaTag(html, "og:title")
                        ?: extractMetaTag(html, "title")
                        ?: url
                    val description = extractMetaTag(html, "og:description")
                        ?: extractMetaTag(html, "description")
                        ?: ""
                    val imageUrl = extractMetaTag(html, "og:image") ?: ""

                    if (imageUrl.isNotEmpty()) {
                        LinkPreview(url, title, description, resolveUrl(url, imageUrl))
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("ShareReceiver", "Link preview fetch failed", e)
                    null
                }
            }

            if (preview != null) {
                _uiState.value = _uiState.value.copy(linkPreview = preview)
            }
        }
    }

    private fun extractMetaTag(html: String, property: String): String? {
        val propPattern = Pattern.compile("<meta[^>]+property=\"$property\"[^>]+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
        val propMatcher = propPattern.matcher(html)
        if (propMatcher.find()) {
            return propMatcher.group(1)
        }

        val namePattern = Pattern.compile("<meta[^>]+name=\"$property\"[^>]+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
        val nameMatcher = namePattern.matcher(html)
        if (nameMatcher.find()) {
            return nameMatcher.group(1)
        }

        if (property == "title") {
            val titlePattern = Pattern.compile("<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE)
            val titleMatcher = titlePattern.matcher(html)
            if (titleMatcher.find()) {
                return titleMatcher.group(1)?.trim()
            }
        }

        return null
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return when {
            relativeUrl.startsWith("http") -> relativeUrl
            relativeUrl.startsWith("//") -> "https:$relativeUrl"
            relativeUrl.startsWith("/") -> {
                val base = URL(baseUrl)
                "${base.protocol}://${base.host}$relativeUrl"
            }
            else -> {
                val base = URL(baseUrl)
                val path = base.path.substringBeforeLast("/")
                "${base.protocol}://${base.host}$path/$relativeUrl"
            }
        }
    }

    fun extractVideoInfo(text: String): VideoInfo? {
        val youtubePatterns = listOf(
            Pattern.compile("https?://(?:www\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]+)"),
            Pattern.compile("https?://(?:www\\.)?youtu\\.be/([a-zA-Z0-9_-]+)"),
            Pattern.compile("https?://(?:www\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]+)")
        )

        for (pattern in youtubePatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val videoId = matcher.group(1)
                val videoUrl = matcher.group(0)
                val thumbnailUrl = "https://img.youtube.com/vi/$videoId/0.jpg"

                return VideoInfo(
                    title = "YouTube Video",
                    thumbnailUrl = thumbnailUrl,
                    videoUrl = videoUrl ?: "",
                    platform = "YouTube"
                )
            }
        }

        val videoExtensions = listOf(".mp4", ".webm", ".mkv", ".mov")
        val words = text.split("\\s+".toRegex())
        for (word in words) {
            if (Patterns.WEB_URL.matcher(word).matches()) {
                val lowerWord = word.lowercase()
                if (videoExtensions.any { lowerWord.endsWith(it) || lowerWord.contains("$it?") }) {
                    return VideoInfo(
                        title = "Video File",
                        thumbnailUrl = word,
                        videoUrl = word,
                        platform = "Direct Link"
                    )
                }
            }
        }

        return null
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
