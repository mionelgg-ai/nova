package app.nova.vpn.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import app.nova.vpn.R
import app.nova.vpn.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AppItem(val pkg: String, val label: String, val icon: ImageBitmap?)

@Composable
fun SplitTunnelScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    var selected by remember { mutableStateOf(Prefs.splitApps) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcher, 0)
                .filter { it.activityInfo.packageName != ctx.packageName }
                .distinctBy { it.activityInfo.packageName }
                .map {
                    AppItem(
                        it.activityInfo.packageName,
                        it.loadLabel(pm).toString(),
                        runCatching { it.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        SubHeader(stringResource(R.string.split_title), onBack)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.split_desc), color = NovaColors.Dim, fontSize = 13.sp)
        Text(stringResource(R.string.split_note), color = NovaColors.Gold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth().height(48.dp).background(NovaColors.Surface, RoundedCornerShape(24.dp))
                .border(1.dp, NovaColors.Border, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = NovaColors.Dim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(stringResource(R.string.split_search), color = NovaColors.Dim, fontSize = 14.sp)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = NovaColors.Text, fontSize = 14.sp),
                    cursorBrush = SolidColor(NovaColors.Lime), modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        val list = apps
        if (list == null) {
            Text("…", color = NovaColors.Dim, modifier = Modifier.padding(top = 24.dp))
        } else {
            val shown = list.filter { query.isBlank() || it.label.contains(query, true) }
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shown, key = { it.pkg }) { app ->
                    val on = app.pkg in selected
                    fun toggle(v: Boolean) {
                        selected = if (v) selected + app.pkg else selected - app.pkg
                        Prefs.splitApps = selected
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NovaColors.Surface)
                            .clickable { toggle(!on) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (app.icon != null) {
                            Image(app.icon, null, Modifier.size(40.dp))
                        } else Spacer(Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1)
                        Switch(
                            checked = on, onCheckedChange = { toggle(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NovaColors.Bg, checkedTrackColor = NovaColors.Lime,
                                uncheckedThumbColor = NovaColors.Dim, uncheckedTrackColor = NovaColors.Surface2,
                                uncheckedBorderColor = NovaColors.Border
                            )
                        )
                    }
                }
            }
        }
    }
}
