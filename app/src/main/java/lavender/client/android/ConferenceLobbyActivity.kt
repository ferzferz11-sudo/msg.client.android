package lavender.client.android

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import lavender.client.android.data.calls.CallManager
import lavender.client.android.data.calls.CallNavigator
import lavender.client.android.data.calls.WebRtcClient
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CallMessageProto
import lavender.client.android.databinding.ActivityConferenceLobbyBinding
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.ui.adapter.SelectableUserAdapter
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.*
import java.text.SimpleDateFormat
import java.util.*

import lavender.client.android.ui.widget.SearchableListBottomSheet
import lavender.client.android.ui.widget.WidgetManager

class ConferenceLobbyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConferenceLobbyBinding
    private var webRtcClient: WebRtcClient? = null
    private val eglBase = EglBase.create()
    
    private var isMicEnabled = true
    private var isCameraEnabled = true
    private var roomId: String = ""
    private var conferenceCreatorId: String = ""
    private var isCreator = false
    private var startTime: Long = System.currentTimeMillis() + 5 * 60 * 1000 // Default +5 min
    private var currentTopic: String = ""
    private var isTopicManual = false

    private lateinit var invitedAdapter: InvitedUserAdapter
    private var avatarCache: Map<String, String> = emptyMap()
    private var currentlyInvited = setOf<String>()

    companion object {
        private const val PERMISSION_CODE = 101
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConferenceLobbyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        roomId = intent.getStringExtra("ROOM_ID") ?: ""
        
        updateDefaultTopic()

        applyTheme()
        setupInvitedRecyclerView()
        
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        binding.btnJoin.setOnClickListener {
            updateMetadataOnServer()
            CallNavigator.navigateToCall(this, "", "", false, isConference = true, roomId = roomId)
            finish()
        }

        binding.btnDelete.setOnClickListener {
            CallManager.endConference(roomId)
            finish()
        }

        binding.btnInviteFab.setOnClickListener {
            showInviteParticipantsDialog()
        }

        binding.btnEditTopic.setOnClickListener {
            showEditTopicDialog()
        }

        binding.btnAdd5Min.setOnClickListener {
            startTime += 5 * 60 * 1000
            if (!isTopicManual) updateDefaultTopic()
            updateTimeDisplay()
            updateMetadataOnServer()
        }

        binding.btnCustomTime.setOnClickListener {
            showTimePickerDialog()
        }

        binding.btnNotify.setOnClickListener {
            sendNotificationTrigger()
            Toast.makeText(this, R.string.notifications_sent, Toast.LENGTH_SHORT).show()
        }

        binding.btnMicToggle.setOnClickListener {
            isMicEnabled = !isMicEnabled
            binding.btnMicToggle.setImageResource(if (isMicEnabled) R.drawable.ic_mic_on else R.drawable.ic_mic_off)
        }

        binding.btnCameraToggle.setOnClickListener {
            isCameraEnabled = !isCameraEnabled
            binding.btnCameraToggle.setImageResource(if (isCameraEnabled) R.drawable.ic_videocam_on else R.drawable.ic_videocam_off)
            binding.localPreview.isVisible = isCameraEnabled
            binding.imgAvatarPreview.isVisible = !isCameraEnabled
            binding.imgNoVideo.isVisible = false
            if (!isCameraEnabled) {
                loadCurrentUserAvatar()
            }
        }

        binding.imgAvatarPreview.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(RelativeCornerSize(0.5f))
            .build()

        loadCurrentUserAvatar()

        if (hasPermissions()) {
            initPreview()
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_CODE)
        }
        
        observeConferenceStatus()
        observeAvatarCache()
        
        CallManager.initiateConference(roomId)
        updateTimeDisplay()
    }

    private fun updateDefaultTopic() {
        val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        currentTopic = getString(R.string.new_conference_format, sdf.format(Date(startTime)))
        binding.tvTopic.text = currentTopic
    }

    private fun showEditTopicDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        val input = android.widget.EditText(this)
        input.setText(currentTopic)
        input.setSelection(currentTopic.length)
        input.setPadding(64, 64, 64, 64)
        
        builder.setTitle(R.string.edit_topic)
        builder.setView(input)
        builder.setPositiveButton(R.string.apply) { _, _ ->
            val newTopic = input.text.toString().trim()
            if (newTopic.isNotEmpty()) {
                currentTopic = newTopic
                isTopicManual = true
                binding.tvTopic.text = currentTopic
                updateMetadataOnServer()
            }
        }
        builder.setNegativeButton(R.string.cancel, null)
        builder.show()
    }

    private fun updateMetadataOnServer() {
        if (!isCreator) return
        CallManager.updateConferenceMetadata(roomId, currentTopic, startTime)
    }

    private fun sendNotificationTrigger() {
        if (!isCreator) return
        val payload = JSONObject().apply {
            put("topic", currentTopic)
            put("start_time", startTime)
            put("trigger_notify", true)
        }.toString()
        CallManager.sendWebRtcSignal("", CallMessageProto.Type.UPDATE_CONFERENCE, payload)
    }

    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = startTime
        val timePicker = android.app.TimePickerDialog(this, { _, hour, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            startTime = calendar.timeInMillis
            if (!isTopicManual) updateDefaultTopic()
            updateTimeDisplay()
            updateMetadataOnServer()
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
        timePicker.show()
    }

    private fun updateTimeDisplay() {
        val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        binding.tvStartTime.text = getString(R.string.starts_at, sdf.format(Date(startTime)))
    }

    private fun showInviteParticipantsDialog() {
        val sheet = SearchableListBottomSheet(this)
            .setTitle(getString(R.string.add_participants))
            .setActionButtonText(getString(R.string.send_notifications))
            .setExtraInputVisible(false)
            .setLoading(true)

        val adapter = SelectableUserAdapter(lifecycleScope, avatarCache = avatarCache) { count ->
            sheet.setActionButtonEnabled(count > 0)
            sheet.setActionButtonText(if (count > 0) "${getString(R.string.send_notifications)} ($count)" else getString(R.string.send_notifications))
        }
        sheet.setAdapter(adapter)

        GrpcClient.getAllChats { allChats ->
            val chat = allChats.find { it.id == roomId }
            if (chat != null) {
                try {
                    val jsonArray = JSONArray(chat.participants)
                    val members = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) members.add(jsonArray.getString(i))
                    
                    val session = lavender.client.android.data.session.SessionManager.session.value
                    val myId = session.userId
                    val myName = session.username
                    
                    val usersToInvite = members.filter { 
                        val isMe = (it == myId || it == myName)
                        !isMe && !currentlyInvited.contains(it)
                    }
                    
                    usersToInvite.forEach { username ->
                        GrpcClient.getUserAvatar(username) { /* cached */ }
                    }
                    
                    runOnUiThread { 
                        sheet.setLoading(false)
                        adapter.setUsers(usersToInvite) 
                    }
                } catch (_: Exception) {
                    runOnUiThread { sheet.setLoading(false) }
                }
            } else {
                runOnUiThread { sheet.setLoading(false) }
            }
        }

        sheet.onSearchTextChanged { query ->
            adapter.filter(query)
        }

        sheet.onActionClick {
            val selected = adapter.getSelectedUsers()
            selected.forEach { userId ->
                CallManager.inviteToConference(roomId, userId, userId)
            }
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun observeConferenceStatus() {
        lifecycleScope.launch {
            CallManager.incomingSignals.collectLatest { signal ->
                if (signal.roomId == roomId) {
                    when (signal.type) {
                        CallMessageProto.Type.JOIN_CONFERENCE -> handlePresence(signal)
                        CallMessageProto.Type.END_CONFERENCE -> {
                            runOnUiThread {
                                Toast.makeText(this@ConferenceLobbyActivity, R.string.conference_ended, Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        }
                        else -> {
                            // Other signals like UPDATE_CONFERENCE might also contain topic/time
                            // but the server usually broadcasts JOIN_CONFERENCE for status updates
                        }
                    }
                }
            }
        }
    }

    private fun handlePresence(signal: CallMessageProto) {
        try {
            val response = JSONObject(signal.payload)
            
            // Block entry to ended or deleted conferences
            val isEnded = response.optBoolean("ended", false) || response.optBoolean("is_deleted", false)
            if (isEnded) {
                runOnUiThread {
                    Toast.makeText(this@ConferenceLobbyActivity, R.string.conference_ended, Toast.LENGTH_LONG).show()
                    finish()
                }
                return
            }

            val participants = response.optJSONObject("participants") ?: JSONObject()
            val invited = response.optJSONObject("invited") ?: JSONObject()
            conferenceCreatorId = response.optString("creator_id", "")
            
            val topic = response.optString("topic", "")
            val sTime = response.optLong("start_time", 0)
            
            if (sTime > 0) {
                startTime = sTime
            }

            val myId = GrpcClient.getUserId() ?: GrpcClient.getCurrentUsername()
            isCreator = myId == conferenceCreatorId

            runOnUiThread {
                if (topic.isNotEmpty()) {
                    currentTopic = topic
                    binding.tvTopic.text = currentTopic
                }
                
                binding.btnEditTopic.isVisible = isCreator
                binding.btnAdd5Min.isVisible = isCreator
                binding.btnCustomTime.isVisible = isCreator
                binding.btnDelete.isVisible = isCreator
                binding.btnNotify.isVisible = isCreator && invited.length() > 0
                binding.btnInviteFab.isVisible = isCreator
                
                updateTimeDisplay()
                
                val invitedList = mutableListOf<String>()
                val iKeys = invited.keys()
                while (iKeys.hasNext()) invitedList.add(invited.getString(iKeys.next()))
                
                currentlyInvited = invitedList.toSet()
                invitedAdapter.updateUsers(invitedList, isCreator)
                
                val pCount = participants.length()
                binding.toolbar.subtitle = "Участников в звонке: $pCount"
            }
        } catch (e: Exception) {
            Log.e("Lobby", "Failed to parse participants", e)
        }
    }

    private fun applyTheme() {
        val theme = ThemeStore.currentTheme()
        ThemeApplier.apply(this, theme)
        
        try {
            val bgColor = ThemeUtils.parseSafeColor(theme.backgroundColor, Color.BLACK)
            val pColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
            val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
            val onPColor = ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE)
            
            binding.lobbyRoot.setBackgroundColor(bgColor)
            binding.tvTopic.setTextColor(txtColor)
            binding.btnEditTopic.imageTintList = ColorStateList.valueOf(txtColor)
            binding.tvStartTime.setTextColor(txtColor)
            binding.tvParticipantsHeader.setTextColor(txtColor)
            binding.toolbar.setNavigationIconTint(txtColor)
            binding.toolbar.setTitleTextColor(txtColor)
            binding.toolbar.setSubtitleTextColor(ThemeUtils.adjustAlpha(txtColor, 0.7f))
            
            binding.btnJoin.backgroundTintList = ColorStateList.valueOf(pColor)
            binding.btnJoin.setTextColor(onPColor)

            binding.btnNotify.backgroundTintList = ColorStateList.valueOf(ThemeUtils.adjustAlpha(pColor, 0.8f))
            binding.btnNotify.setTextColor(onPColor)
            
            binding.btnAdd5Min.setTextColor(pColor)
            binding.btnCustomTime.setTextColor(pColor)

            binding.btnMicToggle.backgroundTintList = ColorStateList.valueOf(pColor)
            binding.btnMicToggle.imageTintList = ColorStateList.valueOf(onPColor)
            binding.btnCameraToggle.backgroundTintList = ColorStateList.valueOf(pColor)
            binding.btnCameraToggle.imageTintList = ColorStateList.valueOf(onPColor)

            binding.btnInviteFab.backgroundTintList = ColorStateList.valueOf(pColor)
            binding.btnInviteFab.imageTintList = ColorStateList.valueOf(onPColor)
            
            binding.btnDelete.setTextColor(Color.WHITE)
        } catch (_: Exception) {}
    }

    private fun initPreview() {
        binding.localPreview.init(eglBase.eglBaseContext, null)
        binding.localPreview.setMirror(true)
        binding.localPreview.setEnableHardwareScaler(true)
        
        webRtcClient = WebRtcClient(this, eglBase.eglBaseContext, object : WebRtcClient.Observer {
            override fun onLocalStream(stream: MediaStream) {
                runOnUiThread {
                    stream.videoTracks.getOrNull(0)?.addSink(binding.localPreview)
                }
            }
            override fun onRemoteStream(stream: MediaStream) {}
            override fun onRemoteTrack(track: MediaStreamTrack) {}
            override fun onIceCandidate(candidate: IceCandidate) {}
            override fun onOfferCreated(description: SessionDescription) {}
            override fun onAnswerCreated(description: SessionDescription) {}
            override fun onRemoteDescriptionSet() {}
        })
        webRtcClient?.startLocalStream(binding.localPreview)
    }

    private fun loadCurrentUserAvatar() {
        val session = lavender.client.android.data.session.SessionManager.session.value
        val url = session.fullAvatarUrl
        val theme = ThemeStore.currentTheme()
        
        runOnUiThread {
            if (url.isNotEmpty()) {
                Glide.with(this@ConferenceLobbyActivity)
                    .load(url)
                    .placeholder(R.drawable.ic_default_avatar)
                    .circleCrop()
                    .into(binding.imgAvatarPreview)
            } else {
                ThemeUtils.applyDefaultAvatar(binding.imgAvatarPreview, theme)
            }
        }
    }

    private fun observeAvatarCache() {
        lifecycleScope.launch {
            GrpcClient.avatarCacheFlow.collectLatest { cache ->
                avatarCache = cache
                invitedAdapter.updateAvatarCache(cache)
                
                val myId = GrpcClient.getUserId() ?: GrpcClient.getCurrentUsername()
                if (myId != null && cache.containsKey(myId)) {
                    loadCurrentUserAvatar()
                }
            }
        }
    }

    private fun setupInvitedRecyclerView() {
        val theme = ThemeStore.currentTheme()
        invitedAdapter = InvitedUserAdapter(
            theme = theme,
            onRemoveClick = { userId ->
                if (isCreator) CallManager.removeFromConference(roomId, userId)
            }
        )
        binding.rvInvited.layoutManager = LinearLayoutManager(this)
        binding.rvInvited.adapter = invitedAdapter
    }

    private fun hasPermissions() = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE && hasPermissions()) {
            initPreview()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webRtcClient?.close()
        binding.localPreview.release()
        eglBase.release()
    }
}

class InvitedUserAdapter(
    private val theme: lavender.client.android.theme.Theme,
    private val onRemoveClick: (String) -> Unit
) : RecyclerView.Adapter<InvitedUserAdapter.ViewHolder>() {
    private var users = listOf<String>()
    private var canRemove = false
    private var avatarCache: Map<String, String> = emptyMap()

    fun updateUsers(newUsers: List<String>, canRemove: Boolean) {
        this.users = newUsers
        this.canRemove = canRemove
        notifyDataSetChanged()
    }

    fun updateAvatarCache(newCache: Map<String, String>) {
        this.avatarCache = newCache
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_participant, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user, canRemove, avatarCache[user], theme, onRemoveClick)
    }

    override fun getItemCount() = users.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.participantName)
        private val avatarImg: ImageView = itemView.findViewById(R.id.participantAvatar)
        private val removeButton: ImageButton = itemView.findViewById(R.id.btnRemove)
        
        fun bind(name: String, canRemove: Boolean, avatarUrl: String?, theme: lavender.client.android.theme.Theme, onRemoveClick: (String) -> Unit) {
            nameText.text = name
            
            try {
                val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
                val surfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
                
                nameText.setTextColor(txtColor)
                val shape = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 24f * itemView.resources.displayMetrics.density
                    setColor(surfaceColor)
                }
                itemView.background = shape
                
                (itemView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                    lp.setMargins(0, 0, 0, (8 * itemView.resources.displayMetrics.density).toInt())
                    itemView.layoutParams = lp
                }
            } catch (_: Exception) {}

            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(itemView.context).load(avatarUrl).placeholder(R.drawable.ic_default_avatar).into(avatarImg)
            } else {
                ThemeUtils.applyDefaultAvatar(avatarImg, theme)
            }

            removeButton.isVisible = canRemove
            removeButton.setOnClickListener { onRemoveClick(name) }
        }
    }
}
