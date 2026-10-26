package app.nova.vpn.core

import android.content.Context
import app.nova.vpn.BuildConfig
import app.nova.vpn.data.Prefs
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

/**
 * The ONLY file that touches the Xray AAR (app/libs/libv2ray.aar from 2dust/AndroidLibXrayLite).
 * If a newer/older AAR has slightly different method names, only edit this file.
 */
object XrayCore {
    private var controller: CoreController? = null

    /** true when geoip.dat is available, so the "bypass Iranian sites" rules can be used */
    @Volatile
    var geoReady = false
        private set

    private val handler = object : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(code: Long, message: String?): Long = 0
    }

    fun init(context: Context) {
        Libv2ray.initCoreEnv(context.filesDir.absolutePath, "")
        Thread { copyGeo(context) }.start()
    }

    /** assets/geo/*.dat (downloaded by the GitHub workflow) -> filesDir, once per app version. */
    private fun copyGeo(context: Context) {
        runCatching {
            val names = context.assets.list("geo")?.filter { it.endsWith(".dat") }.orEmpty()
            if (names.isEmpty()) return
            val need = Prefs.geoVersion != BuildConfig.VERSION_CODE ||
                names.any { !File(context.filesDir, it).exists() }
            if (need) {
                for (n in names) {
                    context.assets.open("geo/$n").use { input ->
                        File(context.filesDir, n).outputStream().use { input.copyTo(it) }
                    }
                }
                Prefs.geoVersion = BuildConfig.VERSION_CODE
            }
        }
        geoReady = File(context.filesDir, "geoip.dat").exists()
    }

    /** [tunFd] is the VpnService interface; Xray's `tun` inbound reads/writes packets on it. */
    fun start(configJson: String, tunFd: Int) {
        val c = Libv2ray.newCoreController(handler)
        c.startLoop(configJson, tunFd)
        controller = c
    }

    /** Round-trip time (ms) of an HTTP request THROUGH the running proxy. Throws on failure. */
    fun measureDelay(url: String): Long = controller?.measureDelay(url) ?: -1L

    fun stop() {
        runCatching { controller?.stopLoop() }
        controller = null
    }
}
