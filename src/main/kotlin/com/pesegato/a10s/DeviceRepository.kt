package com.pesegato.a10s

import com.google.crypto.tink.*
import com.google.crypto.tink.aead.AeadConfig
import com.webauthn4j.data.attestation.authenticator.COSEKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.*

class DeviceRepository(private val keyPath: String) {
    private val aead: Aead

    init {
        AeadConfig.register()

        val tinkKeysetJson = File(keyPath).readText()

        val keysetHandle = TinkJsonProtoKeysetFormat.parseKeyset(
            tinkKeysetJson,
            InsecureSecretKeyAccess.get() // Accesso esplicito e consapevole
        )

        aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    object SystemSettings {
        fun isRegistrationLocked(): Boolean = transaction {
            // Legge il valore 'lock' dalla tabella
            SettingsTable
                .selectAll().where { SettingsTable.key eq "lock" }
                .map { it[SettingsTable.value] }
                .singleOrNull() ?: false // Se non esiste, default false (sbloccato)
        }

        fun setLock(locked: Boolean) = transaction {
            // Cerca di aggiornare; se non esiste, inserisce
            val updated = SettingsTable.update({ SettingsTable.key eq "lock" }) {
                it[value] = locked
            }

            if (updated == 0) {
                SettingsTable.insertIgnore {
                    it[key] = "lock"
                    it[value] = locked
                }
            }
        }
    }

    fun getDevice(deviceId: String): Device? = transaction {
        val row = DevicesTable.selectAll().where { DevicesTable.id eq deviceId }.singleOrNull() ?: return@transaction null

        // Decriptazione e autenticazione (Tink verificherà l'integrità automaticamente)
        val decryptedBytes = aead.decrypt(row[DevicesTable.data], null)
        val sensitiveData = Json.decodeFromString<SensitiveDeviceData>(String(decryptedBytes))

        Device(
            id = row[DevicesTable.id],
            status = sensitiveData.status,
            publicKey = sensitiveData.publicKey,
            credentialId = sensitiveData.credentialId,
            name = sensitiveData.name,
            displayName = sensitiveData.displayName,
            counter = row[DevicesTable.counter]
        )
    }

    fun addDevice(device: Device) = transaction {
        val sensitiveData = SensitiveDeviceData(
            status = device.status,
            publicKey = device.publicKey,
            credentialId = device.credentialId,
            displayName = device.displayName,
            registrationDate = System.currentTimeMillis(),
            name = device.name
        )

        val jsonString = Json.encodeToString(sensitiveData)
        val encryptedBlob = aead.encrypt(jsonString.toByteArray(), null)

        DevicesTable.insert {
            it[id] = device.id
            it[data] = encryptedBlob
            it[counter] = device.counter
            // Salviamo il credentialId in chiaro (convertito in Base64)
            it[credentialIdPlain] = Base64.getEncoder().encodeToString(device.credentialId)
        }
    }

    // Nuovo metodo per trovare il dispositivo tramite credentialId
    fun findDeviceByCredentialId(credentialId: ByteArray): Device? = transaction {
        val b64Id = Base64.getEncoder().encodeToString(credentialId)

        val row = DevicesTable.selectAll()
            .where { DevicesTable.credentialIdPlain eq b64Id }
            .singleOrNull() ?: return@transaction null

        val decryptedBytes = aead.decrypt(row[DevicesTable.data], null)
        val sensitiveData = Json.decodeFromString<SensitiveDeviceData>(String(decryptedBytes))

        Device(
            id = row[DevicesTable.id],
            status = sensitiveData.status,
            publicKey = sensitiveData.publicKey,
            credentialId = sensitiveData.credentialId,
            name = sensitiveData.name,
            displayName = sensitiveData.displayName,
            counter = row[DevicesTable.counter]
        )
    }

    fun updateCounter(deviceId: String, newCounter: Long) = transaction {
        DevicesTable.update({ DevicesTable.id eq deviceId }) {
            it[counter] = newCounter
        }
    }

    fun getAllDevicesDecrypted(): List<Device> = transaction {
        DevicesTable.selectAll().map { row ->
            val decryptedBytes = aead.decrypt(row[DevicesTable.data], null)
            val sensitiveData = Json.decodeFromString<SensitiveDeviceData>(String(decryptedBytes))

            Device(
                id = row[DevicesTable.id],
                status = sensitiveData.status,
                publicKey = sensitiveData.publicKey,
                credentialId = sensitiveData.credentialId,
                name = sensitiveData.name,
                displayName = sensitiveData.displayName,
                counter = row[DevicesTable.counter]
            )
        }
    }

    fun deleteDevice(deviceId: String) = transaction {
        // Supponendo che 'DevicesTable' sia il nome del tuo oggetto Table
        // e 'id' sia la colonna che usi come identificativo
        DevicesTable.deleteWhere { DevicesTable.id eq deviceId }
    }

    // Aggiungi in DeviceRepository.kt
    fun nukeDatabase() = transaction {
        // Rimuove tutti i record dalla tabella Devices
        DevicesTable.deleteAll()
    }

    /**
     * Funzione per aggiornare lo stato di un dispositivo in modo sicuro:
     * Decripta, modifica lo stato, ricripta e salva.
     */
    fun updateDeviceStatus(deviceId: String, newStatus: DeviceStatus) = transaction {
        val row = DevicesTable.selectAll().where { DevicesTable.id eq deviceId }.singleOrNull()
            ?: throw Exception("Dispositivo non trovato: $deviceId")

        // 1. Decriptazione
        val decryptedBytes = aead.decrypt(row[DevicesTable.data], null)
        val sensitiveData = Json.decodeFromString<SensitiveDeviceData>(String(decryptedBytes))

        // 2. Modifica
        val updatedSensitiveData = sensitiveData.copy(status = newStatus)

        // 3. Ricriptazione
        val jsonString = Json.encodeToString(updatedSensitiveData)
        val encryptedBlob = aead.encrypt(jsonString.toByteArray(), null)

        // 4. Salvataggio
        DevicesTable.update({ DevicesTable.id eq deviceId }) {
            it[data] = encryptedBlob
        }
    }
}

fun String.toCoseKey(): COSEKey = decodePublicKey(this)
fun ByteArray.toCoseKey(): COSEKey = decodePublicKey(Base64.getEncoder().encodeToString(this))
