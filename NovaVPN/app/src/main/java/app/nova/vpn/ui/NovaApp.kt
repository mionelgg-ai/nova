package app.nova.vpn.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.nova.vpn.BuildConfig
import app.nova.vpn.Cfg
import app.nova.vpn.MainViewModel
import app.nova.vpn.R
import app.nova.vpn.core.AdManager
import app.nova.vpn.core.AppSignals
import app.nova.vpn.core.VpnController
import app.nova.vpn.core.VpnState
import app.nova.vpn.core.VpnStatus
import app.nova.vpn.core.ConnectPlanner
import app.nova.vpn.data.AppSettings
import app.nova.vpn.data.Avatar
import app.nova.vpn.data.Jalali
import app.nova.vpn.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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

/** splash on top of (onboarding -> sign-in -> app) */
@Composable
fun NovaRoot() {
    var onboardingDone by remember { mutableStateOf(Prefs.onboardingDone) }
    var signedIn by remember { mutableStateOf(Prefs.signedIn) }
    var splashDone by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        when {
            !onboardingDone -> OnboardingScreen(onFinish = { Prefs.onboardingDone = true; onboardingDone = true })
            !signedIn -> AuthFlow(onDone = { signedIn = true })
            else -> NovaApp(ready = splashDone, onSignOut = { Prefs.signOut(); signedIn = false })
        }
        if (!splashDone) SplashScreen { splashDone = true }
    }
}

