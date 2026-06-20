package lavender.client.android.data.grpc

import android.util.Log
import io.grpc.MethodDescriptor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import lavender.client.android.data.proto.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

// ======= Remote Agent =======

suspend fun listRemoteAgents(filterStatus: String = ""): List<RemoteAgentInfoProto> = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("RemoteAgentGrpc", "listRemoteAgents: channel dead")
        return@withContext emptyList()
    }
    if (lavender.client.android.BuildConfig.DEBUG) {
    Log.d("RemoteAgentGrpc", "listRemoteAgents: calling messenger.ChatService/ListRemoteAgents")
    }
    val methodDesc = MethodDescriptor.newBuilder<ListRemoteAgentsRequestProto, ListRemoteAgentsResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/ListRemoteAgents")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<ListRemoteAgentsRequestProto> {
            override fun stream(v: ListRemoteAgentsRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.filterStatus.isNotEmpty()) cos.writeString(1, v.filterStatus)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): ListRemoteAgentsRequestProto = ListRemoteAgentsRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<ListRemoteAgentsResponseProto> {
            override fun stream(v: ListRemoteAgentsResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): ListRemoteAgentsResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                val agents = mutableListOf<RemoteAgentInfoProto>()
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> {
                            val len = cis.readRawVarint32()
                            val msgBytes = cis.readRawBytes(len)
                            if (msgBytes.isNotEmpty()) {
                                try {
                                    val inner = com.google.protobuf.CodedInputStream.newInstance(msgBytes)
                                    var id = ""; var name = ""; var host = ""; var ipAddress = ""; var os = ""
                                    var status = ""; val capabilities = mutableListOf<String>()
                                    var activeTasks = 0; var lastHeartbeat = ""
                                    while (!inner.isAtEnd) {
                                        val innerTag = inner.readTag()
                                        if (innerTag == 0) break
                                        when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                            1 -> id = inner.readString()
                                            2 -> name = inner.readString()
                                            3 -> host = inner.readString()
                                            4 -> ipAddress = inner.readString()
                                            5 -> os = inner.readString()
                                            6 -> status = inner.readString()
                                            7 -> capabilities.add(inner.readString())
                                            8 -> activeTasks = inner.readInt32()
                                            9 -> lastHeartbeat = inner.readString()
                                            else -> inner.skipField(innerTag)
                                        }
                                    }
                                    agents.add(RemoteAgentInfoProto(
                                        id = id, name = name, host = host, ipAddress = ipAddress, os = os,
                                        status = status, capabilities = capabilities,
                                        activeTasks = activeTasks, lastHeartbeat = lastHeartbeat
                                    ))
                                } catch (_: Exception) {}
                            }
                        }
                        else -> cis.skipField(tag)
                    }
                }
                return ListRemoteAgentsResponseProto(agents = agents)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<List<RemoteAgentInfoProto>>()

    call.start(object : io.grpc.ClientCall.Listener<ListRemoteAgentsResponseProto>() {
        override fun onMessage(message: ListRemoteAgentsResponseProto) {
            if (lavender.client.android.BuildConfig.DEBUG) {
            Log.d("RemoteAgentGrpc", "listRemoteAgents: received ${message.agents.size} agents")
            }
            result.complete(message.agents)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (lavender.client.android.BuildConfig.DEBUG) {
            Log.d("RemoteAgentGrpc", "listRemoteAgents: onClose status=${status.code} desc=${status.description}")
            }
            if (!result.isCompleted) result.complete(emptyList())
        }
    }, io.grpc.Metadata())

    call.sendMessage(ListRemoteAgentsRequestProto(filterStatus = filterStatus))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: emptyList<RemoteAgentInfoProto>().also {
        Log.w("RemoteAgentGrpc", "listRemoteAgents: timeout or empty result")
    }
}

// ======= Agent Token Management =======

