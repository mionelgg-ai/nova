package app.nova.vpn.data

import android.util.Base64
import app.nova.vpn.Cfg
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Mirrors tools/build_config.py */
object Crypto {
    private const val FREE_ITER = 10_000
    private const val CODE_ITER = 60_000

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun freeKey(salt: ByteArray) = pbkdf2(Cfg.APP_SECRET, salt, FREE_ITER)
    fun codeKey(code: String, salt: ByteArray) = pbkdf2(code, salt, CODE_ITER)

    fun normalize(code: String) = code.trim().uppercase().replace(" ", "")

    fun codeId(normalized: String): String =
        MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(8)

    /** input: base64( iv(12) || ciphertext || tag ) */
    fun decrypt(b64: String, key: ByteArray): ByteArray {
        val raw = Base64.decode(b64, Base64.DEFAULT)
        val iv = raw.copyOfRange(0, 12)
        val body = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }
}
