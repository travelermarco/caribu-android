package com.travelermarco.caribu

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        const val VERSION_NAME = "1.0"
        const val CARIBU_URL   = "https://caribu.vercel.app"
        const val GITHUB_USER  = "travelermarco"
        const val GITHUB_REPO  = "caribu-android"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnOpen      = findViewById<Button>(R.id.btnOpenChrome)
        val statusBridge = findViewById<TextView>(R.id.statusBridge)
        val versionText  = findViewById<TextView>(R.id.versionText)

        versionText.text = "v$VERSION_NAME"

        // Open PWA in Chrome
        btnOpen.setOnClickListener { openInChrome() }

        // Refresh bridge status every 5s
        val handler = android.os.Handler(mainLooper)
        val refreshStatus = object : Runnable {
            override fun run() {
                val prefs   = CaribuBridgeServer.getPrefs(this@MainActivity)
                val lastTs  = prefs.getLong(CaribuBridgeServer.KEY_LAST_UPDATE, 0L)
                val ageMs   = System.currentTimeMillis() - lastTs
                statusBridge.text = if (lastTs > 0 && ageMs < 60_000) {
                    val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastTs))
                    "📡 Bridge: ultimo dato alle $t"
                } else {
                    "📡 Bridge: in attesa della PWA"
                }
                handler.postDelayed(this, 5_000)
            }
        }
        handler.post(refreshStatus)

        // Check for updates
        lifecycleScope.launch {
            UpdateChecker.check(VERSION_NAME)?.let { update ->
                showUpdateBanner(update)
            }
        }

        // Auto-open Chrome on first launch
        if (savedInstanceState == null) openInChrome()
    }

    private fun openInChrome() {
        val uri    = Uri.parse(CARIBU_URL)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            try { setPackage("com.android.chrome") } catch (_: Exception) {}
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    private fun showUpdateBanner(update: UpdateInfo) {
        val banner  = findViewById<LinearLayout>(R.id.updateBanner)
        val text    = findViewById<TextView>(R.id.updateText)
        val btn     = findViewById<Button>(R.id.updateBtn)
        text.text   = getString(R.string.update_available, update.version)
        banner.visibility = android.view.View.VISIBLE
        btn.setOnClickListener {
            if (update.apkUrl != null) {
                lifecycleScope.launch { downloadAndInstall(update.apkUrl, btn) }
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl)))
            }
        }
    }

    private suspend fun downloadAndInstall(apkUrl: String, btn: Button) {
        btn.isEnabled = false
        btn.text = getString(R.string.update_downloading)
        try {
            val file = UpdateChecker.downloadApk(this, apkUrl)
            if (file != null) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this, "${packageName}.fileprovider", file
                )
                val install = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(install)
            } else {
                btn.text = getString(R.string.update_download_error)
            }
        } catch (_: Exception) {
            btn.text = getString(R.string.update_download_error)
        }
    }
}