suspend fun generateAgentToken(
    agentId: String,
    agentName: String,
    capabilities: List<String>,
    ttlHours: Int,
    adminUserId: String
): GenerateAgentTokenResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        return@withContext GenerateAgentTokenResponseProto(success = false, error = "Channel dead")
    }
    val methodDesc = MethodDescriptor.newBuilder<GenerateAgentTokenRequestProto, GenerateAgentTokenResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("hermes_agent.HermesAgentService/GenerateAgentToken")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<GenerateAgentTokenRequestProto> {
            override fun stream(v: GenerateAgentTokenRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
                if (v.agentName.isNotEmpty()) cos.writeString(2, v.agentName)
                v.capabilities.forEach { cos.writeString(3, it) }
                if (v.ttlHours != 0) cos.writeInt32(4, v.ttlHours)
                if (v.adminUserId.isNotEmpty()) cos.writeString(5, v.adminUserId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): GenerateAgentTokenRequestProto = GenerateAgentTokenRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<GenerateAgentTokenResponseProto> {
            override fun stream(v: GenerateAgentTokenResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): GenerateAgentTokenResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var success = false; var token = ""; var error = ""; var expiresAt = 0L
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()
                        2 -> token = cis.readString()
                        3 -> error = cis.readString()
                        4 -> expiresAt = cis.readInt64()
                        else -> cis.skipField(tag)
                    }
                }
                return GenerateAgentTokenResponseProto(success = success, token = token, error = error, expiresAt = expiresAt)
            }
        })
        .build()
    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    var earlyResult: GenerateAgentTokenResponseProto? = null
    val result = suspendCancellableCoroutine<GenerateAgentTokenResponseProto> { cont ->
        try {
            call.start(object : io.grpc.ClientCall.Listener<GenerateAgentTokenResponseProto>() {
                override fun onMessage(message: GenerateAgentTokenResponseProto) {
                    cont.resumeWith(Result.success(message))
                }
                override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                    if (cont.isActive) {
                        cont.resumeWith(Result.success(GenerateAgentTokenResponseProto(success = false, error = status.description ?: status.code.toString())))
                    } else {
                        earlyResult = GenerateAgentTokenResponseProto(success = false, error = status.description ?: status.code.toString())
                    }
                }
            }, io.grpc.Metadata())
            call.sendMessage(GenerateAgentTokenRequestProto(
                agentId = agentId, agentName = agentName, capabilities = capabilities,
                ttlHours = ttlHours, adminUserId = adminUserId
            ))
            call.halfClose()
            call.request(1)
        } catch (e: Exception) {
            if (cont.isActive) {
                cont.resumeWith(Result.success(GenerateAgentTokenResponseProto(success = false, error = "error: ${e.message}")))
            }
        }
    }
    val response = if (result.success) result else (earlyResult ?: result)
    return@withContext response
}

suspend fun revokeAgentToken(agentId: String, adminUserId: String): RevokeAgentTokenResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        return@withContext RevokeAgentTokenResponseProto(success = false, error = "Channel dead")
    }
    val methodDesc = MethodDescriptor.newBuilder<RevokeAgentTokenRequestProto, RevokeAgentTokenResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("hermes_agent.HermesAgentService/RevokeAgentToken")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<RevokeAgentTokenRequestProto> {
            override fun stream(v: RevokeAgentTokenRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
                if (v.adminUserId.isNotEmpty()) cos.writeString(2, v.adminUserId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): RevokeAgentTokenRequestProto = RevokeAgentTokenRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<RevokeAgentTokenResponseProto> {
            override fun stream(v: RevokeAgentTokenResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): RevokeAgentTokenResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var success = false; var error = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()
                        2 -> error = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return RevokeAgentTokenResponseProto(success = success, error = error)
            }
        })
        .build()
    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = suspendCancellableCoroutine<RevokeAgentTokenResponseProto> { cont ->
        call.start(object : io.grpc.ClientCall.Listener<RevokeAgentTokenResponseProto>() {
            override fun onMessage(message: RevokeAgentTokenResponseProto) {
                cont.resumeWith(Result.success(message))
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (cont.isActive) {
                    cont.resumeWith(Result.success(RevokeAgentTokenResponseProto(success = false, error = status.description ?: status.code.toString())))
                }
            }
        }, io.grpc.Metadata())
    }
    call.sendMessage(RevokeAgentTokenRequestProto(agentId = agentId, adminUserId = adminUserId))
    call.halfClose()
    call.request(1)
    return@withContext withTimeoutOrNull(10000) { result }
        ?: RevokeAgentTokenResponseProto(success = false, error = "Timeout")
}

