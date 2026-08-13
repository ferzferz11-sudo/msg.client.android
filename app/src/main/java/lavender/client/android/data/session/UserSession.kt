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
    val email: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val authMethod: String = "", // "v1_legacy" or "v2_jwt"
    val companyId: String = "",
    val companyName: String = "",
    val positionTitle: String = "",
    val positionLevel: Int = 0
) {
    val isLoggedIn: Boolean get() = username.isNotEmpty()
    val isJwtAuth: Boolean get() = authMethod == "v2_jwt" && accessToken.isNotEmpty()
    val hasCompany: Boolean get() = companyId.isNotEmpty()
}
