package app.nova.vpn.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.vpn.BuildConfig
import app.nova.vpn.MainViewModel
import app.nova.vpn.R
import app.nova.vpn.data.Avatar
import app.nova.vpn.data.Jalali
import app.nova.vpn.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    vm: MainViewModel,
    onTelegram: () -> Unit,
    onShare: () -> Unit,
    onPrivacy: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val premium by vm.premium.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val lang = LocalConfiguration.current.locales[0].language
    var avatarVersion by remember { mutableIntStateOf(Prefs.avatarVersion) }
    val canPickPhoto = Prefs.authMethod == "email"

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { Avatar.save(ctx, uri) }
            if (ok) { Prefs.avatarVersion = Prefs.avatarVersion + 1; avatarVersion = Prefs.avatarVersion }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(44.dp))
            Text(
                stringResource(R.string.profile_title), Modifier.weight(1f), textAlign = TextAlign.Center,
                fontSize = 22.sp, fontWeight = FontWeight.SemiBold
            )
            CircleIconButton(Icons.Rounded.Settings, onSettings)
        }
        Spacer(Modifier.height(20.dp))

        Box(contentAlignment = Alignment.BottomCenter) {
            AvatarView(
                size = 96.dp, version = avatarVersion, ring = if (premium.active) NovaColors.Lime else NovaColors.Border,
                onClick = if (canPickPhoto) ({ picker.launch("image/*") }) else null
            )
            if (premium.active) {
                Text(
                    stringResource(R.string.pro), color = NovaColors.Bg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = 8.dp).background(NovaColors.Lime, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(Prefs.userName.ifBlank { "—" }, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(Prefs.userEmail, color = NovaColors.Dim, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (premium.active) R.string.plan_pro else R.string.plan_free),
            color = if (premium.active) NovaColors.Lime else NovaColors.Dim, fontSize = 13.sp, fontWeight = FontWeight.Medium
        )
        if (premium.active) {
            Text(
                premium.expires?.let { stringResource(R.string.plan_expires, Jalali.display(it, lang == "fa")) } ?: stringResource(R.string.plan_no_expiry),
                color = NovaColors.Dim, fontSize = 12.sp
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

        ActionRow(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.join_channel), onClick = onTelegram)
        Spacer(Modifier.height(12.dp))
        ActionRow(Icons.Rounded.Share, stringResource(R.string.share_app), onClick = onShare)
        Spacer(Modifier.height(12.dp))
        val okMsg = stringResource(R.string.refreshed)
        val failMsg = stringResource(R.string.refresh_failed)
        ActionRow(Icons.Rounded.Refresh, stringResource(R.string.refresh_servers), spinning = refreshing) {
            vm.refresh { ok -> Toast.makeText(ctx, if (ok) okMsg else failMsg, Toast.LENGTH_SHORT).show() }
        }
        Spacer(Modifier.height(12.dp))
        ActionRow(Icons.Rounded.PrivacyTip, stringResource(R.string.privacy_title), onClick = onPrivacy)
        Spacer(Modifier.height(12.dp))
        ActionRow(Icons.AutoMirrored.Rounded.Logout, tr("Sign out", "خروج از حساب"), onClick = onSignOut)

        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.version_fmt, BuildConfig.VERSION_NAME), color = NovaColors.Dim, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
    }
}

/** Picked photo if there is one, otherwise a Google-style initial or a plain placeholder. */
@Composable
fun AvatarView(size: Dp, version: Int, ring: androidx.compose.ui.graphics.Color, onClick: (() -> Unit)?) {
    val ctx = LocalContext.current
    val photo = remember(version) { Avatar.load(ctx) }
    val google = Prefs.authMethod == "google"
    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            Modifier.size(size).clip(CircleShape).background(NovaColors.Surface2)
                .border(2.dp, ring, CircleShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            when {
                photo != null -> Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                google -> Text(
                    Prefs.userName.take(1).uppercase().ifBlank { "G" }, color = NovaColors.Lime,
                    fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold
                )
                else -> Icon(Icons.Rounded.Person, null, tint = NovaColors.Dim, modifier = Modifier.size(size * 0.55f))
            }
        }
        if (onClick != null) {
            Box(
                Modifier.size(size * 0.3f).clip(CircleShape).background(NovaColors.Lime),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.CameraAlt, null, tint = NovaColors.Bg, modifier = Modifier.size(size * 0.17f)) }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, spinning: Boolean = false, detail: String? = null, onClick: () -> Unit) {
    NovaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = NovaColors.Lime, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f), fontSize = 15.sp)
            if (spinning) Text("…", color = NovaColors.Dim)
            if (detail != null) Text(detail, color = NovaColors.Dim, fontSize = 12.sp)
        }
    }
}