suspend fun listAgentTokens(adminUserId: String): ListAgentTokensResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        return@withContext ListAgentTokensResponseProto(success = false, error = "Channel dead")
    }
    val methodDesc = MethodDescriptor.newBuilder<ListAgentTokensRequestProto, ListAgentTokensResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("hermes_agent.HermesAgentService/ListAgentTokens")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<ListAgentTokensRequestProto> {
            override fun stream(v: ListAgentTokensRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.adminUserId.isNotEmpty()) cos.writeString(1, v.adminUserId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): ListAgentTokensRequestProto = ListAgentTokensRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<ListAgentTokensResponseProto> {
            override fun stream(v: ListAgentTokensResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): ListAgentTokensResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var success = false; var error = ""
                val tokens = mutableListOf<AgentTokenInfoProto>()
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()
                        2 -> {
                            val len = cis.readRawVarint32()
                            val msgBytes = cis.readRawBytes(len)
                            if (msgBytes.isNotEmpty()) {
                                val inner = com.google.protobuf.CodedInputStream.newInstance(msgBytes)
                                var id = 0L; var agentId = ""; var agentName = ""; var tokenHash = ""
                                val capabilities = mutableListOf<String>()
                                var createdAt = ""; var expiresAt = ""; var revoked = false; var createdBy = ""
                                while (!inner.isAtEnd) {
                                    val innerTag = inner.readTag()
                                    if (innerTag == 0) break
                                    when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                        1 -> id = inner.readInt64()
                                        2 -> agentId = inner.readString()
                                        3 -> agentName = inner.readString()
                                        4 -> tokenHash = inner.readString()
                                        5 -> capabilities.add(inner.readString())
                                        6 -> createdAt = inner.readString()
                                        7 -> expiresAt = inner.readString()
                                        8 -> revoked = inner.readBool()
                                        9 -> createdBy = inner.readString()
                                        else -> inner.skipField(innerTag)
                                    }
                                }
                                tokens.add(AgentTokenInfoProto(
                                    id = id, agentId = agentId, agentName = agentName,
                                    tokenHash = tokenHash, capabilities = capabilities,
                                    createdAt = createdAt, expiresAt = expiresAt,
                                    revoked = revoked, createdBy = createdBy
                                ))
                            }
                        }
                        3 -> error = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return ListAgentTokensResponseProto(success = success, tokens = tokens, error = error)
            }
        })
        .build()
    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = suspendCancellableCoroutine<ListAgentTokensResponseProto> { cont ->
        call.start(object : io.grpc.ClientCall.Listener<ListAgentTokensResponseProto>() {
            override fun onMessage(message: ListAgentTokensResponseProto) {
                cont.resumeWith(Result.success(message))
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (cont.isActive) {
                    cont.resumeWith(Result.success(ListAgentTokensResponseProto(success = false, error = status.description ?: status.code.toString())))
                }
            }
        }, io.grpc.Metadata())
    }
    call.sendMessage(ListAgentTokensRequestProto(adminUserId = adminUserId))
    call.halfClose()
    call.request(1)
    return@withContext withTimeoutOrNull(10000) { result }
        ?: ListAgentTokensResponseProto(success = false, error = "Timeout")
}

// ======= Remote Agent task deployment =======

