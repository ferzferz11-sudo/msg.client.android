package lavender.client.android.ui.widget

import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import lavender.client.android.R
import lavender.client.android.theme.Theme
import lavender.client.android.theme.ThemeStore

/**
 * Login Bottom Sheet — reusable widget for server login.
 *
 * Hides server selector elements since server is already chosen.
 * Caller provides callbacks for login/cancel actions.
 */
class LoginBottomSheet(
    context: Context,
    private val onLogin: (String, String) -> Unit,
    private val onCancel: () -> Unit,
    theme: Theme = ThemeStore.currentTheme()
) : StandardBottomSheet(context, R.layout.bottom_sheet_login, theme) {

    private var editTextUsername: EditText? = null
    private var editTextPassword: EditText? = null
    private var btnJoin: MaterialButton? = null
    private var btnCancel: MaterialButton? = null
    private var joinProgressBar: ProgressBar? = null

    init {
        initViews()
    }

    private fun initViews() {
        editTextUsername = findViewById(R.id.editTextUsername)
        editTextPassword = findViewById(R.id.editTextPassword)
        btnJoin = findViewById(R.id.btnJoin)
        btnCancel = findViewById(R.id.btnCancel)
        joinProgressBar = findViewById(R.id.joinProgressBar)

        // Hide server selector — server is always pre-determined
        findViewById<View>(R.id.serverAddressSpinner)?.visibility = View.GONE
        findViewById<View>(R.id.serverStatusLayout)?.visibility = View.GONE
        findViewById<View>(R.id.serverAddressLabel)?.visibility = View.GONE
        findViewById<View>(R.id.serverStatusIndicator)?.visibility = View.GONE
        findViewById<View>(R.id.dragHandle)?.visibility = View.GONE
        // Hide spinner background container (LinearLayout wrapping serverAddressSpinner)
        (findViewById<Spinner>(R.id.serverAddressSpinner)?.parent as? android.view.ViewGroup)?.visibility = View.GONE

        btnCancel?.setOnClickListener { onCancel() }
        btnJoin?.setOnClickListener {
            val u = editTextUsername?.text.toString().trim()
            val p = editTextPassword?.text.toString().trim()
            if (u.isNotEmpty() && p.isNotEmpty()) {
                btnJoin?.text = ""
                btnJoin?.isEnabled = false
                joinProgressBar?.visibility = View.VISIBLE
                onLogin(u, p)
            }
        }
    }

    fun setLoading(loading: Boolean) {
        if (loading) {
            btnJoin?.text = ""
            btnJoin?.isEnabled = false
            joinProgressBar?.visibility = View.VISIBLE
        } else {
            btnJoin?.text = context.getString(R.string.join)
            btnJoin?.isEnabled = true
            joinProgressBar?.visibility = View.GONE
        }
    }

    fun clearFields() {
        editTextUsername?.text?.clear()
        editTextPassword?.text?.clear()
    }
}
