package lavender.client.android.data.session

/**
 * Server endpoint configuration — single source of truth for all server addresses.
 *
 * Line B (v1.1.3.17+): Used by all components that need server host/port.
 * Never hardcode IPs — always reference ServerConfig.PROD or ServerConfig.DEV.
 *
 * Ports:
 *   PROD: gRPC 50051, HTTP 8082 (v1 endpoints on prod)
 *   DEV:  gRPC 50052, HTTP 8083 (v1 + v2 endpoints on dev)
 */
object ServerConfig {

    data class ServerEndpoint(
        val host: String,
        val grpcPort: Int,
        val httpPort: Int,
        val label: String
    ) {
        val grpcAddress: String get() = "$host:$grpcPort"
        val httpUrl: String get() = "http://$host:$httpPort"
    }

    val PROD = ServerEndpoint(
        host = "13.140.25.249",
        grpcPort = 50051,
        httpPort = 8082,
        label = "Production"
    )

    val DEV = ServerEndpoint(
        host = "13.140.25.249",
        grpcPort = 50052,
        httpPort = 8083,
        label = "Development"
    )

    /** Returns the default server endpoint (PROD). */
    fun defaultServer(): ServerEndpoint = PROD

    /** Returns the endpoint for a known server address, or null if unknown. */
    fun findKnown(address: String): ServerEndpoint? {
        return when (address) {
            PROD.grpcAddress -> PROD
            DEV.grpcAddress -> DEV
            else -> null
        }
    }

    /** Returns the HTTP port for a given gRPC port. */
    fun getHttpPort(grpcPort: Int): Int = when (grpcPort) {
        50051 -> 8082
        50052 -> 8083
        else -> 8082 // default to prod
    }

    /** Returns true if the gRPC port belongs to the dev server. */
    fun isDevServer(grpcPort: Int): Boolean = grpcPort == 50052
}
