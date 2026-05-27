package lavender.client.android.data.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-End Encryption manager for secret chats.
 *
 * Uses ECDH (Elliptic Curve Diffie-Hellman) for key exchange
 * and AES-256-GCM for message encryption.
 *
 * Each device generates a persistent EC key pair on first use.
 * When a secret chat is created, public keys are exchanged through the server.
 * After both keys are exchanged, all messages are E2EE-encrypted.
 */
object E2EEManager {

    private const val TAG = "E2EEManager"
    private const val PREFS_NAME = "e2ee_keys"

    private const val KEY_PRIVATE = "private_key_"
    private const val KEY_PUBLIC = "public_key_"
    private const val KEY_SHARED_SECRET = "shared_secret_"
    private const val KEY_FINGERPRINT = "fingerprint_"

    private const val EC_CURVE = "secp256r1"
    private const val AES_KEY_SIZE = 32
    private const val GCM_IV_SIZE = 12
    private const val GCM_TAG_SIZE = 128

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Generate a new EC key pair for E2EE.
     * Should be called once per device, or when user wants to rotate keys.
     */
    fun generateKeyPair(context: Context): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(EC_CURVE))
        val keyPair = generator.generateKeyPair()

        val privateKeyBase64 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        val publicKeyBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

        getPrefs(context).edit().apply {
            putString(KEY_PRIVATE, privateKeyBase64)
            putString(KEY_PUBLIC, publicKeyBase64)
            apply()
        }

        Log.d(TAG, "Generated new EC key pair")
        return keyPair
    }

    /**
     * Get the stored key pair, generating if necessary.
     */
    fun getKeyPair(context: Context): KeyPair {
        val prefs = getPrefs(context)
        val privateKeyBase64 = prefs.getString(KEY_PRIVATE, null)
        val publicKeyBase64 = prefs.getString(KEY_PUBLIC, null)

        if (privateKeyBase64 == null || publicKeyBase64 == null) {
            return generateKeyPair(context)
        }

        try {
            val privateKeyBytes = Base64.decode(privateKeyBase64, Base64.NO_WRAP)
            val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)

            val keyFactory = java.security.KeyFactory.getInstance("EC")
            val privateKeySpec = java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes)
            val publicKeySpec = java.security.spec.X509EncodedKeySpec(publicKeyBytes)

            val privateKey = keyFactory.generatePrivate(privateKeySpec)
            val publicKey = keyFactory.generatePublic(publicKeySpec)

            return KeyPair(publicKey, privateKey)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load key pair, regenerating", e)
            return generateKeyPair(context)
        }
    }

    fun getPublicKeyBase64(context: Context): String {
        return Base64.encodeToString(getKeyPair(context).public.encoded, Base64.NO_WRAP)
    }

    /**
     * Compute shared secret via ECDH using our private key and peer's public key.
     */
    fun computeSharedSecret(context: Context, peerPublicKeyBase64: String): ByteArray {
        val ourKeyPair = getKeyPair(context)
        val peerPublicKeyBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)

        val keyFactory = java.security.KeyFactory.getInstance("EC")
        val publicKeySpec = java.security.spec.X509EncodedKeySpec(peerPublicKeyBytes)
        val peerPublicKey = keyFactory.generatePublic(publicKeySpec)

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(ourKeyPair.private)
        keyAgreement.doPhase(peerPublicKey, true)

        val sharedSecret = keyAgreement.generateSecret()

        // Derive AES key using SHA-256
        val digest = MessageDigest.getInstance("SHA-256")
        val aesKey = digest.digest(sharedSecret)

        // Store for later use
        val chatId = UUID.randomUUID().toString()
        getPrefs(context).edit().apply {
            putString(KEY_SHARED_SECRET + chatId, Base64.encodeToString(aesKey, Base64.NO_WRAP))
            apply()
        }

        Log.d(TAG, "Computed shared secret (${aesKey.size} bytes)")
        return aesKey
    }

    /**
     * Derive a chat-specific shared secret and store it.
     */
    fun deriveAndStoreSharedSecret(context: Context, chatId: String, peerPublicKeyBase64: String): ByteArray {
        val ourKeyPair = getKeyPair(context)
        val peerPublicKeyBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)

        val keyFactory = java.security.KeyFactory.getInstance("EC")
        val publicKeySpec = java.security.spec.X509EncodedKeySpec(peerPublicKeyBytes)
        val peerPublicKey = keyFactory.generatePublic(publicKeySpec)

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(ourKeyPair.private)
        keyAgreement.doPhase(peerPublicKey, true)

        val rawSecret = keyAgreement.generateSecret()

        // HKDF-like: SHA-256(raw || chat_id) for chat-specific key
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(rawSecret)
        digest.update(chatId.toByteArray())
        val aesKey = digest.digest()

        getPrefs(context).edit().apply {
            putString(KEY_SHARED_SECRET + chatId, Base64.encodeToString(aesKey, Base64.NO_WRAP))
            // Also store fingerprint for verification
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(aesKey)
            putString(KEY_FINGERPRINT + chatId, Base64.encodeToString(fingerprint, Base64.NO_WRAP))
            apply()
        }

        Log.d(TAG, "Stored shared secret for chat: $chatId")
        return aesKey
    }

    /**
     * Get stored shared secret for a specific chat.
     */
    fun getSharedSecret(context: Context, chatId: String): ByteArray? {
        val base64 = getPrefs(context).getString(KEY_SHARED_SECRET + chatId, null) ?: return null
        return Base64.decode(base64, Base64.NO_WRAP)
    }

    /**
     * Get fingerprint for verification display.
     */
    fun getFingerprint(context: Context, chatId: String): String? {
        val fp = getPrefs(context).getString(KEY_FINGERPRINT + chatId, null) ?: return null
        // Return first 20 bytes as hex for display
        val bytes = Base64.decode(fp, Base64.NO_WRAP)
        return bytes.take(10).joinToString(" ") { String.format("%02X", it) }
    }

    /**
     * Encrypt a message using AES-256-GCM with the shared secret.
     * Returns Base64(IV + ciphertext).
     */
    fun encryptMessage(context: Context, chatId: String, plaintext: String): String? {
        val key = getSharedSecret(context, chatId) ?: run {
            Log.e(TAG, "No shared secret for chat: $chatId")
            return null
        }

        try {
            val iv = ByteArray(GCM_IV_SIZE)
            java.security.SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(key, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE, iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            // Prepend IV to ciphertext
            val combined = iv + ciphertext
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            return null
        }
    }

    /**
     * Decrypt a message using AES-256-GCM with the shared secret.
     */
    fun decryptMessage(context: Context, chatId: String, encryptedBase64: String): String? {
        val key = getSharedSecret(context, chatId) ?: run {
            Log.e(TAG, "No shared secret for chat: $chatId")
            return null
        }

        try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < GCM_IV_SIZE + 1) {
                Log.e(TAG, "Encrypted data too short")
                return null
            }

            val iv = combined.sliceArray(0 until GCM_IV_SIZE)
            val ciphertext = combined.sliceArray(GCM_IV_SIZE until combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(key, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

            val plaintext = cipher.doFinal(ciphertext)
            return String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            return null
        }
    }

    /**
     * Check if E2EE is ready for a chat (shared secret computed).
     */
    fun isE2EEActive(context: Context, chatId: String): Boolean {
        return getSharedSecret(context, chatId) != null
    }

    /**
     * Clear all E2EE data for a chat (e.g., on deletion).
     */
    fun clearChat(context: Context, chatId: String) {
        getPrefs(context).edit().apply {
            remove(KEY_SHARED_SECRET + chatId)
            remove(KEY_FINGERPRINT + chatId)
            apply()
        }
    }

    /**
     * Clear all E2EE data (e.g., on logout).
     */
    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
