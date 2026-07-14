package com.pesegato.a10s

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.prompt
import kotlin.system.exitProcess

object AdminTool {
    val terminal = Terminal()

    fun runCli() {
        val repo = DeviceRepository(System.getenv("TINK_KEY_PATH"))
        initDatabase()
        terminal.println(magenta(bold("🚀 Ankylosaurus Admin CLI")))
        showHelp()

        if (DeviceRepository.SystemSettings.isRegistrationLocked())
            terminal.println("🔒 Table is LOCKED. New requests are discarded.")
        else
            terminal.println("🔓 Table is UNLOCKED. New requests are set in PENDING state.")

        while (true) {
            val input = terminal.prompt(blue("\ncmd> "))?.split(" ") ?: break
            val command = input[0]

            when (command) {
                "list" -> {
                    val locked = DeviceRepository.SystemSettings.isRegistrationLocked()
                    terminal.println(if(locked) red("🔒 Sistema attualmente BLOCCATO") else green("🔓 Sistema APERTO alle nuove registrazioni"))
                    val devices = repo.getAllDevicesDecrypted()
                    renderDeviceTable(devices)
                }
                "authorize" -> {
                    if (input.size > 1) {
                        val target = input[1]
                        // Se l'utente scrive il numero dell'indice, prendiamo l'ID corrispondente
                        val devices = repo.getAllDevicesDecrypted()
                        val idToAuth = if (target.toIntOrNull() != null) {
                            devices.getOrNull(target.toInt() - 1)?.id
                        } else target

                        if (idToAuth != null) {
                            repo.updateDeviceStatus(idToAuth, DeviceStatus.AUTHORIZED)
                            terminal.println(green("✔ Autorizzato: $idToAuth"))
                        } else {
                            terminal.println(red("ID o indice non trovato!"))
                        }
                    }
                }
                "delete" -> {
                    if (input.size > 1) {
                        val target = input[1]
                        // Se l'utente scrive il numero dell'indice, prendiamo l'ID corrispondente
                        val devices = repo.getAllDevicesDecrypted()
                        val idToAuth = if (target.toIntOrNull() != null) {
                            devices.getOrNull(target.toInt() - 1)?.id
                        } else target

                        if (idToAuth != null) {
                            repo.deleteDevice(idToAuth)
                            terminal.println(yellow("Dispositivo eliminato."))
                        }
                    } else terminal.println(red("Errore: specifica un ID"))
                }
                "lock" -> {
                    DeviceRepository.SystemSettings.setLock(true)
                    terminal.println(red("🔒 Registrazioni bloccate."))
                }
                "unlock" -> {
                    DeviceRepository.SystemSettings.setLock(false)
                    terminal.println(green("🔓 Registrazioni sbloccate."))
                }
                "nuke" -> {
                    terminal.print(red(bold("Sei sicuro di voler eliminare TUTTO? (yes/no): ")))
                    if (terminal.prompt("") == "yes") {
                        repo.nukeDatabase()
                        terminal.println(red("Database piallato!"))
                    }
                }
                "help" -> showHelp() // Nuovo comando
                "quit" -> exitProcess(0) // Nuovo comando
                "exit" -> exitProcess(0)
                else -> {
                    terminal.println(red("Comando non riconosciuto."))
                    showHelp() // Mostra l'aiuto automaticamente se sbagliano
                }
            }
        }
    }

    fun renderDeviceTable(devices: List<Device>) {
        val t = table {
            header { row("#", "ID", "Status", "Device") }
            body {
                devices.forEachIndexed { index, d ->
                    row(
                        index + 1, // Usiamo un indice progressivo
                        d.id,      // ID completo per riferimento
                        if(d.status == DeviceStatus.AUTHORIZED) green("AUTH") else yellow("PEND"),
                        d.displayName
                    )
                }
            }
        }
        terminal.println(t)
    }

    fun showHelp() {
        terminal.println(bold(white("\n--- Comandi Disponibili ---")))
        terminal.println("${cyan("list")}       - Elenca tutti i dispositivi registrati")
        terminal.println("${cyan("authorize")} <#> - Autorizza un dispositivo tramite indice o ID")
        terminal.println("${cyan("delete")} <id>   - Rimuove permanentemente un dispositivo")
        terminal.println("${cyan("lock/unlock")}    - Blocca/Sblocca nuove registrazioni")
        terminal.println("${cyan("nuke")}       - Elimina TUTTI i dati dal database")
        terminal.println("${cyan("help")}       - Mostra questo menu")
        terminal.println("${cyan("quit/exit")}       - Chiude la CLI\n")
    }
}