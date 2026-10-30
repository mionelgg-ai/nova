package app.nova.vpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.vpn.R
import app.nova.vpn.data.Jalali
import app.nova.vpn.data.Pricing
import kotlin.math.roundToInt

/** 1234567 -> "1,234,567" (Persian digits and separator when [fa]) */
fun formatToman(n: Long, fa: Boolean): String {
    val grouped = n.toString().reversed().chunked(3).joinToString(",").reversed()
    return if (fa) Jalali.toPersianDigits(grouped).replace(',', '٬') else grouped
}

/**
 * "Build your plan": data (GB), duration (months) and simultaneous users, each a slider.
 * The price updates live; Buy hands the choice to the caller (which opens Telegram).
 */
@Composable
fun PurchasePanel(pricing: Pricing, fa: Boolean, onBuy: (gb: Int, months: Int, users: Int, total: Long) -> Unit) {
    val minGb = pricing.minGb
    val maxGb = pricing.maxGb.coerceAtLeast(minGb + 1)
    val maxUsers = pricing.maxUsers.coerceAtLeast(1)

    var gb by rememberSaveable { mutableIntStateOf(minGb) }
    var months by rememberSaveable { mutableIntStateOf(1) }
    var users by rememberSaveable { mutableIntStateOf(1) }

    val total = gb * pricing.gb + months * pricing.month + (users - 1) * pricing.extraUser

    NovaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.plan_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))

            PlanSlider(
                label = stringResource(R.string.plan_data), value = stringResource(R.string.gb_fmt, gb),
                slider = {
                    Slider(
                        value = gb.toFloat(), onValueChange = { gb = it.roundToInt() },
                        valueRange = minGb.toFloat()..maxGb.toFloat(), colors = sliderColors()
                    )
                }
            )
            PlanSlider(
                label = stringResource(R.string.plan_duration), value = stringResource(R.string.months_fmt, months),
                slider = {
                    Slider(
                        value = months.toFloat(), onValueChange = { months = it.roundToInt() },
                        valueRange = 1f..12f, steps = 10, colors = sliderColors()
                    )
                }
            )
            if (maxUsers > 1) {
                PlanSlider(
                    label = stringResource(R.string.plan_users), value = stringResource(R.string.users_fmt, users),
                    slider = {
                        Slider(
                            value = users.toFloat(), onValueChange = { users = it.roundToInt() },
                            valueRange = 1f..maxUsers.toFloat(), steps = (maxUsers - 2).coerceAtLeast(0), colors = sliderColors()
                        )
                    }
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.plan_total), color = NovaColors.Dim, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatToman(total, fa), color = NovaColors.Lime, fontSize = 34.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.price_unit), color = NovaColors.Lime, fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            LimeButton(stringResource(R.string.plan_buy), { onBuy(gb, months, users, total) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = NovaColors.Lime,
    activeTrackColor = NovaColors.Lime,
    inactiveTrackColor = NovaColors.Border,
    activeTickColor = NovaColors.Bg,
    inactiveTickColor = NovaColors.Dim,
)

@Composable
private fun PlanSlider(label: String, value: String, slider: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = NovaColors.Dim, fontSize = 13.sp)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
    slider()
    Spacer(Modifier.height(8.dp))
}
