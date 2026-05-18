package lavender.client.android.data.session

data class UserSession(
    val userId: String = "",
    val username: String = "",
    val password: String = "",
    val avatarUrl: String = "",
    val fullAvatarUrl: String = "",
    val isSuperAdmin: Boolean = false,
    val deviceId: String = "",
    val deviceName: String = ""
) {
    val isLoggedIn: Boolean get() = username.isNotEmpty() && password.isNotEmpty()
    
    // For backward compatibility with older server logic
    fun getIdentifier(): String = if (userId.isNotEmpty()) userId else username
}
