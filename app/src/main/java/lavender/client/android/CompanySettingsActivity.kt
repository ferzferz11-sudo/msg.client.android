package lavender.client.android

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcCompanyClient
import lavender.client.android.data.proto.CompanyPositionProto
import lavender.client.android.data.proto.CompanySettingsProto
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi

class CompanySettingsActivity : AppCompatActivity() {

    private var companyId: String = ""
    private var positions: List<CompanyPositionProto> = emptyList()
    private var selectedPositionId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_company_settings)

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
        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        val switchInviteOnly = findViewById<SwitchMaterial>(R.id.switchInviteOnly)
        val switchAllowMemberInvite = findViewById<SwitchMaterial>(R.id.switchAllowMemberInvite)
        val switchRequireApproval = findViewById<SwitchMaterial>(R.id.switchRequireApproval)
        val chipGroupChatAccess = findViewById<ChipGroup>(R.id.chipGroupChatAccess)
        val dropdownPosition = findViewById<AutoCompleteTextView>(R.id.dropdownPosition)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)

        btnSave.setOnClickListener {
            saveSettings()
        }

        loadData(progressBar, scrollView, switchInviteOnly, switchAllowMemberInvite,
            switchRequireApproval, chipGroupChatAccess, dropdownPosition)
    }

    private fun loadData(
        progressBar: ProgressBar,
        scrollView: ScrollView,
        switchInviteOnly: SwitchMaterial,
        switchAllowMemberInvite: SwitchMaterial,
        switchRequireApproval: SwitchMaterial,
        chipGroupChatAccess: ChipGroup,
        dropdownPosition: AutoCompleteTextView
    ) {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE

            val settingsResponse = withContext(Dispatchers.IO) {
                GrpcCompanyClient.getCompanySettings(companyId)
            }
            val companyResponse = withContext(Dispatchers.IO) {
                GrpcCompanyClient.getCompany(companyId)
            }

            progressBar.visibility = View.GONE
            scrollView.visibility = View.VISIBLE

            positions = companyResponse?.positions ?: emptyList()

            val settings = settingsResponse?.settings
            if (settings != null) {
                switchInviteOnly.isChecked = settings.inviteOnly
                switchAllowMemberInvite.isChecked = settings.allowMemberInvite
                switchRequireApproval.isChecked = settings.requireApproval
                selectedPositionId = settings.defaultPositionId

                when (settings.chatAccess) {
                    "management" -> chipGroupChatAccess.check(R.id.chipManagement)
                    "owner_only" -> chipGroupChatAccess.check(R.id.chipOwnerOnly)
                    else -> chipGroupChatAccess.check(R.id.chipMember)
                }
            }

            setupPositionDropdown(dropdownPosition)
        }
    }

    private fun setupPositionDropdown(dropdown: AutoCompleteTextView) {
        val positionNames = mutableListOf(getString(R.string.no_default_position))
        val positionIds = mutableListOf("")

        for (pos in positions) {
            positionNames.add(pos.title)
            positionIds.add(pos.id)
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, positionNames)
        dropdown.setAdapter(adapter)

        val selectedIndex = positionIds.indexOf(selectedPositionId)
        if (selectedIndex >= 0) {
            dropdown.setText(positionNames[selectedIndex], false)
        } else {
            dropdown.setText(positionNames[0], false)
        }

        dropdown.setOnItemClickListener { _, _, position, _ ->
            selectedPositionId = positionIds[position]
        }
    }

    private fun saveSettings() {
        val switchInviteOnly = findViewById<SwitchMaterial>(R.id.switchInviteOnly)
        val switchAllowMemberInvite = findViewById<SwitchMaterial>(R.id.switchAllowMemberInvite)
        val switchRequireApproval = findViewById<SwitchMaterial>(R.id.switchRequireApproval)
        val chipGroupChatAccess = findViewById<ChipGroup>(R.id.chipGroupChatAccess)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)

        val chatAccess = when (chipGroupChatAccess.checkedChipId) {
            R.id.chipManagement -> "management"
            R.id.chipOwnerOnly -> "owner_only"
            else -> "member"
        }

        val settings = CompanySettingsProto(
            companyId = companyId,
            inviteOnly = switchInviteOnly.isChecked,
            defaultPositionId = selectedPositionId,
            allowMemberInvite = switchAllowMemberInvite.isChecked,
            chatAccess = chatAccess,
            requireApproval = switchRequireApproval.isChecked
        )

        btnSave.isEnabled = false

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                GrpcCompanyClient.updateCompanySettings(companyId, settings)
            }

            btnSave.isEnabled = true

            if (response?.success == true) {
                Toast.makeText(this@CompanySettingsActivity, getString(R.string.company_settings_saved), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@CompanySettingsActivity, getString(R.string.error_colon, "Failed to save settings"), Toast.LENGTH_LONG).show()
            }
        }
    }
}
