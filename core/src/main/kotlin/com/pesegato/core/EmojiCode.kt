package com.pesegato.core

import java.io.ByteArrayOutputStream

object EmojiCode {
// ─── Alfabeto: esattamente 1024 emoji unici (10 bit per simbolo) ──────────────

    private val EMOJI_ALPHABET: List<String> = listOf(
        "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆", "😉", "😊", "😋", "😎", "😍", "🥰", "😘", "😗",
        "😙", "😚", "🙂", "🤗", "🤩", "🤔", "🤨", "😐", "😑", "😶", "🙄", "😏", "😣", "😥", "😮", "🤐",
        "😯", "😪", "😫", "🥱", "😴", "😌", "😛", "😜", "😝", "🤤", "😒", "😓", "😔", "😕", "🙃", "🤑",
        "😲", "☹️", "🙁", "😖", "😞", "😟", "😤", "😢", "😭", "😦", "😧", "😨", "😩", "🤯", "😬", "😰",
        "😱", "🥵", "🥶", "😳", "🤪", "😵", "🥴", "😠", "😡", "🤬", "😷", "🤒", "🤕", "🤢", "🤮", "🤧",
        "😇", "🥳", "🥸", "🤠", "🤡", "🤥", "🤫", "🤭", "🧐", "🤓", "😈", "👿", "👹", "👺", "💀", "👻",
        "👽", "🤖", "💩", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾", "🙈", "🙉", "🙊", "💋",
        "💌", "💘", "💝", "💖", "💗", "💓", "💞", "💕", "💟", "❣️", "💔", "❤️", "🧡", "💛", "💚", "💙",
        "💜", "🖤", "🤍", "🤎", "💯", "💢", "💥", "💫", "💦", "💨", "🕳️", "💬", "💭", "💤", "👋", "🤚",
        "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇",
        "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳",
        "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🫀", "🫁", "🧠", "🦷", "🦴", "👀", "👁️", "👅",
        "👄", "💏", "💑", "👶", "🧒", "👦", "👧", "🧑", "👱", "👨", "🧔", "👩", "🧓", "👴", "👵", "🙍",
        "🙎", "🙅", "🙆", "💁", "🙋", "🧏", "🙇", "🤦", "🤷", "👮", "🕵️", "💂", "🥷", "👷", "🤴", "👸",
        "👳", "👲", "🧕", "🤵", "👰", "🤰", "🤱", "👼", "🎅", "🤶", "🦸", "🦹", "🧙", "🧚", "🧛", "🧜",
        "🧝", "🧞", "🧟", "💆", "💇", "🚶", "🧍", "🧎", "🏃", "💃", "🕺", "🕴️", "👯", "🧖", "🧗", "🤺",
        "🏇", "⛷️", "🏂", "🪂", "🏋️", "🤼", "🤸", "🤾", "🏌️", "🏄", "🚣", "🧘", "🛀", "🛌", "👫", "👬",
        "👭", "🗣️", "👤", "👥", "🫂", "👣", "🦰", "🦱", "🦳", "🦲", "🐵", "🐒", "🦍", "🦧", "🐶", "🐕",
        "🦮", "🐩", "🐺", "🦊", "🦝", "🐱", "🐈", "🐯", "🐅", "🐆", "🐴", "🐎", "🦄", "🫏", "🦓", "🦌",
        "🦬", "🐮", "🐂", "🐃", "🐄", "🐷", "🐖", "🐗", "🐏", "🐑", "🦙", "🐐", "🦘", "🦛", "🐭", "🐁",
        "🐀", "🐹", "🐰", "🐇", "🐿️", "🦫", "🦔", "🦇", "🐻", "🐨", "🐼", "🦥", "🦦", "🦨", "🦡", "🐾",
        "🦃", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦚", "🦜", "🦩", "🦢", "🕊️", "🐓",
        "🦤", "🪶", "🦋", "🐛", "🐌", "🐝", "🪱", "🐜", "🦟", "🦗", "🪳", "🕷️", "🕸️", "🦂", "🐢", "🐍",
        "🦎", "🦖", "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🐊",
        "🦭", "🌵", "🎄", "🌲", "🌳", "🌴", "🪵", "🌱", "🌿", "☘️", "🍀", "🎍", "🪴", "🎋", "🍃", "🍂",
        "🍁", "🪺", "🪹", "🍄", "🌾", "💐", "🌷", "🌹", "🥀", "🌺", "🌸", "🌼", "🌻", "🌞", "🌝", "🌛",
        "🌜", "🌚", "🌕", "🌖", "🌗", "🌘", "🌑", "🌒", "🌓", "🌔", "🌙", "🌟", "⭐", "🌠", "🌌", "☁️",
        "⛅", "🌤️", "🌥️", "🌦️", "🌧️", "⛈️", "🌩️", "🌨️", "❄️", "☃️", "⛄", "🌬️", "💧", "🌊", "🌫️", "🌈",
        "🔥", "⚡", "🌀", "🌪️", "🌍", "🌎", "🌏", "🗺️", "🧭", "🏔️", "⛰️", "🌋", "🗻", "🏕️", "🏖️", "🏜️",
        "🏝️", "🏞️", "🏟️", "🏛️", "🏗️", "🧱", "🪨", "🛖", "🏠", "🏡", "🏢", "🏣", "🏤", "🏥", "🏦", "🏨",
        "🏩", "🏪", "🏫", "🏬", "🏭", "🏯", "🏰", "🗼", "🗽", "🗾", "⛪", "🕌", "🛕", "🕍", "⛩️", "🕋",
        "⛲", "⛺", "🌁", "🌃", "🏙️", "🌄", "🌅", "🌆", "🌇", "🌉", "🎠", "🎡", "🎢", "💈", "🎪", "🛎️",
        "🎫", "🎟️", "🎭", "🎨", "🎬", "🎤", "🎧", "🎼", "🎵", "🎶", "🎙️", "🎚️", "🎛️", "📻", "🎷", "🪗",
        "🎸", "🎹", "🎺", "🎻", "🪕", "🥁", "🪘", "📱", "📲", "☎️", "📞", "📟", "📠", "🔋", "🪫", "🔌",
        "💻", "🖥️", "🖨️", "⌨️", "🖱️", "🖲️", "💾", "💿", "📀", "🧮", "📷", "📸", "📹", "🎥", "📽️", "🎞️",
        "📺", "⏱️", "⏲️", "⏰", "🕰️", "⌛", "⏳", "📡", "💡", "🔦", "🕯️", "💰", "💴", "💵", "💶", "💷",
        "💸", "💳", "🪙", "💹", "📈", "📉", "📊", "📋", "📌", "📍", "🗂️", "📁", "📂", "🗃️", "🗳️", "🗄️",
        "🗑️", "🔒", "🔓", "🔏", "🔐", "🔑", "🗝️", "🔨", "🪓", "⛏️", "⚒️", "🛠️", "🗡️", "⚔️", "🛡️", "🔧",
        "🔩", "⚙️", "🗜️", "🔗", "⛓️", "🪤", "🧲", "💣", "🧨", "🔮", "💎", "🪄", "🧿", "🎏", "🎀", "🎁",
        "🎗️", "🎊", "🎉", "🎎", "🎐", "🎑", "🎃", "🪔", "🎆", "🎇", "🧧", "🎈", "🍕", "🍔", "🌮", "🌯",
        "🥙", "🧆", "🥚", "🍳", "🥘", "🍲", "🥣", "🥗", "🧂", "🥫", "🍱", "🍘", "🍙", "🍚", "🍛", "🍜",
        "🍝", "🍠", "🍢", "🍣", "🍤", "🍥", "🥮", "🍡", "🥟", "🦪", "🍦", "🍧", "🍨", "🍩", "🍪", "🎂",
        "🍰", "🧁", "🥧", "🍫", "🍬", "🍭", "🍮", "🍯", "🍼", "🥛", "☕", "🍵", "🧃", "🥤", "🧋", "🍶",
        "🍺", "🍻", "🥂", "🍷", "🥃", "🍸", "🍹", "🧉", "🍾", "🥄", "🍴", "🍽️", "🥢", "🧊", "🏆", "🥇",
        "🥈", "🥉", "🏅", "🎖️", "🎯", "🎱", "🎮", "🎲", "♟️", "🃏", "🀄", "🎴", "🧩", "🪀", "🪁", "⚽",
        "🏀", "🏈", "⚾", "🥎", "🏐", "🏉", "🥏", "🎾", "🏸", "🏒", "🏑", "🥍", "🏏", "🪃", "🥅", "⛳",
        "🏹", "🎣", "🤿", "🎿", "🛷", "🥌", "🥋", "🥊", "🏊", "🚴", "🚂", "🚃", "🚄", "🚅", "🚆", "🚇",
        "🚈", "🚉", "🚊", "🚝", "🚞", "🚋", "🚌", "🚍", "🚎", "🚐", "🚑", "🚒", "🚓", "🚔", "🚕", "🚖",
        "🚗", "🚘", "🚙", "🛻", "🚚", "🚛", "🚜", "🏎️", "🏍️", "🛵", "🦽", "🦼", "🛺", "🚲", "🛴", "🛹",
        "🛼", "🚏", "🛣️", "🛤️", "⛽", "🚨", "🚥", "🚦", "🚧", "⚓", "⛵", "🛶", "🚤", "🛳️", "⛴️", "🛥️",
        "🚢", "✈️", "🛩️", "🛫", "🛬", "💺", "🛰️", "🚀", "🛸", "🚁", "🔴", "🟠", "🟡", "🟢", "🔵", "🟣",
        "⚫", "⚪", "🟤", "🔺", "🔻", "🔷", "🔶", "🔹", "🔸", "🔘", "🏁", "🚩", "🎌", "🏴", "🏳️", "🔰",
        "♻️", "✅", "❎", "🚷", "❌", "⭕", "🛑", "⛔", "📛", "🚫", "🔱", "📯", "🔔", "🔕", "💲", "©️",
        "®️", "™️", "🔅", "🔆", "📢", "📣", "♠️", "♥️", "♦️", "♣️", "🕐", "🕑", "🕒", "🕓", "🕔", "🕕",
        "🕖", "🕗", "🕘", "🕙", "🕚", "🕛", "🕜", "🕝", "🕞", "🕟", "🕠", "🕡", "🕢", "🕣", "🕤", "🕥",
        "🕦", "🕧", "🇮🇹", "🇺🇸", "🇬🇧", "🇩🇪", "🇫🇷", "🇯🇵", "🇨🇳", "🇷🇺", "🇧🇷", "🇨🇦", "🇦🇺", "🇮🇳", "🇰🇷", "🇪🇸",
        "🇲🇽", "🇦🇷", "🇿🇦", "🇳🇬", "🇪🇬", "🇸🇦", "🇹🇷", "🇮🇷", "🇵🇰", "🇧🇩", "🇵🇹", "🇳🇱", "🇧🇪", "🇨🇭", "🇸🇪", "🇳🇴",
        "🇩🇰", "🇫🇮", "🇵🇱", "🇨🇿", "🇭🇺", "🇷🇴", "🇬🇷", "🇦🇹", "🇮🇱", "🇺🇦", "🇹🇭", "🇻🇳", "🇮🇩", "🇲🇾", "🇸🇬", "🇵🇭",
        "🇳🇿", "🇦🇪", "🥐", "🥖", "🫓", "🥨", "🧀", "🥞", "🧇", "🥓", "🌭", "🥪", "🥩", "🍖", "🍗", "🍿",
        "🧈", "🥜", "🫘", "🌰", "🥦", "🥬", "🥒", "🌶️", "🫑", "🥕", "🧄", "🧅", "🥔", "🍞", "🫗", "🪷",
        "🪻", "🫠", "🫢", "🫣", "🫤", "🫥", "🫡", "🫦", "🫃", "🫄", "‼️", "⁉️", "❓", "❔", "❕", "❗",
        "〰️", "✳️", "✴️", "❇️", "🔃", "🔄", "🔙", "🔚", "🔛", "🔜", "🔝", "🛐", "⚛️", "🕉️", "✡️", "☸️",
        "☯️", "✝️", "☦️", "🔠", "🔡", "🔢", "🔣", "🔤", "🅰️", "🅱️", "🆎", "🆑", "🅾️", "🆘", "🆗", "🆙",
        "🆒", "🆕", "🆓", "🆖", "📵", "🔞", "🔇", "🔈", "🔉", "🔊", "📴", "📳", "📶", "〽️", "♨️", "🈳",
    )

// ─── Costanti speciali ────────────────────────────────────────────────────────

