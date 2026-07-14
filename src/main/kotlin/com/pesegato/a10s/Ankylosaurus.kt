package com.pesegato.a10s

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.AuthenticatorDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.AuthenticationData
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData
import com.webauthn4j.data.attestation.authenticator.COSEKey
import com.webauthn4j.data.client.CollectedClientData
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionAuthenticatorOutput
import com.webauthn4j.server.ServerProperty
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.ExperimentalEncodingApi

val a10sDomain = System.getenv("A10S_DOMAIN")
val a10sURL = System.getenv("A10S_URL")
val targetName = System.getenv("TARGET_NAME")
val targetURL = System.getenv("TARGET_URL")


val allowedOrigins = setOf(
    "https://${a10sURL}",
    "https://${targetURL}"
)

@Serializable
data class WebAuthnCredentialResponse(
    val id: String,
    val rawId: String, // Stringa Base64URL
    val type: String,
    val response: AuthenticatorResponse,
    val clientExtensionResults: JsonElement = Json.parseToJsonElement("{}")
)

@Serializable
data class RegistrationOptionsResponse(
    val challenge: String,
    val tempId: String,
    val rp: RelyingPartyResponse,
    val user: UserResponse,
    val pubKeyCredParams: List<PubKeyCredParams>,
    val authenticatorSelection: AuthenticatorSelection
)

@Serializable
data class RelyingPartyResponse(val name: String, val id: String)

@Serializable
data class UserResponse(val id: String, val name: String, val displayName: String)

@Serializable
data class PubKeyCredParams(val type: String, val alg: Int)

@Serializable
data class WebAuthnRegistrationData(
    val deviceId: String,
    val publicKey: String, // La chiave pubblica che il TPM ha generato
    val counter: Long = 0,    // AGGIUNTO: WebAuthn lo richiede
    val credentialId: String,
    val status: DeviceStatus = DeviceStatus.PENDING
)

@Serializable
data class AuthenticatorSelection(
    @SerialName("authenticatorAttachment") val authenticatorAttachment: String,
    @SerialName("userVerification") val userVerification: String,
    @SerialName("residentKey") val residentKey: String
)

@Serializable
data class WebAuthnData(
    val credentialId: String,    // Sostituito deviceId con credentialId
    val loginSessionId: String,  // Per recuperare la challenge corretta dalla cache
    val signature: String,
    val authenticatorData: String,
    val clientDataJSON: String
)

@Serializable
data class WebAuthnRegistrationPayload(
    val tempId: String,
    val credential: WebAuthnCredentialResponse
)

@Serializable
data class WebAuthnRegistrationResult(
    val id: String,
    val rawId: String,
    val type: String,
    val response: AuthenticatorResponse,
    val clientExtensionResults: Map<String, String> = emptyMap() // Questo va bene se è vuoto
)

@Serializable
data class AuthenticatorResponse(
    val attestationObject: String,
    val clientDataJSON: String,
    val transports: JsonElement? = null, // Anche qui, più permissivo
    val publicKeyAlgorithm: Int? = null,
    val publicKey: String? = null,
    val authenticatorData: String? = null
)


private val objectConverter = ObjectConverter()
private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager(objectConverter)

fun decodePublicKey(encodedKey: String): COSEKey {
    val keyBytes = Base64.getUrlDecoder().decode(encodedKey)
    return objectConverter.cborMapper.readValue(keyBytes, COSEKey::class.java)
}

fun initDatabase() {
    val config = HikariConfig().apply {
        jdbcUrl = System.getenv("DB_URL") // jdbc:postgresql://webauthn-db:5432/webauthndb
        username = System.getenv("DB_USER")
        password = java.io.File("/run/secrets/db_password").readText().trim()
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 10
    }
    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)
    transaction {
        SchemaUtils.createMissingTablesAndColumns(DevicesTable, SettingsTable)
    }
}

