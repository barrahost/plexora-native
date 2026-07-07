package com.dinfras.plexora.data

import android.content.Context
import java.io.File

// Journal de diagnostic minimal : quelques evenements cles (clic chaine,
// creation du lecteur...) ajoutes a un petit fichier, consultables dans
// Parametres ou via le mini serveur HTTP (DebugHttpServer).
//
// Les outils lourds utilises pour traquer le gel plein ecran (battement
// toutes les 300ms, dump logcat en continu) ont ete RETIRES : le dump logcat
// grossissait sans limite (les lignes "Quality" de la TCL font plusieurs Ko)
// et sa relecture entiere en memoire allouait 70-180 Mo d'un coup — c'etait
// devenu une cause d'OutOfMemory a part entiere (le decodeur video ne
// pouvait plus allouer : "start: cannot allocate memory at all").
object DebugLog {
    private const val FILE_NAME = "debug_log.txt"
    // Releve a 3000 (etait 300) : la nouvelle instrumentation (compteur de
    // recompositions, battement regulier) genere beaucoup plus de lignes sur
    // une session de lecture prolongee, et les evenements les plus utiles
    // (battements, premier signe de ralentissement) se faisaient trimmer
    // avant meme de pouvoir etre consultes via le serveur de diagnostic.
    // Reste largement sous la taille qui avait cause l'OOM du dump logcat
    // (des dizaines de Mo) : quelques milliers de courtes lignes = quelques
    // centaines de Ko au pire.
    private const val MAX_LINES = 3000
    private const val TRIM_EVERY = 200
    @Volatile private var appContext: Context? = null
    @Volatile private var started = false
    private var eventsSinceTrim = 0

    fun init(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        event("=== app start ===")
    }

    @Synchronized
    fun event(tag: String) {
        val ctx = appContext ?: return
        val line = "${System.currentTimeMillis()} $tag\n"
        val file = File(ctx.filesDir, FILE_NAME)
        runCatching { file.appendText(line) }
        eventsSinceTrim++
        if (eventsSinceTrim >= TRIM_EVERY) {
            eventsSinceTrim = 0
            runCatching {
                val lines = file.readLines()
                if (lines.size > MAX_LINES) {
                    file.writeText(lines.takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
                }
            }
        }
    }

    fun readAll(context: Context): String =
        runCatching { File(context.filesDir, FILE_NAME).readText() }.getOrDefault("(vide)")

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
        // Purge aussi l'ancien dump logcat des versions precedentes, qui
        // pouvait atteindre des dizaines de Mo sur le disque.
        runCatching { File(context.filesDir, "logcat_dump.txt").delete() }
    }

    fun summarize(context: Context): String {
        val lines = runCatching { File(context.filesDir, FILE_NAME).readLines() }.getOrDefault(emptyList())
        if (lines.isEmpty()) return "(vide)"
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.FRANCE)
        return lines.asReversed().take(30).joinToString("\n") { line ->
            val spaceIdx = line.indexOf(' ')
            val time = if (spaceIdx > 0) line.substring(0, spaceIdx).toLongOrNull() else null
            if (time != null) "${sdf.format(java.util.Date(time))}  ${line.substring(spaceIdx + 1)}" else line
        }
    }
}
