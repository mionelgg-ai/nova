package app.nova.vpn.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nova.vpn.R
import kotlinx.coroutines.launch
import kotlin.math.abs

private class OnbPage(val image: Int, val line1: String, val line2: String, val body: String)

private val ENGLISH = FontFamily.Default

@Composable
private fun T(
    text: String, size: Float, weight: FontWeight = FontWeight.Normal,
    color: androidx.compose.ui.graphics.Color = NovaColors.Text, modifier: Modifier = Modifier, lineHeight: Float = 0f,
) {
    Text(
        text, modifier, color = color, fontSize = size.sp, fontWeight = weight, fontFamily = ENGLISH,
        lineHeight = if (lineHeight > 0) lineHeight.sp else androidx.compose.ui.unit.TextUnit.Unspecified
    )
}

/** First-run slides (always English, always left-to-right). Swipe or tap Next. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = remember {
        listOf(
            OnbPage(
                R.drawable.ob_shield, "Your Privacy,", "Our Priority",
                "Keep your online activity private and secure. Protect your data from trackers, hackers and unwanted eyes."
            ),
            OnbPage(
                R.drawable.ob_servers, "Fast & Stable", "Connection",
                "Enjoy high-speed, reliable VPN servers across the world. Stream, browse and work without limits."
            ),
            OnbPage(
                R.drawable.ob_cloud, "Access a Free", "Internet",
                "Get around geo-restrictions and access your favorite websites, apps and content — anywhere in the world."
            ),
        )
    }
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pages.size - 1

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            Modifier.fillMaxSize().background(NovaColors.Bg).systemBarsPadding().padding(horizontal = 24.dp)
        ) {
            // brand
            Row(
                Modifier.fillMaxWidth().padding(top = 28.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                LogoMark(34.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = NovaColors.Text)) { append("Nova ") }
                        withStyle(SpanStyle(color = NovaColors.Lime)) { append("VPN") }
                    },
                    fontSize = 26.sp, fontFamily = ENGLISH, fontWeight = FontWeight.Medium
                )
            }

            // dots
            Row(
                Modifier.fillMaxWidth().padding(top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                repeat(pages.size) { i ->
                    val selected = pager.currentPage == i
                    val w by animateDpAsState(if (selected) 22.dp else 8.dp, tween(350), label = "dot")
                    Box(
                        Modifier.height(8.dp).width(w).clip(CircleShape)
                            .background(if (selected) NovaColors.Lime else NovaColors.Border)
                    )
                }
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                OnbPageContent(pages[page], page, pager)
            }

            // actions
            Row(
                Modifier.fillMaxWidth().padding(bottom = 28.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                T(
                    "Skip", 17f, color = NovaColors.Dim,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onFinish).padding(horizontal = 4.dp, vertical = 10.dp)
                )
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.height(54.dp).clip(RoundedCornerShape(27.dp)).background(NovaColors.Lime)
                        .clickable {
                            if (pager.currentPage < last) {
                                scope.launch {
                                    pager.animateScrollToPage(
                                        pager.currentPage + 1,
                                        animationSpec = tween(650, easing = FastOutSlowInEasing)
                                    )
                                }
                            } else onFinish()
                        }
                        .padding(horizontal = 28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    T(if (pager.currentPage == last) "Get Started" else "Next", 16f, FontWeight.SemiBold, NovaColors.Bg)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Rounded.ArrowForward, null, tint = NovaColors.Bg, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun OnbPageContent(page: OnbPage, index: Int, pager: androidx.compose.foundation.pager.PagerState) {
    // 0 when this page is centered, +/-1 when it is a full page away
    val offset = (pager.currentPage - index) + pager.currentPageOffsetFraction
    val dist = abs(offset).coerceIn(0f, 1f)

    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = -7f, targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val density = LocalDensity.current

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                translationX = offset * size.width * 0.35f
                alpha = 1f - dist
                val sc = 1f - 0.18f * dist
                scaleX = sc; scaleY = sc
            },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painterResource(page.image), null,
                Modifier.fillMaxSize()
                    .graphicsLayer { translationY = with(density) { bob.dp.toPx() } }
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp).graphicsLayer {
                translationX = offset * size.width * 0.12f
                alpha = (1f - dist * 1.4f).coerceIn(0f, 1f)
            }
        ) {
            T(page.line1, 34f, FontWeight.Bold, NovaColors.Text, lineHeight = 40f)
            T(page.line2, 34f, FontWeight.Bold, NovaColors.Lime, lineHeight = 40f)
            Spacer(Modifier.height(14.dp))
            T(page.body, 16f, FontWeight.Normal, NovaColors.Dim, lineHeight = 24f)
        }
    }
}
