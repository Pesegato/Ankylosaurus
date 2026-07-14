package com.pesegato.a10s

import org.jetbrains.exposed.sql.Table

object WebAuthnCredentials : Table("user_credentials") {
    val credentialId = binary("credential_id")
    val publicKey = binary("public_key")
    val signCount = long("sign_count")
    val userHandle = binary("user_handle").nullable()
    val backupEligible = bool("backup_eligible").default(false)
    val backupState = bool("backup_state").default(false)
    val uvInitialized = bool("uv_initialized").default(false)

    override val primaryKey = PrimaryKey(credentialId)
}