package lavender.client.android.data.grpc

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import lavender.client.android.data.proto.ServerInfoProto

/**
 * Handles server discovery: fetching server list from bootstrap server, raw protobuf parsing.
 *
 * Owns all ServerService-related RPC calls and raw protobuf parsing utilities.
 *
 * Extracted from RealGrpcClient v1.1.3.27 to continue modular decomposition.
 */
class GrpcServerDiscoveryClient(
    private val getSavedServerAddress: () -> String?
) {
    companion object {
        private const val BOOTSTRAP_HOST = "13.140.25.249"
        private const val BOOTSTRAP_PORT = 50051
    }

    // ======= Fetch Server List =======

    fun fetchServersList(cb: (List<ServerInfoProto>) -> Unit) {
        val savedAddress = getSavedServerAddress()
        val (host, port) = if (!savedAddress.isNullOrEmpty()) {
            val parts = savedAddress.split(":")
            Pair(parts[0], parts.getOrNull(1)?.toIntOrNull() ?: BOOTSTRAP_PORT)
        } else {
            Pair(BOOTSTRAP_HOST, BOOTSTRAP_PORT)
        }
        fetchServersFromHost(host, port, cb)
    }

    fun fetchServersFromHost(host: String, port: Int, cb: (List<ServerInfoProto>) -> Unit) {
        val tempChannel = io.grpc.okhttp.OkHttpChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .build()
        try {
            val methodDesc = MethodDescriptor.newBuilder<com.google.protobuf.ByteString, com.google.protobuf.ByteString>()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ServerService/ListServers")
                .setRequestMarshaller(object : MethodDescriptor.Marshaller<com.google.protobuf.ByteString> {
                    override fun stream(value: com.google.protobuf.ByteString) = value.newInput()
                    override fun parse(stream: java.io.InputStream) = com.google.protobuf.ByteString.readFrom(stream)
                })
                .setResponseMarshaller(object : MethodDescriptor.Marshaller<com.google.protobuf.ByteString> {
                    override fun stream(value: com.google.protobuf.ByteString) = value.newInput()
                    override fun parse(stream: java.io.InputStream) = com.google.protobuf.ByteString.readFrom(stream)
                })
                .build()

            val call = tempChannel.newCall(methodDesc, CallOptions.DEFAULT)
            val responseHolder = mutableListOf<com.google.protobuf.ByteString>()

            call.start(object : ClientCall.Listener<com.google.protobuf.ByteString>() {
                override fun onMessage(message: com.google.protobuf.ByteString) { responseHolder.add(message) }
                override fun onClose(status: Status, trailers: Metadata) {
                    try { tempChannel.shutdownNow() } catch (_: Exception) {}
                    if (!status.isOk || responseHolder.isEmpty()) { cb(emptyList()); return }
                    try { cb(parseServerList(responseHolder[0])) } catch (_: Exception) { cb(emptyList()) }
                }
            }, Metadata())
            call.sendMessage(com.google.protobuf.ByteString.EMPTY)
            call.halfClose()
            call.request(1)
        } catch (_: Exception) {
            try { tempChannel.shutdownNow() } catch (_: Exception) {}
            cb(emptyList())
        }
    }

    // ======= Proto Parsing =======

    fun parseServerList(data: com.google.protobuf.ByteString): List<ServerInfoProto> {
        val servers = mutableListOf<ServerInfoProto>()
        val bytes = data.toByteArray()
        var i = 0
        while (i < bytes.size) {
            val (tag, newI) = readVarint(bytes, i); i = newI
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 0x7).toInt()
            if (fieldNumber == 1 && wireType == 2) {
                val (len, newI2) = readVarint(bytes, i); i = newI2
                val msgLen = len.toInt()
                val msgBytes = bytes.copyOfRange(i, i + msgLen)
                i += msgLen
                servers.add(parseServerInfo(msgBytes))
            } else {
                i = skipField(bytes, i, wireType)
            }
        }
        return servers
    }

    private fun parseServerInfo(data: ByteArray): ServerInfoProto {
        var id = ""; var name = ""; var host = ""; var port = 50051; var isDefault = false
        var i = 0
        while (i < data.size) {
            val (tag, newI) = readVarint(data, i); i = newI
            val field = (tag ushr 3).toInt()
            val wireType = (tag and 0x7).toInt()
            when {
                field == 1 && wireType == 2 -> { val (len, p) = readVarint(data, i); i = p; id = String(data, i, len.toInt()); i += len.toInt() }
                field == 2 && wireType == 2 -> { val (len, p) = readVarint(data, i); i = p; name = String(data, i, len.toInt()); i += len.toInt() }
                field == 3 && wireType == 2 -> { val (len, p) = readVarint(data, i); i = p; host = String(data, i, len.toInt()); i += len.toInt() }
                field == 4 && wireType == 0 -> { val (v, p) = readVarint(data, i); i = p; port = v.toInt() }
                field == 5 && wireType == 0 -> { val (v, p) = readVarint(data, i); i = p; isDefault = v != 0L }
                else -> i = skipField(data, i, wireType)
            }
        }
        return ServerInfoProto(id = id, name = name, host = host, port = port, isDefault = isDefault)
    }

    private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int> {
        var value = 0L; var shift = 0; var i = start
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            value = value or ((b and 0x7F).toLong() shl shift)
            i++
            if (b and 0x80 == 0) break
            shift += 7
        }
        return Pair(value, i)
    }

    private fun skipField(data: ByteArray, start: Int, wireType: Int): Int {
        var i = start
        when (wireType) {
            0 -> { while (i < data.size && (data[i].toInt() and 0x80) != 0) i++; i++ }
            1 -> i += 8
            2 -> { val (len, p) = readVarint(data, i); i = p + len.toInt() }
            5 -> i += 4
        }
        return i
    }
}
