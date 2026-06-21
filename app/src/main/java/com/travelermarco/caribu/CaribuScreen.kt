package com.travelermarco.caribu

import android.content.SharedPreferences
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main Android Auto screen — shows Caribu van data on the car display.
 * Reads state from SharedPreferences (populated by CaribuBridgeServer).
 * Refreshes automatically when state changes.
 */
class CaribuScreen(carContext: CarContext) : Screen(carContext) {

    private val prefs: SharedPreferences =
        carContext.applicationContext.getSharedPreferences(
            CaribuBridgeServer.PREFS_NAME, android.content.Context.MODE_PRIVATE
        )

    // Strong reference — SharedPreferences uses weak refs internally
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == CaribuBridgeServer.KEY_STATE) invalidate()
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) =
                prefs.registerOnSharedPreferenceChangeListener(prefsListener)
            override fun onStop(owner: LifecycleOwner) =
                prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        })
    }

    override fun onGetTemplate(): Template {
        val json    = prefs.getString(CaribuBridgeServer.KEY_STATE, null)
        val lastTs  = prefs.getLong(CaribuBridgeServer.KEY_LAST_UPDATE, 0L)
        val ageMs   = System.currentTimeMillis() - lastTs
        val hasData = json != null && ageMs < 5 * 60_000   // stale after 5 min

        return if (hasData) buildDataTemplate(json!!, lastTs)
        else              buildIdleTemplate()
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    private fun buildDataTemplate(json: String, lastTs: Long): Template {
        val s = try { JSONObject(json) } catch (_: Exception) { return buildIdleTemplate() }

        val soc         = s.optInt("soc", -1)
        val heaterTemp  = s.optDouble("heaterTemp", Double.NaN)
        val heaterOn    = s.optBoolean("heaterOn", false)
        val solarW      = s.optDouble("solarW", 0.0)
        val bmsConn     = s.optBoolean("bmsConnected", false)
        val heaterConn  = s.optBoolean("heaterConnected", false)
        val timeStr     = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastTs))

        val rows = mutableListOf<Row>()

        // Batteria
        rows += Row.Builder()
            .setTitle(if (soc >= 0) "🔋 Batteria: $soc%" else "🔋 Batteria")
            .addText(
                when {
                    !bmsConn  -> "Non connesso"
                    soc >= 50 -> "Buona carica"
                    soc >= 20 -> "⚠ Bassa"
                    soc >= 0  -> "⛔ Critica"
                    else      -> "—"
                }
            )
            .build()

        // Riscaldatore
        if (heaterConn || !heaterTemp.isNaN()) {
            rows += Row.Builder()
                .setTitle(if (!heaterTemp.isNaN()) "🔥 Riscaldatore: ${heaterTemp.toInt()}°C" else "🔥 Riscaldatore")
                .addText(if (heaterOn) "Acceso" else if (heaterConn) "Spento" else "Non connesso")
                .build()
        }

        // Solare
        if (solarW > 0) {
            rows += Row.Builder()
                .setTitle("☀️ Solare: ${solarW.toInt()} W")
                .addText("Pannelli attivi")
                .build()
        }

        // Aggiornamento
        rows += Row.Builder()
            .setTitle("🕐 Aggiornato alle $timeStr")
            .addText("Apri Caribù in Chrome per i dati live")
            .build()

        val pane = Pane.Builder()
            .apply { rows.forEach { addRow(it) } }
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("🚐 Caribù")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun buildIdleTemplate(): Template {
        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("📡 In attesa dei dati")
                    .addText("Apri Caribù in Chrome sul telefono per sincronizzare")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("ℹ️ Come funziona")
                    .addText("I dati BLE vengono trasmessi automaticamente da Chrome")
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("🚐 Caribù")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
