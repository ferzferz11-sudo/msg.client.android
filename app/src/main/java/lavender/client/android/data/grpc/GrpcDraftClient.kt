package lavender.client.android.data.grpc

import lavender.client.android.data.proto.*

/**
 * Handles draft operations: saveDraft, getDraft, deleteDraft.
 *
 * Extracted from RealGrpcClient v1.1.3.25 to reduce God Object anti-pattern.
 */
class GrpcDraftClient(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    private val getUserId: () -> String?
) {

    fun saveDraft(
        roomId: String, text: String,
        replyId: String, replyUser: String, replyText: String,
        callback: (Boolean, String) -> Unit
    ) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<SaveDraftRequestProto, SaveDraftResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/SaveDraft")
                .setRequestMarshaller(SaveDraftRequestMarshaller())
                .setResponseMarshaller(SaveDraftResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<SaveDraftResponseProto>() {
            override fun onMessage(message: SaveDraftResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(SaveDraftRequestProto(getUserId() ?: "", roomId, text, replyId, replyUser, replyText))
        call.halfClose()
        call.request(1)
    }

    fun getDraft(roomId: String, callback: (String, String, String, String, Boolean) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<GetDraftRequestProto, GetDraftResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/GetDraft")
                .setRequestMarshaller(GetDraftRequestMarshaller())
                .setResponseMarshaller(GetDraftResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<GetDraftResponseProto>() {
            override fun onMessage(message: GetDraftResponseProto) {
                callback(message.draftText, message.repliedToMessageId, message.repliedToUser, message.repliedToText, message.hasDraft)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetDraftRequestProto(getUserId() ?: "", roomId))
        call.halfClose()
        call.request(1)
    }

    fun deleteDraft(roomId: String, callback: (Boolean) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<DeleteDraftRequestProto, DeleteDraftResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/DeleteDraft")
                .setRequestMarshaller(DeleteDraftRequestMarshaller())
                .setResponseMarshaller(DeleteDraftResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<DeleteDraftResponseProto>() {
            override fun onMessage(message: DeleteDraftResponseProto) { callback(message.success) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(DeleteDraftRequestProto(getUserId() ?: "", roomId))
        call.halfClose()
        call.request(1)
    }
}
