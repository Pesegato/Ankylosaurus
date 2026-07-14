package com.pesegato.a10s

import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.selectAll

class CredentialRepository {
    fun findByCredentialId(id: ByteArray): CredentialRecord? {
        return transaction {
            WebAuthnCredentials
                .selectAll().where { WebAuthnCredentials.credentialId eq id }
                .map { row ->
                    val attestedCredentialData = AttestedCredentialData(
                        AAGUID.ZERO,
                        row[WebAuthnCredentials.credentialId],
                        row[WebAuthnCredentials.publicKey].toCoseKey()
                    )

                    CredentialRecordImpl(
                        null, false, false, false,
                        row[WebAuthnCredentials.signCount],
                        attestedCredentialData,
                        null, null, null, null
                    )
                }.singleOrNull()
        }
    }
}