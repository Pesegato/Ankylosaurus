package com.pesegato.a10s

import org.jetbrains.exposed.sql.Table

object DevicesTable : Table("devices") {
    val id = varchar("id", 255)
    val counter = long("counter")
    val data = binary("data")
    // Nuova colonna indicizzata per ricerche veloci
    val credentialIdPlain = varchar("credential_id", 255).index()

    override val primaryKey = PrimaryKey(id)
}

object SettingsTable : Table("settings") {
    val key = varchar("key", 50)
    val value = bool("value")
    override val primaryKey = PrimaryKey(key)
}