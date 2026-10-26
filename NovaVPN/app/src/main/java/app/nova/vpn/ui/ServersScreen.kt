package app.nova.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.vpn.MainViewModel
import app.nova.vpn.R
import app.nova.vpn.data.Server

@Composable
fun ServersScreen(
    vm: MainViewModel,
    onPicked: () -> Unit,
    onPremiumNeeded: () -> Unit,
    onBonusNeeded: () -> Unit,
    onMaintenance: () -> Unit,
) {
    val config by vm.config.collectAsState()
    val selected by vm.selected.collectAsState()
    val pings by vm.pings.collectAsState()
    val premium by vm.premium.collectAsState()
    val bonusLeft by vm.bonusLeftMs.collectAsState()
    val fa = LocalConfiguration.current.locales[0].language == "fa"
    var query by remember { mutableStateOf("") }

    val all = config?.servers.orEmpty().filter {
        query.isBlank() || it.name.contains(query, true) || it.nameFa.contains(query) || it.city.contains(query, true)
    }
    val free = all.filter { !it.premium && !it.bonus }
    val bonus = all.filter { !it.premium && it.bonus }
    val paid = all.filter { it.premium }
    val bonusActive = bonusLeft > 0

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 12.dp)) {
        Text(stringResource(R.string.servers_title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth().height(50.dp).background(NovaColors.Surface, RoundedCornerShape(25.dp))
                .border(1.dp, NovaColors.Border, RoundedCornerShape(25.dp)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = NovaColors.Dim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(stringResource(R.string.search_hint), color = NovaColors.Dim, fontSize = 14.sp)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = NovaColors.Text, fontSize = 14.sp),
                    cursorBrush = SolidColor(NovaColors.Lime), modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (all.isEmpty()) item {
                Text(stringResource(R.string.no_results), color = NovaColors.Dim, modifier = Modifier.padding(top = 24.dp))
            }

            if (free.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.section_free)) }
                items(free, key = { it.id }) { s ->
                    ServerRow(s, fa, pings[s.id] ?: 0, s.id == selected?.id, locked = false) {
                        if (s.inMaintenance) onMaintenance() else { vm.select(s); onPicked() }
                    }
                }
            }

            if (bonus.isNotEmpty()) {
                item {
                    SectionTitle(
                        if (bonusActive || premium.active) {
                            if (premium.active) stringResource(R.string.section_bonus)
                            else stringResource(R.string.bonus_active, formatLeft(bonusLeft))
                        } else stringResource(R.string.section_bonus) + " · " + stringResource(R.string.bonus_locked_hint),
                        accent = if (bonusActive) NovaColors.Lime else NovaColors.Dim
                    )
                }
                items(bonus, key = { it.id }) { s ->
                    val open = premium.active || bonusActive
                    ServerRow(s, fa, pings[s.id] ?: 0, s.id == selected?.id, locked = !open) {
                        when {
                            !open -> onBonusNeeded()
                            s.inMaintenance -> onMaintenance()
                            else -> { vm.select(s); onPicked() }
                        }
                    }
                }
            }

            if (paid.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.section_premium)) }
                items(paid, key = { it.id }) { s ->
                    ServerRow(s, fa, pings[s.id] ?: 0, s.id == selected?.id, locked = !premium.active) {
                        when {
                            !premium.active -> onPremiumNeeded()
                            s.inMaintenance -> onMaintenance()
                            else -> { vm.select(s); onPicked() }
                        }
                    }
                }
            }
        }
    }
}

private fun formatLeft(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun SectionTitle(text: String, accent: Color = NovaColors.Dim) {
    Text(text, color = accent, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

@Composable
private fun ServerRow(s: Server, fa: Boolean, ping: Int, isSelected: Boolean, locked: Boolean, onClick: () -> Unit) {
    NovaCard(
        Modifier.fillMaxWidth().alpha(if (s.inMaintenance) 0.55f else 1f),
        borderColor = if (isSelected) NovaColors.Lime else NovaColors.Border,
        onClick = onClick
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            FlagBadge(s.cc, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.displayName(fa), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    TagBadge(s.tag)
                }
                Text(s.city, color = NovaColors.Dim, fontSize = 12.sp, maxLines = 1)
            }
            SignalBars(ping)
            Spacer(Modifier.width(8.dp))
            Text("$ping ms", color = NovaColors.Dim, fontSize = 12.sp)
            Spacer(Modifier.width(12.dp))
            when {
                locked -> Icon(Icons.Rounded.Lock, null, tint = NovaColors.Gold, modifier = Modifier.size(18.dp))
                s.premium -> Icon(Icons.Rounded.WorkspacePremium, null, tint = NovaColors.Gold, modifier = Modifier.size(18.dp))
                s.bonus -> Icon(Icons.Rounded.CardGiftcard, null, tint = NovaColors.Lime, modifier = Modifier.size(18.dp))
                else -> Spacer(Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(10.dp).background(if (isSelected) NovaColors.Lime else NovaColors.Border, CircleShape))
        }
    }
}

@Composable
private fun TagBadge(tag: String) {
    val (label, color) = when (tag) {
        "new" -> R.string.badge_new to NovaColors.Lime
        "busy" -> R.string.badge_busy to NovaColors.Gold
        "maintenance" -> R.string.badge_maint to NovaColors.Danger
        else -> return
    }
    Text(
        stringResource(label), color = NovaColors.Bg, fontSize = 9.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
