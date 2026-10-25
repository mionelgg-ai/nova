package app.nova.vpn.ui

import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import app.nova.vpn.BuildConfig
import app.nova.vpn.MainViewModel
import app.nova.vpn.R

@Composable
fun ProfileScreen(vm: MainViewModel, onTelegram: () -> Unit) {
    val ctx = LocalContext.current
    val premium by vm.premium.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val lang = LocalConfiguration.current.locales[0].language

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.profile_title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(20.dp))

        Box(contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(NovaColors.Surface)
                    .border(2.dp, if (premium.active) NovaColors.Lime else NovaColors.Border, CircleShape),
                contentAlignment = Alignment.Center
            ) { LogoMark(44.dp) }
            if (premium.active) {
                Text(
                    stringResource(R.string.pro), color = NovaColors.Bg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = 8.dp).background(NovaColors.Lime, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(if (premium.active) R.string.plan_pro else R.string.plan_free),
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold
        )
        if (premium.active) {
            Text(
                premium.expires?.let { stringResource(R.string.plan_expires, it) } ?: stringResource(R.string.plan_no_expiry),
                color = NovaColors.Dim, fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        if (!premium.active) {
            NovaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.code_title), fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    ActivateForm(vm)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // language
        NovaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Language, null, tint = NovaColors.Lime, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.language), Modifier.weight(1f), fontSize = 15.sp)
                LangChip("فارسی", lang == "fa") {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fa"))
                }
                Spacer(Modifier.width(8.dp))
                LangChip("English", lang != "fa") {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        ActionRow(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.join_channel), onTelegram)
        Spacer(Modifier.height(12.dp))
        val okMsg = stringResource(R.string.refreshed)
        val failMsg = stringResource(R.string.refresh_failed)
        ActionRow(Icons.Rounded.Refresh, stringResource(R.string.refresh_servers), spinning = refreshing) {
            vm.refresh { ok -> Toast.makeText(ctx, if (ok) okMsg else failMsg, Toast.LENGTH_SHORT).show() }
        }

        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.version_fmt, BuildConfig.VERSION_NAME), color = NovaColors.Dim, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
    }
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

@Composable
private fun ActionRow(icon: ImageVector, label: String, spinning: Boolean = false, onClick: () -> Unit) {
    NovaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = NovaColors.Lime, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f), fontSize = 15.sp)
            if (spinning) Text("…", color = NovaColors.Dim)
        }
    }
}
