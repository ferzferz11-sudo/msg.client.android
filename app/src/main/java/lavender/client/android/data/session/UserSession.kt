package lavender.client.android.data.session

data class UserSession(
    val userId: String = "",
    val username: String = "",
    val password: String = "",
    val avatarUrl: String = "",
    val fullAvatarUrl: String = "",
    val isSuperAdmin: Boolean = false,
    val deviceId: String = "",
    val deviceName: String = "",
    val email: String = ""
) {
    val isLoggedIn: Boolean get() = username.isNotEmpty() && password.isNotEmpty()
}
