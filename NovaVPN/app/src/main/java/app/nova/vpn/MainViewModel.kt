package app.nova.vpn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.nova.vpn.data.ActivateResult
import app.nova.vpn.data.ConfigRepository
import app.nova.vpn.data.Prefs
import app.nova.vpn.data.Server
import app.nova.vpn.data.accessible
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val config = ConfigRepository.config
    val premium = ConfigRepository.premium

    private val selectedId = MutableStateFlow(Prefs.selectedServer)

    val selected: StateFlow<Server?> = combine(config, selectedId) { c, id ->
        val list = c?.servers.orEmpty()
        list.firstOrNull { it.id == id } ?: list.firstOrNull { !it.premium && !it.bonus && !it.inMaintenance }
            ?: list.firstOrNull { !it.premium }
            ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Cosmetic ping per server, generated once per app run. */
    val pings = MutableStateFlow<Map<String, Int>>(emptyMap())

    val refreshing = MutableStateFlow(false)

    /** milliseconds of bonus access left (0 = locked) */
    val bonusLeftMs = MutableStateFlow(0L)
    val bonusActive: StateFlow<Boolean> = bonusLeftMs.map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val bypassIran = MutableStateFlow(Prefs.bypassIran)

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
                        val range = if (s.premium) Cfg.PREMIUM_PING else Cfg.FREE_PING
                        s.id to Random.nextInt(range.first, range.last + 1)
                    }
                if (add.isNotEmpty()) pings.value = current + add
            }
        }
        viewModelScope.launch {
            while (true) {
                bonusLeftMs.value = (Prefs.bonusUntil - System.currentTimeMillis()).coerceAtLeast(0L)
                delay(1000)
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

    fun canUse(server: Server) = server.accessible(premium.value.active, bonusActive.value)

    fun grantBonus(minutes: Int) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        Prefs.bonusUntil = until
        bonusLeftMs.value = minutes * 60_000L
    }

    fun setBypassIran(v: Boolean) {
        Prefs.bypassIran = v
        bypassIran.value = v
    }

    suspend fun activate(code: String): ActivateResult = ConfigRepository.activate(code)
}
