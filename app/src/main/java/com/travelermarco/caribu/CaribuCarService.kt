package com.travelermarco.caribu

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Android Auto Car App Service.
 * Registers with the Android Auto host and provides the Caribu dashboard screen.
 * Also runs the local HTTP bridge server so Chrome can POST BLE data to it.
 */
class CaribuCarService : CarAppService() {

    private val bridgeServer = CaribuBridgeServer(this)

    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent) = CaribuScreen(carContext)
    }

    override fun onCreate() {
        super.onCreate()
        bridgeServer.start()
    }

    override fun onDestroy() {
        bridgeServer.stop()
        super.onDestroy()
    }
}
