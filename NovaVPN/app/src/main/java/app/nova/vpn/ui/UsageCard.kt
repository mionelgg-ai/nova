package app.nova.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.vpn.R

/** PLACEHOLDER numbers. They will come from the server once accounts exist. */
object DemoAccount {
    const val TOTAL_GB = 100.0
    const val REMAINING_GB = 36.4
    const val DAYS_LEFT = 21
    const val ONLINE = 2
    const val OFFLINE = 1
}

/** Remaining data + days + devices (green dot = online, red dot = offline). */
@Composable
fun UsageCard(modifier: Modifier = Modifier) {
    val left = (DemoAccount.REMAINING_GB / DemoAccount.TOTAL_GB).toFloat().coerceIn(0f, 1f)
    NovaCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DataUsage, null, tint = NovaColors.Lime, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.usage_remaining), Modifier.weight(1f), color = NovaColors.Dim, fontSize = 12.sp)
                Text(
                    stringResource(R.string.usage_gb_fmt, DemoAccount.REMAINING_GB, DemoAccount.TOTAL_GB.toInt()),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            // progress bar: how much is left
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(NovaColors.Border)) {
                Box(Modifier.fillMaxWidth(left).fillMaxHeight().clip(CircleShape).background(NovaColors.Lime))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, null, tint = NovaColors.Gold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.usage_days), color = NovaColors.Dim, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.days_fmt, DemoAccount.DAYS_LEFT), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                // devices: green dot = online, red dot = offline
                Icon(Icons.Rounded.Devices, null, tint = NovaColors.Dim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Dot(NovaColors.Lime); Spacer(Modifier.width(4.dp)); Text("${DemoAccount.ONLINE}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Dot(NovaColors.Danger); Spacer(Modifier.width(4.dp)); Text("${DemoAccount.OFFLINE}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(9.dp).clip(CircleShape).background(color))
}
