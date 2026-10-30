package app.nova.vpn.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.vpn.data.Prefs

/** English / Persian text without going through strings.xml */
@Composable
fun tr(en: String, fa: String): String =
    if (LocalConfiguration.current.locales[0].language == "fa") fa else en

@Composable
fun NovaTextField(
    value: String, onValueChange: (String) -> Unit, placeholder: String,
    modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false, singleLine: Boolean = true, trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, singleLine = singleLine, modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = NovaColors.Dim) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = trailing,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NovaColors.Lime, unfocusedBorderColor = NovaColors.Border,
            focusedTextColor = NovaColors.Text, unfocusedTextColor = NovaColors.Text,
            cursorColor = NovaColors.Lime,
            focusedContainerColor = NovaColors.Surface2, unfocusedContainerColor = NovaColors.Surface2,
        )
    )
}

/**
 * Sign-in flow: choose (Google / email) -> email form -> name.
 * Nothing is sent anywhere yet: any valid-looking email + 6-character password is accepted.
 */
@Composable
fun AuthFlow(onDone: () -> Unit) {
    var stage by rememberSaveable { mutableStateOf("choose") }       // choose | email | name

    BackHandler(enabled = stage != "choose") { stage = if (stage == "name") "email" else "choose" }

    when (stage) {
        "email" -> EmailScreen(
            onBack = { stage = "choose" },
            onSubmit = { email, isNewAccount ->
                Prefs.authMethod = "email"
                Prefs.userEmail = email
                if (isNewAccount) {
                    stage = "name"
                } else {
                    Prefs.userName = email.substringBefore('@').replaceFirstChar { it.uppercase() }
                    Prefs.signedIn = true
                    onDone()
                }
            }
        )
        "name" -> NameScreen(onContinue = { name ->
            Prefs.userName = name.trim()
            Prefs.signedIn = true
            onDone()
        })
        else -> ChooseScreen(
            onGoogle = {
                // placeholder: real Google sign-in comes with the backend
                Prefs.authMethod = "google"
                Prefs.userName = "Google User"
                Prefs.userEmail = "user@gmail.com"
                Prefs.signedIn = true
                onDone()
            },
            onEmail = { stage = "email" }
        )
    }
}

@Composable
private fun ChooseScreen(onGoogle: () -> Unit, onEmail: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(NovaColors.Bg)
            .background(Brush.radialGradient(listOf(NovaColors.Lime.copy(alpha = 0.16f), Color.Transparent), radius = 900f))
            .systemBarsPadding().padding(horizontal = 24.dp)
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            LogoMark(72.dp)
            Spacer(Modifier.height(18.dp))
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append("Nova ")
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = NovaColors.Lime)); append("VPN"); pop()
                },
                fontSize = 30.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(28.dp))
            Text(tr("Welcome", "خوش آمدید"), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                tr("Sign in to manage your plan and connect.", "برای مدیریت اشتراک و اتصال وارد شوید."),
                color = NovaColors.Dim, fontSize = 14.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1.3f))

            // Google
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(Color.White)
                    .clickable(onClick = onGoogle),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                Text("G", color = Color(0xFF4285F4), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Text(tr("Continue with Google", "ادامه با گوگل"), color = Color(0xFF1F1F1F), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            // Email
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                    .border(1.5.dp, NovaColors.Lime, RoundedCornerShape(28.dp)).clickable(onClick = onEmail),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                Text("@", color = NovaColors.Lime, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Text(tr("Continue with email", "ادامه با ایمیل"), color = NovaColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                tr("By continuing you agree to our terms and privacy policy.", "با ادامه دادن، قوانین و حریم خصوصی را می‌پذیرید."),
                color = NovaColors.Dim, fontSize = 12.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EmailScreen(onBack: () -> Unit, onSubmit: (email: String, isNewAccount: Boolean) -> Unit) {
    var signUp by rememberSaveable { mutableStateOf(true) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val badEmail = tr("Enter a valid email address.", "یک ایمیل معتبر وارد کنید.")
    val badPass = tr("Password must be at least 6 characters.", "رمز عبور باید حداقل ۶ کاراکتر باشد.")

    Column(
        Modifier.fillMaxSize().background(NovaColors.Bg).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
    ) {
        SubHeader("", onBack)
        Spacer(Modifier.height(24.dp))
        Text(
            if (signUp) tr("Create your account", "ساخت حساب کاربری") else tr("Sign in", "ورود"),
            fontSize = 26.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr("Use your email and a password.", "ایمیل و یک رمز عبور وارد کنید."),
            color = NovaColors.Dim, fontSize = 14.sp
        )
        Spacer(Modifier.height(28.dp))
        NovaTextField(email, { email = it.trim(); error = null }, tr("Email", "ایمیل"), keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        NovaTextField(
            password, { password = it; error = null }, tr("Password", "رمز عبور"),
            keyboardType = KeyboardType.Password, password = !show,
            trailing = {
                Icon(
                    if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = NovaColors.Dim,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { show = !show }.padding(6.dp)
                )
            }
        )
        error?.let { Text(it, color = NovaColors.Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(22.dp))
        LimeButton(
            if (signUp) tr("Create account", "ساخت حساب") else tr("Sign in", "ورود"),
            {
                when {
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> error = badEmail
                    password.length < 6 -> error = badPass
                    else -> onSubmit(email, signUp)
                }
            },
            Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (signUp) tr("Already have an account? Sign in", "حساب دارید؟ وارد شوید")
            else tr("New here? Create an account", "حساب ندارید؟ ثبت‌نام کنید"),
            color = NovaColors.Lime, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(12.dp))
                .clickable { signUp = !signUp; error = null }.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NameScreen(onContinue: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(NovaColors.Bg).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(72.dp))
        LogoMark(56.dp)
        Spacer(Modifier.height(24.dp))
        Text(tr("What's your name?", "اسم شما چیست؟"), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            tr("We'll show it on your profile.", "این نام در پروفایل شما نمایش داده می‌شود."),
            color = NovaColors.Dim, fontSize = 14.sp
        )
        Spacer(Modifier.height(28.dp))
        NovaTextField(name, { name = it }, tr("Your name", "نام شما"))
        Spacer(Modifier.height(22.dp))
        LimeButton(tr("Continue", "ادامه"), { onContinue(name) }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
    }
}
