package app.nova.vpn.core

import kotlinx.coroutines.flow.MutableSharedFlow
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

    /** id of the server that is really connected (differs from the selected one after an automatic fallback) */
    val activeServerId = MutableStateFlow<String?>(null)

    /** one-shot messages for the UI: "switched:<name>", "all_failed", "unverified", "failed" */
    val events = MutableSharedFlow<String>(extraBufferCapacity = 8)
}

/** Lets other entry points (Quick Settings tile) ask the UI to start a connection. */
object AppSignals {
    val autoConnect = MutableStateFlow(false)
}
