package com.hvacpanel.transport

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Gree wraps every LAN message in an AES-ECB blob, base64'd into the `pack`
 * field. Discovery and binding use one well-known key shared by all units;
 * everything afterwards uses the per-device key handed out at bind time.
 */
object GreeCipher {

    /** The key every Gree Wi-Fi module ships with. Public knowledge, not a secret. */
    const val GENERIC_KEY = "a3K8Bx%2r8Y7#xDh"

    private fun cipher(mode: Int, key: String): Cipher =
        Cipher.getInstance("AES/ECB/PKCS5Padding").apply {
            init(mode, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        }

    fun encrypt(plain: String, key: String = GENERIC_KEY): String {
        val out = cipher(Cipher.ENCRYPT_MODE, key).doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(packed: String, key: String = GENERIC_KEY): String {
        val raw = Base64.decode(packed, Base64.NO_WRAP)
        return String(cipher(Cipher.DECRYPT_MODE, key).doFinal(raw), Charsets.UTF_8)
    }
}
