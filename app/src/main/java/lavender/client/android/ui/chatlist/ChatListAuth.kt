package lavender.client.android.ui.chatlist

import android.content.Intent
import android.widget.Toast
import lavender.client.android.R
import lavender.client.android.SplashLoadingActivity
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.session.SessionManager
import lavender.client.android.ui.widget.LoginBottomSheet
import lavender.client.android.ui.widget.RegisterBottomSheet
import lavender.client.android.ui.widget.ServerAuthBottomSheet

/**
 * ChatListAuth — authentication dialogs for ChatListActivity.
 * Extracted from ChatListActivity to reduce its size.
 */

internal fun showAuthChoiceDialog(activity: ChatListActivity) {
    var serverAddress = CredentialStore.getServerAddress(activity)
    var host: String
    var port: Int
    var serverName: String

    if (serverAddress.isEmpty()) {
        val prod = ServerConfig.PROD
        serverAddress = prod.grpcAddress
        host = prod.host
        port = prod.grpcPort
        serverName = "Lava Germany"
        CredentialStore.setServerAddress(activity, serverAddress)
    } else {
        val parts = serverAddress.split(":")
        host = parts[0]
        port = parts.getOrNull(1)?.toIntOrNull() ?: ServerConfig.PROD.grpcPort
        serverName = if (ServerConfig.isDevServer(port)) "Lava Germany dev" else "Lava Germany"
    }

    var isTransitioning = false
    lateinit var authSheet: ServerAuthBottomSheet

    authSheet = ServerAuthBottomSheet(
        context = activity,
        serverName = serverName,
        serverHost = host,
        serverPort = port,
        onLogin = {
            isTransitioning = true
            authSheet.dismiss()
            showLoginBottomSheet(activity, serverAddress)
        },
        onRegister = {
            isTransitioning = true
            authSheet.dismiss()
            showRegisterBottomSheet(activity, serverAddress)
        }
    )
    authSheet.setOnDismissListener {
        if (!isTransitioning) {
            val uname = SessionManager.session.value.username
            val pwd = SessionManager.session.value.password
            if (uname.isEmpty() || pwd.isEmpty()) {
                showAuthChoiceDialog(activity)
            }
        }
    }
    authSheet.show()
}

internal fun showLoginBottomSheet(activity: ChatListActivity, serverAddress: String) {
    var isTransitioning = false

    lateinit var loginSheet: LoginBottomSheet

    loginSheet = LoginBottomSheet(
        context = activity,
        onLogin = { u: String, p: String ->
            try {
                activity.startActivity(Intent(activity, SplashLoadingActivity::class.java))
            } catch (_: Exception) {}

            SessionManager.login(activity, u, p, serverAddress, register = false, email = "") { result ->
                activity.runOnUiThread {
                    SplashLoadingActivity.finishIfShowing()
                    when (result) {
                        "SUCCESS" -> {
                            CredentialStore.setCredentials(
                                context = activity,
                                username = u,
                                password = p,
                                serverAddress = serverAddress
                            )
                            val userId = SessionManager.session.value.userId
                            if (userId.isNotEmpty()) {
                                CredentialStore.setUserId(activity, userId)
                            }
                            isTransitioning = true
                            loginSheet.dismiss()
                            activity.recreate()
                        }
                        "USER_NOT_FOUND" -> {
                            loginSheet.setLoading(false)
                            Toast.makeText(activity, R.string.user_not_found, Toast.LENGTH_LONG).show()
                        }
                        "AUTH_FAILED" -> {
                            loginSheet.setLoading(false)
                            Toast.makeText(activity, R.string.wrong_password, Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            loginSheet.setLoading(false)
                            Toast.makeText(activity, R.string.connection_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        },
        onCancel = {
            isTransitioning = true
            loginSheet.dismiss()
            showAuthChoiceDialog(activity)
        }
    )

    loginSheet.setOnDismissListener {
        if (!isTransitioning) {
            val uname = SessionManager.session.value.username
            val pwd = SessionManager.session.value.password
            if (uname.isEmpty() || pwd.isEmpty()) {
                showAuthChoiceDialog(activity)
            }
        }
    }

    val lastUsername = activity.getSharedPreferences("lavender_prefs", android.content.Context.MODE_PRIVATE)
        .getString("last_username", "") ?: ""
    if (lastUsername.isNotEmpty()) {
        loginSheet.prefillUsername(lastUsername)
    }

    loginSheet.show()
}

internal fun showRegisterBottomSheet(activity: ChatListActivity, serverAddress: String) {
    var isTransitioning = false

    lateinit var registerSheet: RegisterBottomSheet

    registerSheet = RegisterBottomSheet(
        context = activity,
        onRegister = { u: String, p: String, email: String ->
            try {
                activity.startActivity(Intent(activity, SplashLoadingActivity::class.java))
            } catch (_: Exception) {}

            SessionManager.login(activity, u, p, serverAddress, register = true, email = email) { result ->
                activity.runOnUiThread {
                    SplashLoadingActivity.finishIfShowing()
                    when (result) {
                        "SUCCESS", "REGISTRATION_SUCCESS" -> {
                            CredentialStore.setCredentials(
                                context = activity,
                                username = u,
                                password = p,
                                email = email,
                                serverAddress = serverAddress
                            )
                            val userId = SessionManager.session.value.userId
                            if (userId.isNotEmpty()) {
                                CredentialStore.setUserId(activity, userId)
                            }
                            Toast.makeText(activity, R.string.registration_success, Toast.LENGTH_LONG).show()
                            isTransitioning = true
                            registerSheet.dismiss()
                            activity.recreate()
                        }
                        "USER_ALREADY_EXISTS" -> {
                            registerSheet.setLoading(false)
                            Toast.makeText(activity, R.string.user_already_exists, Toast.LENGTH_LONG).show()
                        }
                        "EMAIL_ALREADY_IN_USE" -> {
                            registerSheet.setLoading(false)
                            Toast.makeText(activity, R.string.email_already_in_use, Toast.LENGTH_LONG).show()
                        }
                        "AUTH_FAILED" -> {
                            registerSheet.setLoading(false)
                            Toast.makeText(activity, R.string.auth_failed, Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            registerSheet.setLoading(false)
                            Toast.makeText(activity, R.string.connection_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        },
        onCancel = {
            isTransitioning = true
            registerSheet.dismiss()
            showAuthChoiceDialog(activity)
        }
    )

    registerSheet.setOnDismissListener {
        if (!isTransitioning) {
            val uname = SessionManager.session.value.username
            val pwd = SessionManager.session.value.password
            if (uname.isEmpty() || pwd.isEmpty()) {
                showAuthChoiceDialog(activity)
            }
        }
    }

    registerSheet.show()
}
