package lavender.client.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcCompanyClient
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.company.InviteCodeAdapter

class InviteCodeActivity : AppCompatActivity() {

    private var companyId: String = ""
    private lateinit var adapter: InviteCodeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invite_codes)

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

        val rvInviteCodes = findViewById<RecyclerView>(R.id.rvInviteCodes)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
        val fabGenerate = findViewById<FloatingActionButton>(R.id.fabGenerate)

        adapter = InviteCodeAdapter(
            onShare = { code -> shareCode(code.code) },
            onRevoke = { code -> revokeCode(code.id) }
        )
        rvInviteCodes.layoutManager = LinearLayoutManager(this)
        rvInviteCodes.adapter = adapter

        ViewCompat.setOnApplyWindowInsetsListener(fabGenerate) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = view.layoutParams as android.view.ViewGroup.MarginLayoutParams
            lp.bottomMargin = (32 * resources.displayMetrics.density).toInt() + systemBars.bottom
            view.layoutParams = lp
            insets
        }

        fabGenerate.setOnClickListener {
            showGenerateDialog()
        }

        loadCodes(tvEmpty, rvInviteCodes)
    }

    private fun loadCodes(tvEmpty: TextView, rvInviteCodes: RecyclerView) {
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE

            val response = withContext(Dispatchers.IO) {
                GrpcCompanyClient.listInviteCodes(companyId)
            }

            progressBar.visibility = View.GONE

            val codes = response?.codes ?: emptyList()
            adapter.submitList(codes)

            if (codes.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                rvInviteCodes.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                rvInviteCodes.visibility = View.VISIBLE
            }
        }
    }

    private fun showGenerateDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_generate_invite_code, null)
        val editHours = dialogView.findViewById<EditText>(R.id.editExpiresHours)
        val editMaxUses = dialogView.findViewById<EditText>(R.id.editMaxUses)

        AlertDialog.Builder(this)
            .setTitle(R.string.company_generate_code)
            .setView(dialogView)
            .setPositiveButton(R.string.company_generate_code) { _, _ ->
                val hours = editHours.text.toString().toIntOrNull() ?: 0
                val maxUses = editMaxUses.text.toString().toIntOrNull() ?: 1
                generateCode(hours, maxUses)
            }
            .setNegativeButton(R.string.cancel_dialog, null)
            .show()
    }

    private fun generateCode(expiresHours: Int, maxUses: Int) {
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                GrpcCompanyClient.generateInviteCode(companyId, expiresHours, maxUses)
            }

            if (response?.success == true && response.code != null) {
                Toast.makeText(this@InviteCodeActivity, getString(R.string.company_code_generated), Toast.LENGTH_SHORT).show()
                shareCode(response.code.code)
                val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
                val rvInviteCodes = findViewById<RecyclerView>(R.id.rvInviteCodes)
                loadCodes(tvEmpty, rvInviteCodes)
            } else {
                Toast.makeText(this@InviteCodeActivity, getString(R.string.error_colon, "Failed to generate code"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun shareCode(code: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Join my company on Lava: $code")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, getString(R.string.company_code_share))
        startActivity(shareIntent)
    }

    private fun revokeCode(codeId: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.company_code_revoke)
            .setMessage("Revoke this invite code?")
            .setPositiveButton(R.string.company_code_revoke) { _, _ ->
                lifecycleScope.launch {
                    val response = withContext(Dispatchers.IO) {
                        GrpcCompanyClient.revokeInviteCode(codeId)
                    }

                    if (response?.success == true) {
                        Toast.makeText(this@InviteCodeActivity, getString(R.string.company_code_revoked), Toast.LENGTH_SHORT).show()
                        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
                        val rvInviteCodes = findViewById<RecyclerView>(R.id.rvInviteCodes)
                        loadCodes(tvEmpty, rvInviteCodes)
                    } else {
                        Toast.makeText(this@InviteCodeActivity, getString(R.string.error_colon, "Failed to revoke code"), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel_dialog, null)
            .show()
    }
}
