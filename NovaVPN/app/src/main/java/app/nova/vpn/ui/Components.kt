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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
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

/**
 * Bottom navigation. Every item owns a full quarter of the bar (icon + all the space around it is tappable).
 * The lime pill is drawn as one shape whose two edges move with different springs: the leading edge shoots
 * ahead and the trailing edge lags and wobbles, so it stretches and snaps back like a water drop.
 */
@Composable
fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Icons.Rounded.Home to R.string.nav_home,
        Icons.Rounded.Explore to R.string.nav_servers,
        Icons.Rounded.BarChart to R.string.nav_stats,
        Icons.Rounded.Person to R.string.nav_profile,
    )
    val shape = RoundedCornerShape(33.dp)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current

    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    var barW by remember { mutableIntStateOf(0) }
    var placed by remember { mutableStateOf(false) }

    LaunchedEffect(selected, barW, rtl) {
        if (barW == 0) return@LaunchedEffect
        val cell = barW / items.size.toFloat()
        val extra = with(density) { 12.dp.toPx() }
        val pad = with(density) { 5.dp.toPx() }
        val idx = if (rtl) items.size - 1 - selected else selected
        val tl = maxOf(idx * cell - extra, pad)
        val tr = minOf((idx + 1) * cell + extra, barW - pad)
        if (!placed) {
            left.snapTo(tl); right.snapTo(tr); placed = true
            return@LaunchedEffect
        }
        val movingRight = tl > left.value
        val fast = spring<Float>(dampingRatio = 0.62f, stiffness = 650f)
        val slow = spring<Float>(dampingRatio = 0.50f, stiffness = 150f)
        launch { left.animateTo(tl, if (movingRight) slow else fast) }
        launch { right.animateTo(tr, if (movingRight) fast else slow) }
    }

    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).height(66.dp)
            .onSizeChanged { barW = it.width }
            .clip(shape).background(NovaColors.Surface).border(1.dp, NovaColors.Border, shape)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val padY = 5.dp.toPx()
            val baseH = size.height - 2 * padY
            val w = (right.value - left.value).coerceAtLeast(1f)
            val baseW = size.width / items.size + 24.dp.toPx()
            val stretch = (w / baseW - 1f).coerceIn(0f, 1f)
            val h = baseH * (1f - 0.12f * stretch)          // a stretched drop is thinner
            val top = (size.height - h) / 2f
            // soft glow + the pill itself
            drawRoundRect(
                NovaColors.Lime.copy(alpha = 0.16f),
                topLeft = Offset(left.value - 5.dp.toPx(), top - 5.dp.toPx()),
                size = Size(w + 10.dp.toPx(), h + 10.dp.toPx()),
                cornerRadius = CornerRadius((h + 10.dp.toPx()) / 2f)
            )
            drawRoundRect(
                NovaColors.Lime, topLeft = Offset(left.value, top), size = Size(w, h),
                cornerRadius = CornerRadius(h / 2f)
            )
        }
        Row(Modifier.matchParentSize()) {
            items.forEachIndexed { i, (icon, label) ->
                val sel = i == selected
                val tint by animateColorAsState(if (sel) NovaColors.Bg else NovaColors.Dim, tween(250), label = "tint")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(Modifier.wrapContentWidth(unbounded = true), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                        AnimatedVisibility(
                            visible = sel,
                            enter = fadeIn(tween(220, delayMillis = 120)) + expandHorizontally(tween(300)),
                            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(220)),
                        ) {
                            Text(
                                stringResource(label), Modifier.padding(start = 6.dp), color = NovaColors.Bg,
                                fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, softWrap = false
                            )
                        }
                    }
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
