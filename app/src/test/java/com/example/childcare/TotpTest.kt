package com.example.childcare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpTest {
    private val secret = "12345678901234567890".toByteArray()

    @Test fun generatesSixDigits() {
        assertEquals("287082", Totp.code(secret, 59)) // RFC 6238 SHA1, six digits
    }

    @Test fun verifiesCurrentCode() {
        val currentCode = Totp.code(secret, 59)
        assertTrue(Totp.verify(secret, currentCode, 59))
    }

    @Test fun rejectsMalformedAndIncorrectCodes() {
        assertFalse(Totp.verify(secret, "12345", 59))
        assertFalse(Totp.verify(secret, "abcdef", 59))
        assertFalse(Totp.verify(secret, "000000", 59))
    }
}