    /** Emoji cifra per padding: indice = numero di bit di padding (0–9) */
    private val PADDING_EMOJIS = listOf("0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣")

    /** CRC-8 (0–255) → uno dei primi 256 emoji dell'alfabeto */
    private fun crcToEmoji(crc: Int): String = EMOJI_ALPHABET[crc and 0xFF]

    private val EMOJI_TO_INDEX: Map<String, Int> by lazy {
        buildMap { EMOJI_ALPHABET.forEachIndexed { i, e -> putIfAbsent(e, i) } }
    }

// ─── CRC-8, polinomio 0x07 (CRC-8/SMBUS) ────────────────────────────────────

    private fun crc8(bytes: ByteArray): Int {
        var crc = 0
        for (byte in bytes) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) { crc = if (crc and 0x80 != 0) (crc shl 1) xor 0x07 else crc shl 1 }
            crc = crc and 0xFF
        }
        return crc
    }

// ─── Tokenizer emoji ─────────────────────────────────────────────────────────

    /**
     * Divide la stringa in token emoji, gestendo sequenze multi-codepoint
     * (varianti ️, ZWJ, keycap ⃣, flag 🇮🇹, ecc.).
     * Strategia greedy: prova match da 8 a 1 char.
     */
    private fun tokenizeEmojis(s: String): List<String> = buildList {
        var i = 0
        while (i < s.length) {
            var found = false
            for (len in 8 downTo 1) {
                if (i + len > s.length) continue
                val candidate = s.substring(i, i + len)
                if (candidate in EMOJI_TO_INDEX || candidate in PADDING_EMOJIS) {
                    add(candidate)
                    i += len
                    found = true
                    break
                }
            }
            if (!found) i++
        }
    }

