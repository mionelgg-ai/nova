package app.nova.vpn

import android.app.Application
import app.nova.vpn.core.AdManager
import app.nova.vpn.core.XrayCore
import app.nova.vpn.data.Prefs

class NovaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        runCatching { XrayCore.init(this) }
        AdManager.init(this)
    }
}