fun main(args: Array<String>) {
    // Controllo se il flag di generazione è presente
    if ("--generate-keys" in args) {
        val outputPath = System.getenv("OUTPUT_PATH") ?: "/app/secrets/keyset.json"
        println("Generazione nuove chiavi in $outputPath...")
        TinkKeyGenerator.generateAndSave(outputPath)
        println("Chiavi generate correttamente.")
        return // Esce dopo la generazione
    }
    if ("--admin-tool" in args) {
        println("Modalità Admin rilevata. Bypass avvio Ktor...")
        AdminTool.runCli()
        return
    }
    if ("--nuke" in args) {
        println("ATTENZIONE: Operazione di distruzione dati in corso...")
        val repo = DeviceRepository(System.getenv("TINK_KEY_PATH"))
        repo.nukeDatabase()
        println("Database pulito con successo.")
        return
    }
    // Avvio standard del server Ktor
    println("Avvio Server Ktor...")
    io.ktor.server.netty.EngineMain.main(args)
}

@OptIn(ExperimentalEncodingApi::class)
fun Application.module() {
    initDatabase()
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true // <--- AGGIUNGI QUESTO
        })
    }
    install(CORS) {
        allowHost(a10sURL)
        allowHost(targetURL)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowCredentials = true
    }

    intercept(ApplicationCallPipeline.Plugins) {
        val host = call.request.host()

        val expectedHost = a10sURL

        if (host != expectedHost) {
            val MisdirectedRequest = HttpStatusCode(421, "Misdirected Request")
            call.respond(MisdirectedRequest, "Richiesta inviata al server sbagliato.")
            finish()
        }
    }

    routing {

        get("/favicon.ico") {
            call.respond(HttpStatusCode.NoContent)
        }

        get("/auth/register") {
            val browser = call.request.queryParameters["browser"] ?: "Unknown"
            println("Browser: $browser")
            val os = call.request.queryParameters["os"] ?: "Unknown"
            println("OS: $os")

            //val displayName = call.request.queryParameters["displayName"] ?: "User Device"
            val deviceName = call.request.queryParameters["deviceName"] ?: "User Device"
            println("DeviceName: $deviceName")

            // Costruisci il nome utente univoco
            val username = "$deviceName ($browser on $os)"

            val attachment = call.request.queryParameters["attachment"] ?: "platform"
            val uv = call.request.queryParameters["uv"] ?: "required"
            val resident = call.request.queryParameters["resident"] ?: "required"

            val challenge = generateSecureChallenge()
            val tempId = UUID.randomUUID().toString()
            saveChallenge(tempId, challenge)
            saveDeviceMetadata(tempId, username, deviceName) // Salvataggio dati

            val options = RegistrationOptionsResponse(
                challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge),
                tempId = tempId,
                rp = RelyingPartyResponse(targetName, a10sURL),
                user = UserResponse(
                    id = Base64.getUrlEncoder().withoutPadding().encodeToString("user-id".toByteArray()),
                    name = username,
                    displayName = deviceName
                ),
                pubKeyCredParams = listOf(
                    PubKeyCredParams("public-key", -7), // ES256
                    PubKeyCredParams("public-key", -257) // RS256
                ),
                authenticatorSelection = AuthenticatorSelection(
                    authenticatorAttachment = attachment,
                    userVerification = uv,
                    residentKey = resident
                )
            )

            call.respond(options)
        }

        // In Ktor
        post("/api/webauthn/register-finish") {
            if (DeviceRepository.SystemSettings.isRegistrationLocked()) {
                return@post call.respond(HttpStatusCode.ServiceUnavailable, "Registrazioni chiuse.")
            }

            // --- AGGIUNTO: Controllo dell'origine anche in registrazione ---
            val clientOriginString = call.request.headers["Origin"] ?: ""
            if (clientOriginString !in allowedOrigins) {
                return@post call.respond(HttpStatusCode.Forbidden, "Origine non autorizzata: $clientOriginString")
            }

            val rawJson = call.receiveText() // Leggi come testo per debug
            println("JSON RICEVUTO: $rawJson") // Controlla il log di Docker
            // 2. Deserializza la stringa usando Json.decodeFromString
            val payload = try {
                Json.decodeFromString<WebAuthnRegistrationPayload>(rawJson)
            } catch (e: Exception) {
                println("Errore di deserializzazione: ${e.message}")
                return@post call.respond(HttpStatusCode.BadRequest, "JSON malformato")
            }

            val attestationObjectBytes = Base64.getUrlDecoder().decode(payload.credential.response.attestationObject)

            // 2. Usa il parser di WebAuthn4J per estrarre i dati reali
            val attestationObject = objectConverter.cborMapper.readValue(
                attestationObjectBytes,
                com.webauthn4j.data.attestation.AttestationObject::class.java
            )

            // 3. Estrai la chiave pubblica (COSE Key)
            val coseKey = attestationObject.authenticatorData.attestedCredentialData!!.coseKey

            // 4. Ora hai la chiave pubblica!


            val tempId = payload.tempId
            val credential = payload.credential

            // 2. Recupera la sfida dalla cache
            val originalChallenge = try {
                getChallengeFromCache(tempId)
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Sfida scaduta o non trovata")
            }

            // 3. Verifica la sfida
            if (!verifyChallenge(credential, originalChallenge)) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Sfida non valida")
            }
            // Recupera i metadati salvati inizialmente
            val metadata = getDeviceMetadata(payload.tempId)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Dati registrazione non trovati")

            val (username, deviceName) = metadata // Destrutturazione

            // 4. Estrai i dati per il salvataggio