suspend fun deployAgentTask(
    agentId: String,
    taskType: String,
    params: Map<String, String> = emptyMap(),
    workingDir: String = "",
    timeoutSec: Int = 60,
    tunnelMode: Int = 0,
    tunnelHost: String = "",
    tunnelPort: Int = 22,
    tunnelUser: String = "",
    tunnelPassword: String = "",
    tunnelServerHost: String = "localhost",
    tunnelServerPort: Int = 50051,
    tunnelLocalPort: Int = 50052
): DeployAgentTaskResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        return@withContext DeployAgentTaskResponseProto(taskId = "", success = false, message = "Channel dead")
    }
    val methodDesc = MethodDescriptor.newBuilder<DeployAgentTaskRequestProto, DeployAgentTaskResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/DeployAgentTask")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<DeployAgentTaskRequestProto> {
            override fun stream(v: DeployAgentTaskRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
                if (v.taskType.isNotEmpty()) cos.writeString(2, v.taskType)
                v.params.forEach { (k, v2) ->
                    val entryBaos = ByteArrayOutputStream()
                    val entryCos = com.google.protobuf.CodedOutputStream.newInstance(entryBaos)
                    entryCos.writeString(1, k)
                    entryCos.writeString(2, v2)
                    entryCos.flush()
                    val entryBytes = entryBaos.toByteArray()
                    cos.writeTag(3, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
                    cos.writeUInt32NoTag(entryBytes.size)
                    cos.writeRawBytes(entryBytes)
                }
                if (v.workingDir.isNotEmpty()) cos.writeString(4, v.workingDir)
                if (v.timeoutSec > 0) cos.writeInt32(5, v.timeoutSec)
                if (v.tunnelMode != 0) cos.writeEnum(6, v.tunnelMode)
                if (v.tunnelHost.isNotEmpty()) cos.writeString(7, v.tunnelHost)
                if (v.tunnelPort != 22) cos.writeInt32(8, v.tunnelPort)
                if (v.tunnelUser.isNotEmpty()) cos.writeString(9, v.tunnelUser)
                if (v.tunnelPassword.isNotEmpty()) cos.writeString(10, v.tunnelPassword)
                if (v.tunnelServerHost != "localhost") cos.writeString(11, v.tunnelServerHost)
                if (v.tunnelServerPort != 50051) cos.writeInt32(12, v.tunnelServerPort)
                if (v.tunnelLocalPort != 50052) cos.writeInt32(13, v.tunnelLocalPort)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): DeployAgentTaskRequestProto = DeployAgentTaskRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<DeployAgentTaskResponseProto> {
            override fun stream(v: DeployAgentTaskResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): DeployAgentTaskResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var success = false; var taskId = ""; var error = ""
                var stdout = ""; var stderr = ""; var exitCode = 0
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()
                        2 -> taskId = cis.readString()
                        3 -> error = cis.readString()
                        4 -> stdout = cis.readString()
                        5 -> stderr = cis.readString()
                        6 -> exitCode = cis.readInt32()
                        else -> cis.skipField(tag)
                    }
                }
                return DeployAgentTaskResponseProto(
                    taskId = taskId, success = success, message = error,
                    stdout = stdout, stderr = stderr, exitCode = exitCode
                )
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<DeployAgentTaskResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<DeployAgentTaskResponseProto>() {
        override fun onMessage(message: DeployAgentTaskResponseProto) {
            result.complete(message)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(
                DeployAgentTaskResponseProto(taskId = "", success = false, message = status.description ?: status.code.toString())
            )
        }
    }, io.grpc.Metadata())

    call.sendMessage(DeployAgentTaskRequestProto(
        agentId, taskType, params, workingDir, timeoutSec,
        tunnelMode, tunnelHost, tunnelPort, tunnelUser, tunnelPassword,
        tunnelServerHost, tunnelServerPort, tunnelLocalPort
    ))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(15000) { result.await() }
        ?: DeployAgentTaskResponseProto(taskId = "", success = false, message = "Timeout")
}

