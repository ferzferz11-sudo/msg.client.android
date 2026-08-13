package lavender.client.android.data.grpc

import lavender.client.android.data.proto.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanyMarshallersTest {

    // Company response marshallers are parse-only (stream() returns empty bytes)

    // ======= CreateCompany =======
    @Test
    fun createCompanyRequestProto() {
        val req = CreateCompanyRequestProto(name = "Acme")
        assertEquals("Acme", req.name)
    }

    @Test
    fun createCompanyResponse_marshallerParseEmpty() {
        val parsed = CreateCompanyResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertNull(parsed.company)
    }

    // ======= GetCompany =======
    @Test
    fun getCompanyResponse_marshallerParseEmpty() {
        val parsed = GetCompanyResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertNull(parsed.company)
        assertTrue(parsed.positions.isEmpty())
        assertEquals(0, parsed.memberCount)
    }

    // ======= ListCompanies =======
    @Test
    fun listCompaniesResponse_marshallerParseEmpty() {
        val parsed = ListCompaniesResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertTrue(parsed.companies.isEmpty())
    }

    // ======= CreatePosition =======
    @Test
    fun createPositionRequestProto() {
        val req = CreatePositionRequestProto(companyId = "c1", title = "Manager", level = 3, chatAccess = "full")
        assertEquals("c1", req.companyId)
        assertEquals("Manager", req.title)
        assertEquals(3, req.level)
        assertEquals("full", req.chatAccess)
    }

    @Test
    fun createPositionResponse_marshallerParseEmpty() {
        val parsed = CreatePositionResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertNull(parsed.position)
    }

    // ======= ListPositions =======
    @Test
    fun listPositionsResponse_marshallerParseEmpty() {
        val parsed = ListPositionsResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertTrue(parsed.positions.isEmpty())
    }

    // ======= AddMember =======
    @Test
    fun addMemberRequestProto() {
        val req = AddMemberRequestProto(companyId = "c1", userId = "u1", positionId = "p1")
        assertEquals("c1", req.companyId)
        assertEquals("u1", req.userId)
        assertEquals("p1", req.positionId)
    }

    @Test
    fun addMemberResponse_marshallerParseEmpty() {
        val parsed = AddMemberResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertNull(parsed.member)
    }

    // ======= ListMembers =======
    @Test
    fun listMembersResponse_marshallerParseEmpty() {
        val parsed = ListMembersResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertTrue(parsed.members.isEmpty())
        assertEquals("", parsed.nextCursor)
        assertFalse(parsed.hasMore)
    }

    // ======= JoinCompany =======
    @Test
    fun joinCompanyRequestProto() {
        val req = JoinCompanyRequestProto(companyId = "c1", inviteCode = "abc123")
        assertEquals("c1", req.companyId)
        assertEquals("abc123", req.inviteCode)
    }

    @Test
    fun joinCompanyResponse_marshallerParseEmpty() {
        val parsed = JoinCompanyResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertNull(parsed.member)
    }

    // ======= LeaveCompany =======
    @Test
    fun leaveCompanyResponse_marshallerParseEmpty() {
        val parsed = LeaveCompanyResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    // ======= DeleteCompany =======
    @Test
    fun deleteCompanyResponse_marshallerParseEmpty() {
        val parsed = DeleteCompanyResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    // ======= UpdateCompany =======
    @Test
    fun updateCompanyResponse_marshallerParseEmpty() {
        val parsed = UpdateCompanyResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertNull(parsed.company)
    }

    // ======= GetCompanyByUser =======
    @Test
    fun getCompanyByUserResponse_marshallerParseEmpty() {
        val parsed = GetCompanyByUserResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertNull(parsed.company)
        assertNull(parsed.member)
    }

    // ======= RemoveMember =======
    @Test
    fun removeMemberResponse_marshallerParseEmpty() {
        val parsed = RemoveMemberResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    // ======= UpdateMemberPosition =======
    @Test
    fun updateMemberPositionResponse_marshallerParseEmpty() {
        val parsed = UpdateMemberPositionResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    // ======= SetCompanyChatAccess =======
    @Test
    fun setCompanyChatAccessResponse_marshallerParseEmpty() {
        val parsed = SetCompanyChatAccessResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    // ======= CreateCompanyChat =======
    @Test
    fun createCompanyChatResponse_marshallerParseEmpty() {
        val parsed = CreateCompanyChatResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.chatId)
    }

    // ======= GetCompanyChats =======
    @Test
    fun getCompanyChatsResponse_marshallerParseEmpty() {
        val parsed = GetCompanyChatsResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertTrue(parsed.chats.isEmpty())
    }

    // ======= Proto data class defaults =======
    @Test
    fun companyProto_defaults() {
        val c = CompanyProto()
        assertEquals("", c.id)
        assertEquals("", c.name)
        assertEquals("", c.ownerId)
        assertEquals("", c.avatarUrl)
        assertEquals("", c.createdAt)
        assertEquals(0, c.memberCount)
    }

    @Test
    fun companyPositionProto_defaults() {
        val p = CompanyPositionProto()
        assertEquals("", p.id)
        assertEquals("", p.companyId)
        assertEquals("", p.title)
        assertEquals(0, p.level)
        assertEquals("", p.chatAccess)
    }

    @Test
    fun companyMemberProto_defaults() {
        val m = CompanyMemberProto()
        assertEquals("", m.id)
        assertEquals("", m.companyId)
        assertEquals("", m.userId)
        assertEquals("", m.username)
        assertEquals("", m.avatarUrl)
        assertNull(m.position)
        assertEquals("", m.joinedAt)
    }

    @Test
    fun companyChatInfoProto_defaults() {
        val c = CompanyChatInfoProto()
        assertEquals("", c.chatId)
        assertEquals("", c.companyId)
        assertEquals("", c.accessLevel)
        assertEquals(0, c.minPositionLevel)
    }

    @Test
    fun companyProto_equality() {
        val c1 = CompanyProto(id = "c1", name = "Acme", ownerId = "u1")
        val c2 = CompanyProto(id = "c1", name = "Acme", ownerId = "u1")
        assertEquals(c1, c2)
        assertEquals(c1.hashCode(), c2.hashCode())
    }

    @Test
    fun companyMemberProto_withPosition() {
        val pos = CompanyPositionProto(id = "p1", title = "Admin", level = 5)
        val member = CompanyMemberProto(id = "m1", companyId = "c1", userId = "u1", username = "alice", position = pos)
        assertEquals("alice", member.username)
        assertEquals("Admin", member.position?.title)
        assertEquals(5, member.position?.level)
    }

    // ======= CompanySettings =======
    @Test
    fun companySettingsProto_defaults() {
        val settings = CompanySettingsProto()
        assertEquals("", settings.companyId)
        assertFalse(settings.inviteOnly)
        assertEquals("", settings.defaultPositionId)
        assertFalse(settings.allowMemberInvite)
        assertEquals("member", settings.chatAccess)
        assertFalse(settings.requireApproval)
    }

    @Test
    fun companySettingsProto_withValues() {
        val settings = CompanySettingsProto(
            companyId = "c1",
            inviteOnly = true,
            defaultPositionId = "p1",
            allowMemberInvite = true,
            chatAccess = "management",
            requireApproval = true
        )
        assertEquals("c1", settings.companyId)
        assertTrue(settings.inviteOnly)
        assertEquals("p1", settings.defaultPositionId)
        assertTrue(settings.allowMemberInvite)
        assertEquals("management", settings.chatAccess)
        assertTrue(settings.requireApproval)
    }

    @Test
    fun getCompanySettingsResponse_marshallerParseEmpty() {
        val parsed = GetCompanySettingsResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertNull(parsed.settings)
    }

    @Test
    fun updateCompanySettingsRequestProto() {
        val settings = CompanySettingsProto(companyId = "c1", inviteOnly = true)
        val req = UpdateCompanySettingsRequestProto(companyId = "c1", settings = settings)
        assertEquals("c1", req.companyId)
        assertNotNull(req.settings)
        assertTrue(req.settings!!.inviteOnly)
    }

    @Test
    fun updateCompanySettingsResponse_marshallerParseEmpty() {
        val parsed = UpdateCompanySettingsResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    // ======= InviteCodes =======
    @Test
    fun inviteCodeInfoProto_defaults() {
        val code = InviteCodeInfoProto()
        assertEquals("", code.id)
        assertEquals("", code.code)
        assertEquals("", code.companyId)
        assertEquals("", code.createdBy)
        assertEquals("", code.createdAt)
        assertEquals("", code.expiresAt)
        assertEquals(1, code.maxUses)
        assertEquals(0, code.useCount)
        assertTrue(code.isActive)
    }

    @Test
    fun inviteCodeInfoProto_withValues() {
        val code = InviteCodeInfoProto(
            id = "ic-1",
            code = "ABC123",
            companyId = "c1",
            createdBy = "alice",
            maxUses = 10,
            useCount = 3,
            isActive = true
        )
        assertEquals("ic-1", code.id)
        assertEquals("ABC123", code.code)
        assertEquals("c1", code.companyId)
        assertEquals("alice", code.createdBy)
        assertEquals(10, code.maxUses)
        assertEquals(3, code.useCount)
        assertTrue(code.isActive)
    }

    @Test
    fun generateInviteCodeRequestProto() {
        val req = GenerateInviteCodeRequestProto(companyId = "c1", expiresHours = 24, maxUses = 5)
        assertEquals("c1", req.companyId)
        assertEquals(24, req.expiresHours)
        assertEquals(5, req.maxUses)
    }

    @Test
    fun generateInviteCodeResponse_marshallerParseEmpty() {
        val parsed = GenerateInviteCodeResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertNull(parsed.code)
    }

    @Test
    fun listInviteCodesResponse_marshallerParseEmpty() {
        val parsed = ListInviteCodesResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertTrue(parsed.codes.isEmpty())
    }

    @Test
    fun revokeInviteCodeRequestProto() {
        val req = RevokeInviteCodeRequestProto(codeId = "ic-1")
        assertEquals("ic-1", req.codeId)
    }

    @Test
    fun revokeInviteCodeResponse_marshallerParseEmpty() {
        val parsed = RevokeInviteCodeResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }

    @Test
    fun joinByInviteCodeRequestProto() {
        val req = JoinByInviteCodeRequestProto(code = "ABC123")
        assertEquals("ABC123", req.code)
    }

    @Test
    fun joinByInviteCodeResponse_marshallerParseEmpty() {
        val parsed = JoinByInviteCodeResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertEquals("", parsed.companyId)
        assertNull(parsed.member)
    }

    // ======= Company Notifications =======
    @Test
    fun sendCompanyNotificationRequestProto() {
        val req = SendCompanyNotificationRequestProto(
            companyId = "c1",
            eventType = 1,
            actorUsername = "alice",
            targetUsername = "bob",
            positionName = "Manager"
        )
        assertEquals("c1", req.companyId)
        assertEquals(1, req.eventType)
        assertEquals("alice", req.actorUsername)
        assertEquals("bob", req.targetUsername)
        assertEquals("Manager", req.positionName)
    }

    @Test
    fun sendCompanyNotificationResponse_marshallerParseEmpty() {
        val parsed = SendCompanyNotificationResponseMarshaller().parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
    }
}
