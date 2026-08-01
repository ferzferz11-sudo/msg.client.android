package lavender.client.android

import android.Manifest
import android.content.Intent
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
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.launch
import lavender.client.android.data.calls.CallManager
import lavender.client.android.data.calls.CallNavigator
import lavender.client.android.data.calls.WebRtcClient
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.databinding.ActivityConferenceLobbyBinding
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.ui.adapter.SelectableUserAdapter
import lavender.client.android.ui.conference.ConferenceLobbyViewModel
import org.json.JSONArray
import org.webrtc.*
import java.util.*

import lavender.client.android.ui.widget.SearchableListBottomSheet

class ConferenceLobbyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConferenceLobbyBinding
    private lateinit var viewModel: ConferenceLobbyViewModel
    private var webRtcClient: WebRtcClient? = null

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            initPreview()
        }
    }
    private val eglBase = EglBase.create()

    private lateinit var invitedAdapter: InvitedUserAdapter

    companion object {
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityConferenceLobbyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ConferenceLobbyViewModel::class.java]

        val roomId = intent.getStringExtra("ROOM_ID") ?: ""
        viewModel.init(roomId)

        applyTheme()
        setupInvitedRecyclerView()

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnJoin.setOnClickListener {
            viewModel.joinConference()
            val creatorId = viewModel.uiState.value.conferenceCreatorId
            CallNavigator.navigateToCall(this, "", "", false, isConference = true, roomId = roomId, creatorId = creatorId)
            finish()
        }

        binding.btnOpenChat.setOnClickListener {
            val intent = Intent(this, NewChatActivity::class.java).apply {
                putExtra("ROOM_ID", roomId)
                putExtra("CHAT_NAME", viewModel.uiState.value.topic)
                putExtra("CHAT_TYPE", "conference")
                putExtra("PARTICIPANTS", JSONArray(viewModel.uiState.value.invited).toString())
                putExtra("CREATOR", viewModel.uiState.value.conferenceCreatorId)
            }
            startActivity(intent)
            finish()
        }

        binding.btnDelete.setOnClickListener {
            viewModel.deleteConference()
            finish()
        }

        binding.btnLeave.setOnClickListener {
            viewModel.leaveConference()
        }

        binding.btnInviteFab.setOnClickListener {
            showInviteParticipantsDialog()
        }

        binding.btnEditTopic.setOnClickListener {
            showEditTopicDialog()
        }

        binding.btnAdd5Min.setOnClickListener {
            viewModel.addFiveMinutes()
        }

        binding.btnCustomTime.setOnClickListener {
            showTimePickerDialog()
        }

        binding.btnNotify.setOnClickListener {
            viewModel.sendNotification()
            Toast.makeText(this, R.string.notifications_sent, Toast.LENGTH_SHORT).show()
        }

        binding.btnMicToggle.setOnClickListener {
            viewModel.toggleMic()
        }

        binding.btnCameraToggle.setOnClickListener {
            viewModel.toggleCamera()
        }

        binding.imgAvatarPreview.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(RelativeCornerSize(0.5f))
            .build()

        loadCurrentUserAvatar()

        if (hasPermissions()) {
            initPreview()
        } else {
            permissionLauncher.launch(PERMISSIONS)
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.tvTopic.text = state.topic
                binding.tvStartTime.text = viewModel.getTimeFormatted()

                binding.btnEditTopic.isVisible = state.isCreator
                binding.btnAdd5Min.isVisible = state.isCreator
                binding.btnCustomTime.isVisible = state.isCreator
                binding.btnDelete.isVisible = state.isCreator
                binding.btnLeave.isVisible = !state.isCreator
                binding.btnNotify.isVisible = state.isCreator && state.invited.isNotEmpty()
                binding.btnInviteFab.isVisible = state.isCreator

                binding.btnJoin.text = if (state.isCreator) getString(R.string.join) else getString(R.string.join_conference_action)

                binding.toolbar.subtitle = getString(R.string.conference_participants, state.participantCount)

                invitedAdapter.updateUsers(state.invited, state.isCreator)

                binding.btnMicToggle.setImageResource(if (state.isMicEnabled) R.drawable.ic_mic_on else R.drawable.ic_mic_off)
                binding.btnCameraToggle.setImageResource(if (state.isCameraEnabled) R.drawable.ic_videocam_on else R.drawable.ic_videocam_off)
                binding.localPreview.isVisible = state.isCameraEnabled
                binding.imgAvatarPreview.isVisible = !state.isCameraEnabled
                binding.imgNoVideo.isVisible = false

                if (!state.isCameraEnabled) {
                    loadCurrentUserAvatar()
                }

                invitedAdapter.updateAvatarCache(state.avatarCache)

                state.successMessage?.let { message ->
                    Toast.makeText(this@ConferenceLobbyActivity, message, Toast.LENGTH_SHORT).show()
                    viewModel.clearSuccess()
                    if (message.contains("ended") || message.contains("Left")) {
                        finish()
                    }
                }

                state.error?.let { error ->
                    Toast.makeText(this@ConferenceLobbyActivity, error, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }
    }

    private fun showEditTopicDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        val input = android.widget.EditText(this)
        input.setText(viewModel.uiState.value.topic)
        input.setSelection(viewModel.uiState.value.topic.length)
        input.setPadding(64, 64, 64, 64)

        builder.setTitle(R.string.edit_topic)
        builder.setView(input)
        builder.setPositiveButton(R.string.apply) { _, _ ->
            val newTopic = input.text.toString().trim()
            viewModel.updateTopic(newTopic)
        }
        builder.setNegativeButton(R.string.cancel, null)
        builder.show()
    }

    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = viewModel.uiState.value.startTime
        val timePicker = android.app.TimePickerDialog(this, { _, hour, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            viewModel.setTime(calendar.timeInMillis)
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
        timePicker.show()
    }

    private fun showInviteParticipantsDialog() {
        val sheet = SearchableListBottomSheet(this)
            .setTitle(getString(R.string.add_participants))
            .setActionButtonText(getString(R.string.send_notifications))
            .setExtraInputVisible(false)
            .setLoading(true)

        val adapter = SelectableUserAdapter(lifecycleScope, avatarCache = viewModel.uiState.value.avatarCache) { count ->
            sheet.setActionButtonEnabled(count > 0)
            sheet.setActionButtonText(if (count > 0) "${getString(R.string.send_notifications)} ($count)" else getString(R.string.send_notifications))
        }
        sheet.setAdapter(adapter)

        GrpcClient.getAllChats { allChats ->
            val chat = allChats.find { it.id == viewModel.uiState.value.roomId }
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
                        !isMe && !viewModel.uiState.value.invited.contains(it)
                    }

                    usersToInvite.forEach { username ->
                        GrpcClient.getUserAvatar(username) { /* cached */ }
                    }

                    lifecycleScope.launch {
                        sheet.setLoading(false)
                        adapter.setUsers(usersToInvite)
                    }
                } catch (_: Exception) {
                    lifecycleScope.launch { sheet.setLoading(false) }
                }
            } else {
                lifecycleScope.launch { sheet.setLoading(false) }
            }
        }

        sheet.onSearchTextChanged { query ->
            adapter.filter(query)
        }

        sheet.onActionClick {
            val selected = adapter.getSelectedUsers()
            selected.forEach { userId ->
                viewModel.inviteToConference(userId)
            }
            sheet.dismiss()
        }
        sheet.show()
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

            binding.btnOpenChat.setTextColor(pColor)
            binding.btnOpenChat.iconTint = ColorStateList.valueOf(pColor)
            binding.btnOpenChat.rippleColor = ColorStateList.valueOf(ThemeUtils.adjustAlpha(pColor, 0.1f))

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
            binding.btnLeave.setTextColor(Color.WHITE)
        } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }
    }

    private fun initPreview() {
        binding.localPreview.init(eglBase.eglBaseContext, null)
        binding.localPreview.setMirror(true)
        binding.localPreview.setEnableHardwareScaler(true)

        webRtcClient = WebRtcClient(this, eglBase.eglBaseContext, object : WebRtcClient.Observer {
            override fun onLocalStream(stream: MediaStream) {
                lifecycleScope.launch {
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

        lifecycleScope.launch {
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

    private fun setupInvitedRecyclerView() {
        val theme = ThemeStore.currentTheme()
        invitedAdapter = InvitedUserAdapter(
            theme = theme,
            onRemoveClick = { userId ->
                viewModel.removeFromConference(userId)
            }
        )
        binding.rvInvited.layoutManager = LinearLayoutManager(this)
        binding.rvInvited.adapter = invitedAdapter
    }

    private fun hasPermissions() = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
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
            } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }

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
