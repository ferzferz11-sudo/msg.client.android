package lavender.client.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.app.DownloadManager
import android.content.Context
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import lavender.client.android.data.db.toDomain
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.models.Message
import lavender.client.android.data.session.SessionManager
import lavender.client.android.databinding.ActivityShareReceiverBinding
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import lavender.client.android.network.HttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URL
import java.util.regex.Pattern

class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareReceiverBinding
    private lateinit var chatAdapter: ShareChatAdapter
    private var sharedText: String = ""
    private var sharedUri: Uri? = null
    private var videoInfo: VideoInfo? = null
    private var linkPreview: LinkPreview? = null
    private var selectedChat: ChatInfo? = null
    private var username: String = ""

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        SessionManager.initFromPrefs(this)
        username = SessionManager.session.value.username

        // Apply theme before setting content view
        val currentTheme = ThemeStore.currentTheme()
        ThemeUtils.applyThemeToActivity(this, currentTheme)
        
        binding = ActivityShareReceiverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle shared intent
        handleSharedIntent()
        
        // Setup UI
        setupUI()
        
        // Load chats
        loadChats()
    }

    private fun handleSharedIntent() {
        if (intent.action == Intent.ACTION_SEND) {
            val type = intent.type
            if (type == "text/plain") {
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                binding.sharedTextView.text = sharedText
                binding.sharedContentCard.isVisible = sharedText.isNotEmpty()

                // Check for video links
                videoInfo = extractVideoInfo(sharedText)
                if (videoInfo != null) {
                    showVideoPreview(videoInfo!!)
                } else {
                    // Check for regular URL to fetch link preview
                    val url = extractUrl(sharedText)
                    if (url != null) {
                        fetchLinkPreview(url)
                    }
                }
            } else if (type != null && (type.startsWith("image/") || type.startsWith("video/"))) {
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                binding.sharedTextView.text = sharedText
                binding.sharedContentCard.isVisible = sharedText.isNotEmpty()
                
                sharedUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (sharedUri != null) {
                    showFilePreview(sharedUri!!, type)
                }
            }
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

    private fun fetchLinkPreview(url: String) {
        lifecycleScope.launch {
            val preview = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()
                    
                    val response = HttpClient.client.newCall(request).execute()
                    val html = response.body.string()
                    
                    // Extract Open Graph metadata
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
                    e.printStackTrace()
                    null
                }
            }
            
            if (preview != null) {
                linkPreview = preview
                showLinkPreview(preview)
            }
        }
    }

    private fun extractMetaTag(html: String, property: String): String? {
        // Try property first (Open Graph)
        val propPattern = Pattern.compile("<meta[^>]+property=\"$property\"[^>]+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
        val propMatcher = propPattern.matcher(html)
        if (propMatcher.find()) {
            return propMatcher.group(1)
        }
        
        // Try name attribute
        val namePattern = Pattern.compile("<meta[^>]+name=\"$property\"[^>]+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
        val nameMatcher = namePattern.matcher(html)
        if (nameMatcher.find()) {
            return nameMatcher.group(1)
        }
        
        // Try title tag for title
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

    private fun showLinkPreview(preview: LinkPreview) {
        binding.videoPreviewCard.isVisible = true
        binding.videoTitleText.text = preview.title
        binding.videoPlatformText.text = preview.url
        binding.playIcon.isVisible = false
        binding.watchVideoButton.isVisible = false
        binding.downloadPreviewButton.isVisible = false
        
        Glide.with(this)
            .load(preview.imageUrl)
            .placeholder(R.drawable.ic_image_placeholder)
            .error(R.drawable.ic_image_placeholder)
            .into(binding.videoThumbnail)
    }

    private fun showFilePreview(uri: Uri, mimeType: String) {
        binding.videoPreviewCard.isVisible = true
        val isVideo = mimeType.startsWith("video/")
        binding.videoTitleText.text = if (isVideo) getString(R.string.video_preview) else getString(R.string.image_placeholder)
        binding.videoPlatformText.text = mimeType
        binding.playIcon.isVisible = isVideo
        
        Glide.with(this)
            .load(uri)
            .placeholder(R.drawable.ic_default_avatar)
            .into(binding.videoThumbnail)
            
        binding.watchVideoButton.isVisible = isVideo
        binding.watchVideoButton.setOnClickListener {
            val intent = Intent(this, VideoPlayerActivity::class.java).apply {
                putExtra("VIDEO_URL", uri.toString())
                putExtra("IS_LOCAL", true)
            }
            startActivity(intent)
        }
    }

    private fun extractVideoInfo(text: String): VideoInfo? {
        // YouTube URL patterns
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

        // Direct video link patterns
        val videoExtensions = listOf(".mp4", ".webm", ".mkv", ".mov")
        val words = text.split("\\s+".toRegex())
        for (word in words) {
            if (Patterns.WEB_URL.matcher(word).matches()) {
                val lowerWord = word.lowercase()
                if (videoExtensions.any { lowerWord.endsWith(it) || lowerWord.contains("$it?") }) {
                    return VideoInfo(
                        title = "Video File",
                        thumbnailUrl = word, // Glide can try to load thumbnail from video URL
                        videoUrl = word,
                        platform = "Direct Link"
                    )
                }
            }
        }
        
        return null
    }

    private fun showVideoPreview(info: VideoInfo) {
        binding.videoPreviewCard.isVisible = true
        binding.videoTitleText.text = info.title
        binding.videoPlatformText.text = info.platform
        binding.playIcon.isVisible = true
        
        Glide.with(this)
            .load(info.thumbnailUrl)
            .placeholder(R.drawable.ic_default_avatar)
            .into(binding.videoThumbnail)
        
        val isYouTube = info.platform == "YouTube"
        binding.downloadPreviewButton.isVisible = !isYouTube && info.platform == "Direct Link"
        binding.downloadPreviewButton.setOnClickListener {
            downloadVideo(info.videoUrl)
        }

        binding.watchVideoButton.setOnClickListener {
            if (isYouTube) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.videoUrl))
                startActivity(intent)
            } else {
                val intent = Intent(this, VideoPlayerActivity::class.java).apply {
                    putExtra("VIDEO_URL", info.videoUrl)
                    putExtra("IS_LOCAL", false)
                }
                startActivity(intent)
            }
        }
    }

    private fun downloadVideo(url: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Downloading Video")
                .setDescription(getString(R.string.share_app_description))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "video_${System.currentTimeMillis()}.mp4")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            Toast.makeText(this, getString(R.string.loading), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        binding.sendButton.setOnClickListener {
            if (selectedChat == null) {
                Toast.makeText(this, R.string.select_chat_to_send, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            sendMessageToChat(selectedChat!!)
        }
        
        // Setup chats recycler
        chatAdapter = ShareChatAdapter { chat ->
            selectedChat = chat
            chatAdapter.setSelectedChat(chat)
            binding.selectedChatLabel.isVisible = true
            binding.selectedChatText.isVisible = true
            binding.selectedChatText.text = chat.getDisplayName(username)
        }
        
        binding.chatsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ShareReceiverActivity)
            adapter = chatAdapter
        }
    }

    private fun loadChats() {
        lifecycleScope.launch {
            val chatInfos = withContext(Dispatchers.IO) {
                // Get from local DB and convert to ChatInfo
                val db = lavender.client.android.data.db.AppDatabase.getDatabase(this@ShareReceiverActivity)
                val list = db.chatDao().getAllChats().map { it.toDomain() }.toMutableList()
                
                // Ensure Favorites is at the top
                val favoritesId = "favorites_$username"
                val favoritesIndex = list.indexOfFirst { it.id == favoritesId }
                
                if (favoritesIndex != -1) {
                    val fav = list.removeAt(favoritesIndex)
                    list.add(0, fav)
                } else if (username.isNotEmpty()) {
                    // Create virtual Favorites chat if not in DB
                    list.add(0, ChatInfo(
                        id = favoritesId,
                        name = getString(R.string.favorites),
                        type = "direct",
                        lastMessageText = getString(R.string.favorites_description)
                    ))
                }
                
                list
            }
            
            if (chatInfos.isEmpty()) {
                binding.noChatsText.isVisible = true
                binding.chatsRecyclerView.isVisible = false
            } else {
                binding.noChatsText.isVisible = false
                binding.chatsRecyclerView.isVisible = true
                chatAdapter.submitList(chatInfos)
            }
        }
    }

    private fun sendMessageToChat(chat: ChatInfo) {
        lifecycleScope.launch {
            val session = SessionManager.session.value
            if (session.username.isEmpty()) {
                Toast.makeText(this@ShareReceiverActivity, R.string.username_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }

            binding.sendButton.isEnabled = false
            
            // Upload file if present
            var imageUrl = ""
            if (sharedUri != null) {
                Toast.makeText(this@ShareReceiverActivity, R.string.loading, Toast.LENGTH_SHORT).show()
                imageUrl = uploadFile(sharedUri!!) ?: ""
                if (imageUrl.isEmpty()) {
                    Toast.makeText(this@ShareReceiverActivity, getString(R.string.failed_to_upload_file), Toast.LENGTH_SHORT).show()
                    binding.sendButton.isEnabled = true
                    return@launch
                }
            }

            // If we have link preview image and no other image, download and upload it
            if (imageUrl.isEmpty() && linkPreview?.imageUrl?.isNotEmpty() == true) {
                Toast.makeText(this@ShareReceiverActivity, R.string.loading, Toast.LENGTH_SHORT).show()
                imageUrl = downloadAndUploadImage(linkPreview!!.imageUrl) ?: ""
            }

            // Build message text with video link if present
            val messageText = if (videoInfo != null) {
                if (sharedText.isNotEmpty()) {
                    "$sharedText\n\n${getString(R.string.video_preview)}: ${videoInfo!!.videoUrl}"
                } else {
                    "${getString(R.string.video_preview)}: ${videoInfo!!.videoUrl}"
                }
            } else {
                sharedText
            }

            // Ensure we are connected and authenticated
            if (GrpcClient.connectionStatus.value != ConnectionStatus.READY) {
                GrpcClient.startChatV2(chat.id) { /* ignore */ }
                
                // Wait for READY status
                var retries = 0
                while (GrpcClient.connectionStatus.value != ConnectionStatus.READY && retries < 10) {
                    delay(500)
                    retries++
                }
            }

            val message = Message(
                user = session.username,
                text = messageText,
                timestamp = System.currentTimeMillis(),
                roomId = chat.id,
                imageUrl = imageUrl
            )
            
            GrpcClient.sendMessageV2(message)
            
            Toast.makeText(this@ShareReceiverActivity, getString(R.string.messages_forwarded), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private suspend fun uploadFile(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val stream = contentResolver.openInputStream(uri)
            val bytes = stream?.readBytes()
            stream?.close()
            if (bytes == null) return@withContext null

            val type = contentResolver.getType(uri) ?: "application/octet-stream"
            val isImage = type.startsWith("image/")
            val endpoint = if (isImage) "upload-image" else "upload-file"
            val formKey = if (isImage) "image" else "file"
            val fileName = getFileName(uri) ?: (if (isImage) "image.jpg" else "file")

            val body = MultipartBody.Part.createFormData(
                formKey, 
                fileName, 
                bytes.toRequestBody(type.toMediaTypeOrNull())
            )
            
            val request = Request.Builder()
                .url("${lavender.client.android.data.session.CredentialStore.getHttpServerUrl(this@ShareReceiverActivity)}/$endpoint")
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
            e.printStackTrace()
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
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
                result = result.substring(cut + 1)
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
            
            val uploadRequest = Request.Builder()
                .url("${lavender.client.android.data.session.CredentialStore.getHttpServerUrl(this@ShareReceiverActivity)}/upload-image")
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
            e.printStackTrace()
            null
        }
    }

    // Adapter for chat selection
    inner class ShareChatAdapter(
        private val onChatSelected: (ChatInfo) -> Unit
    ) : RecyclerView.Adapter<ShareChatAdapter.ViewHolder>() {

        private var chats = listOf<ChatInfo>()
        private var selectedChatId: String? = null

        fun submitList(newChats: List<ChatInfo>) {
            chats = newChats
            notifyDataSetChanged()
        }

        fun setSelectedChat(chat: ChatInfo) {
            selectedChatId = chat.id
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_share_chat, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(chats[position])
        }

        override fun getItemCount() = chats.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val chatName: TextView = itemView.findViewById(R.id.tvChatName)
            private val chatType: TextView = itemView.findViewById(R.id.tvChatType)
            private val selectedIndicator: ImageView = itemView.findViewById(R.id.selectedIndicator)
            private val avatarView: ImageView = itemView.findViewById(R.id.chatAvatar)

            fun bind(chat: ChatInfo) {
                chatName.text = chat.getDisplayName(username)
                chatType.text = if (chat.type == "direct") getString(R.string.direct_chat_type) else getString(R.string.group_chat_type)
                
                selectedIndicator.isVisible = chat.id == selectedChatId
                
                itemView.setOnClickListener {
                    onChatSelected(chat)
                }
                
                // Load avatar
                val theme = ThemeStore.currentTheme()
                ThemeUtils.applyDefaultAvatar(avatarView, theme)
            }
        }
    }
}
