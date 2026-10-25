package app.nova.vpn.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.nova.vpn.Cfg
import app.nova.vpn.MainViewModel
import app.nova.vpn.R
import app.nova.vpn.core.AdManager
import app.nova.vpn.core.VpnController
import app.nova.vpn.core.VpnState
import app.nova.vpn.core.VpnStatus
import app.nova.vpn.core.XrayConfig
import app.nova.vpn.data.ConfigRepository

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

private fun Context.openLink(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun NovaApp(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    val activity = ctx.findActivity()
    val status by VpnState.status.collectAsState()
    val selected by vm.selected.collectAsState()
    val premium by vm.premium.collectAsState()
    val config by vm.config.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showPremium by remember { mutableStateOf(false) }
    var adPending by remember { mutableStateOf(false) }

    fun toast(res: Int) = Toast.makeText(ctx, res, Toast.LENGTH_LONG).show()
    val telegram = config?.telegram ?: Cfg.DEFAULT_TELEGRAM

    // ---- connect flow:  permission -> (ad) -> start service
    fun startVpn() {
        val server = selected
        if (server == null) { toast(R.string.err_no_server); VpnState.status.value = VpnStatus.Disconnected; return }
        val link = ConfigRepository.linkFor(server)
        val built = link?.let { runCatching { XrayConfig.fromLink(it) }.getOrNull() }
        if (built == null) { toast(R.string.err_bad_link); VpnState.status.value = VpnStatus.Disconnected; return }
        VpnController.start(ctx, built, server.name)
    }

    fun afterPermission() {
        val skipAd = premium.active && Cfg.SKIP_ADS_FOR_PREMIUM
        if (skipAd || activity == null) { startVpn(); return }
        adPending = true
        VpnState.status.value = VpnStatus.Connecting
        AdManager.showThen(activity) { adPending = false; startVpn() }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) afterPermission() else toast(R.string.err_perm)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun onPower() {
        if (adPending) return
        if (status !is VpnStatus.Disconnected) { VpnController.stop(ctx); return }
        val server = selected ?: run { toast(R.string.err_no_server); return }
        if (server.premium && !premium.active) { showPremium = true; return }
        val intent = VpnService.prepare(ctx)
        if (intent != null) permLauncher.launch(intent) else afterPermission()
    }

    BackHandler(enabled = tab != 0) { tab = 0 }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> HomeScreen(
                    vm, status, ::onPower,
                    onOpenServers = { tab = 1 },
                    onPremium = { if (premium.active) tab = 3 else showPremium = true },
                    onTelegram = { ctx.openLink(telegram) },
                )
                1 -> ServersScreen(vm, onPicked = { tab = 0 }, onPremiumNeeded = { showPremium = true })
                2 -> StatsScreen(status)
                3 -> ProfileScreen(vm, onTelegram = { ctx.openLink(telegram) })
            }
        }
        BottomBar(tab) { tab = it }
    }

    if (showPremium) {
        PremiumSheet(
            vm,
            onDismiss = { showPremium = false },
            onSubscribe = { ctx.openLink(telegram) },
        )
    }
}
