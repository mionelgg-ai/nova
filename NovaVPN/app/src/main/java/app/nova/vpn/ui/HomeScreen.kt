package app.nova.vpn.ui

import android.net.TrafficStats
import android.os.Process
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.vpn.MainViewModel
import app.nova.vpn.R
import app.nova.vpn.core.VpnStatus
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.collectAsState

@Composable
fun HomeScreen(
    vm: MainViewModel,
    status: VpnStatus,
    onPower: () -> Unit,
    onOpenServers: () -> Unit,
    onPremium: () -> Unit,
    onTelegram: () -> Unit,
) {
    val selected by vm.selected.collectAsState()
    val pings by vm.pings.collectAsState()
    val premium by vm.premium.collectAsState()
    val fa = LocalConfiguration.current.locales[0].language == "fa"
    val connected = status is VpnStatus.Connected

    // session timer
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(status) {
        val c = status as? VpnStatus.Connected
        if (c == null) { elapsed = 0; return@LaunchedEffect }
        while (true) {
            elapsed = (System.currentTimeMillis() - c.since) / 1000
            delay(500)
        }
    }

    // real throughput of the tunnel = this app's own network traffic (Xray's outbound sockets)
    var down by remember { mutableDoubleStateOf(0.0) }
    var up by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(connected) {
        if (!connected) { down = 0.0; up = 0.0; return@LaunchedEffect }
        val uid = Process.myUid()
        var lastRx = TrafficStats.getUidRxBytes(uid)
        var lastTx = TrafficStats.getUidTxBytes(uid)
        while (true) {
            delay(1000)
            val rx = TrafficStats.getUidRxBytes(uid)
            val tx = TrafficStats.getUidTxBytes(uid)
            if (rx >= 0 && lastRx >= 0) down = (rx - lastRx) * 8 / 1_000_000.0
            if (tx >= 0 && lastTx >= 0) up = (tx - lastTx) * 8 / 1_000_000.0
            lastRx = rx; lastTx = tx
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 12.dp)) {
        // top bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Rounded.Send, onTelegram)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(NovaColors.Surface)
                    .border(1.dp, NovaColors.Border, RoundedCornerShape(22.dp))
                    .clickable(onClick = onPremium).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.WorkspacePremium, null, tint = NovaColors.Gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (premium.active) R.string.pro else R.string.premium),
                    fontWeight = FontWeight.Medium, fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // server card
        NovaCard(
            Modifier.fillMaxWidth(),
            borderColor = NovaColors.Lime.copy(alpha = 0.45f),
            shape = RoundedCornerShape(32.dp),
            onClick = onOpenServers
        ) {
            val s = selected
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (s != null) {
                    FlagBadge(s.cc, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.displayName(fa), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
                        Text(s.city, color = NovaColors.Dim, fontSize = 13.sp, maxLines = 1)
                    }
                    SignalBars(pings[s.id] ?: 120)
                    Spacer(Modifier.width(8.dp))
                    Text("${pings[s.id] ?: "—"} ${stringResource(R.string.unit_ms)}", color = NovaColors.Dim, fontSize = 12.sp)
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = NovaColors.Dim)
            }
        }

        Spacer(Modifier.height(26.dp))

        // status
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val (label, color) = when (status) {
                is VpnStatus.Connected -> R.string.status_connected to NovaColors.Lime
                VpnStatus.Connecting -> R.string.status_connecting to NovaColors.Gold
                VpnStatus.Disconnected -> R.string.status_disconnected to NovaColors.Dim
            }
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).background(NovaColors.Surface)
                    .border(1.dp, NovaColors.Border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (connected) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = color, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(stringResource(label), color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            formatClock(elapsed),
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 50.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 1.sp,
            color = if (connected) NovaColors.Text else NovaColors.Dim
        )

        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PowerButton(status, onPower) }
        Spacer(Modifier.weight(1f))

        // speeds
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            SpeedItem(Modifier.weight(1f), Icons.Rounded.ArrowDownward, NovaColors.Lime, R.string.download, down)
            SpeedItem(Modifier.weight(1f), Icons.Rounded.ArrowUpward, NovaColors.Gold, R.string.upload, up)
        }
    }
}

@Composable
private fun SpeedItem(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector,
                      tint: androidx.compose.ui.graphics.Color, label: Int, mbps: Double) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(stringResource(label), color = tint, fontSize = 12.sp)
            Text(
                "%.2f %s".format(mbps, stringResource(R.string.unit_mbps)),
                fontSize = 16.sp, fontWeight = FontWeight.Medium
            )
        }
    }
}
