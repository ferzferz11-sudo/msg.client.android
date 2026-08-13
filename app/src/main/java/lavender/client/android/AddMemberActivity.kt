package lavender.client.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.GrpcCompanyClient
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeUi

class AddMemberActivity : AppCompatActivity() {

    private var companyId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_member)

        val username = SessionManager.session.value.username
        ThemeUi.bind(this, username)

        companyId = intent.getStringExtra("COMPANY_ID") ?: ""
        if (companyId.isEmpty()) {
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Load contacts
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE

            // Trigger loadAllUsers and wait for allUsers to be populated
            GrpcClient.loadAllUsers()
            val users = GrpcClient.allUsers.value

            progressBar.visibility = View.GONE

            if (users.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
            } else {
                val adapter = ContactAdapter(
                    contacts = users,
                    onContactClick = { user ->
                        showSelectPositionDialog(user.userId, user.username)
                    }
                )
                recyclerView.adapter = adapter
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyThemeToViews()
    }

    private fun applyThemeToViews() {
        val theme = ThemeStore.currentTheme()
        val surfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, android.graphics.Color.WHITE)
        val textPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, android.graphics.Color.BLACK)
        val textSecondary = ThemeUtils.parseSafeColor(theme.textSecondaryColor, android.graphics.Color.GRAY)

        findViewById<View>(android.R.id.content)?.setBackgroundColor(surfaceColor)
        findViewById<TextView>(R.id.tvEmpty)?.setTextColor(textSecondary)
    }

    private fun showSelectPositionDialog(userId: String, username: String) {
        lifecycleScope.launch {
            val positionsResponse = withContext(Dispatchers.IO) {
                GrpcCompanyClient.listPositions(companyId)
            }
            val positions = positionsResponse?.positions ?: emptyList()

            if (positions.isEmpty()) {
                Toast.makeText(this@AddMemberActivity, getString(R.string.error_colon, "No positions available"), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val titles = positions.map { it.title }.toTypedArray()

            AlertDialog.Builder(this@AddMemberActivity)
                .setTitle(R.string.add_member)
                .setItems(titles) { _, which ->
                    val selectedPosition = positions[which]
                    addMember(userId, selectedPosition.id, username)
                }
                .setNegativeButton(R.string.cancel_dialog, null)
                .show()
        }
    }

    private fun addMember(userId: String, positionId: String, username: String) {
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                GrpcCompanyClient.addMember(companyId, userId, positionId)
            }
            if (response?.success == true) {
                // Send company notification for member join
                withContext(Dispatchers.IO) {
                    GrpcCompanyClient.sendCompanyNotification(
                        companyId = companyId,
                        eventType = 1, // member_joined
                        actorUsername = SessionManager.session.value.username,
                        targetUsername = username
                    )
                }
                Toast.makeText(this@AddMemberActivity, getString(R.string.member_added), Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this@AddMemberActivity, getString(R.string.error_colon, "Failed"), Toast.LENGTH_LONG).show()
            }
        }
    }
}

class ContactAdapter(
    private val contacts: List<lavender.client.android.data.proto.UserInfoProto>,
    private val onContactClick: (lavender.client.android.data.proto.UserInfoProto) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvPosition: TextView = view.findViewById(R.id.tvPosition)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_company_member, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = contacts.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.tvName.text = contact.username
        holder.tvPosition.text = contact.email
        holder.itemView.setOnClickListener { onContactClick(contact) }
    }
}
