package lavender.client.android.data.grpc

import io.grpc.MethodDescriptor
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import lavender.client.android.data.proto.OWLRequestProto
import lavender.client.android.data.proto.OWLResponseProto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

// ======= OWL AI Assistant =======

class OWLRequestMarshaller : MethodDescriptor.Marshaller<OWLRequestProto> {
    override fun stream(v: OWLRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
        if (v.message.isNotEmpty()) cos.writeString(2, v.message)
        if (v.sessionId.isNotEmpty()) cos.writeString(3, v.sessionId)
        cos.flush()
        return ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(s: java.io.InputStream): OWLRequestProto = OWLRequestProto()
}

class OWLResponseMarshaller : MethodDescriptor.Marshaller<OWLResponseProto> {
    override fun stream(v: OWLResponseProto): java.io.InputStream {
        val baos = ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.text.isNotEmpty()) cos.writeString(1, v.text)
        if (v.finished) cos.writeBool(2, v.finished)
        if (v.error.isNotEmpty()) cos.writeString(3, v.error)
        cos.flush()
        return ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(s: java.io.InputStream): OWLResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var text = ""
        var finished = false
        var error = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> text = cis.readString()
                2 -> finished = cis.readBool()
                3 -> error = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return OWLResponseProto(text, finished, error)
    }
}

// OWL streaming state
private val _owlResponses = MutableSharedFlow<OWLResponseProto>(extraBufferCapacity = 64)
val owlResponses: SharedFlow<OWLResponseProto> = _owlResponses

private val _owlTyping = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
val owlTyping: SharedFlow<Boolean> = _owlTyping

/**
 * Chat with OWL AI assistant via streaming gRPC.
 * Sends a message and returns a SharedFlow of response chunks.
 */
fun chatWithOWL(
    userId: String,
    message: String,
    scope: CoroutineScope,
    onResponse: (OWLResponseProto) -> Unit
) {
    val channel = RealGrpcClient.getChannel() ?: return
    val methodDesc = MethodDescriptor.newBuilder<OWLRequestProto, OWLResponseProto>()
        .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
        .setFullMethodName("messenger.ChatService/ChatWithOWL")
        .setRequestMarshaller(OWLRequestMarshaller())
        .setResponseMarshaller(OWLResponseMarshaller())
        .build()

    val request = OWLRequestProto(userId = userId, message = message)

    scope.launch(Dispatchers.IO) {
        try {
            _owlTyping.emit(true)
            val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
            val responseHolder = mutableListOf<OWLResponseProto>()

            call.start(object : io.grpc.ClientCall.Listener<OWLResponseProto>() {
                override fun onMessage(message: OWLResponseProto) {
                    responseHolder.add(message)
                    _owlResponses.tryEmit(message)
                    onResponse(message)
                }

                override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                    _owlTyping.emit(false)
                    if (!status.isOk) {
                        val errorResp = OWLResponseProto(
                            text = "",
                            finished = true,
                            error = status.description ?: "Connection error: ${status.code}"
                        )
                        _owlResponses.tryEmit(errorResp)
                        onResponse(errorResp)
                    }
                }
            }, io.grpc.Metadata())

            call.sendMessage(request)
            call.halfClose()
            call.request(1)
        } catch (e: Exception) {
            _owlTyping.emit(false)
            _owlResponses.tryEmit(OWLResponseProto(text = "", finished = true, error = e.message ?: "Unknown error"))
            onResponse(OWLResponseProto(text = "", finished = true, error = e.message ?: "Unknown error"))
        }
    }
}
