package lavender.client.android.ui.widget

import android.content.Context
import android.widget.EditText
import com.google.android.material.button.MaterialButton
import lavender.client.android.R
import lavender.client.android.theme.Theme
import lavender.client.android.theme.ThemeStore

/**
 * Register Bottom Sheet — reusable widget for user registration.
 *
 * Shows: username/password/email fields, register/cancel buttons.
 * No server info (server name/address/status shown on ServerAuthBottomSheet).
 * Used in: ChatListActivity (first login), ServersActivity (after ServerAuthBottomSheet).
 */
class RegisterBottomSheet(
    context: Context,
    private val onRegister: (String, String, String) -> Unit,
    private val onCancel: () -> Unit,
    private val prefillUsername: String = "",
    private val prefillPassword: String = "",
    theme: Theme = ThemeStore.currentTheme()
) : StandardBottomSheet(context, R.layout.dialog_register, theme) {

    private var editTextUsername: EditText? = null
    private var editTextPassword: EditText? = null
    private var editTextConfirmPassword: EditText? = null
    private var editTextEmail: EditText? = null
    private var btnRegister: MaterialButton? = null
    private var btnCancel: MaterialButton? = null

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

        if (prefillUsername.isNotEmpty()) editTextUsername?.setText(prefillUsername)
        if (prefillPassword.isNotEmpty()) {
            editTextPassword?.setText(prefillPassword)
            editTextConfirmPassword?.setText(prefillPassword)
        }

        btnCancel?.setOnClickListener { onCancel() }
        btnRegister?.setOnClickListener {
            val u = editTextUsername?.text.toString().trim()
            val p = editTextPassword?.text.toString().trim()
            val cp = editTextConfirmPassword?.text.toString().trim()
            val e = editTextEmail?.text.toString().trim()

            if (u.isEmpty() || p.isEmpty()) return@setOnClickListener
            if (p != cp) {
                editTextConfirmPassword?.error = context.getString(R.string.passwords_do_not_match)
                return@setOnClickListener
            }

            btnRegister?.isEnabled = false
            onRegister(u, p, e)
        }
    }

    fun setLoading(loading: Boolean) {
        if (loading) {
            btnRegister?.isEnabled = false
        } else {
            btnRegister?.text = context.getString(R.string.register)
            btnRegister?.isEnabled = true
        }
    }

    fun clearFields() {
        editTextUsername?.text?.clear()
        editTextPassword?.text?.clear()
        editTextConfirmPassword?.text?.clear()
        editTextEmail?.text?.clear()
    }
}
