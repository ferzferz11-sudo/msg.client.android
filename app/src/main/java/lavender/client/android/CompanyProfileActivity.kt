package lavender.client.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import androidx.viewpager2.widget.ViewPager2
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcCompanyClient
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.company.CompanyPagerAdapter
import lavender.client.android.ui.widget.ActionBottomSheet
import lavender.client.android.ui.widget.SheetAction
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import lavender.client.android.network.HttpClient
import okhttp3.RequestBody.Companion.toRequestBody

class CompanyProfileActivity : AppCompatActivity() {

    private var companyId: String = ""
    private var isOwner: Boolean = false
    private var currentCompanyAvatarUrl: String = ""

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadCompanyLogo(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_company_profile)

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
        val contentLayout = findViewById<LinearLayout>(R.id.contentLayout)
        val tvCompanyName = findViewById<TextView>(R.id.tvCompanyName)
        val tvMemberCount = findViewById<TextView>(R.id.tvMemberCount)
        val ivCompanyLogo = findViewById<CircleImageView>(R.id.ivCompanyLogo)
        val btnEditCompanyName = findViewById<android.widget.ImageButton>(R.id.btnEditCompanyName)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val fabActions = findViewById<FloatingActionButton>(R.id.fabCompanyActions)

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

        // FAB — company actions sheet
        ViewCompat.setOnApplyWindowInsetsListener(fabActions) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = view.layoutParams as android.view.ViewGroup.MarginLayoutParams
            lp.bottomMargin = (32 * resources.displayMetrics.density).toInt() + systemBars.bottom
            view.layoutParams = lp
            insets
        }
        fabActions.setOnClickListener {
            showCompanyActionsSheet()
        }

        // Logo click — owner can change logo
        ivCompanyLogo.setOnClickListener {
            if (isOwner) {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                pickImageLauncher.launch(intent)
            }
        }

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
                currentCompanyAvatarUrl = response.company.avatarUrl

                val currentUserId = SessionManager.session.value.userId
                isOwner = response.company.ownerId == currentUserId

                invalidateOptionsMenu()

                // Show edit button for owner
                btnEditCompanyName.isVisible = isOwner
                btnEditCompanyName.setOnClickListener {
                    showRenameCompanyDialog(tvCompanyName)
                }

                // Show current user's position bubble
                val session = SessionManager.session.value
                val positionBubble = findViewById<com.google.android.material.card.MaterialCardView>(R.id.positionBubble)
                val tvPosition = findViewById<TextView>(R.id.tvPosition)
                if (session.positionTitle.isNotEmpty() || session.positionLevel > 0) {
                    positionBubble?.isVisible = true
                    tvPosition?.text = formatCompanyPosition(session.positionTitle, session.positionLevel)
                    val currentTheme = ThemeStore.currentTheme()
                    val primaryColor = ThemeUtils.parseSafeColor(currentTheme.primaryColor, android.graphics.Color.BLUE)
                    val primaryContainerBg = ThemeUtils.adjustAlpha(primaryColor, 0.15f)
                    positionBubble?.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(primaryContainerBg))
                    val textPrimary = ThemeUtils.parseSafeColor(currentTheme.textPrimaryColor, android.graphics.Color.BLACK)
                    tvPosition?.setTextColor(textPrimary)
                } else {
                    positionBubble?.isVisible = false
                }

                // Load company logo
                ivCompanyLogo.isVisible = true
                if (currentCompanyAvatarUrl.isNotEmpty()) {
                    Glide.with(this@CompanyProfileActivity)
                        .load(currentCompanyAvatarUrl)
                        .placeholder(R.drawable.ic_default_avatar)
                        .into(ivCompanyLogo)
                } else {
                    val currentTheme = ThemeStore.currentTheme()
                    ThemeUtils.applyDefaultAvatar(ivCompanyLogo, currentTheme)
                }
            } else {
                Toast.makeText(this@CompanyProfileActivity, getString(R.string.error_colon, "Failed to load company"), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun showCompanyActionsSheet() {
        val sheet = ActionBottomSheet(this)
            .setTitle(getString(R.string.my_company))
            .setActions(listOf(
                SheetAction(1, R.drawable.ic_contacts, getString(R.string.add_member)) {
                    lavender.client.android.ui.widget.AddMemberSheet(
                        this,
                        lavender.client.android.ui.widget.AddMemberSheet.Mode.COMPANY,
                        companyId
                    ) {
                        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
                        viewPager.adapter?.notifyDataSetChanged()
                    }.showAddMember()
                },
                SheetAction(2, R.drawable.ic_add, getString(R.string.create_company_chat)) {
                    lifecycleScope.launch {
                        val response = withContext(Dispatchers.IO) {
                            GrpcCompanyClient.getCompany(companyId)
                        }
                        showCreateCompanyChatDialog(response?.positions ?: emptyList())
                    }
                }
            ))
        sheet.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_company_profile, menu)
        val deleteItem = menu.findItem(R.id.action_delete)
        deleteItem?.isVisible = isOwner
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                showDeleteCompanyDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
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
        val primary = ThemeUtils.parseSafeColor(theme.primaryColor, android.graphics.Color.BLUE)
        val onPrimary = ThemeUtils.parseSafeColor(theme.onPrimaryColor, android.graphics.Color.WHITE)

        val contentLayout = findViewById<LinearLayout>(R.id.contentLayout)
        contentLayout?.setBackgroundColor(surfaceColor)

        // Company info card — same as chat list cards
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.companyInfoCard)?.setCardBackgroundColor(surfaceColor)

        findViewById<TextView>(R.id.tvCompanyName)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvMemberCount)?.setTextColor(textSecondary)
        findViewById<android.widget.ImageButton>(R.id.btnEditCompanyName)?.imageTintList = android.content.res.ColorStateList.valueOf(primary)

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabCompanyActions)?.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(primary)
            imageTintList = android.content.res.ColorStateList.valueOf(onPrimary)
        }
    }

    private fun showRenameCompanyDialog(tvCompanyName: TextView) {
        val sheet = lavender.client.android.ui.widget.StandardBottomSheet(this, R.layout.dialog_edit_username)
        val inputLayout = sheet.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.usernameInputLayout)
        val editNewUsername = sheet.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editNewUsername)
        val btnCancel = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnSave = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)

        sheet.setTitle(getString(R.string.company))
        inputLayout?.hint = getString(R.string.company)
        inputLayout?.startIconDrawable = null
        editNewUsername?.setText(tvCompanyName.text)
        editNewUsername?.selectAll()
        editNewUsername?.requestFocus()

        btnCancel?.setOnClickListener { sheet.dismiss() }

        btnSave?.setOnClickListener {
            val newName = editNewUsername?.text.toString().trim()
            if (newName.isEmpty() || newName == tvCompanyName.text) {
                sheet.dismiss()
                return@setOnClickListener
            }

            btnSave.isEnabled = false

            lifecycleScope.launch {
                val response = withContext(Dispatchers.IO) {
                    GrpcCompanyClient.updateCompany(companyId, name = newName)
                }
                runOnUiThread {
                    btnSave.isEnabled = true
                    if (response?.success == true) {
                        tvCompanyName.text = newName
                        Toast.makeText(this@CompanyProfileActivity, getString(R.string.company_updated), Toast.LENGTH_SHORT).show()
                        sheet.dismiss()
                    } else {
                        Toast.makeText(this@CompanyProfileActivity, getString(R.string.error_colon, "Failed"), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        sheet.show()
    }

    private fun uploadCompanyLogo(uri: Uri) {
        val ivCompanyLogo = findViewById<CircleImageView>(R.id.ivCompanyLogo)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri) ?: return@withContext
                    val bytes = inputStream.readBytes()
                    inputStream.close()

                    if (bytes.isEmpty()) return@withContext

                    val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("avatar", "company_logo.jpg", bytes.toRequestBody("image/jpeg".toMediaTypeOrNull()))
                        .build()

                    val url = "${lavender.client.android.data.session.CredentialStore.getHttpServerUrl(this@CompanyProfileActivity)}/upload-avatar"
                    val request = Request.Builder().url(url).post(requestBody).build()
                    val response = HttpClient.client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val body = response.body.string()
                        val logoUrl = """"url"\s*:\s*"([^"]+)"""".toRegex().find(body)?.groupValues?.get(1) ?: ""

                        if (logoUrl.isNotEmpty()) {
                            val updateResponse = GrpcCompanyClient.updateCompany(companyId, avatarUrl = logoUrl)
                            if (updateResponse?.success == true) {
                                currentCompanyAvatarUrl = logoUrl
                                runOnUiThread {
                                    Glide.with(this@CompanyProfileActivity)
                                        .load(logoUrl)
                                        .placeholder(R.drawable.ic_default_avatar)
                                        .into(ivCompanyLogo)
                                    Toast.makeText(this@CompanyProfileActivity, getString(R.string.company_updated), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@CompanyProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val addMemberLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val viewPager = findViewById<ViewPager2>(R.id.viewPager)
            viewPager.adapter?.notifyDataSetChanged()
        }
    }

    private fun showCreateCompanyChatDialog(positions: List<lavender.client.android.data.proto.CompanyPositionProto>) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.create_company_chat)

        val input = android.widget.EditText(this)
        input.hint = getString(R.string.chat_name_hint)
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
                Toast.makeText(this, getString(R.string.chat_name_hint), Toast.LENGTH_SHORT).show()
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

    private fun formatCompanyPosition(positionTitle: String, positionLevel: Int): String {
        val englishNames = mapOf(0 to "Employee", 1 to "Manager", 2 to "Top Manager", 3 to "Owner")
        val levelName = when (positionLevel) {
            0 -> getString(R.string.employee)
            1 -> getString(R.string.manager)
            2 -> getString(R.string.top_manager)
            3 -> getString(R.string.owner)
            else -> positionTitle
        }
        if (positionTitle.isEmpty()) return levelName
        val englishName = englishNames[positionLevel]
        return if (englishName != null && positionTitle.equals(englishName, ignoreCase = true)) {
            levelName
        } else if (positionTitle != levelName) {
            "$positionTitle ($levelName)"
        } else {
            levelName
        }
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