// ─── Codifica ─────────────────────────────────────────────────────────────────

    /**
     * Converte un [ByteArray] in una stringa EmojiBase1024.
     *
     * **Formato:** `[dati emoji…][0️⃣–9️⃣][CRC emoji]`
     * - I simboli dati codificano 10 bit ciascuno.
     * - La cifra emoji indica i bit di padding dell'ultimo simbolo (0 = nessun padding).
     * - Il CRC emoji è il CRC-8 dei byte originali, mappato sui primi 256 simboli.
     */
    fun encodeToEmoji(bytes: ByteArray): String {
        val sb = StringBuilder()
        var register = 0L
        var bitsInReg = 0

        for (byte in bytes) {
            register = (register shl 8) or (byte.toLong() and 0xFF)
            bitsInReg += 8
            while (bitsInReg >= 10) {
                bitsInReg -= 10
                sb.append(EMOJI_ALPHABET[((register shr bitsInReg) and 0x3FF).toInt()])
            }
        }

        val padding = if (bitsInReg > 0) {
            val p = 10 - bitsInReg
            sb.append(EMOJI_ALPHABET[((register shl p) and 0x3FF).toInt()])
            p
        } else 0

        sb.append(PADDING_EMOJIS[padding])
        sb.append(crcToEmoji(crc8(bytes)))
        return sb.toString()
    }

