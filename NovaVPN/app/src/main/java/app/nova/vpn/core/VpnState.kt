package app.nova.vpn.core

import kotlinx.coroutines.flow.MutableStateFlow

sealed interface VpnStatus {
    object Disconnected : VpnStatus
    object Connecting : VpnStatus
    class Connected(val since: Long) : VpnStatus
}

object VpnState {
    val status = MutableStateFlow<VpnStatus>(VpnStatus.Disconnected)

    /** e.g. "VLESS" — shown on the stats screen */
    val protocol = MutableStateFlow("")
}
