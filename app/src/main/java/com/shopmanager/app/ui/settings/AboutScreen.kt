package com.shopmanager.app.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.shopmanager.app.ui.common.ClaudeSparkLogo
import com.shopmanager.app.ui.common.DeveloperLogo
import com.shopmanager.app.ui.common.LinkDots
import com.shopmanager.app.data.updates.AppVersionInfo
import com.shopmanager.app.ui.theme.ClaudeOrangeDark
import kotlin.math.cos
import kotlin.math.sin

private val ClaudeRay = ClaudeOrangeDark
private val DevGradientTop = Color(0xFF3B4A9E)
private val DevGradientBottom = Color(0xFF7B4FD8)
private val DevChevron = Color(0xFF7FA7FF)
private val DevDot = Color(0xFFF0A24B)

/**
 * شاشة "حول التطبيق" كاملة — نفس تصميم شاشة FreeLLM Chat:
 * شعار المطوّر سلمان + شعار Claude المتحرك + خط متحرك يربط بينهما،
 * ثم اسم التطبيق والإصدار والوصف وبطاقة "من صنع هذا التطبيق".
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenHelp: () -> Unit
) {
    val context = LocalContext.current
    val appVersion = remember { AppVersionInfo.current(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AboutTopBar(onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // الشعاران والربط بينهما (ترتيب ثابت: Claude يسار، سلمان يمين، كما في FreeLLM).
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top
                ) {
                    LogoColumn(name = "Claude AI", role = "شريك البرمجة") { ClaudeSparkLogo() }
                    Box(Modifier.padding(top = 52.dp)) { LinkDots() }
                    LogoColumn(name = "سلمان", role = "المطوّر") { DeveloperLogo() }
                }
            }

            Spacer(Modifier.height(34.dp))

            Text(
                "إدارة المحل",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "v${appVersion.name}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "إدارة المحل تطبيق واحد لإدارة الديون والمواد والأسعار والملاحظات، مبني خصيصًا لمحلك: " +
                    "يزامن بين أجهزتك، ويتعلّم عادات محلك ليقترح عليك النواقص بذكاء، ويعمل حتى بدون اتصال دائم بالإنترنت.",
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 30.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(30.dp))
            Text(
                "من صنع هذا التطبيق",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            SettingsGroup {
                item(
                    icon = Icons.Default.Code,
                    title = "المطوّر: سلمان",
                    subtitle = "صاحب الفكرة والتصميم والقرارات، ويقود كل خطوة في تطوير التطبيق."
                )
                item(
                    icon = Icons.Default.AutoAwesome,
                    title = "شريك البرمجة: Claude AI",
                    subtitle = "كُتب الكود وطُوّر وصُقل عبر حوار مستمر بين سلمان وClaude من Anthropic."
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup {
                item(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "سياسة الخصوصية",
                    subtitle = "ما يخزّنه التطبيق ولماذا",
                    onClick = onOpenPrivacyPolicy
                )
                item(
                    icon = Icons.Outlined.Info,
                    title = "دليل الاستخدام",
                    subtitle = "كل ميزات التطبيق خطوة بخطوة",
                    onClick = onOpenHelp
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun AboutTopBar(onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(72.dp)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "حول التطبيق",
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            modifier = Modifier.align(Alignment.CenterStart).size(46.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun LogoColumn(name: String, role: String, logo: @Composable () -> Unit) {
    Column(Modifier.width(132.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) { logo() }
        Spacer(Modifier.height(10.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Default),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            role,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
