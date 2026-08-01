package lavender.client.android.ui.chat.message

import android.app.Activity
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.crypto.E2EEManager
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message

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
    private var retryCount = 0
    private val maxRetries = 20
    private var retryJob: Job? = null
    @Volatile private var exchangeInProgress = false

    var onKeyExchangeComplete: ((Boolean) -> Unit)? = null
    var onKeyExchangeStart: (() -> Unit)? = null

    fun configure(roomId: String, isSecret: Boolean, toolbarSubtitle: TextView?) {
        this.roomId = roomId
        this.isSecret = isSecret
        this.toolbarSubtitle = toolbarSubtitle
    }

    private fun getRetryDelayMs(): Long {
        return when {
            retryCount <= 2 -> 2000L
            retryCount <= 5 -> 3000L
            retryCount <= 10 -> 5000L
            retryCount <= 15 -> 10000L
            else -> 15000L
        }
    }

    fun initE2EE() {
        if (!isSecret) return
        if (exchangeInProgress) {
            android.util.Log.d("E2EE", "initE2EE: exchange already in progress, skipping")
            return
        }
        if (secretKeyExchanged) {
            android.util.Log.d("E2EE", "initE2EE: key already exchanged, skipping")
            return
        }
        if (activity.isFinishing || activity.isDestroyed) return

        exchangeInProgress = true
        val publicKey = E2EEManager.getPublicKeyBase64(activity)
        android.util.Log.d("E2EE", "initE2EE: exchanging keys for chat: $roomId (attempt ${retryCount + 1}/$maxRetries)")
        onKeyExchangeStart?.invoke()

        grpcClient.exchangeSecretKey(roomId, publicKey) { success, peerKey, peerHasKey ->
            exchangeInProgress = false
            if (activity.isFinishing || activity.isDestroyed) return@exchangeSecretKey

            (activity as LifecycleOwner).lifecycleScope.launch {
                if (success && peerHasKey && peerKey.isNotEmpty()) {
                    E2EEManager.deriveAndStoreSharedSecret(activity, roomId, peerKey)
                    secretKeyExchanged = true
                    retryCount = 0
                    retryJob?.cancel()
                    onKeyExchangeComplete?.invoke(true)
                } else {
                    retryCount++
                    android.util.Log.d("E2EE", "Key exchange pending (attempt $retryCount/$maxRetries): success=$success, peerHasKey=$peerHasKey")
                    if (retryCount < maxRetries) {
                        val delayMs = getRetryDelayMs()
                        android.util.Log.d("E2EE", "Retrying in ${delayMs}ms")
                        retryJob?.cancel()
                        retryJob = (activity as? androidx.appcompat.app.AppCompatActivity)?.lifecycleScope?.launch {
                            delay(delayMs)
                            if (!activity.isFinishing && !activity.isDestroyed && !secretKeyExchanged) {
                                initE2EE()
                            }
                        }
                    } else {
                        android.util.Log.w("E2EE", "Key exchange failed after $maxRetries attempts for chat: $roomId")
                        onKeyExchangeComplete?.invoke(false)
                    }
                }
            }
        }
    }

    fun cancelPendingRetries() {
        retryJob?.cancel()
        retryJob = null
        exchangeInProgress = false
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
