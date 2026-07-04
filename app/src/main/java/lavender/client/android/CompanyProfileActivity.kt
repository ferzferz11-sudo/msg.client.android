package lavender.client.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcCompanyClient
import lavender.client.android.data.session.SessionManager
import lavender.client.android.ui.company.CompanyPagerAdapter

class CompanyProfileActivity : AppCompatActivity() {

    private var companyId: String = ""
    private var isOwner: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_company_profile)

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
        val contentLayout = findViewById<LinearLayout>(R.id.contentLayout)
        val tvCompanyName = findViewById<TextView>(R.id.tvCompanyName)
        val tvMemberCount = findViewById<TextView>(R.id.tvMemberCount)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val btnAddMember = findViewById<Button>(R.id.btnAddMember)
        val btnCreateChat = findViewById<Button>(R.id.btnCreateChat)
        val btnLeaveCompany = findViewById<Button>(R.id.btnLeaveCompany)

        // Setup tabs
        tabLayout.addTab(tabLayout.newTab().setText(R.string.members))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.positions))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.company_chats))

        viewPager.adapter = CompanyPagerAdapter(this, companyId)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { viewPager.currentItem = tab.position }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tabLayout.selectTab(tabLayout.getTabAt(position))
            }
        })

        // Load company info
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val response = withContext(Dispatchers.IO) {
                GrpcCompanyClient.getCompany(companyId)
            }
            progressBar.visibility = View.GONE

            if (response?.company != null) {
                contentLayout.visibility = View.VISIBLE
                tvCompanyName.text = response.company.name
                tvMemberCount.text = getString(R.string.members) + ": ${response.memberCount}"

                val currentUserId = SessionManager.session.value.userId
                isOwner = response.company.ownerId == currentUserId

                if (isOwner) {
                    btnLeaveCompany.text = getString(R.string.delete_company)
                    btnLeaveCompany.setOnClickListener { showDeleteCompanyDialog() }
                } else {
                    btnLeaveCompany.setOnClickListener { showLeaveCompanyDialog() }
                }

                btnAddMember.setOnClickListener {
                    val intent = Intent(this@CompanyProfileActivity, AddMemberActivity::class.java).apply {
                        putExtra("COMPANY_ID", companyId)
                    }
                    addMemberLauncher.launch(intent)
                }

                btnCreateChat.setOnClickListener {
                    showCreateCompanyChatDialog(response.positions)
                }
            } else {
                Toast.makeText(this@CompanyProfileActivity, getString(R.string.error_colon, "Failed to load company"), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private val addMemberLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Refresh fragments
            val viewPager = findViewById<ViewPager2>(R.id.viewPager)
            viewPager.adapter?.notifyDataSetChanged()
        }
    }

    private fun showCreateCompanyChatDialog(positions: List<lavender.client.android.data.proto.CompanyPositionProto>) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.create_company_chat)

        val input = android.widget.EditText(this)
        input.hint = getString(R.string.create_company_name_hint)
        builder.setView(input)

        val accessLevels = arrayOf(
            getString(R.string.access_member),
            getString(R.string.access_management),
            getString(R.string.access_owner_only)
        )
        val accessValues = arrayOf("member", "management", "owner_only")

        var selectedAccess = "member"

        builder.setSingleChoiceItems(accessLevels, 0) { _, which ->
            selectedAccess = accessValues[which]
        }

        builder.setPositiveButton(R.string.create_company_chat) { _, _ ->
            val name = input.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.create_company_name_hint), Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            createCompanyChat(name, selectedAccess)
        }

        builder.setNegativeButton(R.string.cancel_dialog, null)
        builder.show()
    }

    private fun createCompanyChat(name: String, accessLevel: String) {
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                GrpcCompanyClient.createCompanyChat(companyId, name, accessLevel)
            }
            if (response?.success == true) {
                Toast.makeText(this@CompanyProfileActivity, getString(R.string.company_chat_created), Toast.LENGTH_SHORT).show()
                val viewPager = findViewById<ViewPager2>(R.id.viewPager)
                viewPager.adapter?.notifyDataSetChanged()
            } else {
                Toast.makeText(this@CompanyProfileActivity, getString(R.string.error_colon, "Failed"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLeaveCompanyDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.leave_company)
            .setMessage(R.string.leave_company_confirm)
            .setPositiveButton(R.string.leave_company) { _, _ ->
                lifecycleScope.launch {
                    val response = withContext(Dispatchers.IO) {
                        GrpcCompanyClient.leaveCompany(companyId)
                    }
                    if (response?.success == true) {
                        Toast.makeText(this@CompanyProfileActivity, getString(R.string.company_updated), Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this@CompanyProfileActivity, getString(R.string.error_colon, response?.message ?: "Failed"), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel_dialog, null)
            .show()
    }

    private fun showDeleteCompanyDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_company)
            .setMessage(R.string.delete_company_confirm)
            .setPositiveButton(R.string.delete_company) { _, _ ->
                lifecycleScope.launch {
                    val response = withContext(Dispatchers.IO) {
                        GrpcCompanyClient.deleteCompany(companyId)
                    }
                    if (response?.success == true) {
                        Toast.makeText(this@CompanyProfileActivity, getString(R.string.company_deleted), Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this@CompanyProfileActivity, getString(R.string.error_colon, response?.message ?: "Failed"), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel_dialog, null)
            .show()
    }
}
