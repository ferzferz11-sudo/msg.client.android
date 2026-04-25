package lavender.client.android

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import java.util.*

class SuperAdminActivity : AppCompatActivity() {

    private val grpcClient = GrpcClient
    private lateinit var usersContainer: LinearLayout
    private lateinit var progressOverlay: View

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

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedColorScheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_super_admin)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.super_admin)
        toolbar.setNavigationOnClickListener { finish() }

        usersContainer = findViewById(R.id.usersContainer)
        progressOverlay = findViewById(R.id.progressOverlay)

        loadAllUsers()
    }

    private fun loadAllUsers() {
        progressOverlay.isVisible = true
        grpcClient.loadAllUsers { allUsers ->
            runOnUiThread {
                progressOverlay.isVisible = false
                usersContainer.removeAllViews()
                
                for (user in allUsers) {
                    val userView = layoutInflater.inflate(R.layout.item_participant, usersContainer, false)
                    val nameText = userView.findViewById<TextView>(R.id.participantName)
                    val avatarView = userView.findViewById<CircleImageView>(R.id.participantAvatar)
                    val statusDot = userView.findViewById<View>(R.id.statusIndicator)
                    
                    nameText.text = user
                    statusDot.isVisible = grpcClient.users.value.contains(user)
                    statusDot.setBackgroundResource(if (statusDot.isVisible) R.drawable.status_online_dot else R.drawable.status_offline_dot)

                    grpcClient.getUserAvatar(user) { url ->
                        runOnUiThread {
                            Glide.with(this).load(url).placeholder(R.drawable.ic_default_avatar).into(avatarView)
                        }
                    }

                    userView.setOnLongClickListener {
                        confirmDeleteUser(user)
                        true
                    }
                    
                    usersContainer.addView(userView)
                }
            }
        }
    }

    private fun confirmDeleteUser(targetUser: String) {
        if (targetUser == "ferz") {
            Toast.makeText(this, "Cannot delete super admin", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.delete_profile)
            .setMessage("Are you sure you want to delete user $targetUser? This will remove all their data.")
            .setPositiveButton(R.string.delete) { _, _ ->
                progressOverlay.isVisible = true
                grpcClient.deleteProfile(targetUser) { success, msg ->
                    runOnUiThread {
                        progressOverlay.isVisible = false
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        if (success) loadAllUsers()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applySavedColorScheme() {
        val theme = when (getSharedPreferences("ChatPrefs", MODE_PRIVATE).getString("color_scheme", "dark")) {
            "light" -> R.style.Theme_Lavender_Light_NoActionBar
            else -> R.style.Theme_Lavender_Dark_NoActionBar
        }
        setTheme(theme)
    }
}
