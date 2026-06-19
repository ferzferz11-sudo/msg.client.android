package lavender.client.android.data

/**
 * Centralized server configuration.
 * All server addresses and ports in one place — no hardcoding elsewhere.
 */
object ServerConfig {

    const val PROD_HOST = "13.140.25.249"
    const val PROD_GRPC_PORT = 50051
    const val PROD_HTTP_PORT = 8082

    const val DEV_HOST = "13.140.25.249"
    const val DEV_GRPC_PORT = 50052
    const val DEV_HTTP_PORT = 8083

    const val PROD_SERVER_ADDRESS = "$PROD_HOST:$PROD_GRPC_PORT"
    const val DEV_SERVER_ADDRESS = "$DEV_HOST:$DEV_GRPC_PORT"

    fun grpcAddress(host: String, port: Int = PROD_GRPC_PORT) = "$host:$port"

    fun httpUrl(host: String, port: Int = PROD_HTTP_PORT) = "http://$host:$port"

    fun turnCredentialsUrl(host: String = PROD_HOST, port: Int = PROD_HTTP_PORT) =
        "http://$host:$port/turn-credentials"

    fun isDevPort(port: Int) = port == DEV_GRPC_PORT

    fun httpPort(grpcPort: Int) = if (grpcPort == DEV_GRPC_PORT) DEV_HTTP_PORT else PROD_HTTP_PORT
}
