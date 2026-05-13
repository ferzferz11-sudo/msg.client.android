package lavender.client.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import de.hdodenhof.circleimageview.CircleImageView
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.proto.ProtoUtils
import lavender.client.android.data.proto.UserInfoProto
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeUi
import java.io.File
import java.util.Locale

class SuperAdminActivity : AppCompatActivity() {

    private val grpcClient = GrpcClient
    private lateinit var usersContainer: LinearLayout
    private lateinit var progressOverlay: View
    private lateinit var searchLayout: View
    private lateinit var searchEditText: EditText
    
    private var allUsers = listOf<UserInfoProto>()
    private var allChats = listOf<ChatInfo>()
    private var currentMode = Mode.USERS
    
    enum class Mode { USERS, GROUPS }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_super_admin)

        val username = SessionManager.session.value.username
        ThemeUi.bind(this, username)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.super_admin)
        toolbar.setNavigationOnClickListener { finish() }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        usersContainer = findViewById(R.id.usersContainer)
        progressOverlay = findViewById(R.id.progressOverlay)
        searchLayout = findViewById(R.id.searchLayout)
        searchEditText = findViewById(R.id.searchEditText)

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCurrentList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.super_admin_menu, menu)
        
        val themeObj = ThemeStore.currentTheme()
        val iconColor = try {
            themeObj.onPrimaryColor.toColorInt()
        } catch (_: Exception) {
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
            typedValue.data
        }
        
        menu.findItem(R.id.action_show_users)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
        menu.findItem(R.id.action_show_groups)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
        menu.findItem(R.id.action_search)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
        
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_show_users -> {
                currentMode = Mode.USERS
                updateUI(allUsers, emptyList())
                return true
            }
            R.id.action_show_groups -> {
                currentMode = Mode.GROUPS
                updateUI(emptyList(), allChats)
                return true
            }
            R.id.action_search -> {
                searchLayout.isVisible = !searchLayout.isVisible
                if (searchLayout.isVisible) searchEditText.requestFocus()
                else {
                    searchEditText.text.clear()
                    filterCurrentList("")
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadData() {
        progressOverlay.isVisible = true
        grpcClient.loadAllUsers { users ->
            allUsers = users
            grpcClient.getAllChats { chats ->
                allChats = chats
                runOnUiThread {
                    progressOverlay.isVisible = false
                    updateUI(allUsers, allChats)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI(users: List<UserInfoProto>, chats: List<ChatInfo>) {
        usersContainer.removeAllViews()
        val theme = ThemeStore.currentTheme()
        val surfaceColor = try { theme.surfaceColor.toColorInt() } catch (_: Exception) { android.graphics.Color.DKGRAY }
        val textPrimary = try { theme.textPrimaryColor.toColorInt() } catch (_: Exception) { android.graphics.Color.WHITE }
        val textSecondary = try { theme.textSecondaryColor.toColorInt() } catch (_: Exception) { android.graphics.Color.LTGRAY }

        if (currentMode == Mode.USERS) {
            // Sort users by last seen time (most recent first)
            val sortedUsers = users.sortedByDescending { it.lastSeenAt?.seconds ?: 0 }
            for (user in sortedUsers) {
                val userView = layoutInflater.inflate(R.layout.item_user_super_admin, usersContainer, false)
                val card = userView as MaterialCardView
                val nameText = userView.findViewById<TextView>(R.id.participantName)
                val versionText = userView.findViewById<TextView>(R.id.clientVersion)
                val avatarView = userView.findViewById<CircleImageView>(R.id.participantAvatar)
                val statusDot = userView.findViewById<View>(R.id.statusIndicator)
                
                card.setCardBackgroundColor(surfaceColor)
                nameText.text = user.username
                nameText.setTextColor(textPrimary)
                
                val versionStr = if (user.lastClientVersion.isNotEmpty()) "v${user.lastClientVersion}" else ""
                val lastSeenStr = user.lastSeenAt?.let {
                    val date = java.util.Date(it.seconds * 1000)
                    val sdf = java.text.SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
                    sdf.format(date)
                } ?: ""
                
                versionText.text = if (versionStr.isNotEmpty() && lastSeenStr.isNotEmpty()) {
                    "$versionStr • $lastSeenStr"
                } else {
                    versionStr + lastSeenStr
                }
                versionText.setTextColor(textSecondary)
                
                val isOnline = grpcClient.users.value.contains(user.username)
                statusDot.isVisible = true
                statusDot.setBackgroundResource(if (isOnline) R.drawable.status_online_dot else R.drawable.status_offline_dot)

                if (user.avatarUrl.isNotEmpty()) {
                    Glide.with(this).load(user.avatarUrl).placeholder(R.drawable.ic_default_avatar).into(avatarView)
                    avatarView.clearColorFilter()
                } else {
                    ThemeUtils.applyDefaultAvatar(avatarView, theme)
                }

                userView.setOnClickListener {
                    val intent = Intent(this, ProfileActivity::class.java).apply {
                        putExtra("username", user.username)
                        putExtra("is_group", false)
                    }
                    startActivity(intent)
                }

                userView.setOnLongClickListener {
                    confirmDeleteUser(user.username)
                    true
                }
                usersContainer.addView(userView)
            }
        } else {
            for (chat in chats) {
                val chatView = layoutInflater.inflate(R.layout.item_chat, usersContainer, false)
                val card = chatView as MaterialCardView
                val nameText = chatView.findViewById<TextView>(R.id.chatName)
                val typeText = chatView.findViewById<TextView>(R.id.chatType)
                val participantAvatars = chatView.findViewById<LinearLayout>(R.id.participantAvatars)
                
                card.setCardBackgroundColor(surfaceColor)
                nameText.text = chat.name
                nameText.setTextColor(textPrimary)
                typeText.text = "${chat.type} - ID: ${chat.id}"
                typeText.setTextColor(textSecondary)

                // Load avatars for the group
                participantAvatars.removeAllViews()
                if (chat.avatarUrl.isNotEmpty()) {
                    val iv = CircleImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(52.dpToPx(), 52.dpToPx())
                    }
                    Glide.with(this).load(chat.avatarUrl).placeholder(R.drawable.ic_default_avatar).into(iv)
                    participantAvatars.addView(iv)
                } else {
                    ThemeUtils.applyDefaultAvatar(CircleImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(52.dpToPx(), 52.dpToPx())
                        participantAvatars.addView(this)
                    }, theme)
                }
                
                chatView.setOnClickListener {
                    val intent = Intent(this, ProfileActivity::class.java).apply {
                        putExtra("username", chat.name)
                        putExtra("is_group", !chat.type.equals("direct", true))
                        putExtra("room_id", chat.id)
                        putExtra("avatar_url", chat.avatarUrl)
                        putExtra("full_avatar_url", chat.fullAvatarUrl)
                        putExtra("creator", chat.creator)
                        putExtra("participants", chat.participants)
                    }
                    startActivity(intent)
                }

                chatView.setOnLongClickListener {
                    confirmDeleteChat(chat)
                    true
                }
                usersContainer.addView(chatView)
            }
        }
    }

    private fun filterCurrentList(query: String) {
        val q = query.lowercase()
        if (currentMode == Mode.USERS) {
            val filtered = allUsers.filter { it.username.lowercase().contains(q) }
            updateUI(filtered, emptyList())
        } else {
            val filtered = allChats.filter { it.name.lowercase().contains(q) || it.id.lowercase().contains(q) }
            updateUI(emptyList(), filtered)
        }
    }

    private fun confirmDeleteUser(targetUser: String) {
        if (targetUser == "ferz") return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_profile)
            .setMessage("${getString(R.string.delete_profile)}: $targetUser?")
            .setPositiveButton(R.string.delete) { _, _ ->
                progressOverlay.isVisible = true
                grpcClient.deleteProfile(targetUser) { success, message ->
                    runOnUiThread {
                        if (success) {
                            loadData()
                        } else {
                            progressOverlay.isVisible = false
                            Toast.makeText(this, "${getString(R.string.connection_failed)}: $message", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun confirmDeleteChat(chat: ChatInfo) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_group)
            .setMessage("${getString(R.string.delete_group)}: ${chat.name}?")
            .setPositiveButton(R.string.delete) { _, _ ->
                progressOverlay.isVisible = true
                grpcClient.deleteChat(chat.id) { success, message ->
                    runOnUiThread {
                        if (success) {
                            loadData()
                        } else {
                            progressOverlay.isVisible = false
                            Toast.makeText(this, "${getString(R.string.connection_failed)}: $message", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
