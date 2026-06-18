package lavender.client.android.ui.chat.message

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import de.hdodenhof.circleimageview.CircleImageView
import lavender.client.android.ConferenceLobbyActivity
import lavender.client.android.ProfileActivity
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import org.json.JSONArray
import lavender.client.android.data.grpc.GrpcClientExtensions.*

/**
 * Toolbar setup and management for chat screen.
 * Handles: avatar, title, subtitle, navigation icon, group avatars, lobby button, secret chat indicator.
 */
class ChatToolbarDelegate(
    private val activity: androidx.appcompat.app.AppCompatActivity,
    private val grpcClient: GrpcClient
) {
    lateinit var toolbar: MaterialToolbar
    lateinit var toolbarTitle: TextView
    lateinit var toolbarSubtitle: TextView
    lateinit var toolbarAvatar: CircleImageView
    lateinit var groupParticipantsContainer: LinearLayout
    lateinit var toolbarContent: View
    lateinit var btnLobby: ImageView

    private var roomId: String = ""
    private var username: String = ""
    private var chatName: String = ""
    private var isDirect: Boolean = false
    private var chatType: String = "group"
    private var participantsJson: String = "[]"
    private var creator: String = ""
    private var chatAvatarUrl: String = ""
    private var chatFullAvatarUrl: String = ""
    private var isSecret: Boolean = false

    fun initViews() {
        toolbar = activity.findViewById(R.id.toolbar)
        toolbarTitle = activity.findViewById(R.id.toolbarTitle)
        toolbarSubtitle = activity.findViewById(R.id.toolbarSubtitle)
        toolbarAvatar = activity.findViewById(R.id.toolbarAvatar)
        groupParticipantsContainer = activity.findViewById(R.id.groupParticipantsContainer)
        toolbarContent = activity.findViewById(R.id.toolbarContent)
        btnLobby = activity.findViewById(R.id.btnLobby)
    }

    fun configure(
        roomId: String, username: String, chatName: String,
        isDirect: Boolean, chatType: String, participantsJson: String,
        creator: String, chatAvatarUrl: String, chatFullAvatarUrl: String,
        isSecret: Boolean
    ) {
        this.roomId = roomId
        this.username = username
        this.chatName = chatName
        this.isDirect = isDirect
        this.chatType = chatType
        this.participantsJson = participantsJson
        this.creator = creator
        this.chatAvatarUrl = chatAvatarUrl
        this.chatFullAvatarUrl = chatFullAvatarUrl
        this.isSecret = isSecret
    }

    fun setup() {
        activity.setSupportActionBar(toolbar)
        activity.supportActionBar?.setDisplayShowTitleEnabled(false)
        setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.setNavigationOnClickListener { activity.onBackPressedDispatcher.onBackPressed() }

        if (isSecret) {
            setupSecretChatToolbar()
        } else if (roomId.startsWith("favorites_")) {
            setupFavoritesToolbar()
            return
        } else {
            setupNormalToolbar()
        }

        setupLobbyButton()
    }

    private fun setupSecretChatToolbar() {
        toolbarAvatar.isVisible = true
        groupParticipantsContainer.isVisible = false
        toolbarAvatar.setImageResource(R.drawable.ic_lock)
        val secretTheme = ThemeStore.currentTheme()
        toolbarAvatar.imageTintList = ColorStateList.valueOf(secretTheme.primaryColor.toColorInt())
        val secretBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(secretTheme.surfaceContainer.toColorInt())
        }
        toolbarAvatar.background = secretBg
        val secretPad = 4.dpToPx()
        toolbarAvatar.setPadding(secretPad, secretPad, secretPad, secretPad)
        toolbarSubtitle.text = activity.getString(R.string.e2ee_enabled)
        toolbarSubtitle.setTextColor(secretTheme.primaryColor.toColorInt())
    }

    private fun setupFavoritesToolbar() {
        toolbarAvatar.isVisible = true
        groupParticipantsContainer.isVisible = false
        toolbarAvatar.setImageResource(R.drawable.ic_star)
        val theme = ThemeStore.currentTheme()
        val primColor = theme.primaryColor.toColorInt()
        toolbarAvatar.imageTintList = ColorStateList.valueOf(theme.onPrimaryColor.toColorInt())
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(primColor)
        }
        toolbarAvatar.background = bg
        val p = 8.dpToPx()
        toolbarAvatar.setPadding(p, p, p, p)
        toolbarTitle.text = activity.getString(R.string.favorites)
        toolbarSubtitle.isVisible = true
        toolbarSubtitle.text = activity.getString(R.string.favorites)
        toolbarContent.setOnClickListener(null)
    }

    private fun setupNormalToolbar() {
        val effectiveAvatarUrl = if (chatAvatarUrl.isNotEmpty()) chatAvatarUrl else if (isDirect) {
            try {
                val arr = JSONArray(participantsJson)
                var other = ""
                for (i in 0 until arr.length()) {
                    val p = arr.getString(i)
                    if (p != username) { other = p; break }
                }
                if (other.isNotEmpty()) grpcClient.getAvatarCache()[other] else null
            } catch (_: Exception) { null }
        } else null

        if (isDirect || chatAvatarUrl.isNotEmpty()) {
            toolbarAvatar.isVisible = true
            groupParticipantsContainer.isVisible = false
            if (!effectiveAvatarUrl.isNullOrEmpty()) {
                com.bumptech.glide.Glide.with(activity).load(effectiveAvatarUrl)
                    .placeholder(R.drawable.ic_default_avatar).circleCrop().into(toolbarAvatar)
            } else {
                ThemeUtils.applyDefaultAvatar(toolbarAvatar, ThemeStore.currentTheme())
            }
        } else {
            toolbarAvatar.isVisible = false
            groupParticipantsContainer.isVisible = true
            setupGroupAvatars()
        }

        toolbarTitle.text = chatName
        val openProfile = View.OnClickListener {
            openProfile()
        }
        toolbarContent.setOnClickListener(openProfile)
        toolbarTitle.setOnClickListener(openProfile)
        toolbarAvatar.setOnClickListener(openProfile)
        groupParticipantsContainer.setOnClickListener(openProfile)
    }

    private fun openProfile() {
        if (chatType == "conference") {
            val intent = Intent(activity, ConferenceLobbyActivity::class.java).apply {
                putExtra("ROOM_ID", roomId)
                putExtra("CHAT_NAME", chatName)
                putExtra("PARTICIPANTS", participantsJson)
                putExtra("CREATOR", creator)
            }
            activity.startActivity(intent)
            return
        }

        val profileUsername = if (isDirect) {
            try {
                val arr = JSONArray(participantsJson)
                var other = chatName
                for (i in 0 until arr.length()) {
                    val p = arr.getString(i)
                    if (p != username) { other = p; break }
                }
                other
            } catch (_: Exception) { chatName }
        } else chatName

        val intent = Intent(activity, ProfileActivity::class.java).apply {
            putExtra("username", profileUsername)
            putExtra("is_group", !isDirect)
            putExtra("room_id", roomId)
            putExtra("avatar_url", if (isDirect) chatAvatarUrl else chatAvatarUrl)
            putExtra("full_avatar_url", chatFullAvatarUrl)
            putExtra("participants", participantsJson)
            putExtra("creator", creator)
        }
        activity.startActivity(intent)
    }

    private fun setupGroupAvatars() {
        groupParticipantsContainer.removeAllViews()
        try {
            val arr = JSONArray(participantsJson)
            for (i in 0 until arr.length().coerceAtMost(3)) {
                val u = arr.getString(i)
                val iv = CircleImageView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(34.dpToPx(), 34.dpToPx()).apply {
                        marginStart = if (i > 0) (-10).dpToPx() else 0
                    }
                    borderWidth = 1.dpToPx()
                    val theme = ThemeStore.currentTheme()
                    borderColor = try { theme.onPrimaryColor.toColorInt() } catch (_: Exception) {
                        ContextCompat.getColor(activity, R.color.white)
                    }
                }
                val cache = grpcClient.getAvatarCache()
                val url = cache[u]
                if (!url.isNullOrEmpty()) {
                    com.bumptech.glide.Glide.with(activity).load(url)
                        .placeholder(R.drawable.ic_default_avatar).circleCrop().into(iv)
                    iv.clearColorFilter()
                } else {
                    ThemeUtils.applyDefaultAvatar(iv, ThemeStore.currentTheme())
                }
                groupParticipantsContainer.addView(iv)
            }
        } catch (_: Exception) {}
    }

    private fun setupLobbyButton() {
        if (chatType == "conference") {
            val isMeAdmin = username.trim().equals(creator.trim(), ignoreCase = true) && creator.isNotEmpty()
            btnLobby.isVisible = isMeAdmin
            btnLobby.setOnClickListener {
                val intent = Intent(activity, ConferenceLobbyActivity::class.java).apply {
                    putExtra("ROOM_ID", roomId)
                    putExtra("CHAT_NAME", chatName)
                    putExtra("PARTICIPANTS", participantsJson)
                    putExtra("CREATOR", creator)
                }
                activity.startActivity(intent)
            }
        } else {
            btnLobby.isVisible = false
        }
    }

    fun setNavigationIcon(iconResId: Int) {
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationIcon(iconResId)
        toolbar.navigationIcon?.let {
            val wrapped = DrawableCompat.wrap(it)
            val theme = ThemeStore.currentTheme()
            val iconColor = try { theme.onPrimaryColor.toColorInt() } catch (_: Exception) {
                ContextCompat.getColor(activity, R.color.white)
            }
            DrawableCompat.setTint(wrapped, iconColor)
            toolbar.navigationIcon = wrapped
        }
    }

    fun updateSubtitle(onlineUsers: List<String>, isConnected: Boolean, typists: List<String>) {
        if (roomId.startsWith("favorites_")) {
            toolbarSubtitle.isVisible = true
            toolbarSubtitle.text = activity.getString(R.string.favorites)
            toolbarSubtitle.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnPrimary))
            return
        }
        val cop = getThemeColor(com.google.android.material.R.attr.colorOnPrimary)
        val cg = android.graphics.Color.parseColor("#32E672") // holo_green_light equivalent
        toolbarSubtitle.isVisible = true
        toolbarSubtitle.setTypeface(null, android.graphics.Typeface.NORMAL)

        when {
            !isConnected -> {
                toolbarSubtitle.text = activity.getString(R.string.connecting)
                toolbarSubtitle.setTextColor(cop)
            }
            typists.isNotEmpty() -> {
                toolbarSubtitle.text = if (typists.size == 1)
                    activity.getString(R.string.user_is_typing, typists.first())
                else activity.getString(R.string.users_are_typing, typists.size)
                toolbarSubtitle.setTextColor(cop)
                toolbarSubtitle.setTypeface(null, android.graphics.Typeface.ITALIC)
            }
            isDirect -> {
                val other = getOtherParticipant()
                val isO = onlineUsers.contains(other)
                if (isO) {
                    toolbarSubtitle.text = activity.getString(R.string.connected)
                    toolbarSubtitle.setTextColor(cg)
                } else {
                    toolbarSubtitle.text = activity.getString(R.string.offline)
                    toolbarSubtitle.setTextColor(cop)
                }
            }
            else -> updateGroupSubtitle(onlineUsers)
        }
    }

    private var cachedOtherUser: String? = null

    fun getOtherParticipant(): String? {
        if (cachedOtherUser != null) return cachedOtherUser
        return try {
            JSONArray(participantsJson).let { a ->
                (0 until a.length()).asSequence().map { a.getString(it) }
                    .find { it != username }.also { cachedOtherUser = it }
            }
        } catch (_: Exception) { null }
    }

    private fun updateGroupSubtitle(onlineUsers: List<String>) {
        if (isDirect) return
        try {
            val a = JSONArray(participantsJson)
            val t = a.length()
            var o = 0
            for (i in 0 until t) if (onlineUsers.contains(a.getString(i))) o++
            toolbarSubtitle.isVisible = true
            toolbarSubtitle.text = activity.getString(R.string.participants_online_count, t, o)
            toolbarSubtitle.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnPrimary))
        } catch (_: Exception) { toolbarSubtitle.isVisible = false }
    }

    private fun getThemeColor(attr: Int): Int {
        val v = android.util.TypedValue()
        activity.theme.resolveAttribute(attr, v, true)
        return v.data
    }

    private fun Int.dpToPx(): Int = (this * activity.resources.displayMetrics.density).toInt()
}
