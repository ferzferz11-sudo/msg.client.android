package lavender.client.android
import android.util.Log

import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcCompanyClient
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeApplier

class JoinCompanyActivity : AppCompatActivity() {

    private lateinit var etInviteCode: EditText
    private lateinit var btnJoin: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_company)
        ThemeApplier.apply(this, ThemeStore.currentTheme())

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.navigationIcon?.setTint(ThemeUtils.parseSafeColor(ThemeStore.currentTheme().onPrimaryColor, Color.WHITE))
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = getString(R.string.join_by_code)

        etInviteCode = findViewById(R.id.etInviteCode)
        btnJoin = findViewById(R.id.btnJoin)

        applyThemeToViews()

        btnJoin.setOnClickListener {
            val code = etInviteCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, getString(R.string.enter_invite_code), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            joinCompany(code)
        }
    }

    private fun applyThemeToViews() {
        try {
            val theme = ThemeStore.currentTheme()
            val primary = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
            val textPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
            val onPrimary = ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE)

            etInviteCode.setTextColor(textPrimary)
            etInviteCode.setHintTextColor(ThemeUtils.adjustAlpha(textPrimary, 0.5f))
            btnJoin.backgroundTintList = android.content.res.ColorStateList.valueOf(primary)
            btnJoin.setTextColor(onPrimary)
        } catch (e: Exception) { Log.w(TAG, "Caught: " + e.message) }
    }

    private fun joinCompany(code: String) {
        btnJoin.isEnabled = false
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                GrpcCompanyClient.joinCompany(code, inviteCode = code)
            }
            btnJoin.isEnabled = true
            if (response?.success == true) {
                Toast.makeText(this@JoinCompanyActivity, getString(R.string.joined_company), Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this@JoinCompanyActivity, getString(R.string.join_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val TAG = "JoinCompanyActivity"
    }
}
