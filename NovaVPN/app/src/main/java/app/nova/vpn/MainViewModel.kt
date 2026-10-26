package app.nova.vpn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.nova.vpn.data.ActivateResult
import app.nova.vpn.data.ConfigRepository
import app.nova.vpn.data.Prefs
import app.nova.vpn.data.Server
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val config = ConfigRepository.config
    val premium = ConfigRepository.premium

    private val selectedId = MutableStateFlow(Prefs.selectedServer)

    val selected: StateFlow<Server?> = combine(config, selectedId) { c, id ->
        val list = c?.servers.orEmpty()
        list.firstOrNull { it.id == id } ?: list.firstOrNull { !it.premium } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Cosmetic ping per server, generated once per app run. */
    val pings = MutableStateFlow<Map<String, Int>>(emptyMap())

    val refreshing = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            ConfigRepository.load(app)
            refresh()
        }
        viewModelScope.launch {
            config.collect { c ->
                val current = pings.value
                val add = c?.servers.orEmpty().filter { it.id !in current }
                    .associate { s ->
                        s.id to Random.nextInt(
                            (if (s.premium) Cfg.PREMIUM_PING else Cfg.FREE_PING).first,
                            (if (s.premium) Cfg.PREMIUM_PING else Cfg.FREE_PING).last + 1
                        )
                    }
                if (add.isNotEmpty()) pings.value = current + add
            }
        }
    }

    fun refresh(onDone: (Boolean) -> Unit = {}) {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val ok = ConfigRepository.refresh()
            refreshing.value = false
            onDone(ok)
        }
    }

    fun select(server: Server) {
        Prefs.selectedServer = server.id
        selectedId.value = server.id
    }

    suspend fun activate(code: String): ActivateResult = ConfigRepository.activate(code)
}
