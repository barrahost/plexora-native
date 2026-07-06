package com.dinfras.plexora.data

import android.content.Context
import java.io.File

// Diagnostic du gel "plein ecran noir + telecommande figee" : impossible de
// recuperer un logcat (pas d'acces adb sur la TV). Un fil d'arriere-plan
// ecrit un battement toutes les 300ms sur le disque + des reperes aux etapes
// cles (clic chaine, creation du lecteur...). Si le thread principal se fige,
// les battements (ecrits depuis un fil separe) continuent normalement — leur
// absence signalerait un blocage plus profond (systeme/GPU) plutot que
// seulement notre code Compose.
//
// Le seul moyen de sortir d'un gel total etant de forcer l'arret de l'appli,
// le journal doit survivre a ce redemarrage : on AJOUTE (append) au fichier
// au lieu de le reecrire a partir d'un tampon memoire qui, lui, est perdu au
// redemarrage — sinon le tout premier "app start" du relancement ecrasait
// justement les lignes du gel qu'on cherche a lire.
object DebugLog {
    private const val FILE_NAME = "debug_log.txt"
    private const val MAX_LINES = 600
    private const val TRIM_EVERY = 50
    @Volatile private var appContext: Context? = null
    @Volatile private var started = false
    private var eventsSinceTrim = 0

    fun init(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        event("=== app start ===")
        Thread {
            while (true) {
                event("tick")
                Thread.sleep(300)
            }
        }.apply { isDaemon = true; name = "plexora-watchdog"; start() }
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
    }

    // Les "tick" (un par 300ms) ne servent qu'a mesurer les ecarts — les
    // centaines de lignes qu'ils representent noient les evenements utiles
    // (clic, creation du lecteur...) dans un journal qui ne defile meme pas
    // a l'ecran. On resume plutot : le plus grand ecart entre deux battements
    // consecutifs (preuve d'un vrai gel du thread principal ou non), suivi de
    // la liste des evenements non-"tick", du plus recent au plus ancien.
    fun summarize(context: Context): String {
        val lines = runCatching { File(context.filesDir, FILE_NAME).readLines() }.getOrDefault(emptyList())
        if (lines.isEmpty()) return "(vide)"
        var lastTickTime: Long? = null
        var maxGap = 0L
        var maxGapAt = 0L
        val breadcrumbs = mutableListOf<String>()
        for (raw in lines) {
            val spaceIdx = raw.indexOf(' ')
            if (spaceIdx <= 0) continue
            val time = raw.substring(0, spaceIdx).toLongOrNull() ?: continue
            val tag = raw.substring(spaceIdx + 1)
            if (tag == "tick") {
                lastTickTime?.let { prev ->
                    val gap = time - prev
                    if (gap > maxGap) { maxGap = gap; maxGapAt = time }
                }
                lastTickTime = time
            } else {
                breadcrumbs.add(raw)
            }
        }
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.FRANCE)
        val header = if (maxGap > 0) {
            "Plus grand écart entre 2 battements : ${maxGap}ms (vers ${sdf.format(java.util.Date(maxGapAt))})\n" +
                "-> Si > 1000ms, le thread principal (ou le systeme) a vraiment gelé a ce moment.\n"
        } else {
            "Aucun écart notable entre les battements (aucun gel detecté).\n"
        }
        val events = breadcrumbs.asReversed().joinToString("\n") { line ->
            val spaceIdx = line.indexOf(' ')
            val time = line.substring(0, spaceIdx).toLongOrNull()
            if (time != null) "${sdf.format(java.util.Date(time))}  ${line.substring(spaceIdx + 1)}" else line
        }
        return header + "\n" + events
    }
}
