package lavender.client.android.data.proto

// ===== Company Models =====

data class CompanyProto(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val avatarUrl: String = "",
    val createdAt: String = "",
    val memberCount: Int = 0
)

data class CompanyPositionProto(
    val id: String = "",
    val companyId: String = "",
    val title: String = "",
    val level: Int = 0,
    val chatAccess: String = ""
)

data class CompanyMemberProto(
    val id: String = "",
    val companyId: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val position: CompanyPositionProto? = null,
    val joinedAt: String = ""
)

data class CompanyChatInfoProto(
    val chatId: String = "",
    val companyId: String = "",
    val accessLevel: String = "",
    val minPositionLevel: Int = 0
)

data class UserPublicInfoProto(
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val fullAvatarUrl: String = "",
    val bio: String = "",
    val status: String = "",
    val isOnline: Boolean = false,
    val lastSeenAt: String = "",
    val companyId: String = "",
    val companyName: String = "",
    val positionTitle: String = "",
    val positionLevel: Int = 0
)

// ===== Company CRUD =====

data class CreateCompanyRequestProto(
    val name: String = ""
)

data class CreateCompanyResponseProto(
    val success: Boolean = false,
    val company: CompanyProto? = null
)

data class GetCompanyRequestProto(
    val companyId: String = ""
)

data class GetCompanyResponseProto(
    val company: CompanyProto? = null,
    val positions: List<CompanyPositionProto> = emptyList(),
    val memberCount: Int = 0
)

data class UpdateCompanyRequestProto(
    val companyId: String = "",
    val name: String = "",
    val avatarUrl: String = ""
)

data class UpdateCompanyResponseProto(
    val success: Boolean = false,
    val company: CompanyProto? = null
)

data class DeleteCompanyRequestProto(
    val companyId: String = ""
)

data class DeleteCompanyResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class ListCompaniesRequestProto(
    val userId: String = ""
)

data class ListCompaniesResponseProto(
    val companies: List<CompanyProto> = emptyList()
)

// ===== Positions =====

data class CreatePositionRequestProto(
    val companyId: String = "",
    val title: String = "",
    val level: Int = 0,
    val chatAccess: String = ""
)

data class CreatePositionResponseProto(
    val success: Boolean = false,
    val position: CompanyPositionProto? = null
)

data class UpdatePositionRequestProto(
    val positionId: String = "",
    val title: String = "",
    val level: Int = 0,
    val chatAccess: String = ""
)

data class UpdatePositionResponseProto(
    val success: Boolean = false,
    val position: CompanyPositionProto? = null
)

data class DeletePositionRequestProto(
    val positionId: String = ""
)

data class DeletePositionResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class ListPositionsRequestProto(
    val companyId: String = ""
)

data class ListPositionsResponseProto(
    val positions: List<CompanyPositionProto> = emptyList()
)

// ===== Members =====

data class AddMemberRequestProto(
    val companyId: String = "",
    val userId: String = "",
    val positionId: String = ""
)

data class AddMemberResponseProto(
    val success: Boolean = false,
    val member: CompanyMemberProto? = null
)

data class RemoveMemberRequestProto(
    val companyId: String = "",
    val userId: String = ""
)

data class RemoveMemberResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class UpdateMemberPositionRequestProto(
    val companyId: String = "",
    val userId: String = "",
    val positionId: String = ""
)

data class UpdateMemberPositionResponseProto(
    val success: Boolean = false,
    val member: CompanyMemberProto? = null
)

data class ListMembersRequestProto(
    val companyId: String = "",
    val cursor: String = "",
    val limit: Int = 50
)

data class ListMembersResponseProto(
    val members: List<CompanyMemberProto> = emptyList(),
    val nextCursor: String = "",
    val hasMore: Boolean = false
)

// ===== Company Chats =====

data class CreateCompanyChatRequestProto(
    val companyId: String = "",
    val name: String = "",
    val accessLevel: String = "",
    val minPositionLevel: Int = 0,
    val participantIds: List<String> = emptyList()
)

data class CreateCompanyChatResponseProto(
    val success: Boolean = false,
    val chatId: String = ""
)

data class SetCompanyChatAccessRequestProto(
    val chatId: String = "",
    val accessLevel: String = "",
    val minPositionLevel: Int = 0
)

data class SetCompanyChatAccessResponseProto(
    val success: Boolean = false
)

data class GetCompanyChatsRequestProto(
    val companyId: String = ""
)

data class GetCompanyChatsResponseProto(
    val chats: List<CompanyChatInfoProto> = emptyList()
)

// ===== Join / Leave =====

data class JoinCompanyRequestProto(
    val companyId: String = "",
    val inviteCode: String = ""
)

data class JoinCompanyResponseProto(
    val success: Boolean = false,
    val member: CompanyMemberProto? = null
)

data class LeaveCompanyRequestProto(
    val companyId: String = ""
)

data class LeaveCompanyResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

// ===== User Info =====

data class GetUserInfoRequestProto(
    val userId: String = ""
)

data class GetUserInfoResponseProto(
    val info: UserPublicInfoProto? = null
)

data class GetCompanyByUserRequestProto(
    val userId: String = ""
)

data class GetCompanyByUserResponseProto(
    val company: CompanyProto? = null,
    val member: CompanyMemberProto? = null
)

// ===== Multi-Company Support =====

data class CompanyCompanyMemberProto(
    val company: CompanyProto? = null,
    val member: CompanyMemberProto? = null,
    val isPrimary: Boolean = false
)

data class GetUserCompaniesRequestProto(
    val placeholder: Boolean = false // empty — user_id from JWT
)

data class GetUserCompaniesResponseProto(
    val companies: List<CompanyCompanyMemberProto> = emptyList()
)

data class SetPrimaryCompanyRequestProto(
    val companyId: String = ""
)

data class SetPrimaryCompanyResponseProto(
    val success: Boolean = false,
    val message: String = ""
)
