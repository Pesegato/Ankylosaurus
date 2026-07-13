package com.pesegato.core

import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKeyFactory
import javax.crypto.interfaces.DHPublicKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

object CryptoEngine {
    val secureRandom: SecureRandom = SecureRandom()

    val ITERATION_BEST = 1000000
    val ITERATION_AVG_2 = 50000 //3 secondi sul POCO!
    val ITERATION_AVG_1 = 10000
    val ITERATION_FAST = 5000

    // Cache: Hash(Password) -> Fingerprint di 8 caratteri
    private val cache = ConcurrentHashMap<String, String>()

    /**
     * Recupera il fingerprint dalla cache o lo calcola deterministicamente
     * estraendo il salt dalla password stessa.
     */
    fun getFingerprint(password: String?): String {
        if (password.isNullOrBlank())
            return "----"
        // 1. Calcoliamo la chiave anonima per l'indicizzazione nella HashMap
        val cacheKey = hashSha256(password)

        // 2. Se è in cache restituisce subito (0ms), altrimenti calcola (30-40ms)
        return cache.computeIfAbsent(cacheKey) {

            // Usiamo l'hash della password STESSA come salt deterministico per il PBKDF2
            val salt = cacheKey.toByteArray()

            // Calcolo lento del fingerprint (50.000 iterazioni) per bloccare il brute-force
            val spec = PBEKeySpec(password.toCharArray(), salt, 50000, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = factory.generateSecret(spec).encoded

            // Trasformiamo i primi 3 byte dell'hash in un numero positivo
            val numericValue = ((hash[0].toInt() and 0xFF) shl 16) or
                    ((hash[1].toInt() and 0xFF) shl 8) or
                    (hash[2].toInt() and 0xFF)
            // Restituiamo le ultime 4 cifre decimali (range 0000-9999)
            "%04d".format(numericValue % 10000)
        }
    }

    /**
     * Svuota completamente la cache in RAM.
     */
    fun clear() {
        cache.clear()
    }

    /**
     * Funzione di supporto per generare lo SHA-256 (usato sia come chiave che come salt)
     */
    private fun hashSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // --- 1. GESTIONE OTP E KDF ---

    // Espande l'OTP di 8 caratteri in una chiave lunga quanto serve (es. per XOR o AES)
    fun deriveKeyFromOTP(otp: String, salt: ByteArray, lengthBytes: Int): ByteArray {
        val spec = PBEKeySpec(otp.toCharArray(), salt, ITERATION_AVG_1, lengthBytes * 8)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }

    // --- 2. DIFFIE-HELLMAN ---

    @JvmStatic
    fun generateInitialDHKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("DiffieHellman")
        kpg.initialize(2048, secureRandom)
        return kpg.generateKeyPair()
    }

    fun generateSecondaryDHKeyPair(encodedPublicKeyA: ByteArray): KeyPair {
        val kf = KeyFactory.getInstance("DiffieHellman")
        val pubKeyA = kf.generatePublic(X509EncodedKeySpec(encodedPublicKeyA)) as DHPublicKey
        val kpg = KeyPairGenerator.getInstance("DiffieHellman")
        kpg.initialize(pubKeyA.params)
        return kpg.generateKeyPair()
    }

    // --- 3. OFFUSCAMENTO XOR ---

    fun applyXOR(data: ByteArray, mask: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        for (i in data.indices) {
            result[i] = (data[i].toInt() xor mask[i].toInt()).toByte()
        }
        return result
    }

    // --- 4. SEGRETO CONDIVISO E AES ---

    fun encryptB(data: ByteArray, key: SecretKeySpec): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).apply { secureRandom.nextBytes(this) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(data)
    }

    fun encrypt(plaintext: String, key: SecretKeySpec): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).apply { secureRandom.nextBytes(this) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plaintext.toByteArray())
    }

    fun decrypt(encryptedData: ByteArray, key: SecretKeySpec): String {
        val iv = encryptedData.sliceArray(0 until 12)
        val ciphertext = encryptedData.sliceArray(12 until encryptedData.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    // Hybrid KDF + SAS

    // 1. Deriva la maschera XOR usando sia il segreto salvato che l'OTP manuale
    fun deriveHybridMask(digitalSecret: ByteArray, manualOtp: String, salt: ByteArray, length: Int): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(digitalSecret)
        digest.update(manualOtp.toByteArray(Charsets.UTF_8))
        val combinedSeed = digest.digest()

        // Usiamo il seed combinato come password per PBKDF2
        val spec = PBEKeySpec(
            Base64.encode(combinedSeed).toCharArray(),
            salt,
            ITERATION_FAST,
            length * 8
        )
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }

    // 2. Genera la chiave finale e il codice SAS
    fun deriveFinalKeyAndSAS(myPrivateKey: PrivateKey, theirPublicKeyBytes: ByteArray): Pair<SecretKeySpec, String> {
        val kf = KeyFactory.getInstance("DiffieHellman")
        val theirPubKey = kf.generatePublic(X509EncodedKeySpec(theirPublicKeyBytes))

        val ka = KeyAgreement.getInstance("DiffieHellman")
        ka.init(myPrivateKey)
        ka.doPhase(theirPubKey, true)
        val sharedSecret = ka.generateSecret()

        // Derivazione Chiave AES
        val keyDigest = MessageDigest.getInstance("SHA-256")
        val aesKey = SecretKeySpec(keyDigest.digest(sharedSecret), "AES")

        // Generazione SAS (5 cifre leggibili)
        val sasDigest = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
        val sasValue = ((sasDigest[0].toInt() and 0xFF) shl 16) or
                ((sasDigest[1].toInt() and 0xFF) shl 8) or
                (sasDigest[2].toInt() and 0xFF)
        val sasCode = String.format("%05d", sasValue % 100000)

        return Pair(aesKey, sasCode)
    }

    // 1. Genera l'OTP per la prossima volta
    fun generateNextOTP(): ByteArray {
        val otp = ByteArray(32)
        secureRandom.nextBytes(otp)
        return otp
    }

    // 2. Prepara il pacchetto per la sessione successiva
    // Viene inviato CIFRATO dentro la sessione attuale
    fun prepareNextSessionPacket(nextOtp: ByteArray): String {
        return Base64.encode(nextOtp)
    }

    fun generateRandomBytes(length: Int = 16): ByteArray {
        val rdata = ByteArray(length)
        secureRandom.nextBytes(rdata)
        return rdata
    }
}