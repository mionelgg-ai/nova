package app.nova.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.vpn.MainViewModel
import app.nova.vpn.R
import app.nova.vpn.data.ActivateResult
import app.nova.vpn.data.Offer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumSheet(vm: MainViewModel, onDismiss: () -> Unit, onSubscribe: () -> Unit) {
    val config by vm.config.collectAsState()
    val premium by vm.premium.collectAsState()
    val fa = LocalConfiguration.current.locales[0].language == "fa"
    var showCode by remember { mutableStateOf(false) }

    // close automatically after a successful activation
    val wasActive = remember { premium.active }
    LaunchedEffect(premium.active) { if (premium.active && !wasActive) onDismiss() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NovaColors.Surface,
        contentColor = NovaColors.Text,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LogoMark(54.dp)
            Spacer(Modifier.height(14.dp))

            val offer = config?.offer?.takeIf { it.endsAtMillis > System.currentTimeMillis() }
            val defaultTitle = stringResource(R.string.premium_title)
            val defaultText = stringResource(R.string.premium_text)
            val title = offer?.let { if (fa) it.titleFa else it.title }.orEmpty().ifBlank { defaultTitle }
            val sub = offer?.let { if (fa) it.textFa else it.text }.orEmpty().ifBlank { defaultText }
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = NovaColors.Lime)
            Spacer(Modifier.height(6.dp))
            Text(sub, color = NovaColors.Dim, fontSize = 14.sp, textAlign = TextAlign.Center)

            Spacer(Modifier.height(18.dp))
            listOf(R.string.benefit_1, R.string.benefit_2, R.string.benefit_3).forEach {
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, null, tint = NovaColors.Lime, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(it), fontSize = 14.sp)
                }
            }

            if (offer != null) {
                Spacer(Modifier.height(16.dp))
                Countdown(offer)
            }

            Spacer(Modifier.height(22.dp))
            LimeButton(stringResource(R.string.get_subscription), onSubscribe, Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { showCode = !showCode }) {
                Text(stringResource(R.string.have_code), color = NovaColors.Text, fontSize = 14.sp)
            }
            if (showCode) ActivateForm(vm)
        }
    }
}

@Composable
private fun Countdown(offer: Offer) {
    var left by remember { mutableLongStateOf(offer.endsAtMillis - System.currentTimeMillis()) }
    LaunchedEffect(offer.endsAtMillis) {
        while (left > 0) { delay(1000); left = offer.endsAtMillis - System.currentTimeMillis() }
    }
    val t = (left / 1000).coerceAtLeast(0)
    val parts = listOf(
        t / 86400 to R.string.unit_days, (t % 86400) / 3600 to R.string.unit_hours,
        (t % 3600) / 60 to R.string.unit_minutes, t % 60 to R.string.unit_seconds
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.offer_ends), color = NovaColors.Dim, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().background(NovaColors.Lime, RoundedCornerShape(18.dp)).padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            parts.forEach { (v, label) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%02d".format(v), color = NovaColors.Bg, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(label), color = NovaColors.Bg.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        }
    }
}

/** Code entry used both in the premium sheet and on the profile screen. */
@Composable
fun ActivateForm(vm: MainViewModel) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Int?>(null) }
    var ok by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = code, onValueChange = { code = it; message = null },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.code_hint), color = NovaColors.Dim) },
            textStyle = TextStyle(textDirection = TextDirection.Ltr, letterSpacing = 1.sp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NovaColors.Lime, unfocusedBorderColor = NovaColors.Border,
                focusedTextColor = NovaColors.Text, unfocusedTextColor = NovaColors.Text,
                cursorColor = NovaColors.Lime,
                focusedContainerColor = NovaColors.Surface2, unfocusedContainerColor = NovaColors.Surface2,
            )
        )
        Spacer(Modifier.height(10.dp))
        LimeButton(stringResource(R.string.code_activate), {
            if (busy || code.isBlank()) return@LimeButton
            busy = true
            scope.launch {
                val r = vm.activate(code)
                ok = r is ActivateResult.Ok
                message = when (r) {
                    is ActivateResult.Ok -> R.string.code_ok
                    ActivateResult.Expired -> R.string.code_expired
                    ActivateResult.Invalid -> R.string.code_invalid
                }
                busy = false
            }
        }, Modifier.fillMaxWidth(), enabled = code.isNotBlank() && !busy)
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(it), color = if (ok) NovaColors.Lime else NovaColors.Danger, fontSize = 13.sp)
        }
    }
}
