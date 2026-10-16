package com.example.childcare

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer
import java.util.Locale

/** RFC 6238 TOTP, 6 digits, 30-second step, HMAC-SHA1. */
object Totp {
    fun code(secret: ByteArray, epochSeconds: Long = System.currentTimeMillis() / 1000L): String {
        val counter = epochSeconds / 30L
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return String.format(Locale.US, "%06d", binary % 1_000_000)
    }

    fun verify(secret: ByteArray, candidate: String, epochSeconds: Long = System.currentTimeMillis() / 1000L): Boolean {
        if (!candidate.matches(Regex("\\d{6}"))) return false
        return (-1L..1L).any { delta -> constantTimeEquals(code(secret, epochSeconds + delta * 30L), candidate) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        var diff = a.length xor b.length
        for (i in 0 until minOf(a.length, b.length)) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
