package lavender.client.android.data.proto

data class ServerInfoProto(
    val id: String = "",
    val name: String = "",
    val host: String = "",
    val port: Int = 50051,
    val isDefault: Boolean = false
) {
    val address: String get() = "$host:$port"
}
