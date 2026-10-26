package app.nova.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.vpn.R
import app.nova.vpn.core.VpnState
import app.nova.vpn.core.VpnStatus
import app.nova.vpn.data.Prefs
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as DayStyle

@Composable
fun StatsScreen(status: VpnStatus) {
    val locale = LocalConfiguration.current.locales[0]
    val fa = locale.language == "fa"
    var daily by remember { mutableStateOf(Prefs.dailySeconds()) }
    var traffic by remember { mutableStateOf(Prefs.dailyTraffic()) }
    LaunchedEffect(Unit) {
        while (true) { daily = Prefs.dailySeconds(); traffic = Prefs.dailyTraffic(); delay(5000) }
    }

    val today = LocalDate.now()
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val secs = week.map { daily[it.toString()] ?: 0L }
    val maxSecs = (secs.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val (streak, best) = remember(daily) { streaks(daily) }
    val usedToday = traffic[today.toString()] ?: 0L
    val usedWeek = week.sumOf { traffic[it.toString()] ?: 0L }
    val protocol by VpnState.protocol.collectAsState()

    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(status) {
        val c = status as? VpnStatus.Connected
        if (c == null) { elapsed = 0; return@LaunchedEffect }
        while (true) { elapsed = (System.currentTimeMillis() - c.since) / 1000; delay(1000) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp)) {
        Text(stringResource(R.string.stats_title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))

        NovaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text(stringResource(R.string.stats_week), color = NovaColors.Dim, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().height(150.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    week.forEachIndexed { i, d ->
                        val frac = secs[i].toFloat() / maxSecs
                        val isToday = i == 6
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier.width(22.dp).height((8 + 100 * frac).dp)
                                    .background(
                                        if (isToday) NovaColors.Lime else NovaColors.Lime.copy(alpha = 0.28f),
                                        RoundedCornerShape(11.dp)
                                    )
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (fa) persianDay(d.dayOfWeek) else d.dayOfWeek.getDisplayName(DayStyle.SHORT, locale),
                                color = if (isToday) NovaColors.Text else NovaColors.Dim, fontSize = 11.sp, maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(1f), R.string.stat_protocol, protocol.ifBlank { "—" })
            Tile(Modifier.weight(1f), R.string.stat_session, if (elapsed > 0) formatClock(elapsed) else "—")
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(1f), R.string.stat_streak, stringResource(R.string.days_fmt, streak))
            Tile(Modifier.weight(1f), R.string.stat_best, stringResource(R.string.days_fmt, best))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(1f), R.string.stat_today, formatBytes(usedToday))
            Tile(Modifier.weight(1f), R.string.stat_week_data, formatBytes(usedWeek))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Tile(modifier: Modifier, label: Int, value: String) {
    NovaCard(modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(label), color = NovaColors.Dim, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

private fun streaks(daily: Map<String, Long>): Pair<Int, Int> {
    val days = daily.filter { it.value > 0 }.keys
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSortedSet()
    var best = 0; var run = 0; var prev: LocalDate? = null
    for (d in days) {
        run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
        best = maxOf(best, run); prev = d
    }
    var cur = 0
    var d = LocalDate.now()
    if (d !in days) d = d.minusDays(1)
    while (d in days) { cur++; d = d.minusDays(1) }
    return cur to best
}

private fun persianDay(d: DayOfWeek) = when (d) {
    DayOfWeek.SATURDAY -> "ش"
    DayOfWeek.SUNDAY -> "ی"
    DayOfWeek.MONDAY -> "د"
    DayOfWeek.TUESDAY -> "س"
    DayOfWeek.WEDNESDAY -> "چ"
    DayOfWeek.THURSDAY -> "پ"
    DayOfWeek.FRIDAY -> "ج"
}

private fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%d KB".format(b shr 10)
    else -> "0 KB"
}
