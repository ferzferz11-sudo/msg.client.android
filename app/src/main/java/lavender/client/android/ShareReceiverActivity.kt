package lavender.client.android

import android.util.Log
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.app.DownloadManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.session.SessionManager
import lavender.client.android.databinding.ActivityShareReceiverBinding
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.ui.share.ShareReceiverViewModel

class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareReceiverBinding
    private lateinit var chatAdapter: ShareChatAdapter
    private lateinit var viewModel: ShareReceiverViewModel
    private var sharedUri: Uri? = null
    private var sharedMimeType: String = ""
    private var username: String = ""
    private val errorHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught coroutine exception", throwable)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val currentTheme = try { ThemeStore.currentTheme() } catch (_: Exception) { lavender.client.android.theme.BuiltInThemes.dark }
            try { ThemeUtils.applyThemeToActivity(this, currentTheme) } catch (e: Exception) { Log.w(TAG, "Caught: " + e.message) }

            binding = ActivityShareReceiverBinding.inflate(layoutInflater)
            setContentView(binding.root)

            viewModel = ViewModelProvider(this)[ShareReceiverViewModel::class.java]

            handleSharedIntent()
            setupUI()
            observeViewModel()

            lifecycleScope.launch(errorHandler) {
                withContext(Dispatchers.IO) {
                    SessionManager.initFromPrefs(this@ShareReceiverActivity)
                }
                username = SessionManager.session.value.username
                viewModel.ensureConnection()
                viewModel.loadChats()
            }
        } catch (e: Exception) {
            Log.e("ShareReceiver", "Fatal error in onCreate", e)
            try {
                binding = ActivityShareReceiverBinding.inflate(layoutInflater)
                setContentView(binding.root)
                viewModel = ViewModelProvider(this)[ShareReceiverViewModel::class.java]
                handleSharedIntent()
                setupUI()
                observeViewModel()
                lifecycleScope.launch(errorHandler) {
                    withContext(Dispatchers.IO) {
                        SessionManager.initFromPrefs(this@ShareReceiverActivity)
                    }
                    username = SessionManager.session.value.username
                    viewModel.ensureConnection()
                    viewModel.loadChats()
                }
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.error) + ": ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun handleSharedIntent() {
        try {
            if (intent.action == Intent.ACTION_SEND) {
                val type = intent.type
                if (type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    binding.sharedTextView.text = sharedText
                    binding.sharedContentCard.isVisible = sharedText.isNotEmpty()
                    viewModel.setSharedText(sharedText)
                } else if (type != null && (type.startsWith("image/") || type.startsWith("video/"))) {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    binding.sharedTextView.text = sharedText
                    binding.sharedContentCard.isVisible = sharedText.isNotEmpty()

                    sharedUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    sharedMimeType = type
                    if (sharedUri != null) {
                        showFilePreview(sharedUri!!, type)
                        viewModel.setSharedUri(sharedUri!!, type)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ShareReceiver", "Error handling shared intent", e)
            Toast.makeText(this, getString(R.string.error) + ": ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun showLinkPreview(preview: lavender.client.android.ui.share.LinkPreview) {
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

    private fun showVideoPreview(info: lavender.client.android.ui.share.VideoInfo) {
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
            Toast.makeText(this, getString(R.string.failed) + ": ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.sendButton.setOnClickListener {
            val selectedChat = viewModel.uiState.value.selectedChat
            if (selectedChat == null) {
                Toast.makeText(this, R.string.select_chat_to_send, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.sendMessageToChat(
                chat = selectedChat,
                sharedText = viewModel.uiState.value.sharedText,
                sharedUri = sharedUri,
                videoInfo = viewModel.uiState.value.videoInfo,
                linkPreview = viewModel.uiState.value.linkPreview
            )
        }

        chatAdapter = ShareChatAdapter { chat ->
            viewModel.selectChat(chat)
            binding.selectedChatLabel.isVisible = true
            binding.selectedChatText.isVisible = true
            binding.selectedChatText.text = chat.getDisplayName(username)
        }

        binding.chatsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ShareReceiverActivity)
            adapter = chatAdapter
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch(errorHandler) {
            viewModel.uiState.collect { state ->
                try {
                    if (state.chats.isEmpty() && !state.isLoading) {
                        binding.noChatsText.isVisible = true
                        binding.chatsRecyclerView.isVisible = false
                    } else {
                        binding.noChatsText.isVisible = false
                        binding.chatsRecyclerView.isVisible = true
                        chatAdapter.submitList(state.chats)
                    }

                    binding.sendButton.isEnabled = !state.isSending

                    state.videoInfo?.let { showVideoPreview(it) }
                    state.linkPreview?.let { showLinkPreview(it) }

                    state.successMessage?.let { message ->
                        Toast.makeText(this@ShareReceiverActivity, message, Toast.LENGTH_SHORT).show()
                        viewModel.clearSuccess()
                        finish()
                    }

                    state.error?.let { error ->
                        Toast.makeText(this@ShareReceiverActivity, getString(R.string.error) + ": $error", Toast.LENGTH_SHORT).show()
                        viewModel.clearError()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating UI state", e)
                }
            }
        }
    }

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

                try {
                    val theme = ThemeStore.currentTheme()
                    ThemeUtils.applyDefaultAvatar(avatarView, theme)
                } catch (_: Exception) {
                    ThemeUtils.applyDefaultAvatar(avatarView, lavender.client.android.theme.BuiltInThemes.dark)
                }
            }
        }
    }

    companion object {
        private const val TAG = "ShareReceiverActivity"
    }
}
