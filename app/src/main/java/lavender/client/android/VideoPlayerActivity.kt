package lavender.client.android

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import lavender.client.android.databinding.ActivityVideoPlayerBinding

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding
    private var player: ExoPlayer? = null
    private var videoUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videoUrl = intent.getStringExtra("VIDEO_URL")
        val isLocal = intent.getBooleanExtra("IS_LOCAL", false)

        if (videoUrl.isNullOrEmpty()) {
            Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupPlayer(videoUrl!!, isLocal)
        setupUI(isLocal)
    }

    private fun setupPlayer(url: String, isLocal: Boolean) {
        player = ExoPlayer.Builder(this).build()
        binding.playerView.player = player

        val uri = Uri.parse(url)
        val mediaItem = MediaItem.fromUri(uri)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true
    }

    private fun setupUI(isLocal: Boolean) {
        binding.closeButton.setOnClickListener { finish() }

        if (!isLocal && !videoUrl!!.contains("youtube.com") && !videoUrl!!.contains("youtu.be")) {
            binding.downloadButton.isVisible = true
            binding.downloadButton.setOnClickListener {
                downloadVideo(videoUrl!!)
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
            Toast.makeText(this, getString(R.string.download_started), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
