package lavender.client.android.data.grpc

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lavender.client.android.data.proto.*

/**
 * Handles profile, avatar, contacts, and themes operations.
 *
 * Owns all profile/contact/theme-related RPC calls to ChatService.
 * Does NOT own channel management — uses channel from GrpcConnectionManager.
 *
 * Extracted from RealGrpcClient v1.1.3.25 to reduce God Object anti-pattern.
 */
class GrpcProfileClient(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    private val getUserId: () -> String?,
    private val getUsername: () -> String?,
    private val avatarCache: MutableMap<String, String>,
    private val fullAvatarCache: MutableMap<String, String>,
    private val avatarCacheFlow: kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val fetchUserId: ((String, (String?, Boolean) -> Unit) -> Unit)? = null,
    private val setUserId: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "GrpcProfileClient"
    }

    // ======= Profile =======

    fun updateProfile(username: String, bio: String, status: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<UpdateProfileRequestProto, UpdateProfileResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/UpdateProfile")
                .setRequestMarshaller(UpdateProfileRequestMarshaller())
                .setResponseMarshaller(UpdateProfileResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<UpdateProfileResponseProto>() {
            override fun onMessage(message: UpdateProfileResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(UpdateProfileRequestProto(username = username, bio = bio, status = status, userId = getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun getUserProfile(userId: String, callback: (GetUserProfileResponseProto?) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<GetUserProfileRequestProto, GetUserProfileResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/GetUserProfile")
                .setRequestMarshaller(GetUserProfileRequestMarshaller())
                .setResponseMarshaller(GetUserProfileResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<GetUserProfileResponseProto>() {
            override fun onMessage(message: GetUserProfileResponseProto) { callback(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(null)
            }
        }, io.grpc.Metadata())
        call.sendMessage(GetUserProfileRequestProto(userId = userId))
        call.halfClose()
        call.request(1)
    }

    fun deleteProfile(username: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<DeleteProfileRequestProto, DeleteProfileResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/DeleteProfile")
                .setRequestMarshaller(DeleteProfileRequestMarshaller())
                .setResponseMarshaller(DeleteProfileResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<DeleteProfileResponseProto>() {
            override fun onMessage(message: DeleteProfileResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(DeleteProfileRequestProto(username = username, userId = getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    // ======= Avatar =======

    fun updateAvatar(username: String, avatarUrl: String, fullAvatarUrl: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<UpdateAvatarRequestProto, UpdateAvatarResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/UpdateAvatar")
                .setRequestMarshaller(UpdateAvatarRequestMarshaller())
                .setResponseMarshaller(UpdateAvatarResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<UpdateAvatarResponseProto>() {
            override fun onMessage(message: UpdateAvatarResponseProto) {
                if (message.success) {
                    updateAvatarCache(username, avatarUrl, fullAvatarUrl)
                }
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(UpdateAvatarRequestProto(username = username, avatarUrl = avatarUrl, fullAvatarUrl = fullAvatarUrl, userId = getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun getUserAvatar(username: String, userId: String = "", callback: (String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<GetUserAvatarRequestProto, GetUserAvatarResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/GetUserAvatar")
                .setRequestMarshaller(GetUserAvatarRequestMarshaller())
                .setResponseMarshaller(GetUserAvatarResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<GetUserAvatarResponseProto>() {
            override fun onMessage(message: GetUserAvatarResponseProto) {
                updateAvatarCache(username, message.avatarUrl, message.fullAvatarUrl)
                callback(message.avatarUrl)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetUserAvatarRequestProto(username, userId))
        call.halfClose()
        call.request(1)
    }

    fun updateAvatarCache(username: String, avatarUrl: String, fullAvatarUrl: String) {
        avatarCache[username] = avatarUrl
        if (fullAvatarUrl.isNotEmpty()) fullAvatarCache[username] = fullAvatarUrl
        avatarCacheFlow.value = avatarCache.toMap()
    }

    // ======= Username / Password =======

    fun updateUsername(oldUsername: String, newUsername: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<UpdateUsernameRequestProto, UpdateUsernameResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/UpdateUsername")
                .setRequestMarshaller(UpdateUsernameRequestMarshaller())
                .setResponseMarshaller(UpdateUsernameResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<UpdateUsernameResponseProto>() {
            override fun onMessage(message: UpdateUsernameResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(UpdateUsernameRequestProto(oldUsername, newUsername, getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun updatePassword(username: String, oldPassword: String, newPassword: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<UpdatePasswordRequestProto, UpdatePasswordResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/UpdatePassword")
                .setRequestMarshaller(UpdatePasswordRequestMarshaller())
                .setResponseMarshaller(UpdatePasswordResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<UpdatePasswordResponseProto>() {
            override fun onMessage(message: UpdatePasswordResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(UpdatePasswordRequestProto(username, oldPassword, newPassword, getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun adminUpdatePassword(targetUsername: String, newPassword: String, adminUsername: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<AdminUpdatePasswordRequestProto, AdminUpdatePasswordResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/AdminUpdatePassword")
                .setRequestMarshaller(AdminUpdatePasswordRequestMarshaller())
                .setResponseMarshaller(AdminUpdatePasswordResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<AdminUpdatePasswordResponseProto>() {
            override fun onMessage(message: AdminUpdatePasswordResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(AdminUpdatePasswordRequestProto(targetUsername, newPassword, adminUsername, getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun requestPasswordReset(email: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<RequestPasswordResetRequestProto, RequestPasswordResetResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/RequestPasswordReset")
                .setRequestMarshaller(RequestPasswordResetRequestMarshaller())
                .setResponseMarshaller(RequestPasswordResetResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<RequestPasswordResetResponseProto>() {
            override fun onMessage(message: RequestPasswordResetResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(RequestPasswordResetRequestProto(email))
        call.halfClose()
        call.request(1)
    }

    fun resetPassword(token: String, newPassword: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<ResetPasswordRequestProto, ResetPasswordResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/ResetPassword")
                .setRequestMarshaller(ResetPasswordRequestMarshaller())
                .setResponseMarshaller(ResetPasswordResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<ResetPasswordResponseProto>() {
            override fun onMessage(message: ResetPasswordResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(ResetPasswordRequestProto(token, newPassword))
        call.halfClose()
        call.request(1)
    }

    // ======= Contacts =======

    fun addContact(username: String, contactUsername: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<AddContactRequestProto, AddContactResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/AddContact")
                .setRequestMarshaller(AddContactRequestMarshaller())
                .setResponseMarshaller(AddContactResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<AddContactResponseProto>() {
            override fun onMessage(message: AddContactResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(AddContactRequestProto(username = username, contactUsername = contactUsername, userId = getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun removeContact(username: String, contactUsername: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<RemoveContactRequestProto, RemoveContactResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/RemoveContact")
                .setRequestMarshaller(RemoveContactRequestMarshaller())
                .setResponseMarshaller(RemoveContactResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<RemoveContactResponseProto>() {
            override fun onMessage(message: RemoveContactResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(RemoveContactRequestProto(username = username, contactUsername = contactUsername, userId = getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun getContacts(username: String, callback: (List<String>) -> Unit) {
        val userId = getUserId()
        if (userId.isNullOrEmpty() && fetchUserId != null) {
            fetchUserId.invoke(username) { fetchedId, found ->
                if (found && !fetchedId.isNullOrEmpty()) {
                    setUserId?.invoke(fetchedId)
                }
                doGetContacts(username, fetchedId ?: "", callback)
            }
        } else {
            doGetContacts(username, userId ?: "", callback)
        }
    }

    private fun doGetContacts(username: String, userId: String, callback: (List<String>) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<GetContactsRequestProto, GetContactsResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/GetContacts")
                .setRequestMarshaller(GetContactsRequestMarshaller())
                .setResponseMarshaller(GetContactsResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<GetContactsResponseProto>() {
            override fun onMessage(message: GetContactsResponseProto) { callback(message.contacts) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetContactsRequestProto(username = username, userId = userId))
        call.halfClose()
        call.request(1)
    }

    // ======= Themes =======

    fun getThemes(username: String, callback: (String, List<CustomThemeProto>) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<GetThemesRequestProto, GetThemesResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/GetThemes")
                .setRequestMarshaller(GetThemesRequestMarshaller())
                .setResponseMarshaller(GetThemesResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<GetThemesResponseProto>() {
            override fun onMessage(message: GetThemesResponseProto) { callback(message.currentThemeId, message.customThemes) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetThemesRequestProto(username = username, userId = getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun saveTheme(username: String, theme: CustomThemeProto, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<SaveThemeRequestProto, SaveThemeResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/SaveTheme")
                .setRequestMarshaller(SaveThemeRequestMarshaller())
                .setResponseMarshaller(SaveThemeResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<SaveThemeResponseProto>() {
            override fun onMessage(message: SaveThemeResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(SaveThemeRequestProto(username = username, theme = theme, userId = getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun setCurrentTheme(username: String, themeId: String, callback: (Boolean) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<SetCurrentThemeRequestProto, SetCurrentThemeResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/SetCurrentTheme")
                .setRequestMarshaller(SetCurrentThemeRequestMarshaller())
                .setResponseMarshaller(SetCurrentThemeResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<SetCurrentThemeResponseProto>() {
            override fun onMessage(message: SetCurrentThemeResponseProto) { callback(message.success) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(SetCurrentThemeRequestProto(username = username, themeId = themeId, userId = getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun deleteTheme(username: String, themeId: String, callback: (Boolean) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<DeleteThemeRequestProto, DeleteThemeResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/DeleteTheme")
                .setRequestMarshaller(DeleteThemeRequestMarshaller())
                .setResponseMarshaller(DeleteThemeResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<DeleteThemeResponseProto>() {
            override fun onMessage(message: DeleteThemeResponseProto) { callback(message.success) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(DeleteThemeRequestProto(username = username, themeId = themeId, userId = getUserId() ?: ""))
        call.halfClose()
        call.request(1)
    }

    // ======= FCM Logs =======

    fun getFCMLogs(callback: (List<FCMLogEntryProto>) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<GetFCMLogsRequestProto, GetFCMLogsResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/GetFCMLogs")
                .setRequestMarshaller(GetFCMLogsRequestMarshaller())
                .setResponseMarshaller(GetFCMLogsResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<GetFCMLogsResponseProto>() {
            override fun onMessage(message: GetFCMLogsResponseProto) { callback(message.logs) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetFCMLogsRequestProto())
        call.halfClose()
        call.request(1)
    }

    // ======= Device Management =======

    fun getDevices(uid: String, callback: (List<DeviceInfoProto>) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<GetDevicesRequestProto, GetDevicesResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/GetDevices")
                .setRequestMarshaller(GetDevicesRequestMarshaller())
                .setResponseMarshaller(GetDevicesResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<GetDevicesResponseProto>() {
            override fun onMessage(message: GetDevicesResponseProto) { callback(message.devices) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(emptyList())
            }
        }, io.grpc.Metadata())
        call.sendMessage(GetDevicesRequestProto(uid))
        call.halfClose()
        call.request(1)
    }

    fun deleteDevice(uid: String, deviceId: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<DeleteDeviceRequestProto, DeleteDeviceResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/DeleteDevice")
                .setRequestMarshaller(DeleteDeviceRequestMarshaller())
                .setResponseMarshaller(DeleteDeviceResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<DeleteDeviceResponseProto>() {
            override fun onMessage(message: DeleteDeviceResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(DeleteDeviceRequestProto(uid, deviceId))
        call.halfClose()
        call.request(1)
    }

    fun deleteOtherDevices(uid: String, currentDeviceId: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<DeleteDeviceRequestProto, DeleteDeviceResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/DeleteOtherDevices")
                .setRequestMarshaller(DeleteDeviceRequestMarshaller())
                .setResponseMarshaller(DeleteDeviceResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<DeleteDeviceResponseProto>() {
            override fun onMessage(message: DeleteDeviceResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(DeleteDeviceRequestProto(uid, currentDeviceId))
        call.halfClose()
        call.request(1)
    }
}
