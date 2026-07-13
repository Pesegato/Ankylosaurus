package com.pesegato.core

object SecretGenerator {

    /*
    non veniva usata, vedi quella sotto?
    fun generateTOTPSecret(): String {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val sb = StringBuilder(32)
        for (i in 0 until 32) {
            sb.append(base32Chars[secureRandom.nextInt(base32Chars.length)])
        }
        return sb.toString()
    }
    */

    fun generateTOTPSecret(): String {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

        return (1..8)
            .map { base32Chars[CryptoEngine.secureRandom.nextInt(base32Chars.length)] }
            .joinToString("")
    }

    fun generateOTPL1(): String {
        val numChars = "0123456789"
        val sb = StringBuilder(6)
        for (i in 0 until 6) {
            sb.append(numChars[CryptoEngine.secureRandom.nextInt(numChars.length)])
        }
        return sb.toString()
    }

    // 1. Genera l'OTP per la prossima volta
    fun generateNextOTP(): ByteArray {
        val otp = ByteArray(32)
        CryptoEngine.secureRandom.nextBytes(otp)
        return otp
    }

    fun generatePIN(): String {
        // 1. Definisci l'alfabeto Base64 (URL-safe per evitare caratteri come '+' e '/')
        val base64Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        // 2. Genera 16 caratteri casuali usando lo stesso approccio di generateTOTPSecret
        return (1..16)
            .map { base64Chars[CryptoEngine.secureRandom.nextInt(base64Chars.length)] }
            .joinToString("")
    }
}