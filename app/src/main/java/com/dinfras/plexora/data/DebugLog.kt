package com.dinfras.plexora.data

import android.content.Context
import java.io.File

// Diagnostic du gel "plein ecran noir + telecommande figee" : impossible de
// recuperer un logcat (pas d'acces adb sur la TV). Un fil d'arriere-plan
// ecrit un battement toutes les 300ms sur le disque + des reperes aux etapes
// cles (clic chaine, creation du lecteur...). Si le thread principal se fige,
// les battements (ecrits depuis un fil separe) continuent normalement — leur
// absence signalerait un blocage plus profond (systeme/GPU) plutot que
// seulement notre code Compose. Consultable dans Parametres sans adb.
object DebugLog {
    private const val FILE_NAME = "debug_log.txt"
    private const val MAX_LINES = 400
    private val buffer = ArrayDeque<String>()
    @Volatile private var appContext: Context? = null
    @Volatile private var started = false

    fun init(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        event("app start")
        Thread {
            while (true) {
                event("tick")
                Thread.sleep(300)
            }
        }.apply { isDaemon = true; name = "plexora-watchdog"; start() }
    }

    @Synchronized
    fun event(tag: String) {
        val line = "${System.currentTimeMillis()} $tag"
        buffer.addLast(line)
        while (buffer.size > MAX_LINES) buffer.removeFirst()
        val ctx = appContext ?: return
        runCatching { File(ctx.filesDir, FILE_NAME).writeText(buffer.joinToString("\n")) }
    }

    fun readAll(context: Context): String =
        runCatching { File(context.filesDir, FILE_NAME).readText() }.getOrDefault("(vide)")
}
