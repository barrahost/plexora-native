package com.dinfras.plexora.data

import android.util.Base64

private val MOJIBAKE_HINT = Regex("[ÃÂ]")

// Certaines chaines/stations de ce serveur renvoient du texte UTF-8 encode
// deux fois (mojibake : "Ã©" au lieu de "é") — deja rencontre sur la version
// web. On ne tente la reparation que si la chaine contient un indice
// classique de mojibake ET que le resultat redecode est du texte valide,
// pour ne jamais abimer un texte deja correct.
fun fixMojibake(s: String): String {
    if (s.isEmpty() || !MOJIBAKE_HINT.containsMatchIn(s)) return s
    if (s.any { it.code > 0xFF }) return s
    return try {
        val bytes = ByteArray(s.length) { s[it].code.toByte() }
        val fixed = String(bytes, Charsets.UTF_8)
        if (fixed.isNotBlank() && '�' !in fixed) fixed else s
    } catch (e: Exception) {
        s
    }
}

// Les titres/descriptions de l'EPG Xtream Codes sont souvent encodes en
// base64 (deja rencontre sur la version web). On ne decode que si le
// resultat est du texte lisible, sinon un titre en clair serait abime.
fun decodeEpgText(s: String?): String {
    if (s.isNullOrBlank()) return ""
    val trimmed = s.trim()
    val looksBase64 = trimmed.length % 4 == 0 && trimmed.matches(Regex("^[A-Za-z0-9+/]+=*$"))
    if (!looksBase64) return trimmed
    return try {
        val decoded = String(Base64.decode(trimmed, Base64.DEFAULT), Charsets.UTF_8)
        val printable = decoded.count { it.code >= 32 || it == '\n' }.toDouble() / decoded.length.coerceAtLeast(1)
        if (printable > 0.9) decoded else trimmed
    } catch (e: Exception) {
        trimmed
    }
}
