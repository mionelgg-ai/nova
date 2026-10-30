package app.nova.vpn.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import app.nova.vpn.data.AppSettings
import app.nova.vpn.data.AppSettings.K
import app.nova.vpn.data.Prefs

private sealed class Dlg
private class EditDlg(val key: String, val title: String, val numeric: Boolean, val multiline: Boolean) : Dlg()
private class PickDlg(val key: String, val title: String, val options: List<Pair<String, String>>) : Dlg()

/** Opened from the gear icon on the profile screen. Sections open/close like in v2rayNG. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onPerApp: () -> Unit) {
    val ctx = LocalContext.current
    val isFa = LocalConfiguration.current.locales[0].language == "fa"
    var rev by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val redrawOnChange = rev            // every change below bumps this
    var open by remember { mutableStateOf(setOf("tunnel")) }
    var dialog by remember { mutableStateOf<Dlg?>(null) }
    var help by remember { mutableStateOf(false) }

    fun t(en: String, fa: String) = if (isFa) fa else en
    val changed: () -> Unit = { rev++ }
    fun toggle(id: String) { open = if (id in open) open - id else open + id }

    val vpn = AppSettings.vpnMode
    val localProxyActive = AppSettings.bool(K.LOCAL_PROXY) || !vpn
    val localDns = AppSettings.bool(K.LOCAL_DNS)
    val sniffing = AppSettings.bool(K.SNIFFING)
    val dynamicPort = AppSettings.bool(K.DYNAMIC_PORT)
    val lang = LocalConfiguration.current.locales[0].language

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        SubHeader(t("Settings", "تنظیمات"), onBack)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            // ---- language
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Language, null, tint = NovaColors.Lime, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(t("Language", "زبان برنامه"), Modifier.weight(1f), fontSize = 16.sp)
                LangChip("فارسی", lang == "fa") { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fa")) }
                Spacer(Modifier.width(8.dp))
                LangChip("English", lang != "fa") { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
            }

            // ---- Tunnel
            Section(t("Tunnel Settings", "تنظیمات تونل"), "tunnel" in open, { toggle("tunnel") }) {
                PickRow(K.MODE, t("Tunnel mode", "حالت تونل"), listOf("vpn" to "VPN", "proxy" to "Proxy"), true, { dialog = it })
                ValueRow(t("Click me for more help", "راهنمای بیشتر"), null, true) { help = true }
                SwitchRow(t("Xray TUN", "Xray TUN"), t("TUN is handled by Xray-core.", "TUN توسط هسته Xray انجام می‌شود."), true, false) { }
                BoolRow(K.IPV6, t("Enable IPv6", "فعال‌سازی IPv6"), t("Add IPv6 address and routes to the VPN interface", "آدرس و مسیر IPv6 به رابط VPN اضافه می‌شود"), vpn, null, changed)
                BoolRow(K.PREFER_IPV6, t("Prefer IPv6", "اولویت با IPv6"), t("Prefer IPv6 addresses when resolving domain names", "هنگام تبدیل نام دامنه، آدرس IPv6 ترجیح داده شود"), true, null, changed)
                BoolRow(
                    K.LOCAL_PROXY, t("Enable local proxy", "فعال‌سازی پروکسی محلی"),
                    t("When disabled, no SOCKS/HTTP proxy is available on this device.", "اگر خاموش باشد، پروکسی SOCKS/HTTP روی این دستگاه در دسترس نیست."),
                    vpn, if (vpn) null else true, changed
                )
                if (localProxyActive) {
                    BoolRow(
                        K.ALLOW_LAN, t("Allow connections from the LAN", "اجازه اتصال از شبکه محلی"),
                        t("Other devices can connect to the proxy by your IP address. Only enable it on trusted networks.", "دستگاه‌های دیگر می‌توانند با IP شما به پروکسی وصل شوند. فقط در شبکه‌های مطمئن روشن کنید."),
                        true, null, changed
                    )
                    TextRow(K.SOCKS_PORT, t("SOCKS5 port", "پورت SOCKS5"), !dynamicPort, false, true, false) { dialog = it }
                    TextRow(K.HTTP_PORT, t("HTTP port", "پورت HTTP"), !dynamicPort, false, true, false) { dialog = it }
                }
                BoolRow(K.PER_APP, t("Per-App Proxy", "پروکسی برای هر برنامه"), t("Choose which apps use or bypass the VPN", "انتخاب کنید کدام برنامه‌ها از VPN استفاده کنند یا نکنند"), vpn, null, changed)
                if (AppSettings.perApp) {
                    PickRow(
                        K.PER_APP_MODE, t("Per-app mode", "حالت برنامه‌ها"),
                        listOf("bypass" to t("Selected apps bypass the VPN", "برنامه‌های انتخابی بدون VPN"), "proxy" to t("Only selected apps use the VPN", "فقط برنامه‌های انتخابی با VPN")),
                        vpn, { dialog = it }
                    )
                    ValueRow(t("Choose apps", "انتخاب برنامه‌ها"), t("${Prefs.splitApps.size} selected", "${Prefs.splitApps.size} مورد"), vpn, onPerApp)
                }
                BoolRow(K.SOCKS_UDP, t("SOCKS5 UDP", "SOCKS5 UDP"), t("SOCKS5 authentication is not fully secure when using UDP.", "احراز هویت SOCKS5 هنگام استفاده از UDP کاملاً امن نیست."), localProxyActive, null, changed)
                BoolRow(
                    K.HTTP_TO_VPN, t("Add HTTP proxy to VPN", "افزودن پروکسی HTTP به VPN"),
                    t("Allow browsers and supported apps to use the HTTP proxy directly instead of the virtual network interface (Android 10+).", "مرورگرها و برنامه‌های پشتیبانی‌شده مستقیماً از پروکسی HTTP استفاده کنند (اندروید ۱۰ به بالا)."),
                    vpn && Build.VERSION.SDK_INT >= 29 && AppSettings.bool(K.LOCAL_PROXY), null, changed
                )
                BoolRow(K.BYPASS_LAN, t("Bypass LAN in VPN mode", "عبور شبکه محلی بدون VPN"), t("Local network traffic does not go through the VPN", "ترافیک شبکه محلی از VPN عبور نمی‌کند"), vpn, null, changed)
                PickRow(
                    K.VPN_ADDRESS, t("VPN interface address", "آدرس رابط VPN"),
                    listOf("10.10.14" to "10.10.14.x", "10.1.10" to "10.1.10.x", "172.19.0" to "172.19.0.x", "192.168.0" to "192.168.0.x"),
                    vpn, { dialog = it }
                )
                TextRow(K.MTU, t("VPN MTU (default 1500)", "MTU ‏VPN (پیش‌فرض ۱۵۰۰)"), vpn, false, true, false) { dialog = it }
                BoolRow(K.BYPASS_IRAN, t("Iranian sites direct", "سایت‌های ایرانی مستقیم"), t("Iranian websites and apps connect without the VPN.", "سایت‌ها و برنامه‌های ایرانی بدون VPN باز می‌شوند."), true, null, changed)
            }

            // ---- DNS
            Section(t("DNS Settings", "تنظیمات DNS"), "dns" in open, { toggle("dns") }) {
                BoolRow(K.LOCAL_DNS, t("Enable local DNS", "فعال‌سازی DNS محلی"), t("DNS processed by the core's DNS module (recommended if you need routing that bypasses LAN and local addresses)", "DNS توسط ماژول DNS هسته پردازش می‌شود (برای مسیریابی با عبور آدرس‌های محلی توصیه می‌شود)"), true, null, changed)
                BoolRow(K.FAKE_DNS, t("Enable FakeDNS", "فعال‌سازی FakeDNS"), t("FakeDNS returns synthetic IP addresses for domain lookups (faster, but may not work with some apps).", "FakeDNS برای جستجوی دامنه، آی‌پی مجازی برمی‌گرداند (سریع‌تر، اما ممکن است با بعضی برنامه‌ها کار نکند)."), localDns, null, changed)
                TextRow(K.VPN_DNS, t("VPN DNS (IPv4/IPv6 only)", "DNS ‏VPN (فقط IPv4/IPv6)"), vpn, false, false, false) { dialog = it }
                TextRow(K.REMOTE_DNS, t("Remote DNS (UDP/TCP/HTTPS/QUIC, optional)", "DNS راه دور (UDP/TCP/HTTPS/QUIC، اختیاری)"), true, false, false, false) { dialog = it }
                TextRow(K.DIRECT_DNS, t("Direct DNS (optional)", "DNS مستقیم (اختیاری)"), true, false, false, false) { dialog = it }
                TextRow(K.DNS_HOSTS, t("DNS host mappings (format: domain:address,…)", "نگاشت هاست DNS (قالب: domain:address,…)"), true, false, false, true) { dialog = it }
                BoolRow(K.RESOLVE_OUTBOUND, t("Outbound domain resolution", "تبدیل دامنه سرور"), t("Resolve and add to DNS Hosts", "تبدیل به IP و افزودن به هاست‌های DNS"), true, null, changed)
            }

            // ---- Core
            Section(t("Core Settings", "تنظیمات هسته"), "core" in open, { toggle("core") }) {
                BoolRow(K.SNIFFING, t("Enable sniffing", "فعال‌سازی Sniffing"), t("Detect destination domains from traffic (enabled by default).", "دامنه مقصد از روی ترافیک تشخیص داده می‌شود (به‌طور پیش‌فرض فعال)."), true, null, changed)
                BoolRow(K.ROUTE_ONLY, t("Enable routeOnly", "فعال‌سازی routeOnly"), t("Use the sniffed domain name for routing only, and keep the target address as the IP address.", "دامنه تشخیص‌داده‌شده فقط برای مسیریابی استفاده شود و آدرس مقصد همان IP بماند."), sniffing, null, changed)
                BoolRow(K.DYNAMIC_PORT, t("Enable dynamic local proxy port", "پورت پویای پروکسی محلی"), t("Use a random local proxy port for each connection", "برای هر اتصال یک پورت تصادفی استفاده شود"), localProxyActive, null, changed)
                TextRow(K.PROXY_USER, t("Local proxy username (optional)", "نام کاربری پروکسی محلی (اختیاری)"), localProxyActive, false, false, false) { dialog = it }
                TextRow(K.PROXY_PASS, t("Local proxy password (optional)", "رمز پروکسی محلی (اختیاری)"), localProxyActive, true, false, false) { dialog = it }
                PickRow(K.LOG_LEVEL, t("Log level", "سطح لاگ"), listOf("debug" to "debug", "info" to "info", "warning" to "warning", "error" to "error", "none" to "none"), true, { dialog = it })
            }

            // ---- Mux
            Section(t("Mux Settings", "تنظیمات Mux"), "mux" in open, { toggle("mux") }) {
                BoolRow(K.MUX, t("Enable Mux", "فعال‌سازی Mux"), t("Several connections share one tunnel. Not used with XTLS flows.", "چند اتصال از یک تونل مشترک استفاده می‌کنند. با جریان‌های XTLS استفاده نمی‌شود."), true, null, changed)
                TextRow(K.MUX_CONCURRENCY, t("Mux concurrency", "تعداد همزمان Mux"), AppSettings.bool(K.MUX), false, true, false) { dialog = it }
                TextRow(K.XUDP_CONCURRENCY, t("XUDP concurrency", "تعداد همزمان XUDP"), AppSettings.bool(K.MUX), false, true, false) { dialog = it }
                PickRow(K.XUDP_QUIC, t("XUDP QUIC (UDP 443)", "XUDP QUIC (‏UDP 443)"), listOf("reject" to "reject", "allow" to "allow", "skip" to "skip"), AppSettings.bool(K.MUX), { dialog = it })
            }

            // ---- Fragment
            Section(t("Fragment Settings", "تنظیمات Fragment"), "frag" in open, { toggle("frag") }) {
                BoolRow(K.FRAGMENT, t("Enable fragment", "فعال‌سازی Fragment"), t("Splits the TLS handshake into small pieces; it can help against filtering.", "دست‌دهی TLS به تکه‌های کوچک تقسیم می‌شود؛ ممکن است در برابر فیلترینگ کمک کند."), true, null, changed)
                TextRow(K.FRAG_PACKETS, t("Packets", "بسته‌ها"), AppSettings.bool(K.FRAGMENT), false, false, false) { dialog = it }
                TextRow(K.FRAG_LENGTH, t("Length", "طول"), AppSettings.bool(K.FRAGMENT), false, false, false) { dialog = it }
                TextRow(K.FRAG_INTERVAL, t("Interval (ms)", "فاصله (میلی‌ثانیه)"), AppSettings.bool(K.FRAGMENT), false, false, false) { dialog = it }
            }

            // ---- Observatory
            Section(t("Observatory Settings", "تنظیمات Observatory"), "obs" in open, { toggle("obs") }) {
                BoolRow(K.OBSERVATORY, t("Enable observatory", "فعال‌سازی Observatory"), t("Periodically probes the proxy outbound.", "به‌صورت دوره‌ای خروجی پروکسی را آزمایش می‌کند."), true, null, changed)
                TextRow(K.OBS_URL, t("Probe URL", "آدرس آزمایش"), AppSettings.bool(K.OBSERVATORY), false, false, false) { dialog = it }
                TextRow(K.OBS_INTERVAL, t("Probe interval (e.g. 1m)", "فاصله آزمایش (مثلاً 1m)"), AppSettings.bool(K.OBSERVATORY), false, false, false) { dialog = it }
            }

            // ---- Advanced
            Section(t("Advanced Settings", "تنظیمات پیشرفته"), "adv" in open, { toggle("adv") }) {
                BoolRow(K.BOOT, t("Connect after device boot", "اتصال پس از روشن شدن دستگاه"), t("Automatically connect to the selected server after the device starts. Android may restrict background starts.", "پس از روشن شدن دستگاه، خودکار به سرور انتخاب‌شده وصل می‌شود. ممکن است اندروید اجرای پس‌زمینه را محدود کند."), true, null, changed)
                ValueRow(
                    t("System VPN settings", "تنظیمات VPN سیستم"),
                    t("Open Android VPN settings to configure Always-on VPN, if supported by this device.", "تنظیمات VPN اندروید را برای فعال‌سازی Always-on VPN باز می‌کند (اگر دستگاه پشتیبانی کند)."),
                    true
                ) { runCatching { ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) } }
                TextRow(K.PROBE_URL, t("Real delay test URL", "آدرس تست تأخیر واقعی"), true, false, false, false) { dialog = it }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---- dialogs
    when (val d = dialog) {
        is EditDlg -> {
            var text by remember(d) { mutableStateOf(AppSettings.str(d.key)) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                containerColor = NovaColors.Surface, titleContentColor = NovaColors.Text, textContentColor = NovaColors.Text,
                title = { Text(d.title, fontSize = 16.sp) },
                text = {
                    NovaTextField(
                        text, { text = it }, "", singleLine = !d.multiline,
                        keyboardType = if (d.numeric) KeyboardType.Number else KeyboardType.Text
                    )
                },
                confirmButton = {
                    TextButton(onClick = { AppSettings.putStr(d.key, text.trim()); dialog = null; rev++ }) {
                        Text(t("Save", "ذخیره"), color = NovaColors.Lime)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = null }) { Text(t("Cancel", "انصراف"), color = NovaColors.Dim) }
                },
            )
        }
        is PickDlg -> {
            val current = AppSettings.str(d.key)
            AlertDialog(
                onDismissRequest = { dialog = null },
                containerColor = NovaColors.Surface, titleContentColor = NovaColors.Text, textContentColor = NovaColors.Text,
                title = { Text(d.title, fontSize = 16.sp) },
                text = {
                    Column {
                        d.options.forEach { (value, label) ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                    AppSettings.putStr(d.key, value); dialog = null; rev++
                                }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = value == current, onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = NovaColors.Lime, unselectedColor = NovaColors.Dim)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(label, fontSize = 15.sp)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(t("Cancel", "انصراف"), color = NovaColors.Dim) } },
            )
        }
        null -> {}
    }
    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            containerColor = NovaColors.Surface, titleContentColor = NovaColors.Text, textContentColor = NovaColors.Dim,
            title = { Text(t("Tunnel mode", "حالت تونل")) },
            text = {
                Text(
                    t(
                        "VPN: all apps (or the apps you choose) go through the tunnel.\n\nProxy: no VPN permission is needed. Only apps or devices that you point at the local SOCKS5/HTTP proxy use it.",
                        "VPN: همه برنامه‌ها (یا برنامه‌هایی که انتخاب کنید) از تونل عبور می‌کنند.\n\nProxy: به مجوز VPN نیازی نیست. فقط برنامه‌ها یا دستگاه‌هایی که به پروکسی محلی SOCKS5/HTTP وصل شوند از آن استفاده می‌کنند."
                    )
                )
            },
            confirmButton = { TextButton(onClick = { help = false }) { Text("OK", color = NovaColors.Lime) } },
        )
    }
}

// ------------------------------------------------------------------ building blocks

@Composable
private fun Section(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, Modifier.weight(1f), color = NovaColors.Lime, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = NovaColors.Lime)
        }
        AnimatedVisibility(visible = expanded) { Column(Modifier.fillMaxWidth(), content = content) }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun SwitchRow(title: String, desc: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (desc != null) Text(desc, color = NovaColors.Dim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NovaColors.Bg, checkedTrackColor = NovaColors.Lime,
                uncheckedThumbColor = NovaColors.Dim, uncheckedTrackColor = NovaColors.Surface2,
                uncheckedBorderColor = NovaColors.Border,
                disabledCheckedThumbColor = NovaColors.Bg, disabledCheckedTrackColor = NovaColors.Lime.copy(alpha = 0.5f),
            )
        )
    }
}

@Composable
private fun BoolRow(key: String, title: String, desc: String?, enabled: Boolean, forced: Boolean?, onChanged: () -> Unit) {
    SwitchRow(title, desc, forced ?: AppSettings.bool(key), enabled) { AppSettings.putBool(key, it); onChanged() }
}

@Composable
private fun ValueRow(title: String, value: String?, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp)
    ) {
        Text(title, fontSize = 15.sp)
        if (!value.isNullOrEmpty()) Text(value, color = NovaColors.Dim, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3)
    }
}

@Composable
private fun TextRow(
    key: String, title: String, enabled: Boolean, mask: Boolean, numeric: Boolean, multiline: Boolean,
    onEdit: (EditDlg) -> Unit,
) {
    val v = AppSettings.str(key)
    ValueRow(title, if (v.isBlank()) "—" else if (mask) "••••••" else v, enabled) { onEdit(EditDlg(key, title, numeric, multiline)) }
}

@Composable
private fun PickRow(key: String, title: String, options: List<Pair<String, String>>, enabled: Boolean, onPick: (PickDlg) -> Unit) {
    val current = AppSettings.str(key)
    ValueRow(title, options.firstOrNull { it.first == current }?.second ?: current, enabled) { onPick(PickDlg(key, title, options)) }
}

@Composable
private fun LangChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) NovaColors.Bg else NovaColors.Text,
        fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) NovaColors.Lime else NovaColors.Surface2)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