fun deployAgentTaskStream(
    agentId: String,
    taskType: String,
    params: Map<String, String> = emptyMap(),
    workingDir: String = "",
    timeoutSec: Int = 60,
    tunnelMode: Int = 0,
    tunnelHost: String = "",
    tunnelPort: Int = 22,
    tunnelUser: String = "",
    tunnelPassword: String = "",
    tunnelServerHost: String = "localhost",
    tunnelServerPort: Int = 50051,
    tunnelLocalPort: Int = 50052
): kotlinx.coroutines.flow.Flow<DeployAgentTaskStreamResponseProto> = kotlinx.coroutines.flow.flow {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        emit(DeployAgentTaskStreamResponseProto(taskId = "", error = "Channel dead", done = true, status = "failed"))
        return@flow
    }
    val responseChannel = Channel<DeployAgentTaskStreamResponseProto>(UNLIMITED)
    var call: io.grpc.ClientCall<DeployAgentTaskRequestProto, DeployAgentTaskStreamResponseProto>? = null

    try {
        val methodDesc = MethodDescriptor.newBuilder<DeployAgentTaskRequestProto, DeployAgentTaskStreamResponseProto>()
            .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
            .setFullMethodName("messenger.ChatService/DeployAgentTaskStream")
            .setRequestMarshaller(object : MethodDescriptor.Marshaller<DeployAgentTaskRequestProto> {
                override fun stream(v: DeployAgentTaskRequestProto): java.io.InputStream {
                    val baos = ByteArrayOutputStream()
                    val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                    if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
                    if (v.taskType.isNotEmpty()) cos.writeString(2, v.taskType)
                    v.params.forEach { (k, v2) ->
                        val entryBaos = ByteArrayOutputStream()
                        val entryCos = com.google.protobuf.CodedOutputStream.newInstance(entryBaos)
                        entryCos.writeString(1, k)
                        entryCos.writeString(2, v2)
                        entryCos.flush()
                        val entryBytes = entryBaos.toByteArray()
                        cos.writeTag(3, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
                        cos.writeUInt32NoTag(entryBytes.size)
                        cos.writeRawBytes(entryBytes)
                    }
                    if (v.workingDir.isNotEmpty()) cos.writeString(4, v.workingDir)
                    if (v.timeoutSec > 0) cos.writeInt32(5, v.timeoutSec)
                    if (v.tunnelMode != 0) cos.writeEnum(6, v.tunnelMode)
                    if (v.tunnelHost.isNotEmpty()) cos.writeString(7, v.tunnelHost)
                    if (v.tunnelPort != 22) cos.writeInt32(8, v.tunnelPort)
                    if (v.tunnelUser.isNotEmpty()) cos.writeString(9, v.tunnelUser)
                    if (v.tunnelPassword.isNotEmpty()) cos.writeString(10, v.tunnelPassword)
                    if (v.tunnelServerHost != "localhost") cos.writeString(11, v.tunnelServerHost)
                    if (v.tunnelServerPort != 50051) cos.writeInt32(12, v.tunnelServerPort)
                    if (v.tunnelLocalPort != 50052) cos.writeInt32(13, v.tunnelLocalPort)
                    cos.flush()
                    return ByteArrayInputStream(baos.toByteArray())
                }
                override fun parse(s: java.io.InputStream): DeployAgentTaskRequestProto = DeployAgentTaskRequestProto()
            })
            .setResponseMarshaller(object : MethodDescriptor.Marshaller<DeployAgentTaskStreamResponseProto> {
                override fun stream(v: DeployAgentTaskStreamResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
                override fun parse(s: java.io.InputStream): DeployAgentTaskStreamResponseProto {
                    val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                    var taskId = ""; var stdoutChunk = ""; var stderrChunk = ""
                    var progress = ""; var status = ""; var stdout = ""; var stderr = ""
                    var exitCode = 0; var durationMs = 0L; var error = ""; var done = false
                    while (!cis.isAtEnd) {
                        val tag = cis.readTag()
                        if (tag == 0) break
                        when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                            1 -> taskId = cis.readString()
                            2 -> stdoutChunk = cis.readString()
                            3 -> stderrChunk = cis.readString()
                            4 -> progress = cis.readString()
                            5 -> status = cis.readString()
                            6 -> stdout = cis.readString()
                            7 -> stderr = cis.readString()
                            8 -> exitCode = cis.readInt32()
                            9 -> durationMs = cis.readInt64()
                            10 -> error = cis.readString()
                            11 -> done = cis.readBool()
                            else -> cis.skipField(tag)
                        }
                    }
                    return DeployAgentTaskStreamResponseProto(
                        taskId = taskId, stdoutChunk = stdoutChunk, stderrChunk = stderrChunk,
                        progress = progress, status = status, stdout = stdout, stderr = stderr,
                        exitCode = exitCode, durationMs = durationMs, error = error, done = done
                    )
                }
            })
            .build()

        call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)

        call.start(object : io.grpc.ClientCall.Listener<DeployAgentTaskStreamResponseProto>() {
            override fun onMessage(message: DeployAgentTaskStreamResponseProto) {
                responseChannel.trySend(message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    responseChannel.trySend(DeployAgentTaskStreamResponseProto(
                        error = status.description ?: status.code.toString(), done = true, status = "failed"
                    ))
                }
                responseChannel.close()
            }
        }, io.grpc.Metadata())

        call.sendMessage(DeployAgentTaskRequestProto(
            agentId, taskType, params, workingDir, timeoutSec,
            tunnelMode, tunnelHost, tunnelPort, tunnelUser, tunnelPassword,
            tunnelServerHost, tunnelServerPort, tunnelLocalPort
        ))
        call.halfClose()
        call.request(1)

        for (update in responseChannel) {
            emit(update)
        }
    } catch (e: Exception) {
        emit(DeployAgentTaskStreamResponseProto(
            error = e.message ?: "Stream error", done = true, status = "failed"
        ))
    } finally {
        call?.cancel("Flow completed", null)
        responseChannel.close()
    }
}

