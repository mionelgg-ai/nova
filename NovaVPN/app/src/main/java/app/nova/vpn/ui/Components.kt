package app.nova.vpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.vpn.R
import app.nova.vpn.core.VpnStatus
import app.nova.vpn.data.flagEmoji

@Composable
fun NovaCard(
    modifier: Modifier = Modifier,
    borderColor: Color = NovaColors.Border,
    shape: Shape = RoundedCornerShape(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var m = modifier.clip(shape).background(NovaColors.Surface).border(1.dp, borderColor, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m, content = content)
}

@Composable
fun CircleIconButton(icon: ImageVector, onClick: () -> Unit, tint: Color = NovaColors.Text) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(NovaColors.Surface)
            .border(1.dp, NovaColors.Border, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
fun LimeButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.height(54.dp).clip(RoundedCornerShape(27.dp))
            .background(if (enabled) NovaColors.Lime else NovaColors.Surface2)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) NovaColors.Bg else NovaColors.Dim, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun LogoMark(size: Dp) {
    Image(painterResource(R.drawable.ic_logo_mark), null, Modifier.height(size).aspectRatio(40f / 50f))
}

@Composable
fun FlagBadge(cc: String, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(NovaColors.Surface2),
        contentAlignment = Alignment.Center
    ) { Text(flagEmoji(cc), fontSize = (size.value * 0.52f).sp) }
}

/** 3-bar signal indicator derived from the (cosmetic) ping. */
@Composable
fun SignalBars(ping: Int) {
    val level = when { ping < 110 -> 3; ping < 160 -> 2; else -> 1 }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(8.dp, 11.dp, 14.dp).forEachIndexed { i, h ->
            Box(
                Modifier.width(4.dp).height(h).clip(RoundedCornerShape(2.dp))
                    .background(if (i < level) NovaColors.Lime else NovaColors.Border)
            )
        }
    }
}

@Composable
fun PowerButton(status: VpnStatus, onClick: () -> Unit) {
    val connected = status is VpnStatus.Connected
    val connecting = status is VpnStatus.Connecting
    val fill by animateColorAsState(if (connected) NovaColors.Lime else NovaColors.Surface2, tween(400), label = "fill")
    val icon by animateColorAsState(if (connected) NovaColors.Bg else NovaColors.Lime, tween(400), label = "icon")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(if (connecting) 650 else 2400, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val ring = when { connected -> 0.30f; connecting -> 0.45f; else -> 0.12f }
    val s = if (connected || connecting) pulse else 1f

    Box(Modifier.size(244.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(244.dp).scale(s).border(1.dp, NovaColors.Lime.copy(alpha = ring), CircleShape))
        Box(Modifier.size(198.dp).scale(s).background(NovaColors.Lime.copy(alpha = ring / 2.5f), CircleShape))
        Box(
            Modifier.size(150.dp).clip(CircleShape).background(fill).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.PowerSettingsNew, null, tint = icon, modifier = Modifier.size(62.dp)) }
    }
}

@Composable
fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Icons.Rounded.Home to R.string.nav_home,
        Icons.Rounded.Explore to R.string.nav_servers,
        Icons.Rounded.BarChart to R.string.nav_stats,
        Icons.Rounded.Person to R.string.nav_profile,
    )
    val shape = RoundedCornerShape(30.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(shape).background(NovaColors.Surface).border(1.dp, NovaColors.Border, shape).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, (icon, label) ->
            val sel = i == selected
            Row(
                Modifier.clip(RoundedCornerShape(24.dp))
                    .background(if (sel) NovaColors.Lime else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = if (sel) 18.dp else 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = if (sel) NovaColors.Bg else NovaColors.Dim, modifier = Modifier.size(22.dp))
                if (sel) {
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(label), color = NovaColors.Bg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
}

fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

/** Small dismissible message card used on the home screen. */
@Composable
fun InfoBanner(
    text: String,
    icon: ImageVector,
    accent: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(NovaColors.Surface)
            .border(1.dp, accent.copy(alpha = 0.45f), shape).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, Modifier.weight(1f), fontSize = 13.sp, maxLines = 2)
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
        if (onClose != null) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Close, null, tint = NovaColors.Dim, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
fun SubHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack)
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}
