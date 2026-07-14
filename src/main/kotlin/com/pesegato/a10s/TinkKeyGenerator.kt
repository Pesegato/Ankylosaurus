package com.pesegato.a10s

import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import java.io.File

object TinkKeyGenerator {
    fun generateAndSave(outputPath: String) {
        AeadConfig.register()
        // Genera un keyset AES256-GCM (standard per AEAD)
        val keysetHandle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))

        // Serializza nel formato JSON protetto da Tink
        val json = TinkJsonProtoKeysetFormat.serializeKeyset(
            keysetHandle,
            InsecureSecretKeyAccess.get()
        )

        File(outputPath).writeText(json)
    }
}