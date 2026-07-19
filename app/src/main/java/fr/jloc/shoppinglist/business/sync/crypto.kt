package fr.jloc.shoppinglist.business.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

private const val GCM_NONCE_LEN: Int = 16
private const val GCM_TAG_LEN: Int = 16

class PadKey private constructor(private val key: SecretKey) {

    companion object {
        fun generate(): PadKey {
            return PadKey(KeyGenerator.getInstance("HmacSHA256").generateKey())
        }

        fun fromBytes(bytes: ByteArray): PadKey {
            val key = SecretKeySpec(bytes, 0, bytes.size, "HmacSHA256")
            return PadKey(key)
        }
    }

    fun toBytes(): ByteArray {
        return key.encoded
    }

    fun deriveRemoteID(): String {
        val derivedBytes = deriveBytes("id")
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(derivedBytes)
    }

    fun encrypt(plaintext: ByteArray): ByteArray {

        val encryptionKey = deriveEncryptionKey()

        // encryption

        val nonce = ByteArray(GCM_NONCE_LEN)
        SecureRandom().nextBytes(nonce)

        val gcmParams = GCMParameterSpec(GCM_TAG_LEN * 8, nonce)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, gcmParams)
        val ciphertext = cipher.doFinal(plaintext)

        // building structured output

        val out = ByteArrayOutputStream()

        out.write(1) // version number

        out.write(nonce)

        out.write(ciphertext)

        //

        return out.toByteArray()
    }

    fun decrypt(ciphertext: ByteArray): ByteArray {

        // decoding the structured ciphertext

        val input = ByteArrayInputStream(ciphertext)

        val version = input.read()
        if (version < 0) throw SyncError.newUnexpectedError("Truncated ciphertext.")
        if (version != 1) throw SyncError.newAppNeedsUpdate("Unsupported ciphertext version.")

        val nonce = ByteArray(GCM_NONCE_LEN)
        if (input.read(nonce) != GCM_NONCE_LEN) {
            throw SyncError.newUnexpectedError("Truncated ciphertext.")
        }

        val ciphertextBody = input.readBytes()

        // decrypting

        val encryptionKey = deriveEncryptionKey()

        val gcmParams = GCMParameterSpec(GCM_TAG_LEN * 8, nonce)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, gcmParams)

        return cipher.doFinal(ciphertextBody)
    }

    private fun deriveEncryptionKey(): SecretKey {
        val encryptionKeyBytes = deriveBytes("encryption")
        return SecretKeySpec(encryptionKeyBytes, 0, 16, "AES")
    }

    private fun deriveBytes(usage: String): ByteArray {
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(key)
        hmac.update("v1:".toByteArray(Charsets.US_ASCII))
        hmac.update(usage.toByteArray(Charsets.US_ASCII))
        return hmac.doFinal()
    }
}