// ─── Decodifica ───────────────────────────────────────────────────────────────

    /**
     * Ricostruisce il [ByteArray] originale da una stringa EmojiBase1024.
     *
     * @throws IllegalArgumentException se il formato non è valido o il CRC non corrisponde.
     */
    fun decodeFromEmoji(encoded: String): ByteArray {
        if (encoded.isEmpty()) return ByteArray(0)

        val tokens = tokenizeEmojis(encoded)
        require(tokens.size >= 2) { "Stringa troppo corta: mancano padding e/o CRC" }

        val crcEmoji = tokens.last()
        val paddingEmoji = tokens[tokens.size - 2]
        val dataTokens = tokens.dropLast(2)

        val padding = PADDING_EMOJIS.indexOf(paddingEmoji)
        require(padding >= 0) { "Emoji di padding non riconosciuto: $paddingEmoji" }

        val expectedCrc = EMOJI_TO_INDEX[crcEmoji]
            ?.takeIf { it < 256 }
            ?: throw IllegalArgumentException("Emoji CRC non valido: $crcEmoji")

        val out = ByteArrayOutputStream()
        var register = 0L
        var bitsInReg = 0
        val total = dataTokens.size

        dataTokens.forEachIndexed { idx, emoji ->
            val symbolIdx = EMOJI_TO_INDEX[emoji]
                ?: throw IllegalArgumentException("Emoji sconosciuto nella posizione $idx: $emoji")

            register = (register shl 10) or symbolIdx.toLong()
            bitsInReg += 10

            val isLast = idx == total - 1
            while (bitsInReg >= 8) {
                // Nell'ultimo simbolo salta i bit di padding
                if (isLast && bitsInReg <= padding) break
                bitsInReg -= 8
                out.write(((register shr bitsInReg) and 0xFF).toInt())
            }
            if (isLast) bitsInReg = 0
        }

        val result = out.toByteArray()

        val actualCrc = crc8(result)
        require(actualCrc == expectedCrc) {
            "CRC non valido: atteso 0x%02X, calcolato 0x%02X — dati corrotti".format(expectedCrc, actualCrc)
        }

        return result
    }

// ─── Test ─────────────────────────────────────────────────────────────────────

    fun main() {
        val tests = listOf(
            "Hello, World!",
            "Kotlin 🚀🌍",
            "EmojiBase1024 ✅",
            "\u0000\u00FF\u007F",
            "a",
            "",
        )

        println("═".repeat(64))
        for (text in tests) {
            val original = text.encodeToByteArray()
            val enc = encodeToEmoji(original)
            val dec = decodeFromEmoji(enc)
            val ok = original.contentEquals(dec)
            println("Input   : \"$text\"")
            println("Encoded : $enc")
            println("Round   : ${if (ok) "✅ OK" else "❌ FAIL"}")
            println("─".repeat(64))
        }

        // Test CRC manomesso
        println("Test CRC manomesso:")
        val enc = encodeToEmoji("test manomesso".encodeToByteArray())
        val tampered = enc.dropLast(2) + "🐉" + enc.last()  // sostituisce il CRC
        runCatching { decodeFromEmoji(tampered) }
            .onSuccess { println("❌ Errore: corruzione non rilevata!") }
            .onFailure { println("✅ Corruzione rilevata: ${it.message}") }
        println("═".repeat(64))
    }
}