//  SOSTITUISCI CON QUESTO:
// Serializza l'intero oggetto COSEKey in byte CBOR (il formato nativo richiesto da WebAuthn4J)
            val pubKeyBytes = objectConverter.cborMapper.writeValueAsBytes(coseKey)

// Codificalo in Base64URL senza padding per uniformità
            val pubKeyBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(pubKeyBytes)

            // Assicurati che rawId sia decodificato correttamente
            val credentialIdBytes = decodeWebAuthnData(credential.rawId)

            val keyPath = System.getenv("TINK_KEY_PATH") ?: "/app/certs/keyset.json"
            val repository = DeviceRepository(keyPath)

            val newDevice = Device(
                id = credential.id, // o credential.rawId, a seconda di cosa usi come ID nel DB
                publicKey = pubKeyBase64,
                credentialId = credentialIdBytes,
                counter = 0,
                name = username,
                displayName = deviceName,
                status = DeviceStatus.PENDING
            )

            try {
            repository.addDevice(newDevice)
                println("Salvataggio riuscito!") // Debug
            call.respond(HttpStatusCode.Accepted, "Dispositivo in attesa di autorizzazione Admin.")
            } catch (e: Exception) {
                e.printStackTrace() // <--- FONDAMENTALE: stampa lo stack trace completo nel log
                call.respond(HttpStatusCode.InternalServerError, "Errore DB: ${e.message}")
            }
        }

// In Ankylosaurus.kt
        get("/auth/get-challenge") {
            val challenge = generateSecureChallenge()
            val loginSessionId = UUID.randomUUID().toString()

            // Salviamo la sfida nella cache
            saveChallenge(loginSessionId, challenge)

            call.respond(mapOf(
                // CORREZIONE: Usa Base64URL senza padding per conformità WebAuthn
                "challenge" to Base64.getUrlEncoder().withoutPadding().encodeToString(challenge),
                "loginSessionId" to loginSessionId
            ))
        }

        // 2. Il server valida la firma del TPM e rilascia il JWT