@Composable
fun NovaApp(ready: Boolean = true, onSignOut: () -> Unit = {}, vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = ctx.findActivity()
    val fa = LocalConfiguration.current.locales[0].language == "fa"

    val status by VpnState.status.collectAsState()
    val activeId by VpnState.activeServerId.collectAsState()
    val selected by vm.selected.collectAsState()
    val premium by vm.premium.collectAsState()
    val config by vm.config.collectAsState()
    val autoConnect by AppSignals.autoConnect.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sub by rememberSaveable { mutableIntStateOf(0) }          // 0 none, 1 per-app list, 2 privacy, 3 settings
    var showPremium by remember { mutableStateOf(false) }
    var adPending by remember { mutableStateOf(false) }
    var dismissedNotice by remember { mutableStateOf(Prefs.dismissedNotice) }
    var dismissedUpdate by remember { mutableIntStateOf(Prefs.dismissedUpdate) }

    fun toast(res: Int) = Toast.makeText(ctx, res, Toast.LENGTH_LONG).show()
    val telegram = config?.telegram ?: Cfg.DEFAULT_TELEGRAM

    // ---- connect flow:  permission -> (ad) -> start service (which falls back to other servers if needed)
    fun startVpn() {
        val server = selected
        if (server == null) { toast(R.string.err_no_server); VpnState.status.value = VpnStatus.Disconnected; return }
        scope.launch {
            // key derivation and DNS lookups happen off the main thread
            val plan = withContext(Dispatchers.IO) { ConnectPlanner.build(server.id) }
            if (plan.candidates.isEmpty()) {
                toast(R.string.err_bad_link); VpnState.status.value = VpnStatus.Disconnected
            } else {
                VpnController.start(ctx, plan.candidates, plan.httpPort)
            }
        }
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

    LaunchedEffect(ready) {
        if (ready && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun onPower() {
        if (adPending) return
        if (status !is VpnStatus.Disconnected) { VpnController.stop(ctx); return }
        val server = selected ?: run { toast(R.string.err_no_server); return }
        if (server.inMaintenance) { toast(R.string.maint_toast); return }
        if (!vm.canUse(server)) { showPremium = true; return }
        val intent = if (AppSettings.vpnMode) VpnService.prepare(ctx) else null
        if (intent != null) permLauncher.launch(intent) else afterPermission()
    }

    // Quick Settings tile asked us to connect
    LaunchedEffect(autoConnect, ready, selected) {
        if (autoConnect && ready && selected != null) {
            AppSignals.autoConnect.value = false
            if (status is VpnStatus.Disconnected) onPower()
        }
    }

    // keep the selected server in sync with the server that really connected (after a fallback)
    LaunchedEffect(activeId) {
        val id = activeId ?: return@LaunchedEffect
        config?.servers?.firstOrNull { it.id == id }?.let { if (it.id != selected?.id) vm.select(it) }
    }

    // messages from the VPN service
    LaunchedEffect(Unit) {
        VpnState.events.collect { e ->
            val msg = when {
                e.startsWith("switched:") -> ctx.getString(R.string.toast_switched, e.removePrefix("switched:"))
                e == "all_failed" -> ctx.getString(R.string.toast_all_failed)
                e == "unverified" -> ctx.getString(R.string.toast_unverified)
                else -> ctx.getString(R.string.toast_failed)
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }

    fun share() {
        val text = config?.shareText.orEmpty().ifBlank { ctx.getString(R.string.share_text, telegram) }
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { ctx.startActivity(Intent.createChooser(send, null)) }
    }

    // Buy: copy the order text (the admin reads it in Telegram) and open the channel
    fun buyPlan(gb: Int, months: Int, users: Int, total: Long) {
        val text = "سفارش اشتراک Nova VPN\n" +
            "حجم: " + Jalali.toPersianDigits(gb.toString()) + " گیگابایت\n" +
            "مدت: " + Jalali.toPersianDigits(months.toString()) + " ماه\n" +
            "تعداد کاربر همزمان: " + Jalali.toPersianDigits(users.toString()) + "\n" +
            "مبلغ: " + formatToman(total, true) + " تومان"
        runCatching {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("order", text))
        }
        toast(R.string.order_copied)
        ctx.openLink(telegram)
    }

    BackHandler(enabled = sub != 0) { sub = if (sub == 1) 3 else 0 }
    BackHandler(enabled = sub == 0 && tab != 0) { tab = 0 }

    // ---- home banners
    val expiryDays: Long? = if (premium.active) {
        premium.expires?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(it)) }.getOrNull() }
    } else null
    val notice = config?.notice

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when {
                sub == 1 -> SplitTunnelScreen(onBack = { sub = 3 })
                sub == 2 -> PrivacyScreen(onBack = { sub = 0 })
                sub == 3 -> SettingsScreen(onBack = { sub = 0 }, onPerApp = { sub = 1 })
                tab == 0 -> HomeScreen(
                    vm, status, ::onPower,
                    onOpenServers = { tab = 1 },
                    onPremium = { if (premium.active) tab = 3 else showPremium = true },
                    onTelegram = { ctx.openLink(telegram) },
                    extra = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (expiryDays != null && expiryDays <= Cfg.EXPIRY_WARN_DAYS) {
                                InfoBanner(
                                    text = if (expiryDays <= 0) stringResource(R.string.expiry_banner_today)
                                    else stringResource(R.string.expiry_banner, expiryDays.toInt()),
                                    icon = Icons.Rounded.Schedule, accent = NovaColors.Gold,
                                    actionLabel = stringResource(R.string.renew), onAction = { ctx.openLink(telegram) }
                                )
                            }
                            if (notice != null && notice.id != dismissedNotice) {
                                InfoBanner(
                                    text = notice.localized(fa),
                                    icon = Icons.Rounded.Campaign, accent = NovaColors.Lime,
                                    actionLabel = if (notice.url.isNotBlank()) stringResource(R.string.notice_open) else null,
                                    onAction = if (notice.url.isNotBlank()) ({ ctx.openLink(notice.url) }) else null,
                                    onClose = { Prefs.dismissedNotice = notice.id; dismissedNotice = notice.id }
                                )
                            }
                        }
                    },
                )
                tab == 1 -> ServersScreen(
                    vm,
                    onPicked = { tab = 0 },
                    onPremiumNeeded = { showPremium = true },
                    onMaintenance = { toast(R.string.maint_toast) },
                    onBuy = ::buyPlan,
                )
                tab == 2 -> StatsScreen(status)
                else -> ProfileScreen(
                    vm,
                    onTelegram = { ctx.openLink(telegram) },
                    onShare = ::share,
                    onPrivacy = { sub = 2 },
                    onSettings = { sub = 3 },
                    onSignOut = {
                        if (status !is VpnStatus.Disconnected) VpnController.stop(ctx)
                        Avatar.delete(ctx)
                        onSignOut()
                    },
                )
            }
        }
        BottomBar(tab) { tab = it; sub = 0 }
    }

    if (showPremium) {
        PremiumSheet(vm, onDismiss = { showPremium = false }, onSubscribe = { showPremium = false; sub = 0; tab = 1 })
    }

    // ---- update notice (optional or forced), controlled from servers.json
    val upd = config?.update
    if (upd != null && upd.versionCode > BuildConfig.VERSION_CODE && (upd.force || dismissedUpdate < upd.versionCode)) {
        val defaultText = stringResource(R.string.update_text_default, upd.versionName)
        AlertDialog(
            onDismissRequest = {
                if (!upd.force) { Prefs.dismissedUpdate = upd.versionCode; dismissedUpdate = upd.versionCode }
            },
            containerColor = NovaColors.Surface,
            titleContentColor = NovaColors.Text,
            textContentColor = NovaColors.Dim,
            properties = DialogProperties(dismissOnBackPress = !upd.force, dismissOnClickOutside = !upd.force),
            title = { Text(stringResource(R.string.update_title)) },
            text = { Text(upd.localized(fa).ifBlank { defaultText }) },
            confirmButton = {
                TextButton(onClick = { ctx.openLink(upd.url.ifBlank { telegram }) }) {
                    Text(stringResource(R.string.update_now), color = NovaColors.Lime)
                }
            },
            dismissButton = {
                if (!upd.force) {
                    TextButton(onClick = { Prefs.dismissedUpdate = upd.versionCode; dismissedUpdate = upd.versionCode }) {
                        Text(stringResource(R.string.update_later), color = NovaColors.Dim)
                    }
                }
            },
        )
    }
}