suspend fun getRemoteAgentStatus(agentId: String): GetRemoteAgentStatusResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        return@withContext GetRemoteAgentStatusResponseProto(status = "unavailable")
    }
    val methodDesc = MethodDescriptor.newBuilder<GetRemoteAgentStatusRequestProto, GetRemoteAgentStatusResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/GetRemoteAgentStatus")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<GetRemoteAgentStatusRequestProto> {
            override fun stream(v: GetRemoteAgentStatusRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): GetRemoteAgentStatusRequestProto = GetRemoteAgentStatusRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<GetRemoteAgentStatusResponseProto> {
            override fun stream(v: GetRemoteAgentStatusResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): GetRemoteAgentStatusResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var status = ""
                var activeTasks = 0; var lastHeartbeat = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> status = cis.readString()
                        2 -> activeTasks = cis.readInt32()
                        3 -> lastHeartbeat = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return GetRemoteAgentStatusResponseProto(
                    status = status, activeTasks = activeTasks, lastHeartbeat = lastHeartbeat
                )
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<GetRemoteAgentStatusResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<GetRemoteAgentStatusResponseProto>() {
        override fun onMessage(message: GetRemoteAgentStatusResponseProto) {
            result.complete(message)
        }
        override fun onClose(closeStatus: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(
                GetRemoteAgentStatusResponseProto(status = "error: ${closeStatus.code}")
            )
        }
    }, io.grpc.Metadata())

    call.sendMessage(GetRemoteAgentStatusRequestProto(agentId))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() }
        ?: GetRemoteAgentStatusResponseProto(status = "timeout")
}

// ======= Agent Process Management (server-side) =======

suspend fun startAgentOnServer(
    agentId: String,
    agentName: String,
    token: String,
    serverAddress: String = "",
    capabilities: List<String> = listOf("shell", "git", "build", "file", "docker", "ai"),
    adminUserId: String = ""
): StartAgentResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        return@withContext StartAgentResponseProto(success = false, error = "Channel dead")
    }
    val methodDesc = MethodDescriptor.newBuilder<StartAgentRequestProto, StartAgentResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("hermes_agent.HermesAgentService/StartAgent")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<StartAgentRequestProto> {
            override fun stream(v: StartAgentRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
                if (v.agentName.isNotEmpty()) cos.writeString(2, v.agentName)
                if (v.token.isNotEmpty()) cos.writeString(3, v.token)
                if (v.serverAddress.isNotEmpty()) cos.writeString(4, v.serverAddress)
                v.capabilities.forEach { cos.writeString(5, it) }
                if (v.adminUserId.isNotEmpty()) cos.writeString(6, v.adminUserId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): StartAgentRequestProto = StartAgentRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<StartAgentResponseProto> {
            override fun stream(v: StartAgentResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): StartAgentResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var success = false; var error = ""; var pid = 0
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()
                        2 -> error = cis.readString()
                        3 -> pid = cis.readInt32()
                        else -> cis.skipField(tag)
                    }
                }
                return StartAgentResponseProto(success = success, error = error, pid = pid)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<StartAgentResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<StartAgentResponseProto>() {
        override fun onMessage(message: StartAgentResponseProto) { result.complete(message) }
        override fun onClose(closeStatus: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(
                StartAgentResponseProto(success = false, error = closeStatus.description ?: closeStatus.code.toString())
            )
        }
    }, io.grpc.Metadata())

    call.sendMessage(StartAgentRequestProto(
        agentId = agentId, agentName = agentName, token = token,
        serverAddress = serverAddress, capabilities = capabilities, adminUserId = adminUserId
    ))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(15000) { result.await() }
        ?: StartAgentResponseProto(success = false, error = "Timeout")
}

