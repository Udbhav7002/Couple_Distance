package com.closer.app.backend.relay

import java.security.SecureRandom

object CodeGenerator {
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun generate(length: Int = 6): String {
        val chars = CharArray(length)
        for (i in 0 until length) {
            chars[i] = ALPHABET[random.nextInt(ALPHABET.length)]
        }
        return String(chars)
    }
}
