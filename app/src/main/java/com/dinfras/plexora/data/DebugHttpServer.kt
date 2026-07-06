package com.dinfras.plexora.data

import android.content.Context
import java.io.File
import java.net.ServerSocket

// Les captures d'ecran de l'ecran Parametres tronquent systematiquement le
// journal de diagnostic (texte non defilable a la telecommande), et il n'y a
// pas d'acces adb a la TV. Ce mini serveur HTTP local sert donc le journal
// COMPLET (evenements + dump logcat brut) sur le reseau domestique : ouvrir
// http://IP-DE-LA-TV:8765 depuis un PC/telephone du meme reseau suffit pour
// tout lire/copier. Demarre avec l'appli, ne sert que des fichiers de
// diagnostic en lecture seule (aucune donnee sensible : pas d'identifiants).
object DebugHttpServer {
    const val PORT = 8765
    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        Thread {
            runCatching {
                val server = ServerSocket(PORT)
                while (true) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: continue
                    Thread {
                        runCatching {
                            socket.use { s ->
                                // Consomme la ligne de requete (peu importe le chemin).
                                val reader = s.getInputStream().bufferedReader()
                                val requestLine = reader.readLine() ?: ""
                                val body = buildString {
                                    append("=== PLEXORA DIAGNOSTIC ===\n")
                                    append("requete: $requestLine\n\n")
                                    append("=== RESUME ===\n")
                                    append(DebugLog.summarize(appContext))
                                    append("\n\n=== JOURNAL COMPLET (brut) ===\n")
                                    append(DebugLog.readAll(appContext))
                                    append("\n\n=== DUMP LOGCAT COMPLET ===\n")
                                    val logcat = File(appContext.filesDir, "logcat_dump.txt")
                                    if (logcat.exists()) {
                                        // Uniquement la fin : le dump complet peut faire
                                        // plusieurs Mo, inutile de tout transferer.
                                        append(logcat.readLines().takeLast(3000).joinToString("\n"))
                                    } else {
                                        append("(pas de dump logcat)")
                                    }
                                }
                                val bytes = body.toByteArray(Charsets.UTF_8)
                                val out = s.getOutputStream()
                                out.write(
                                    ("HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: text/plain; charset=utf-8\r\n" +
                                        "Content-Length: ${bytes.size}\r\n" +
                                        "Connection: close\r\n\r\n").toByteArray(),
                                )
                                out.write(bytes)
                                out.flush()
                            }
                        }
                    }.apply { isDaemon = true; start() }
                }
            }
        }.apply { isDaemon = true; name = "plexora-debug-http"; start() }
    }

    // Adresse IPv4 locale de la TV sur le reseau, pour afficher l'URL a ouvrir.
    fun localIp(): String? =
        runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                ?.hostAddress
        }.getOrNull()
}
