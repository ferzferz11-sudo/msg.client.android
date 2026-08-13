package lavender.client.android.data.grpc

import android.util.Log
import lavender.client.android.data.proto.*
import kotlin.coroutines.resume

/**
 * GrpcCompanyClient — client for CompanyService (JWT Bearer auth).
 *
 * All methods require a valid JWT token (attached automatically by BearerTokenInterceptor).
 */
object GrpcCompanyClient {
    private const val TAG = "GrpcCompanyClient"

    // ===== Company CRUD =====

    suspend fun createCompany(name: String): CreateCompanyResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/CreateCompany",
                requestMarshaller = CreateCompanyRequestMarshaller(),
                responseMarshaller = CreateCompanyResponseMarshaller(),
                request = CreateCompanyRequestProto(name = name)
            )
        } catch (e: Exception) {
            Log.w(TAG, "createCompany failed: ${e.message}")
            null
        }
    }

    suspend fun getCompany(companyId: String): GetCompanyResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/GetCompany",
                requestMarshaller = GetCompanyRequestMarshaller(),
                responseMarshaller = GetCompanyResponseMarshaller(),
                request = GetCompanyRequestProto(companyId = companyId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "getCompany failed: ${e.message}")
            null
        }
    }

    suspend fun updateCompany(companyId: String, name: String = "", avatarUrl: String = ""): UpdateCompanyResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/UpdateCompany",
                requestMarshaller = UpdateCompanyRequestMarshaller(),
                responseMarshaller = UpdateCompanyResponseMarshaller(),
                request = UpdateCompanyRequestProto(companyId = companyId, name = name, avatarUrl = avatarUrl)
            )
        } catch (e: Exception) {
            Log.w(TAG, "updateCompany failed: ${e.message}")
            null
        }
    }

    suspend fun deleteCompany(companyId: String): DeleteCompanyResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/DeleteCompany",
                requestMarshaller = DeleteCompanyRequestMarshaller(),
                responseMarshaller = DeleteCompanyResponseMarshaller(),
                request = DeleteCompanyRequestProto(companyId = companyId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "deleteCompany failed: ${e.message}")
            null
        }
    }

    suspend fun listCompanies(userId: String): ListCompaniesResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/ListCompanies",
                requestMarshaller = ListCompaniesRequestMarshaller(),
                responseMarshaller = ListCompaniesResponseMarshaller(),
                request = ListCompaniesRequestProto(userId = userId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "listCompanies failed: ${e.message}")
            null
        }
    }

    // ===== Positions =====

    suspend fun createPosition(companyId: String, title: String, level: Int, chatAccess: String): CreatePositionResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/CreatePosition",
                requestMarshaller = CreatePositionRequestMarshaller(),
                responseMarshaller = CreatePositionResponseMarshaller(),
                request = CreatePositionRequestProto(companyId = companyId, title = title, level = level, chatAccess = chatAccess)
            )
        } catch (e: Exception) {
            Log.w(TAG, "createPosition failed: ${e.message}")
            null
        }
    }

    suspend fun updatePosition(positionId: String, title: String, level: Int, chatAccess: String): UpdatePositionResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/UpdatePosition",
                requestMarshaller = UpdatePositionRequestMarshaller(),
                responseMarshaller = UpdatePositionResponseMarshaller(),
                request = UpdatePositionRequestProto(positionId = positionId, title = title, level = level, chatAccess = chatAccess)
            )
        } catch (e: Exception) {
            Log.w(TAG, "updatePosition failed: ${e.message}")
            null
        }
    }

    suspend fun deletePosition(positionId: String): DeletePositionResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/DeletePosition",
                requestMarshaller = DeletePositionRequestMarshaller(),
                responseMarshaller = DeletePositionResponseMarshaller(),
                request = DeletePositionRequestProto(positionId = positionId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "deletePosition failed: ${e.message}")
            null
        }
    }

    suspend fun listPositions(companyId: String): ListPositionsResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/ListPositions",
                requestMarshaller = ListPositionsRequestMarshaller(),
                responseMarshaller = ListPositionsResponseMarshaller(),
                request = ListPositionsRequestProto(companyId = companyId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "listPositions failed: ${e.message}")
            null
        }
    }

    // ===== Members =====

    suspend fun addMember(companyId: String, userId: String, positionId: String): AddMemberResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/AddMember",
                requestMarshaller = AddMemberRequestMarshaller(),
                responseMarshaller = AddMemberResponseMarshaller(),
                request = AddMemberRequestProto(companyId = companyId, userId = userId, positionId = positionId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "addMember failed: ${e.message}")
            null
        }
    }

    suspend fun removeMember(companyId: String, userId: String): RemoveMemberResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/RemoveMember",
                requestMarshaller = RemoveMemberRequestMarshaller(),
                responseMarshaller = RemoveMemberResponseMarshaller(),
                request = RemoveMemberRequestProto(companyId = companyId, userId = userId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "removeMember failed: ${e.message}")
            null
        }
    }

    suspend fun updateMemberPosition(companyId: String, userId: String, positionId: String): UpdateMemberPositionResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/UpdateMemberPosition",
                requestMarshaller = UpdateMemberPositionRequestMarshaller(),
                responseMarshaller = UpdateMemberPositionResponseMarshaller(),
                request = UpdateMemberPositionRequestProto(companyId = companyId, userId = userId, positionId = positionId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "updateMemberPosition failed: ${e.message}")
            null
        }
    }

    suspend fun listMembers(companyId: String, cursor: String = "", limit: Int = 50): ListMembersResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/ListMembers",
                requestMarshaller = ListMembersRequestMarshaller(),
                responseMarshaller = ListMembersResponseMarshaller(),
                request = ListMembersRequestProto(companyId = companyId, cursor = cursor, limit = limit)
            )
        } catch (e: Exception) {
            Log.w(TAG, "listMembers failed: ${e.message}")
            null
        }
    }

    // ===== Company Chats =====

    suspend fun createCompanyChat(
        companyId: String,
        name: String,
        accessLevel: String,
        minPositionLevel: Int = 0,
        participantIds: List<String> = emptyList()
    ): CreateCompanyChatResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/CreateCompanyChat",
                requestMarshaller = CreateCompanyChatRequestMarshaller(),
                responseMarshaller = CreateCompanyChatResponseMarshaller(),
                request = CreateCompanyChatRequestProto(
                    companyId = companyId, name = name, accessLevel = accessLevel,
                    minPositionLevel = minPositionLevel, participantIds = participantIds
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "createCompanyChat failed: ${e.message}")
            null
        }
    }

    suspend fun setCompanyChatAccess(chatId: String, accessLevel: String, minPositionLevel: Int = 0): SetCompanyChatAccessResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/SetCompanyChatAccess",
                requestMarshaller = SetCompanyChatAccessRequestMarshaller(),
                responseMarshaller = SetCompanyChatAccessResponseMarshaller(),
                request = SetCompanyChatAccessRequestProto(chatId = chatId, accessLevel = accessLevel, minPositionLevel = minPositionLevel)
            )
        } catch (e: Exception) {
            Log.w(TAG, "setCompanyChatAccess failed: ${e.message}")
            null
        }
    }

    suspend fun getCompanyChats(companyId: String): GetCompanyChatsResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/GetCompanyChats",
                requestMarshaller = GetCompanyChatsRequestMarshaller(),
                responseMarshaller = GetCompanyChatsResponseMarshaller(),
                request = GetCompanyChatsRequestProto(companyId = companyId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "getCompanyChats failed: ${e.message}")
            null
        }
    }

    // ===== Join / Leave =====

    suspend fun joinCompany(companyId: String, inviteCode: String = ""): JoinCompanyResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/JoinCompany",
                requestMarshaller = JoinCompanyRequestMarshaller(),
                responseMarshaller = JoinCompanyResponseMarshaller(),
                request = JoinCompanyRequestProto(companyId = companyId, inviteCode = inviteCode)
            )
        } catch (e: Exception) {
            Log.w(TAG, "joinCompany failed: ${e.message}")
            null
        }
    }

    suspend fun leaveCompany(companyId: String): LeaveCompanyResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/LeaveCompany",
                requestMarshaller = LeaveCompanyRequestMarshaller(),
                responseMarshaller = LeaveCompanyResponseMarshaller(),
                request = LeaveCompanyRequestProto(companyId = companyId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "leaveCompany failed: ${e.message}")
            null
        }
    }

    // ===== User Info =====

    suspend fun getUserInfo(userId: String): GetUserInfoResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/GetUserInfo",
                requestMarshaller = GetUserInfoRequestMarshaller(),
                responseMarshaller = GetUserInfoResponseMarshaller(),
                request = GetUserInfoRequestProto(userId = userId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "getUserInfo failed: ${e.message}")
            null
        }
    }

    suspend fun getCompanyByUser(userId: String): GetCompanyByUserResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/GetCompanyByUser",
                requestMarshaller = GetCompanyByUserRequestMarshaller(),
                responseMarshaller = GetCompanyByUserResponseMarshaller(),
                request = GetCompanyByUserRequestProto(userId = userId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "getCompanyByUser failed: ${e.message}")
            null
        }
    }

    // ===== Multi-Company Support =====

    suspend fun getUserCompanies(): GetUserCompaniesResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/GetUserCompanies",
                requestMarshaller = GetUserCompaniesRequestMarshaller(),
                responseMarshaller = GetUserCompaniesResponseMarshaller(),
                request = GetUserCompaniesRequestProto()
            )
        } catch (e: Exception) {
            Log.w(TAG, "getUserCompanies failed: ${e.message}")
            null
        }
    }

    suspend fun setPrimaryCompany(companyId: String): SetPrimaryCompanyResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/SetPrimaryCompany",
                requestMarshaller = SetPrimaryCompanyRequestMarshaller(),
                responseMarshaller = SetPrimaryCompanyResponseMarshaller(),
                request = SetPrimaryCompanyRequestProto(companyId = companyId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "setPrimaryCompany failed: ${e.message}")
            null
        }
    }

    // ===== Company Settings =====

    suspend fun getCompanySettings(companyId: String): GetCompanySettingsResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/GetCompanySettings",
                requestMarshaller = GetCompanySettingsRequestMarshaller(),
                responseMarshaller = GetCompanySettingsResponseMarshaller(),
                request = GetCompanySettingsRequestProto(companyId = companyId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "getCompanySettings failed: ${e.message}")
            null
        }
    }

    suspend fun updateCompanySettings(companyId: String, settings: CompanySettingsProto): UpdateCompanySettingsResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/UpdateCompanySettings",
                requestMarshaller = UpdateCompanySettingsRequestMarshaller(),
                responseMarshaller = UpdateCompanySettingsResponseMarshaller(),
                request = UpdateCompanySettingsRequestProto(companyId = companyId, settings = settings)
            )
        } catch (e: Exception) {
            Log.w(TAG, "updateCompanySettings failed: ${e.message}")
            null
        }
    }

    // ===== Invite Codes =====

    suspend fun generateInviteCode(companyId: String, expiresHours: Int = 0, maxUses: Int = 1): GenerateInviteCodeResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/GenerateInviteCode",
                requestMarshaller = GenerateInviteCodeRequestMarshaller(),
                responseMarshaller = GenerateInviteCodeResponseMarshaller(),
                request = GenerateInviteCodeRequestProto(companyId = companyId, expiresHours = expiresHours, maxUses = maxUses)
            )
        } catch (e: Exception) {
            Log.w(TAG, "generateInviteCode failed: ${e.message}")
            null
        }
    }

    suspend fun joinByInviteCode(code: String): JoinByInviteCodeResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/JoinByInviteCode",
                requestMarshaller = JoinByInviteCodeRequestMarshaller(),
                responseMarshaller = JoinByInviteCodeResponseMarshaller(),
                request = JoinByInviteCodeRequestProto(code = code)
            )
        } catch (e: Exception) {
            Log.w(TAG, "joinByInviteCode failed: ${e.message}")
            null
        }
    }

    suspend fun revokeInviteCode(codeId: String): RevokeInviteCodeResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/RevokeInviteCode",
                requestMarshaller = RevokeInviteCodeRequestMarshaller(),
                responseMarshaller = RevokeInviteCodeResponseMarshaller(),
                request = RevokeInviteCodeRequestProto(codeId = codeId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "revokeInviteCode failed: ${e.message}")
            null
        }
    }

    suspend fun listInviteCodes(companyId: String): ListInviteCodesResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/ListInviteCodes",
                requestMarshaller = ListInviteCodesRequestMarshaller(),
                responseMarshaller = ListInviteCodesResponseMarshaller(),
                request = ListInviteCodesRequestProto(companyId = companyId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "listInviteCodes failed: ${e.message}")
            null
        }
    }

    // ===== Company Notifications =====

    suspend fun sendCompanyNotification(
        companyId: String,
        eventType: Int,
        actorUsername: String = "",
        targetUsername: String = "",
        positionName: String = ""
    ): SendCompanyNotificationResponseProto? {
        return try {
            unaryCall(
                fullMethod = "messenger.CompanyService/SendCompanyNotification",
                requestMarshaller = SendCompanyNotificationRequestMarshaller(),
                responseMarshaller = SendCompanyNotificationResponseMarshaller(),
                request = SendCompanyNotificationRequestProto(
                    companyId = companyId, eventType = eventType,
                    actorUsername = actorUsername, targetUsername = targetUsername,
                    positionName = positionName
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "sendCompanyNotification failed: ${e.message}")
            null
        }
    }

    // ===== Generic unary call =====

    @Suppress("UNCHECKED_CAST")
    private suspend fun <ReqT, RespT> unaryCall(
        fullMethod: String,
        requestMarshaller: io.grpc.MethodDescriptor.Marshaller<ReqT>,
        responseMarshaller: io.grpc.MethodDescriptor.Marshaller<RespT>,
        request: ReqT
    ): RespT? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val channel = RealGrpcClient.getChannel()
        if (channel == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        val method = io.grpc.MethodDescriptor.newBuilder<ReqT, RespT>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(fullMethod)
            .setRequestMarshaller(requestMarshaller)
            .setResponseMarshaller(responseMarshaller)
            .build()

        val call = channel.newCall(method, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<RespT>() {
            private var response: RespT? = null

            override fun onMessage(message: RespT) {
                response = message
            }   

            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (status.isOk) {
                    cont.resume(response)
                } else {
                    if (status.code == io.grpc.Status.Code.UNAUTHENTICATED) {
                        Log.w(TAG, "$fullMethod auth failed — token may be expired")
                    }
                    cont.resume(null)
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }
}