suspend fun stopAgentOnServer(agentId: String, adminUserId: String = ""): StopAgentResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        return@withContext StopAgentResponseProto(success = false, error = "Channel dead")
    }
    val methodDesc = MethodDescriptor.newBuilder<StopAgentRequestProto, StopAgentResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("hermes_agent.HermesAgentService/StopAgent")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<StopAgentRequestProto> {
            override fun stream(v: StopAgentRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
                if (v.adminUserId.isNotEmpty()) cos.writeString(2, v.adminUserId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): StopAgentRequestProto = StopAgentRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<StopAgentResponseProto> {
            override fun stream(v: StopAgentResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): StopAgentResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var success = false; var error = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()
                        2 -> error = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return StopAgentResponseProto(success = success, error = error)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<StopAgentResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<StopAgentResponseProto>() {
        override fun onMessage(message: StopAgentResponseProto) { result.complete(message) }
        override fun onClose(closeStatus: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(
                StopAgentResponseProto(success = false, error = closeStatus.description ?: closeStatus.code.toString())
            )
        }
    }, io.grpc.Metadata())

    call.sendMessage(StopAgentRequestProto(agentId = agentId, adminUserId = adminUserId))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() }
        ?: StopAgentResponseProto(success = false, error = "Timeout")
}

suspend fun getAgentProcessStatus(agentId: String, adminUserId: String = ""): GetAgentProcessStatusResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        return@withContext GetAgentProcessStatusResponseProto(running = false, error = "Channel dead")
    }
    val methodDesc = MethodDescriptor.newBuilder<GetAgentProcessStatusRequestProto, GetAgentProcessStatusResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("hermes_agent.HermesAgentService/GetAgentProcessStatus")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<GetAgentProcessStatusRequestProto> {
            override fun stream(v: GetAgentProcessStatusRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
                if (v.adminUserId.isNotEmpty()) cos.writeString(2, v.adminUserId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): GetAgentProcessStatusRequestProto = GetAgentProcessStatusRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<GetAgentProcessStatusResponseProto> {
            override fun stream(v: GetAgentProcessStatusResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): GetAgentProcessStatusResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var running = false; var pid = 0; var agentId = ""; var startedAt = ""; var error = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> running = cis.readBool()
                        2 -> pid = cis.readInt32()
                        3 -> agentId = cis.readString()
                        4 -> startedAt = cis.readString()
                        5 -> error = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return GetAgentProcessStatusResponseProto(running = running, pid = pid, agentId = agentId, startedAt = startedAt, error = error)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<GetAgentProcessStatusResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<GetAgentProcessStatusResponseProto>() {
        override fun onMessage(message: GetAgentProcessStatusResponseProto) { result.complete(message) }
        override fun onClose(closeStatus: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(
                GetAgentProcessStatusResponseProto(running = false, error = closeStatus.description ?: closeStatus.code.toString())
            )
        }
    }, io.grpc.Metadata())

    call.sendMessage(GetAgentProcessStatusRequestProto(agentId = agentId, adminUserId = adminUserId))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() }
        ?: GetAgentProcessStatusResponseProto(running = false, error = "Timeout")
}
