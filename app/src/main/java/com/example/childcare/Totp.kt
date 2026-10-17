package com.example.childcare

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer
import java.util.Locale

/**
 * RFC 6238 TOTP using HMAC-SHA1 and six decimal digits.
 *
 * A secret embedded in an APK can still be extracted; this helper improves
 * testability, not secrecy of a client-side shared secret.
 */
internal object Totp {
    fun verifyBase32(
        secretBase32: String,
        code: String,
        epochSeconds: Long,
        window: Long = 1L
    ): Boolean {
        if (code.length != 6 || code.any { it !in '0'..'9' } || window < 0L) return false
        val key = decodeBase32(secretBase32) ?: return false
        val counter = epochSeconds / 30L
        return (-window..window).any { offset ->
            val candidateCounter = counter + offset
            candidateCounter >= 0L && codeAtCounter(key, candidateCounter) == code
        }
    }

    internal fun codeAtCounter(key: ByteArray, counter: Long): String {
        require(counter >= 0L) { "counter must be non-negative" }
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(6, '0')
    }

    internal fun decodeBase32(input: String): ByteArray? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = input.filterNot { it.isWhitespace() || it == '=' }.uppercase(Locale.ROOT)
        if (cleaned.isEmpty() || cleaned.any { alphabet.indexOf(it) < 0 }) return null
        val output = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (ch in cleaned) {
            buffer = (buffer shl 5) or alphabet.indexOf(ch)
            bits += 5
            if (bits >= 8) {
                bits -= 8
                output.write((buffer shr bits) and 0xff)
            }
        }
        // RFC 4648 requires unused trailing bits to be zero. Reject malformed
        // encodings instead of silently accepting ambiguous secret strings.
        if (bits > 0 && (buffer and ((1 shl bits) - 1)) != 0) return null
        return output.toByteArray().takeIf { it.isNotEmpty() }
    }
}
