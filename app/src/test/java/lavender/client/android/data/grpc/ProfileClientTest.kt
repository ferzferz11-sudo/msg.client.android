package lavender.client.android.data.grpc

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for ProfileClient version negotiation logic (v1/v2 capability detection).
 *
 * These tests verify that the capability negotiation correctly identifies
 * whether a server supports v2 features based on version strings from /info.
 */
class ProfileClientTest {

    @Before
    fun setup() {
        // Reset to unknown state before each test
        ProfileClient.serviceProfileVersion = ""
        ProfileClient.serviceChatVersion = ""
        ProfileClient.serviceAuthVersion = ""
        ProfileClient.serviceAIVersion = ""
    }

    @After
    fun tearDown() {
        ProfileClient.serviceProfileVersion = ""
        ProfileClient.serviceChatVersion = ""
        ProfileClient.serviceAuthVersion = ""
        ProfileClient.serviceAIVersion = ""
    }

    // ======= v1 server (prod) — versions unknown/empty =======

    @Test
    fun isProfileV2Supported_emptyVersion_returnsFalse() {
        ProfileClient.serviceProfileVersion = ""
        assertFalse(ProfileClient.isProfileV2Supported())
    }

    @Test
    fun isChatV2Supported_emptyVersion_returnsFalse() {
        ProfileClient.serviceChatVersion = ""
        assertFalse(ProfileClient.isChatV2Supported())
    }

    @Test
    fun isAuthV2Supported_emptyVersion_returnsFalse() {
        ProfileClient.serviceAuthVersion = ""
        assertFalse(ProfileClient.isAuthV2Supported())
    }

    // ======= v1 server explicit "1.0" =======

    @Test
    fun isProfileV2Supported_explicitV1_returnsFalse() {
        ProfileClient.serviceProfileVersion = "1.0"
        assertFalse(ProfileClient.isProfileV2Supported())
    }

    @Test
    fun isChatV2Supported_explicitV1_returnsFalse() {
        ProfileClient.serviceChatVersion = "1.0"
        assertFalse(ProfileClient.isChatV2Supported())
    }

    @Test
    fun isAuthV2Supported_v1_returnsFalse() {
        ProfileClient.serviceAuthVersion = "1.0"
        assertFalse(ProfileClient.isAuthV2Supported())
    }

    // ======= v2 server explicit "2.0" =======

    @Test
    fun isProfileV2Supported_v2_returnsTrue() {
        ProfileClient.serviceProfileVersion = "2.0"
        assertTrue(ProfileClient.isProfileV2Supported())
    }

    @Test
    fun isChatV2Supported_v2_returnsTrue() {
        ProfileClient.serviceChatVersion = "2.0"
        assertTrue(ProfileClient.isChatV2Supported())
    }

    @Test
    fun isAuthV2Supported_v2_returnsTrue() {
        ProfileClient.serviceAuthVersion = "2.0"
        assertTrue(ProfileClient.isAuthV2Supported())
    }

    // ======= Edge cases =======

    @Test
    fun isChatV2Supported_v3_futureVersion_returnsTrue() {
        // Future version should also be recognized as "at least v2"
        ProfileClient.serviceChatVersion = "3.0"
        assertTrue(ProfileClient.isChatV2Supported())
    }

    @Test
    fun isProfileV2Supported_v2Point1_returnsTrue() {
        ProfileClient.serviceProfileVersion = "2.1"
        assertTrue(ProfileClient.isProfileV2Supported())
    }

    @Test
    fun isChatV2Supported_v1Point9_returnsFalse() {
        ProfileClient.serviceChatVersion = "1.9"
        assertFalse(ProfileClient.isChatV2Supported())
    }

    @Test
    fun allV2_allServicesAtV2() {
        // Dev server: chat=2.0, profile=2.0, auth=2.0
        ProfileClient.serviceChatVersion = "2.0"
        ProfileClient.serviceProfileVersion = "2.0"
        ProfileClient.serviceAuthVersion = "2.0"
        ProfileClient.serviceAIVersion = "1.0"

        assertTrue(ProfileClient.isChatV2Supported())
        assertTrue(ProfileClient.isProfileV2Supported())
        assertTrue(ProfileClient.isAuthV2Supported())
    }

    @Test
    fun allV1_allServicesAtV1() {
        // Prod server: chat=1.0, profile=1.0, auth=2.0 (auth is always v2)
        ProfileClient.serviceChatVersion = "1.0"
        ProfileClient.serviceProfileVersion = "1.0"
        ProfileClient.serviceAuthVersion = "2.0"

        assertFalse(ProfileClient.isChatV2Supported())
        assertFalse(ProfileClient.isProfileV2Supported())
    }

    @Test
    fun allV1_emptyAfterHttpFailure() {
        // When /info HTTP request fails, all versions remain empty
        // → all v2 capabilities report as false → v1 fallback
        ProfileClient.serviceChatVersion = ""
        ProfileClient.serviceProfileVersion = ""
        ProfileClient.serviceAuthVersion = ""

        assertFalse(ProfileClient.isChatV2Supported())
        assertFalse(ProfileClient.isProfileV2Supported())
        assertFalse(ProfileClient.isAuthV2Supported())
    }
}
