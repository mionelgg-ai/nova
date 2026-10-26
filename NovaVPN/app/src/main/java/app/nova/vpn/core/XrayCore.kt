package app.nova.vpn.core

import android.content.Context
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * The ONLY file that touches the Xray AAR (app/libs/libv2ray.aar from 2dust/AndroidLibXrayLite).
 * If a newer/older AAR has slightly different method names, only edit this file.
 */
object XrayCore {
    private var controller: CoreController? = null

    private val handler = object : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(code: Long, message: String?): Long = 0
    }

    fun init(context: Context) {
        Libv2ray.initCoreEnv(context.filesDir.absolutePath, "")
    }

    /** [tunFd] is the VpnService interface; Xray's `tun` inbound reads/writes packets on it. */
    fun start(configJson: String, tunFd: Int) {
        val c = Libv2ray.newCoreController(handler)
        c.startLoop(configJson, tunFd)
        controller = c
    }

    fun stop() {
        runCatching { controller?.stopLoop() }
        controller = null
    }
}
