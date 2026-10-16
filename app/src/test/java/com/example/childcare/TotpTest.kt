package com.example.childcare

import org.junit.Assert.assertEquals
import org.junit.Test

class TotpTest {
    @Test fun generatesSixDigits() {
        val code = Totp.code("12345678901234567890".toByteArray(), 59)
        assertEquals(6, code.length)
        assertEquals("287082", code) // RFC 6238 SHA1, truncated to six digits
    }
}
