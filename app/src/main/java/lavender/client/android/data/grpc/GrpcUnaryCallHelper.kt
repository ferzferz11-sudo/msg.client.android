package lavender.client.android.data.grpc

import android.util.Log
import io.grpc.MethodDescriptor
import io.grpc.ClientCall
import io.grpc.Metadata
import io.grpc.Status
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Universal unary gRPC call helper.
 *
 * Eliminates ~100+ duplicated MethodDescriptor/call/listener patterns across RealGrpcClient.
 *
 * Usage:
 * ```kotlin
 * val response = unaryCall<GetChatsRequestProto, GetChatsResponseProto>(
 *     fullMethod = "messenger.ChatService/GetChats",
 *     request = GetChatsRequestProto(username = username),
 *     requestMarshaller = GetChatsRequestMarshaller(),
 *     responseMarshaller = GetChatsResponseMarshaller()
 * )
 * ```
 */
@Suppress("DEPRECATION", "UNCHECKED_CAST")
internal suspend fun <ReqT, RespT> unaryCall(
    getChannel: () -> io.grpc.ManagedChannel?,
    fullMethod: String,
    request: ReqT,
    requestMarshaller: MethodDescriptor.Marshaller<ReqT>,
    responseMarshaller: MethodDescriptor.Marshaller<RespT>,
    tag: String = "RealGrpcClient"
): RespT? = suspendCancellableCoroutine { cont ->
    val ch = getChannel()
    if (ch == null) {
        cont.resume(null, onCancellation = {})
        return@suspendCancellableCoroutine
    }
    val method = MethodDescriptor.newBuilder<ReqT, RespT>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName(fullMethod)
        .setRequestMarshaller(requestMarshaller)
        .setResponseMarshaller(responseMarshaller)
        .build()
    val call = ch.newCall(method, io.grpc.CallOptions.DEFAULT)
    val listener = object : ClientCall.Listener<RespT>() {
        private var response: RespT? = null
        override fun onMessage(message: RespT) { response = message }
        override fun onClose(status: Status, trailers: Metadata) {
            if (status.isOk) {
                cont.resume(response, onCancellation = {})
            } else {
                Log.w(tag, "unaryCall failed [$fullMethod]: ${status.code} - ${status.description}")
                cont.resume(null, onCancellation = {})
            }
        }
    }
    call.start(listener, Metadata())
    call.sendMessage(request)
    call.halfClose()
    call.request(1)
}

/**
 * Unary call variant using class-based instantiation (for v2 ChatList methods).
 * Kept for backward compatibility during migration.
 */
@Suppress("DEPRECATION", "UNCHECKED_CAST")
internal suspend fun <ReqT, RespT> unaryCallWithClass(
    getChannel: () -> io.grpc.ManagedChannel?,
    fullMethod: String,
    request: ReqT,
    responseType: Class<RespT>,
    tag: String = "RealGrpcClient"
): RespT? = suspendCancellableCoroutine { cont ->
    val ch = getChannel()
    if (ch == null) {
        cont.resume(null, onCancellation = {})
        return@suspendCancellableCoroutine
    }
    val method = MethodDescriptor.newBuilder<ReqT, RespT>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName(fullMethod)
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<ReqT> {
            override fun stream(value: ReqT): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(0))
            override fun parse(stream: java.io.InputStream): ReqT = request
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<RespT> {
            override fun stream(value: RespT): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(0))
            override fun parse(stream: java.io.InputStream): RespT = responseType.getDeclaredConstructor().newInstance()
        })
        .build()
    val call = ch.newCall(method, io.grpc.CallOptions.DEFAULT)
    val listener = object : ClientCall.Listener<RespT>() {
        private var response: RespT? = null
        override fun onMessage(message: RespT) { response = message }
        override fun onClose(status: Status, trailers: Metadata) {
            if (status.isOk) {
                cont.resume(response, onCancellation = {})
            } else {
                Log.w(tag, "unaryCallWithClass failed [$fullMethod]: ${status.code}")
                cont.resume(null, onCancellation = {})
            }
        }
    }
    call.start(listener, Metadata())
    call.sendMessage(request)
    call.halfClose()
    call.request(1)
}
