package com.example.childcare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpTest {
    // RFC 6238 SHA-1 test secret; six-digit values from the RFC examples.
    private val secret = "12345678901234567890".toByteArray()

    @Test
    fun rfc6238SixDigitVectors() {
        val vectors = listOf(
            59L to "287082",
            1_111_111_109L to "081804",
            1_111_111_111L to "050471",
            1_234_567_890L to "005924",
            2_000_000_000L to "279037",
            20_000_000_000L to "353130"
        )
        vectors.forEach { (seconds, expected) ->
            assertEquals(expected, Totp.codeAtCounter(secret, seconds / 30L))
        }
    }

    @Test
    fun verifyAcceptsExactTimeStep() {
        val base32Secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertTrue(Totp.verifyBase32(base32Secret, "287082", 59L, window = 0L))
        assertTrue(Totp.verifyBase32(base32Secret, "081804", 1_111_111_109L, window = 0L))
    }

    @Test
    fun verifyRejectsMalformedCodesAndSecrets() {
        assertFalse(Totp.verifyBase32("NOT!BASE32", "123456", 59L))
        assertFalse(Totp.verifyBase32("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "12345", 59L))
        assertFalse(Totp.verifyBase32("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "12A456", 59L))
    }
}
