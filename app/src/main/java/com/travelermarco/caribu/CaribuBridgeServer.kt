package com.travelermarco.caribu

import android.content.Context
import android.content.SharedPreferences
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * Minimal HTTP server on localhost:8888.
 * Receives state POSTs from the Caribu PWA running in Chrome and saves
 * them to SharedPreferences so the Car App can display live data on Android Auto.
 *
 * Handles the Chrome Private Network Access preflight so HTTPS→localhost is allowed.
 */
class CaribuBridgeServer(private val context: Context) {

    companion object {
        const val PORT          = 8888
        const val PREFS_NAME    = "caribu_aa_state"
        const val KEY_STATE     = "state_json"
        const val KEY_LAST_UPDATE = "last_update_ts"
        const val ORIGIN        = "https://caribu.vercel.app"

        fun getPrefs(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var serverSocket: ServerSocket? = null
    private var running = false
    var onStateReceived: (() -> Unit)? = null

    fun start() {
        if (running) return
        running = true
        Thread({
            try {
                serverSocket = ServerSocket(PORT)
                while (running) {
                    val client = serverSocket?.accept() ?: break
                    Thread({ handleClient(client) }, "caribu-bridge-client").start()
                }
            } catch (_: SocketException) {
                // Normal shutdown
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, "caribu-bridge-server").start()
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            // Read request line + headers
            val requestLine = reader.readLine() ?: return
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                val parts = line!!.split(":", limit = 2)
                if (parts.size == 2) headers[parts[0].trim().lowercase()] = parts[1].trim()
            }

            val method = requestLine.split(" ").firstOrNull()?.uppercase() ?: return
            val path   = requestLine.split(" ").getOrNull(1) ?: "/"

            when {
                // CORS preflight — Chrome Private Network Access
                method == "OPTIONS" -> {
                    respond(writer, 204, headers = corsHeaders())
                }

                // State update from PWA
                method == "POST" && path == "/state" -> {
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = if (contentLength > 0) {
                        val buf = CharArray(contentLength)
                        reader.read(buf, 0, contentLength)
                        String(buf)
                    } else ""

                    saveState(body)
                    respond(writer, 200, body = """{"ok":true}""", headers = corsHeaders())
                    onStateReceived?.invoke()
                }

                // Current state (for debugging)
                method == "GET" && path == "/state" -> {
                    val json = getPrefs(context).getString(KEY_STATE, "{}") ?: "{}"
                    respond(writer, 200, body = json, headers = corsHeaders())
                }

                else -> respond(writer, 404, body = "Not found")
            }
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun saveState(json: String) {
        if (json.isBlank()) return
        getPrefs(context).edit()
            .putString(KEY_STATE, json)
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
    }

    private fun corsHeaders() = mapOf(
        "Access-Control-Allow-Origin"          to ORIGIN,
        "Access-Control-Allow-Methods"         to "GET, POST, OPTIONS",
        "Access-Control-Allow-Headers"         to "Content-Type",
        "Access-Control-Allow-Private-Network" to "true",
        "Access-Control-Max-Age"               to "86400"
    )

    private fun respond(
        writer: PrintWriter,
        status: Int,
        body: String = "",
        headers: Map<String, String> = emptyMap()
    ) {
        val statusText = when (status) {
            200 -> "OK"; 204 -> "No Content"; 404 -> "Not Found"
            else -> "Unknown"
        }
        writer.println("HTTP/1.1 $status $statusText")
        writer.println("Content-Type: application/json")
        writer.println("Content-Length: ${body.toByteArray().size}")
        headers.forEach { (k, v) -> writer.println("$k: $v") }
        writer.println()
        if (body.isNotEmpty()) writer.print(body)
        writer.flush()
    }
}