// 2. Il server valida la firma del TPM e rilascia il JWT
        post("/auth/verify") {
            // 1. Ricevi i dati di autenticazione aggiornati dal client
            val authData = call.receive<WebAuthnData>()

            val clientOriginString = call.request.headers["Origin"] ?: ""
            if (clientOriginString !in allowedOrigins) {
                return@post call.respond(HttpStatusCode.Forbidden, "Origine non autorizzata: $clientOriginString")
            }

            println("Credentials id: ${authData.credentialId}")

            // 2. Decodifica il credentialId fornito dal client (Base64URL)
            val credentialIdBytes = decodeWebAuthnData(authData.credentialId)

            val keyPath = System.getenv("TINK_KEY_PATH") ?: "/app/certs/keyset.json"
            val repository = DeviceRepository(keyPath)

            // 3. Ricerca del dispositivo tramite credentialId (grazie all'indice plain che hai aggiunto)
            val device = repository.findDeviceByCredentialId(credentialIdBytes)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Dispositivo non riconosciuto.")

            if (device.status != DeviceStatus.AUTHORIZED) {
                return@post call.respond(HttpStatusCode.Forbidden, "Dispositivo non autorizzato.")
            }

            // 4. Recupera la sfida dalla cache usando il loginSessionId inviato dal client
            val originalChallenge = try {
                getChallengeFromCache(authData.loginSessionId)
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Sfida scaduta o non trovata.")
            }

            val newCounter = verifySignature(authData, device, originalChallenge, "https://${a10sURL}")

            if (newCounter != null) {
                repository.updateCounter(device.id, newCounter)

                val durataMillis = 30_000L // 30s in millisecondi
                val durataSecondi = 30L    // 30s in secondi

                val secret = java.io.File("/run/secrets/jwt_secret").readText().trim()

                val token = JWT.create()
                    .withClaim("deviceId", device.id) // Continua a usare l'id interno per il JWT
                    .withExpiresAt(java.util.Date(System.currentTimeMillis() + durataMillis))
                    .sign(Algorithm.HMAC256(secret))

                call.response.cookies.append(
                    name = "JWT",
                    value = token,
                    maxAge = durataSecondi, // Corretto: il parametro si chiama maxAge
                    domain = a10sDomain,
                    path = "/",
                    secure = true,
                    httpOnly = true,
                    extensions = mapOf("SameSite" to "Strict")
                )

                call.respond(HttpStatusCode.OK, "Login completato con successo")
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Firma non valida.")
            }
        }

        get("/auth/iframe-login") {
            call.respondText("""
            <!DOCTYPE html>
            <html>
            <body>
                <p>Caricamento autenticazione...</p>
                <script>
                    // Qui dovresti includere o chiamare il tuo script di login esistente
                    // Oppure, più semplicemente, reindirizza alla logica di index.html
                    window.location.href = '/'; 
                </script>
            </body>
            </html>
        """, ContentType.Text.Html)
        }
        // Rotta per servire l'index.html modificato dinamicamente
        get("/") {

            // Leggiamo il file index.html dalle risorse
            val htmlContent = object {}.javaClass.classLoader.getResourceAsStream("static/index.html")
                ?.bufferedReader()
                ?.readText()
                ?: return@get call.respond(HttpStatusCode.InternalServerError, "HTML non trovato")

            println("TARGET_URL: $targetURL")
            // Sostituiamo i segnaposto con i valori delle variabili d'ambiente
            val dynamicHtml = htmlContent
                .replace("{{TARGET_URL}}", targetURL)
                .replace("{{A10S_URL}}", a10sURL)

            call.respondText(dynamicHtml, ContentType.Text.Html)
        }
        // Serviamo il resto dei file statici (js, css, icone) ma escludiamo l'index automatico
        staticResources("/", "static")
    }

}

fun verifySignature(authData: WebAuthnData, device: Device, originalChallenge: ByteArray, clientOrigin: String): Long? {
    // 1. Decodifica i dati che arrivano dal client
    val signature = decodeWebAuthnData(authData.signature)
    val authenticatorDataBytes = decodeWebAuthnData(authData.authenticatorData)
    val clientDataBytes = decodeWebAuthnData(authData.clientDataJSON)

    // 2. USA IL CONVERTER DI WEBAUTHN4J per trasformare i byte in oggetti (NO COSTRUTTORI MANUALI)
    val collectedClientData = objectConverter.jsonMapper.readValue(clientDataBytes, CollectedClientData::class.java)

    val authDataType = objectConverter.cborMapper.typeFactory
        .constructParametricType(
            AuthenticatorData::class.java,
            AuthenticationExtensionAuthenticatorOutput::class.java
        )

    val authenticatorDataConverter = AuthenticatorDataConverter(objectConverter)
    val authenticatorData = authenticatorDataConverter.convert<AuthenticationExtensionAuthenticatorOutput>(authenticatorDataBytes)

    // 3. Ora crea l'AuthenticationData
    val authenticationData = AuthenticationData(
        device.credentialId, // <--- CORREZIONE 1
        null, // CredentialPublicKey
        authenticatorData,
        authenticatorDataBytes,
        collectedClientData,
        clientDataBytes,
        null, // Extensions
        signature
    )

    val serverProperty = ServerProperty.builder()
        .origin(Origin("https://${a10sURL}"))
        .rpId(a10sURL)
        .challenge(DefaultChallenge(originalChallenge))
        .topOrigins(setOf(
            Origin("https://${targetURL}")
        ))
        .build()
// 2. Crea l'Authenticator usando il costruttore corretto
// L'Authenticator è l'oggetto "reale" che la libreria vuole

    val attestedCredentialData = AttestedCredentialData(
        AAGUID.ZERO, // Invece di 'null', usa AAGUID.ZERO
        device.credentialId,
        decodePublicKey(device.publicKey) // COSEKey
    )

// 2. Ora usi il Builder di CredentialRecordImpl (questo è il wrapper pronto all'uso)
    val credentialRecord = CredentialRecordImpl(
        null,                    // attestationStatement: AttestationStatement?
        null,                   // uvInitialized: Boolean?
        null,                   // backupEligible: Boolean?
        null,                   // backupState: Boolean?
        device.counter, // counter: Long (assicurati che sia Long, non Int!)
        attestedCredentialData,  // attestedCredentialData: AttestedCredentialData
        null,                    // authenticatorExtensions: AuthenticationExtensionsAuthenticatorOutputs?
        null,                    // clientData: CollectedClientData?
        null,                    // clientExtensions: AuthenticationExtensionsClientOutputs?
        null                     // transports: Set<AuthenticatorTransport>?
    )

    val emptyCredentialsList: List<ByteArray>? = null

// 2. Chiama il costruttore passando TUTTI i parametri richiesti
// Dobbiamo essere espliciti per evitare l'ambiguità
    val authenticationParameters = AuthenticationParameters(
        serverProperty,
        credentialRecord as CredentialRecord, // Cast per sicurezza
        emptyCredentialsList,                 // Il parametro che creava l'ambiguità
        false                                 // UserVerificationRequired (Boolean)
    )

    return try {
        // La magia avviene qui: la libreria valida i dati
        val result = webAuthnManager.verify(authenticationData, authenticationParameters)
        return result.authenticatorData?.signCount
    } catch (e: Exception) {
        e.printStackTrace()
        return null // Cambia il tipo di ritorno della funzione in Long?
    }
}

