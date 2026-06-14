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

/**
 * Register Bottom Sheet — reusable widget for user registration.
 *
 * Hides server selector elements since server is already chosen.
 * Caller provides callbacks for register/cancel actions.
 */
class RegisterBottomSheet(
    context: Context,
    private val onRegister: (username: String, password: String, email: String) -> Unit,
    private val onCancel: () -> Unit,
    private val prefillUsername: String = "",
    private val prefillPassword: String = "",
    theme: Theme = lavender.client.android.theme.ThemeStore.currentTheme()
) : StandardBottomSheet(context, R.layout.bottom_sheet_register, theme) {

    private var editTextUsername: EditText? = null
    private var editTextPassword: EditText? = null
    private var editTextConfirmPassword: EditText? = null
    private var editTextEmail: EditText? = null
    private var btnRegister: MaterialButton? = null
    private var btnCancel: MaterialButton? = null
    private var registerProgressBar: ProgressBar? = null

    init {
        initViews()
    }

    private fun initViews() {
        editTextUsername = findViewById(R.id.editTextUsername)
        editTextPassword = findViewById(R.id.editTextPassword)
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword)
        editTextEmail = findViewById(R.id.editTextEmail)
        btnRegister = findViewById(R.id.btnRegister)
        btnCancel = findViewById(R.id.btnCancel)
        registerProgressBar = findViewById(R.id.registerProgressBar)

        // Prefill if provided
        if (prefillUsername.isNotEmpty()) {
            editTextUsername?.setText(prefillUsername)
        }
        if (prefillPassword.isNotEmpty()) {
            editTextPassword?.setText(prefillPassword)
        }

        // Hide server selector — server is always pre-determined
        findViewById<View>(R.id.serverAddressSpinner)?.visibility = View.GONE
        findViewById<View>(R.id.serverStatusLayout)?.visibility = View.GONE
        findViewById<View>(R.id.serverAddressLabel)?.visibility = View.GONE
        findViewById<View>(R.id.serverStatusIndicator)?.visibility = View.GONE
        findViewById<View>(R.id.dragHandle)?.visibility = View.GONE
        // Hide spinner background container (LinearLayout wrapping serverAddressSpinner)
        (findViewById<Spinner>(R.id.serverAddressSpinner)?.parent as? android.view.ViewGroup)?.visibility = View.GONE

        btnCancel?.setOnClickListener { onCancel() }
        btnRegister?.setOnClickListener {
            val u = editTextUsername?.text.toString().trim()
            val p = editTextPassword?.text.toString().trim()
            val cp = editTextConfirmPassword?.text.toString().trim()
            val e = editTextEmail?.text.toString().trim()

            if (u.isEmpty() || p.isEmpty()) return@setOnClickListener
            if (p != cp) {
                // Show error — passwords don't match
                editTextConfirmPassword?.error = context.getString(R.string.passwords_do_not_match)
                return@setOnClickListener
            }

            btnRegister?.text = ""
            btnRegister?.isEnabled = false
            registerProgressBar?.visibility = View.VISIBLE
            onRegister(u, p, e)
        }
    }

    fun setLoading(loading: Boolean) {
        if (loading) {
            btnRegister?.text = ""
            btnRegister?.isEnabled = false
            registerProgressBar?.visibility = View.VISIBLE
        } else {
            btnRegister?.text = context.getString(R.string.register)
            btnRegister?.isEnabled = true
            registerProgressBar?.visibility = View.GONE
        }
    }

    fun clearFields() {
        editTextUsername?.text?.clear()
        editTextPassword?.text?.clear()
        editTextConfirmPassword?.text?.clear()
        editTextEmail?.text?.clear()
    }
}
