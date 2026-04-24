package lavender.client.android

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.appbar.MaterialToolbar
import de.hdodenhof.circleimageview.CircleImageView
import lavender.client.android.data.grpc.RealGrpcClient
import org.json.JSONArray
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }
    private val grpcClient = RealGrpcClient
    private var username: String = ""
    private var avatarUrl: String = ""
    private var isGroup: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedColorScheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val profileAvatar = findViewById<CircleImageView>(R.id.profileAvatar)
        val profileName = findViewById<TextView>(R.id.profileName)
        val profileStatus = findViewById<TextView>(R.id.profileStatus)
        val profileBio = findViewById<TextView>(R.id.profileBio)

        username = intent.getStringExtra("username") ?: ""
        avatarUrl = intent.getStringExtra("avatar_url") ?: ""
        isGroup = intent.getBooleanExtra("is_group", false)
        val roomId = intent.getStringExtra("room_id") ?: ""
        val participantsJson = intent.getStringExtra("participants") ?: "[]"

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (isGroup) getString(R.string.group_info) else getString(R.string.profile)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        profileName.text = username

        loadProfileData(profileAvatar, profileBio, profileStatus, roomId, participantsJson)
    }

    override fun onResume() {
        super.onResume()
        // Reload profile data when returning from EditProfileActivity
        val profileAvatar = findViewById<CircleImageView>(R.id.profileAvatar)
        val profileBio = findViewById<TextView>(R.id.profileBio)
        val profileStatus = findViewById<TextView>(R.id.profileStatus)
        val roomId = intent.getStringExtra("room_id") ?: ""
        val participantsJson = intent.getStringExtra("participants") ?: ""
        loadProfileData(profileAvatar, profileBio, profileStatus, roomId, participantsJson)
    }

    private fun loadProfileData(profileAvatar: CircleImageView, profileBio: TextView, profileStatus: TextView, roomId: String, participantsJson: String) {
        if (isGroup) {
            profileStatus.text = getString(R.string.group_chat)
            profileBio.text = getString(R.string.chat_id_format, roomId)
            
            // Participant Management
            val participantsCard = findViewById<View>(R.id.participantsCard)
            val participantsContainer = findViewById<LinearLayout>(R.id.participantsContainer)
            val addParticipantButton = findViewById<TextView>(R.id.addParticipantButton)
            
            participantsCard.isVisible = true
            val participants = JSONArray(participantsJson)
            for (i in 0 until participants.length()) {
                val user = participants.getString(i)
                val userView = layoutInflater.inflate(R.layout.item_participant, participantsContainer, false)
                userView.findViewById<TextView>(R.id.participantName).text = user
                
                val avatarView = userView.findViewById<CircleImageView>(R.id.participantAvatar)
                grpcClient.getUserAvatar(user) { url ->
                    runOnUiThread {
                        Glide.with(this).load(url).placeholder(R.drawable.ic_default_avatar).into(avatarView)
                    }
                }

                userView.setOnLongClickListener {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.remove)
                        .setMessage(getString(R.string.remove_participant_confirm, user))
                        .setPositiveButton(R.string.remove) { _, _ ->
                            grpcClient.removeParticipant(roomId, user) { success, msg ->
                                runOnUiThread {
                                    Toast.makeText(this@ProfileActivity, msg, Toast.LENGTH_SHORT).show()
                                    if (success) {
                                        participantsContainer.removeView(userView)
                                    }
                                }
                            }
                        }
                        .setNegativeButton(R.string.cancel, null).show()
                    true
                }
                participantsContainer.addView(userView)
            }

            addParticipantButton.setOnClickListener {
                grpcClient.loadAllUsers { allUsers ->
                    val currentParticipants = mutableSetOf<String>()
                    for (i in 0 until participants.length()) {
                        currentParticipants.add(participants.getString(i))
                    }
                    
                    val availableUsers = allUsers.filter { it !in currentParticipants }
                    
                    runOnUiThread {
                        if (availableUsers.isEmpty()) {
                            Toast.makeText(this, R.string.no_users_available, Toast.LENGTH_SHORT).show()
                            return@runOnUiThread
                        }
                        
                        AlertDialog.Builder(this)
                            .setTitle(R.string.select_user)
                            .setItems(availableUsers.toTypedArray()) { _, which ->
                                val selectedUser = availableUsers[which]
                                grpcClient.addParticipant(roomId, selectedUser) { success, msg ->
                                    runOnUiThread {
                                        Toast.makeText(this@ProfileActivity, msg, Toast.LENGTH_SHORT).show()
                                        if (success) {
                                            // Ideally we should refresh the whole list or add the view
                                            val newUserView = layoutInflater.inflate(R.layout.item_participant, participantsContainer, false)
                                            newUserView.findViewById<TextView>(R.id.participantName).text = selectedUser
                                            val avatarView = newUserView.findViewById<CircleImageView>(R.id.participantAvatar)
                                            grpcClient.getUserAvatar(selectedUser) { url ->
                                                runOnUiThread {
                                                    Glide.with(this@ProfileActivity).load(url).placeholder(R.drawable.ic_default_avatar).into(avatarView)
                                                }
                                            }
                                            participantsContainer.addView(newUserView)
                                        }
                                    }
                                }
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }
                }
            }

            findViewById<Button>(R.id.editProfileButton).apply {
                text = getString(R.string.delete_group)
                isVisible = true
                setOnClickListener {
                    AlertDialog.Builder(this@ProfileActivity)
                        .setTitle(R.string.delete_group)
                        .setMessage(R.string.delete_group_confirm)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            grpcClient.deleteChat(roomId) { success, msg ->
                                runOnUiThread {
                                    Toast.makeText(this@ProfileActivity, msg, Toast.LENGTH_SHORT).show()
                                    if (success) finish()
                                }
                            }
                        }
                        .setNegativeButton(R.string.cancel, null).show()
                }
            }
        } else {
            android.util.Log.d("ProfileActivity", "Loading profile for user: $username")
            grpcClient.getUserProfile(username) { profile ->
                android.util.Log.d("ProfileActivity", "Profile received: bio='${profile?.bio}', status='${profile?.status}', avatarUrl='${profile?.avatarUrl}'")
                runOnUiThread {
                    if (profile != null) {
                        profileBio.text = if (profile.bio.isNotEmpty()) profile.bio else getString(R.string.no_bio)
                        
                        // Set status: if viewing self, always show "Connected"
                        if (username == grpcClient.getCurrentUsername()) {
                            profileStatus.text = getString(R.string.connected)
                            profileStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                        } else {
                            profileStatus.text = if (profile.status.isNotEmpty()) profile.status else getString(R.string.offline)
                        }
                        if (profile.avatarUrl.isNotEmpty() && avatarUrl.isEmpty()) {
                            avatarUrl = profile.avatarUrl
                            Glide.with(this@ProfileActivity)
                                .load(avatarUrl)
                                .placeholder(R.drawable.ic_default_avatar)
                                .into(profileAvatar)
                        }
                    }
                }
            }
        }

        if (avatarUrl.isNotEmpty()) {
            Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.ic_default_avatar)
                .into(profileAvatar)
            
            profileAvatar.setOnClickListener {
                showFullScreenImage(avatarUrl)
            }
        } else {
            profileAvatar.setImageResource(R.drawable.ic_default_avatar)
        }
    }

    private fun showFullScreenImage(imageUrl: String) {
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen).create()
        val layout = RelativeLayout(this)
        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleLarge)
        
        layout.addView(imageView, RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        layout.addView(progressBar, RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            addRule(RelativeLayout.CENTER_IN_PARENT)
        })

        dialog.setView(layout)
        imageView.setOnClickListener { dialog.dismiss() }
        
        Glide.with(this)
            .load(imageUrl)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    progressBar.isVisible = false
                    return false
                }
                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                    progressBar.isVisible = false
                    return false
                }
            })
            .into(imageView)
            
        dialog.show()
    }

    private fun applySavedColorScheme() {
        val theme = when (getSavedColorScheme()) {
            "light" -> R.style.Theme_MsgClientAndroid
            else -> R.style.Theme_Lavender_Dark_NoActionBar
        }
        setTheme(theme)
    }

    private fun getSavedColorScheme(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("color_scheme", null)
    }
}