private val challengeCache = ConcurrentHashMap<String, ByteArray>()
private val deviceMetadataCache = ConcurrentHashMap<String, Pair<String, String>>() // Key: tempId, Value: (Name, DisplayName)

fun saveDeviceMetadata(tempId: String, name: String, displayName: String) {
    deviceMetadataCache[tempId] = Pair(name, displayName)
}

fun getDeviceMetadata(tempId: String): Pair<String, String>? {
    return deviceMetadataCache.remove(tempId) // remove() per pulire la cache dopo l'uso
}

fun verifyChallenge(credential: WebAuthnCredentialResponse, originalChallenge: ByteArray): Boolean {
    try {
        // 1. Decodifica i dati del client (sono in Base64URL)
        val clientDataBytes = Base64.getUrlDecoder().decode(credential.response.clientDataJSON)

        // 2. Usa il convertitore di WebAuthn4J per mappare il JSON in un oggetto Kotlin
        val clientData = objectConverter.jsonMapper.readValue(clientDataBytes, CollectedClientData::class.java)

        // 3. Estrai la sfida dal clientData
        val challengeFromClient = clientData.challenge.value

        // 4. Confronta in modo sicuro (per evitare attacchi di timing)
        return MessageDigest.isEqual(challengeFromClient, originalChallenge)

    } catch (e: Exception) {
        println("Errore durante la verifica della sfida: ${e.message}")
        return false
    }
}

// Aggiungi questa funzione nel tuo file Ankylosaurus.kt
fun decodeWebAuthnData(encoded: String): ByteArray {
    // 1. Ripristiniamo i caratteri di padding '=' mancanti
    var base64UrlClean = encoded
    while (base64UrlClean.length % 4 != 0) {
        base64UrlClean += "="
    }

    // 2. Ora il Decoder standard di Java non darà più errori!
    return Base64.getUrlDecoder().decode(base64UrlClean)
}

// Genera una sfida di 32 byte (standard consigliato per WebAuthn)
fun generateSecureChallenge(): ByteArray {
    val random = SecureRandom()
    val challenge = ByteArray(32)
    random.nextBytes(challenge)
    return challenge
}

fun saveChallenge(deviceId: String, challenge: ByteArray) {
    challengeCache[deviceId] = challenge
}

fun getChallengeFromCache(deviceId: String): ByteArray {
    return challengeCache[deviceId]
        ?: throw IllegalStateException("Sfida non trovata o scaduta per il dispositivo: $deviceId")
}
