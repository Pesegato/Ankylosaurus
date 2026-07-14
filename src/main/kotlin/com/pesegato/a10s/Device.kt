package com.pesegato.a10s

import kotlinx.serialization.Serializable

@Serializable
enum class DeviceStatus {
    PENDING, AUTHORIZED, REVOKED
}

@Serializable
data class SensitiveDeviceData(
    val status: DeviceStatus,
    val publicKey: String,
    val credentialId: ByteArray,
    val name: String,     // Es: "Chrome su Linux", "Pixel 7"
    val registrationDate: Long, // Timestamp
    val displayName: String      // Per tracciare meglio la provenienza
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SensitiveDeviceData

        if (status != other.status) return false
        if (publicKey != other.publicKey) return false
        if (!credentialId.contentEquals(other.credentialId)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = status.hashCode()
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + credentialId.contentHashCode()
        return result
    }
}

@Serializable
data class Device(
    val id: String,         // ID univoco del dispositivo (es. ottenuto dal TPM)
    val publicKey: String,  // Chiave pubblica in formato Base64
    val name: String,
    val displayName: String,
    val status: DeviceStatus = DeviceStatus.PENDING,
    val lastLogin: String? = null,
    val credentialId: ByteArray,
    val counter: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Device

        if (counter != other.counter) return false
        if (id != other.id) return false
        if (publicKey != other.publicKey) return false
        if (status != other.status) return false
        if (lastLogin != other.lastLogin) return false
        if (!credentialId.contentEquals(other.credentialId)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = counter.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + (lastLogin?.hashCode() ?: 0)
        result = 31 * result + (credentialId.contentHashCode() ?: 0)
        return result
    }
}