package lavender.client.android.ui.chat.message

import android.app.Activity
import android.widget.TextView
import lavender.client.android.R
import lavender.client.android.data.crypto.E2EEManager
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message
import lavender.client.android.data.grpc.GrpcClientExtensionsKt.*

/**
 * E2EE (end-to-end encryption) for secret chats.
 * Handles key exchange, encryption/decryption.
 */
class ChatE2EEDelegate(
    private val activity: Activity,
    private val grpcClient: GrpcClient
) {
    private var isSecret = false
    private var secretKeyExchanged = false
    private var roomId: String = ""
    private var toolbarSubtitle: TextView? = null

    var onKeyExchangeComplete: ((Boolean) -> Unit)? = null

    fun configure(roomId: String, isSecret: Boolean, toolbarSubtitle: TextView?) {
        this.roomId = roomId
        this.isSecret = isSecret
        this.toolbarSubtitle = toolbarSubtitle
    }

    fun initE2EE() {
        if (!isSecret) return
        val publicKey = E2EEManager.getPublicKeyBase64(activity)
        grpcClient.exchangeSecretKey(roomId, publicKey) { success, peerKey, peerHasKey ->
            activity.runOnUiThread {
                if (success && peerHasKey && peerKey.isNotEmpty()) {
                    E2EEManager.deriveAndStoreSharedSecret(activity, roomId, peerKey)
                    secretKeyExchanged = true
                    toolbarSubtitle?.text = activity.getString(R.string.e2ee_verified)
                    android.util.Log.d("E2EE", "Key exchange complete for chat: $roomId")
                    onKeyExchangeComplete?.invoke(true)
                } else {
                    toolbarSubtitle?.text = activity.getString(R.string.e2ee_pending)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ initE2EE() }, 3000)
                    onKeyExchangeComplete?.invoke(false)
                }
            }
        }
    }

    fun isKeyExchanged(): Boolean = secretKeyExchanged

    fun encryptAndSend(plainText: String, onResult: (Boolean) -> Unit) {
        if (!isSecret) { onResult(false); return }
        if (!secretKeyExchanged) {
            onResult(false)
            return
        }
        val encrypted = E2EEManager.encryptMessage(activity, roomId, plainText)
        if (encrypted != null) {
            grpcClient.sendE2EEMessage(roomId, encrypted)
            onResult(true)
        } else {
            onResult(false)
        }
    }

    fun decryptMessage(msg: Message): String? {
        if (!msg.isE2EE || msg.e2eePayload.isEmpty()) return null
        return E2EEManager.decryptMessage(activity, roomId, msg.e2eePayload)
    }
}
