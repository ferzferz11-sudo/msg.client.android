package lavender.client.android

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide

class BackgroundsFragment : Fragment() {

    interface BackgroundsCallback {
        fun onChatListBackgroundChanged(uri: Uri?)
        fun onChatBackgroundChanged(uri: Uri?)
        fun getChatListBackgroundUri(): Uri?
        fun getChatBackgroundUri(): Uri?
    }

    private var callback: BackgroundsCallback? = null

    private lateinit var chatListBackgroundPreview: ImageView
    private lateinit var chatBackgroundPreview: ImageView

    private var pendingSelection: String? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        when (pendingSelection) {
            "chatList" -> {
                callback?.onChatListBackgroundChanged(uri)
                updatePreviews()
            }
            "chat" -> {
                callback?.onChatBackgroundChanged(uri)
                updatePreviews()
            }
        }
        pendingSelection = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_backgrounds, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatListBackgroundPreview = view.findViewById(R.id.chatListBackgroundPreview)
        chatBackgroundPreview = view.findViewById(R.id.chatBackgroundPreview)

        view.findViewById<Button>(R.id.selectChatListBackground).setOnClickListener {
            pendingSelection = "chatList"
            pickImage.launch("image/*")
        }

        view.findViewById<Button>(R.id.removeChatListBackground).setOnClickListener {
            callback?.onChatListBackgroundChanged(null)
            updatePreviews()
        }

        view.findViewById<Button>(R.id.selectChatBackground).setOnClickListener {
            pendingSelection = "chat"
            pickImage.launch("image/*")
        }

        view.findViewById<Button>(R.id.removeChatBackground).setOnClickListener {
            callback?.onChatBackgroundChanged(null)
            updatePreviews()
        }

        updatePreviews()
    }

    fun setCallback(cb: BackgroundsCallback) {
        callback = cb
        if (::chatListBackgroundPreview.isInitialized && ::chatBackgroundPreview.isInitialized) {
            updatePreviews()
        }
    }

    private fun updatePreviews() {
        if (!::chatListBackgroundPreview.isInitialized || !::chatBackgroundPreview.isInitialized) return

        val chatListUri = callback?.getChatListBackgroundUri()
        val chatUri = callback?.getChatBackgroundUri()

        if (chatListUri != null) {
            Glide.with(this)
                .load(chatListUri)
                .centerCrop()
                .into(chatListBackgroundPreview)
        } else {
            chatListBackgroundPreview.setImageResource(android.R.color.transparent)
        }

        if (chatUri != null) {
            Glide.with(this)
                .load(chatUri)
                .centerCrop()
                .into(chatBackgroundPreview)
        } else {
            chatBackgroundPreview.setImageResource(android.R.color.transparent)
        }
    }
}
