package lavender.client.android.data.session

import org.junit.Assert.*
import org.junit.Test

class ServerConfigTest {

    @Test
    fun prodEndpoint_correctPorts() {
        assertEquals(50051, ServerConfig.PROD.grpcPort)
        assertEquals(8082, ServerConfig.PROD.httpPort)
        assertEquals("Production", ServerConfig.PROD.label)
    }

    @Test
    fun devEndpoint_correctPorts() {
        assertEquals(50052, ServerConfig.DEV.grpcPort)
        assertEquals(8083, ServerConfig.DEV.httpPort)
        assertEquals("Development", ServerConfig.DEV.label)
    }

    @Test
    fun prodEndpoint_grpcAddressFormat() {
        assertEquals("13.140.25.249:50051", ServerConfig.PROD.grpcAddress)
    }

    @Test
    fun devEndpoint_grpcAddressFormat() {
        assertEquals("13.140.25.249:50052", ServerConfig.DEV.grpcAddress)
    }

    @Test
    fun prodEndpoint_httpUrlFormat() {
        assertEquals("http://13.140.25.249:8082", ServerConfig.PROD.httpUrl)
    }

    @Test
    fun devEndpoint_httpUrlFormat() {
        assertEquals("http://13.140.25.249:8083", ServerConfig.DEV.httpUrl)
    }

    @Test
    fun defaultServer_isProd() {
        val default = ServerConfig.defaultServer()
        assertEquals(ServerConfig.PROD, default)
    }

    @Test
    fun findKnown_prodAddress() {
        val result = ServerConfig.findKnown("13.140.25.249:50051")
        assertEquals(ServerConfig.PROD, result)
    }

    @Test
    fun findKnown_devAddress() {
        val result = ServerConfig.findKnown("13.140.25.249:50052")
        assertEquals(ServerConfig.DEV, result)
    }

    @Test
    fun findKnown_unknownAddress() {
        val result = ServerConfig.findKnown("10.0.0.1:9999")
        assertNull(result)
    }

    @Test
    fun findKnown_emptyAddress() {
        val result = ServerConfig.findKnown("")
        assertNull(result)
    }

    @Test
    fun getHttpPort_prodGrpcPort() {
        assertEquals(8082, ServerConfig.getHttpPort(50051))
    }

    @Test
    fun getHttpPort_devGrpcPort() {
        assertEquals(8083, ServerConfig.getHttpPort(50052))
    }

    @Test
    fun getHttpPort_unknownPort_defaultsToProd() {
        assertEquals(8082, ServerConfig.getHttpPort(9999))
    }

    @Test
    fun isDevServer_devPort() {
        assertTrue(ServerConfig.isDevServer(50052))
    }

    @Test
    fun isDevServer_prodPort() {
        assertFalse(ServerConfig.isDevServer(50051))
    }

    @Test
    fun isDevServer_unknownPort() {
        assertFalse(ServerConfig.isDevServer(9999))
    }

    @Test
    fun serverEndpoint_equality() {
        val a = ServerConfig.ServerEndpoint("host", 50051, 8082, "Test")
        val b = ServerConfig.ServerEndpoint("host", 50051, 8082, "Test")
        assertEquals(a, b)
    }

    @Test
    fun serverEndpoint_inequalityByPort() {
        val a = ServerConfig.ServerEndpoint("host", 50051, 8082, "Test")
        val b = ServerConfig.ServerEndpoint("host", 50052, 8083, "Test")
        assertNotEquals(a, b)
    }

    @Test
    fun prodAndDev_haveDifferentGrpcPorts() {
        assertNotEquals(ServerConfig.PROD.grpcPort, ServerConfig.DEV.grpcPort)
    }

    @Test
    fun prodAndDev_haveDifferentHttpPorts() {
        assertNotEquals(ServerConfig.PROD.httpPort, ServerConfig.DEV.httpPort)
    }

    @Test
    fun prodAndDev_shareSameHost() {
        assertEquals(ServerConfig.PROD.host, ServerConfig.DEV.host)
    }
}
