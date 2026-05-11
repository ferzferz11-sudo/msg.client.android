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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.regex.Pattern

class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareReceiverBinding
    private lateinit var chatAdapter: ShareChatAdapter
    private var sharedText: String = ""
    private var sharedUri: Uri? = null
    private var videoInfo: VideoInfo? = null
    private var selectedChat: ChatInfo? = null
    private var username: String = ""

    data class VideoInfo(
        val title: String,
        val thumbnailUrl: String,
        val videoUrl: String,
        val platform: String
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
                .setDescription("Lavender Messenger")
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
            binding.selectedChatText.text = chat.name
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
                    Toast.makeText(this@ShareReceiverActivity, "Failed to upload file", Toast.LENGTH_SHORT).show()
                    binding.sendButton.isEnabled = true
                    return@launch
                }
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
                GrpcClient.startChat(session.username, session.password, "") { /* ignore */ }
                
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
            
            GrpcClient.sendMessage(message)
            
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
                .url("http://159.195.38.145:8082/$endpoint")
                .post(MultipartBody.Builder().setType(MultipartBody.FORM).addPart(body).build())
                .build()

            val response = OkHttpClient().newCall(request).execute()
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
            private val chatName: TextView = itemView.findViewById(R.id.chatName)
            private val chatType: TextView = itemView.findViewById(R.id.chatType)
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
