package lavender.client.android.ui.chat.message

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lavender.client.android.MapPickerActivity
import lavender.client.android.R
import lavender.client.android.audio.AudioUploader
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message
import lavender.client.android.ui.adapter.MentionAdapter
import lavender.client.android.ui.audio.AudioRecordingView
import lavender.client.android.ui.widget.ActionBottomSheet
import lavender.client.android.ui.widget.SheetAction
import lavender.client.android.ui.widget.StandardBottomSheet
import lavender.client.android.ui.widget.WidgetManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.data.ThemeMappers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import lavender.client.android.data.grpc.GrpcClientExtensionsKt.*

/**
 * Chat input area: text input, send button, attachments, audio recording, emoji picker, mentions.
 */
class ChatInputDelegate(
    private val activity: AppCompatActivity,
    private val grpcClient: GrpcClient
) {
    lateinit var messageInput: EditText
    lateinit var sendButton: ImageButton
    lateinit var attachButton: ImageButton
    lateinit var audioButton: ImageButton
    lateinit var mentionContainer: View
    lateinit var mentionList: RecyclerView
    lateinit var imagePreviewScroll: HorizontalScrollView
    lateinit var imagePreviewContainer: LinearLayout

    private lateinit var mentionAdapter: MentionAdapter
    private val selectedImageUris = mutableListOf<Uri>()
    private var typingJob: Job? = null
    private var isTypingSignalSent = false

    private var roomId: String = ""
    private var username: String = ""
    private var isDirect: Boolean = false
    private var participantsJson: String = "[]"
    private var isSecret: Boolean = false
    private var secretKeyExchanged = false
    private var replyingTo: Message? = null

    var onSendMessage: ((text: String, imageUrl: String) -> Unit)? = null
    var onTypingSignal: ((isTyping: Boolean) -> Unit)? = null
    var onReplyChanged: ((Message?) -> Unit)? = null

    private val pickImageLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uris = mutableSetOf<Uri>()
            result.data?.data?.let { uris.add(it) }
            result.data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) uris.add(clipData.getItemAt(i).uri)
            }
            if (uris.isNotEmpty()) {
                selectedImageUris.addAll(uris)
                showImagePreview()
            }
        }
    }

    private val pickFileLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uris = mutableSetOf<Uri>()
            result.data?.data?.let { uris.add(it) }
            result.data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) uris.add(clipData.getItemAt(i).uri)
            }
            if (uris.isNotEmpty()) {
                val imageUris = uris.filter { uri ->
                    val mimeType = activity.contentResolver.getType(uri)
                    mimeType?.startsWith("image/") == true
                }
                if (imageUris.isNotEmpty()) {
                    selectedImageUris.addAll(imageUris)
                    showImagePreview()
                } else {
                    uploadFiles(uris.toList(), isImage = false)
                }
            }
        }
    }

    private var currentPhotoUri: Uri? = null
    private val takePhotoLauncher = activity.registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) currentPhotoUri?.let {
            selectedImageUris.addAll(listOf(it))
            showImagePreview()
        }
    }

    private val pickLocationLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val lat = result.data?.getDoubleExtra("lat", 0.0) ?: 0.0
            val lng = result.data?.getDoubleExtra("lng", 0.0) ?: 0.0
            if (lat != 0.0 || lng != 0.0) {
                onSendMessage?.invoke("geo:$lat,$lng", "")
            }
        }
    }

    fun initViews() {
        messageInput = activity.findViewById(R.id.messageInput)
        sendButton = activity.findViewById(R.id.sendButton)
        attachButton = activity.findViewById(R.id.attachButton)
        audioButton = activity.findViewById(R.id.audioButton)
        mentionContainer = activity.findViewById(R.id.mentionContainer)
        mentionList = activity.findViewById(R.id.mentionList)
        imagePreviewScroll = activity.findViewById(R.id.imagePreviewScroll)
        imagePreviewContainer = activity.findViewById(R.id.imagePreviewContainer)

        mentionAdapter = MentionAdapter { insertMention(it) }
        mentionList.layoutManager = LinearLayoutManager(activity)
        mentionList.adapter = mentionAdapter
    }

    fun configure(roomId: String, username: String, isDirect: Boolean, participantsJson: String, isSecret: Boolean) {
        this.roomId = roomId
        this.username = username
        this.isDirect = isDirect
        this.participantsJson = participantsJson
        this.isSecret = isSecret
    }

    fun setSecretState(exchanged: Boolean) {
        secretKeyExchanged = exchanged
    }

    fun getReplyingTo(): Message? = replyingTo

    var onAudioRecord: ((File, Int) -> Unit)? = null

    fun setupListeners(audioRecordHandler: ((File, Int) -> Unit)? = null) {
        onAudioRecord = audioRecordHandler
        sendButton.setOnClickListener {
            if (selectedImageUris.isNotEmpty()) {
                sendSelectedImages()
            } else {
                val text = messageInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    onSendMessage?.invoke(text, "")
                    messageInput.text.clear()
                    hideReplyPreview()
                }
            }
        }

        attachButton.setOnClickListener { showAttachmentSheet() }
        audioButton.setOnClickListener {
            showAudioRecordingView { file, dur -> file?.let { onAudioRecord?.invoke(it, dur) } }
        }

        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                handleMention(s)
                val text = s?.toString() ?: ""
                val hasText = text.trim().isNotEmpty()
                val hasImages = selectedImageUris.isNotEmpty()
                sendButton.isVisible = hasText || hasImages
                audioButton.isVisible = !hasText && !hasImages

                if (roomId.startsWith("favorites_")) return
                if (!isTypingSignalSent && hasText) {
                    isTypingSignalSent = true
                    onTypingSignal?.invoke(true)
                }
                typingJob?.cancel()
                typingJob = activity.lifecycleScope.launch {
                    delay(3000)
                    if (isTypingSignalSent) {
                        onTypingSignal?.invoke(false)
                        isTypingSignalSent = false
                    }
                }
                if (!hasText && isTypingSignalSent) {
                    typingJob?.cancel()
                    onTypingSignal?.invoke(false)
                    isTypingSignalSent = false
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        activity.findViewById<ImageButton>(R.id.emojiButton).setOnClickListener { showEmojiPicker() }
    }

    fun resetInput() {
        messageInput.text.clear()
        selectedImageUris.clear()
        imagePreviewScroll.isVisible = false
        sendButton.isVisible = false
        audioButton.isVisible = true
        hideReplyPreview()
    }

    fun getDraftText(): String = messageInput.text?.toString()?.trim() ?: ""
    fun setDraftText(text: String) {
        messageInput.setText(text)
        messageInput.setSelection(text.length)
    }

    // ======= Mentions =======

    private fun handleMention(s: CharSequence?) {
        if (isDirect) return
        val cp = messageInput.selectionStart
        val t = s?.toString() ?: ""
        if (cp <= 0) { mentionContainer.isVisible = false; return }
        var la = -1
        for (i in (cp - 1) downTo 0) {
            val ch = t[i]
            if (ch == '@') { la = i; break }
            if (ch == ' ') break
        }
        if (la == -1 && cp > 0 && t[cp - 1] == '@') { la = cp - 1 }
        if (la != -1) {
            val q = t.substring(la + 1, cp).lowercase()
            if (participantsJson.isEmpty()) {
                mentionContainer.isVisible = false
                return
            }
            val participantsArray = try { JSONArray(participantsJson) } catch (_: Exception) { JSONArray() }
            val matches = mutableListOf<String>()
            val avatarCache = grpcClient.getAvatarCache()
            for (i in 0 until participantsArray.length()) {
                val participant = participantsArray.getString(i)
                if (participant != username && participant.lowercase().contains(q)) {
                    matches.add(participant)
                }
            }
            if (matches.isNotEmpty() || q.isEmpty()) {
                mentionAdapter.setUsers(matches, avatarCache)
                mentionContainer.isVisible = true
            } else {
                mentionContainer.isVisible = false
            }
        } else {
            mentionContainer.isVisible = false
        }
    }

    private fun insertMention(u: String) {
        val cp = messageInput.selectionStart
        val t = messageInput.text.toString()
        var la = -1
        for (i in (cp - 1) downTo 0) { if (t[i] == '@') { la = i; break }; if (t[i] == ' ') break }
        if (la != -1) {
            val nt = t.substring(0, la + 1) + u + " " + t.substring(cp)
            messageInput.setText(nt)
            messageInput.setSelection(la + u.length + 1)
        }
        mentionContainer.isVisible = false
    }

    // ======= Emoji Picker =======

    fun showEmojiPicker() {
        val sheet = StandardBottomSheet(activity, R.layout.dialog_emoji_picker)
        val emojiGrid = sheet.findViewById<android.widget.GridLayout>(R.id.emojiGrid)
        val emojis = listOf(
            "😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃","😉","😌","😍","🥰","😘","😗","😙","😚",
            "😋","😛","😝","😜","🤪","🤨","🧐","🤓","😎","🤩","🥳","😏","😒","😞","😔","😟","😕","🙁","☹️","😣",
            "😖","😫","😩","🥺","😢","😭","😤","😠","😡","🤬","🤯","😳","🥵","🥶","😱","😨","😰","😥","😓","🤔",
            "🤭","🤫","🤥","😶","😐","😑","😬","🙄","😯","😦","😧","😮","😲","🥱","😴","🤤","😪","😵","🤐","🥴",
            "🤢","🤮","🤧","🥵","🥶","😷","🤒","🤕","🤑","🤠","😈","👿","👹","👺","🤡","💩","👻","💀","☠️","👽",
            "👾","🤖","🎃","😺","😸","😹","😻","😼","😽","🙀","😿","😾","👋","🤚","🖐","✋","🖖","👌","🤏","✌️",
            "🤞","🤟","🤘","🤙","👈","👉","👆","🖕","👇","☝️","👍","👎","✊","👊","🤛","🤜","👏","🙌","👐","🤲",
            "🤝","🙏","✍️","💅","🤳","💪","🦾","🦵","🦿","🦶"
        )
        val size = (48 * activity.resources.displayMetrics.density).toInt()
        for (emoji in emojis) {
            val tv = TextView(activity).apply {
                text = emoji
                textSize = 24f
                gravity = android.view.Gravity.CENTER
                layoutParams = android.view.ViewGroup.LayoutParams(size, size)
                val v = TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, v, true)
                setBackgroundResource(v.resourceId)
                setOnClickListener {
                    val cp = messageInput.selectionStart
                    val ct = messageInput.text.toString()
                    messageInput.setText(ct.substring(0, cp) + emoji + ct.substring(cp))
                    messageInput.setSelection(cp + emoji.length)
                    sheet.dismiss()
                }
            }
            emojiGrid?.addView(tv)
        }
        sheet.show()
    }

    // ======= Attachments =======

    fun showAttachmentSheet() {
        WidgetManager.getOrCreate("attachment_sheet") { ActionBottomSheet(activity) }
            .setActions(listOf(
                SheetAction(R.id.attachCamera, R.drawable.ic_mic, activity.getString(R.string.attach_camera)) {
                    try {
                        currentPhotoUri = createImageUri()
                        if (currentPhotoUri != null) {
                            takePhotoLauncher.launch(currentPhotoUri!!)
                        } else {
                            showToast("Failed to create image file")
                        }
                    } catch (e: Exception) {
                        showToast("Could not open camera app")
                        android.util.Log.e("ChatInput", "Camera launch error", e)
                    }
                },
                SheetAction(R.id.attachGallery, R.drawable.ic_gallery, activity.getString(R.string.attach_gallery)) {
                    pickImageLauncher.launch(
                        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                    )
                },
                SheetAction(R.id.attachFile, R.drawable.attach_file_add_24, activity.getString(R.string.attach_file_label)) {
                    pickFileLauncher.launch(
                        Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "*/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                    )
                },
                SheetAction(R.id.attachLocation, R.drawable.ic_location, activity.getString(R.string.attach_location)) {
                    pickLocationLauncher.launch(Intent(activity, MapPickerActivity::class.java))
                }
            )).show()
    }

    private fun createImageUri(): Uri? {
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "temp_photo_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }
        return activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    }

    // ======= Image Preview & Upload =======

    private fun showImagePreview() {
        imagePreviewContainer.removeAllViews()
        for ((index, uri) in selectedImageUris.withIndex()) {
            val v = activity.layoutInflater.inflate(R.layout.image_preview_container, imagePreviewContainer, false)
            val iv = v.findViewById<ImageView>(R.id.previewImage)
            val rb = v.findViewById<ImageButton>(R.id.removeImageButton)
            com.bumptech.glide.Glide.with(activity).load(uri).centerCrop().into(iv)
            rb.setOnClickListener { selectedImageUris.removeAt(index); showImagePreview() }
            imagePreviewContainer.addView(v)
        }
        imagePreviewScroll.isVisible = selectedImageUris.isNotEmpty()
        val hasT = messageInput.text.trim().isNotEmpty()
        val hasI = selectedImageUris.isNotEmpty()
        sendButton.isVisible = hasT || hasI
        audioButton.isVisible = !hasT && !hasI
    }

    private fun sendSelectedImages() {
        val text = messageInput.text.toString().trim()
        val urls = mutableListOf<String>()
        var count = 0
        val total = selectedImageUris.size
        val uploadProgressContainer = activity.findViewById<View>(R.id.uploadProgressContainer)
        val uploadProgressText = activity.findViewById<TextView>(R.id.uploadProgressText)
        val uploadProgressBar = activity.findViewById<android.widget.ProgressBar>(R.id.uploadProgressBar)

        uploadProgressContainer.isVisible = true
        uploadProgressText.text = activity.getString(R.string.uploading_images, 0, total)
        uploadProgressBar.progress = 0

        selectedImageUris.forEach { uri ->
            val bytes = activity.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val body = MultipartBody.Part.createFormData("image", getFileName(uri) ?: "image.jpg",
                    bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                val req = Request.Builder()
                    .url("${lavender.client.android.data.session.CredentialStore.getHttpServerUrl(activity)}/upload-image")
                    .post(MultipartBody.Builder().setType(MultipartBody.FORM).addPart(body).build()).build()
                OkHttpClient().newCall(req).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        activity.runOnUiThread { uploadProgressContainer.isVisible = false; showToast("Upload failed") }
                    }
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        val rb = response.body.string()
                        if (!response.isSuccessful || rb.contains("404")) {
                            activity.runOnUiThread { uploadProgressContainer.isVisible = false; showToast("Server error: 404") }
                            return
                        }
                        val url = if (rb.contains("\"url\"")) try { JSONObject(rb).getString("url") } catch (_: Exception) { "" }
                        else if (rb.startsWith("http")) rb else ""
                        if (url.isNotEmpty() && !url.contains("404")) urls.add(url)
                        count++
                        activity.runOnUiThread {
                            uploadProgressBar.progress = ((count.toFloat() / total) * 100).toInt()
                            uploadProgressText.text = activity.getString(R.string.uploading_images, count, total)
                            if (count == total) {
                                uploadProgressContainer.isVisible = false
                                if (urls.isNotEmpty()) sendGalleryMessage(text, urls)
                                else showToast("Upload failed")
                            }
                        }
                    }
                })
            }
        }
    }

    private fun sendGalleryMessage(text: String, imageUrls: List<String>) {
        typingJob?.cancel()
        if (isTypingSignalSent) { isTypingSignalSent = false; onTypingSignal?.invoke(false) }
        val et = when { text.isEmpty() && imageUrls.isEmpty() -> "Message"; imageUrls.isNotEmpty() && text.isEmpty() -> ""; else -> text }
        val msg = Message(
            id = java.util.UUID.randomUUID().toString(), user = username, text = et,
            timestamp = System.currentTimeMillis(), roomId = roomId,
            imageUrl = imageUrls.firstOrNull() ?: "", imageUrls = imageUrls,
            repliedToMessageId = replyingTo?.id ?: "", repliedToUser = replyingTo?.user ?: "",
            repliedToText = replyingTo?.text ?: "", userId = grpcClient.getUserId() ?: "", isSent = false
        )
        grpcClient.addLocalMessage(msg)
        grpcClient.sendMessage(msg)
        grpcClient.deleteDraft(roomId)
        resetInput()
    }

    private fun uploadFiles(uris: List<Uri>, isImage: Boolean) {
        if (isImage && uris.size > 1) { selectedImageUris.addAll(uris); showImagePreview(); return }
        val uploadProgressBar = activity.findViewById<android.widget.ProgressBar>(R.id.uploadProgressBar)
        uris.forEach { uri ->
            uploadProgressBar.isVisible = true
            val bytes = activity.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val fn = getFileName(uri) ?: (if (isImage) "image.jpg" else "file")
                val body = MultipartBody.Part.createFormData(if (isImage) "image" else "file", fn,
                    bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                val req = Request.Builder()
                    .url("${lavender.client.android.data.session.CredentialStore.getHttpServerUrl(activity)}/${if (isImage) "upload-image" else "upload-file"}")
                    .post(MultipartBody.Builder().setType(MultipartBody.FORM).addPart(body).build()).build()
                OkHttpClient().newCall(req).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        activity.runOnUiThread { uploadProgressBar.isVisible = false; showToast("Upload failed") }
                    }
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        val rb = response.body.string()
                        if (!response.isSuccessful || rb.contains("404")) {
                            activity.runOnUiThread { uploadProgressBar.isVisible = false; showToast("Server error: 404") }
                            return
                        }
                        val url = if (rb.contains("\"url\"")) try { JSONObject(rb).getString("url") } catch (_: Exception) { "" }
                        else if (rb.startsWith("http")) rb else ""
                        activity.runOnUiThread {
                            uploadProgressBar.isVisible = false
                            if (url.isNotEmpty() && !url.contains("404")) {
                                if (isImage) sendGalleryMessage("", listOf(url))
                                else onSendMessage?.invoke("File: $fn\n$url", "")
                            } else showToast("Upload failed")
                        }
                    }
                })
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var r: String? = null
        if (uri.scheme == "content") {
            activity.contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) r = it.getString(idx)
                }
            }
        }
        if (r == null) { r = uri.path; val c = r?.lastIndexOf('/') ?: -1; if (c != -1) r = r?.substring(c + 1) }
        return r
    }

    // ======= Audio Recording =======

    fun showAudioRecordingView(onRecordingFinished: (File, Int) -> Unit) {
        if (activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1001)
            return
        }
        val sheet = StandardBottomSheet(activity)
        val recording = AudioRecordingView(activity)
        recording.applyCustomTheme(ThemeMappers.toProto(ThemeStore.currentTheme()))
        sheet.setContent(recording)
        recording.setOnRecordingFinished { file, dur ->
            sheet.dismiss()
            file?.let { onRecordingFinished(it, dur) }
        }
        recording.setOnRecordingCancelled { sheet.dismiss() }
        sheet.show()
    }

    // ======= Reply =======

    fun showReplyPreview(m: Message) {
        replyingTo = m
        onReplyChanged?.invoke(m)
    }

    fun hideReplyPreview() {
        replyingTo = null
        onReplyChanged?.invoke(null)
    }

    fun clearTypingState() {
        typingJob?.cancel()
        if (isTypingSignalSent) {
            isTypingSignalSent = false
            onTypingSignal?.invoke(false)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}
