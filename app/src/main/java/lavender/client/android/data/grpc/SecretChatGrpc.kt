package lavender.client.android.data.grpc

import android.util.Log
import io.grpc.MethodDescriptor
import kotlinx.coroutines.*
import kotlinx.coroutines.CompletableDeferred
import lavender.client.android.data.proto.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import lavender.client.android.data.grpc.*

// ======= Secret Chat (E2EE) gRPC helpers =======

private const val TAG = "SecretChatGrpc"

suspend fun createSecretChat(
    targetUsername: String,
    targetUserId: String,
    publicKey: String,
    clientVersion: String
): Triple<String, Boolean, String> = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w(TAG, "createSecretChat: channel dead")
        return@withContext Triple("", false, "Connection lost")
    }

    val methodDesc = MethodDescriptor.newBuilder<CreateSecretChatRequestProto, CreateSecretChatResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/CreateSecretChat")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<CreateSecretChatRequestProto> {
            override fun stream(v: CreateSecretChatRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.targetUsername.isNotEmpty()) cos.writeString(1, v.targetUsername)
                if (v.targetUserId.isNotEmpty()) cos.writeString(2, v.targetUserId)
                if (v.publicKey.isNotEmpty()) cos.writeString(3, v.publicKey)
                if (v.clientVersion.isNotEmpty()) cos.writeString(4, v.clientVersion)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): CreateSecretChatRequestProto = CreateSecretChatRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<CreateSecretChatResponseProto> {
            override fun stream(v: CreateSecretChatResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): CreateSecretChatResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var chatId = ""
                var success = false
                var msg = ""
                var peerKey = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> chatId = cis.readString()
                        2 -> success = cis.readBool()
                        3 -> msg = cis.readString()
                        4 -> peerKey = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return CreateSecretChatResponseProto(chatId, success, msg, peerKey)
            }
        })
        .build()

    val request = CreateSecretChatRequestProto(
        targetUsername = targetUsername,
        targetUserId = targetUserId,
        publicKey = publicKey,
        clientVersion = clientVersion
    )
    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<Triple<String, Boolean, String>>()

    call.start(object : io.grpc.ClientCall.Listener<CreateSecretChatResponseProto>() {
        override fun onMessage(message: CreateSecretChatResponseProto) {
            result.complete(Triple(message.chatId, message.success, message.message))
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) {
                result.complete(Triple("", false, status.description ?: "Connection closed"))
            }
        }
    }, io.grpc.Metadata())

    call.sendMessage(request)
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(15000) { result.await() } ?: Triple("", false, "Timeout")
}

suspend fun exchangeSecretKey(
    chatId: String,
    publicKey: String
): Triple<Boolean, String, Boolean> = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w(TAG, "exchangeSecretKey: channel dead")
        return@withContext Triple(false, "", false)
    }

    val methodDesc = MethodDescriptor.newBuilder<ExchangeSecretKeyRequestProto, ExchangeSecretKeyResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/ExchangeSecretKey")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<ExchangeSecretKeyRequestProto> {
            override fun stream(v: ExchangeSecretKeyRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId)
                if (v.publicKey.isNotEmpty()) cos.writeString(2, v.publicKey)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): ExchangeSecretKeyRequestProto = ExchangeSecretKeyRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<ExchangeSecretKeyResponseProto> {
            override fun stream(v: ExchangeSecretKeyResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): ExchangeSecretKeyResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var success = false
                var peerKey = ""
                var peerHasKey = false
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()
                        2 -> peerKey = cis.readString()
                        3 -> peerHasKey = cis.readBool()
                        else -> cis.skipField(tag)
                    }
                }
                return ExchangeSecretKeyResponseProto(success, peerKey, peerHasKey)
            }
        })
        .build()

    val request = ExchangeSecretKeyRequestProto(chatId = chatId, publicKey = publicKey)
    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<Triple<Boolean, String, Boolean>>()

    call.start(object : io.grpc.ClientCall.Listener<ExchangeSecretKeyResponseProto>() {
        override fun onMessage(message: ExchangeSecretKeyResponseProto) {
            result.complete(Triple(message.success, message.peerPublicKey, message.peerHasKey))
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) {
                result.complete(Triple(false, "", false))
            }
        }
    }, io.grpc.Metadata())

    call.sendMessage(request)
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(15000) { result.await() } ?: Triple(false, "", false)
}

suspend fun getSecretChatKey(
    chatId: String
): Pair<String, Boolean> = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w(TAG, "getSecretChatKey: channel dead")
        return@withContext Pair("", false)
    }

    val methodDesc = MethodDescriptor.newBuilder<GetSecretChatKeyRequestProto, GetSecretChatKeyResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/GetSecretChatKey")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<GetSecretChatKeyRequestProto> {
            override fun stream(v: GetSecretChatKeyRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): GetSecretChatKeyRequestProto = GetSecretChatKeyRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<GetSecretChatKeyResponseProto> {
            override fun stream(v: GetSecretChatKeyResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): GetSecretChatKeyResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var peerKey = ""
                var peerHasKey = false
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> peerKey = cis.readString()
                        2 -> peerHasKey = cis.readBool()
                        else -> cis.skipField(tag)
                    }
                }
                return GetSecretChatKeyResponseProto(peerKey, peerHasKey)
            }
        })
        .build()

    val request = GetSecretChatKeyRequestProto(chatId = chatId)
    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<Pair<String, Boolean>>()

    call.start(object : io.grpc.ClientCall.Listener<GetSecretChatKeyResponseProto>() {
        override fun onMessage(message: GetSecretChatKeyResponseProto) {
            result.complete(Pair(message.peerPublicKey, message.peerHasKey))
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) {
                result.complete(Pair("", false))
            }
        }
    }, io.grpc.Metadata())

    call.sendMessage(request)
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(15000) { result.await() } ?: Pair("", false)
}
