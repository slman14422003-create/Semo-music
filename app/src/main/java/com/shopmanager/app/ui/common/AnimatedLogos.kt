package com.shopmanager.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.ui.theme.ClaudeOrangeDark
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * مستوى حركة الرموز بحسب وضع الأداء:
 *  - [NONE]  (الاقتصادي): رسم ثابت فقط، بلا أي حركة ولا حلقات.
 *  - [ONCE]  (المتوازن): حركة دخول واحدة قصيرة ثم يثبت الرسم.
 *  - [LOOP]  (المرتفع): حركة كاملة مستمرة (دوران، تنفّس، جسيمات، لمعان).
 */
enum class LogoMotion { NONE, ONCE, LOOP }

fun logoMotionFor(tier: PerformanceTier): LogoMotion = when (tier) {
    PerformanceTier.LOW -> LogoMotion.NONE
    PerformanceTier.BALANCED -> LogoMotion.ONCE
    PerformanceTier.STANDARD -> LogoMotion.LOOP
}

@Composable
fun rememberLogoMotion(): LogoMotion = logoMotionFor(LocalPerformanceTier.current)

private val ClaudeRay = ClaudeOrangeDark
private val DevTop = Color(0xFF3B4A9E)
private val DevBottom = Color(0xFF7B4FD8)
private val DevChevron = Color(0xFF7FA7FF)
private val DevDot = Color(0xFFF0A24B)
private const val TWO_PI = (2.0 * PI).toFloat()

/** تقدّم حركة دخول من 0 إلى 1 (يبدأ مكتملاً إذا لا حركة). */
@Composable
private fun rememberEntrance(motion: LogoMotion, millis: Int = 900): Animatable<Float, *> {
    val a = remember { Animatable(if (motion == LogoMotion.NONE) 1f else 0f) }
    LaunchedEffect(motion) {
        if (motion == LogoMotion.NONE) a.snapTo(1f) else a.animateTo(1f, tween(millis, easing = FastOutSlowInEasing))
    }
    return a
}

/**
 * نجمة Claude: 12 شعاعاً.
 * LOOP: دوران بطيء + تنفّس + موجة تمرّ على أطوال الأشعة + 3 جسيمات تدور حولها + توهّج.
 * ONCE: تنمو الأشعة واحداً تلو الآخر ثم تثبت.  NONE: رسم ثابت.
 */
@Composable
fun ClaudeSparkLogo(modifier: Modifier = Modifier, size: Dp = 112.dp, motion: LogoMotion = rememberLogoMotion()) {
    val entrance = rememberEntrance(motion, 1100)
    val inf = if (motion == LogoMotion.LOOP) rememberInfiniteTransition(label = "spark") else null
    val rot = inf?.animateFloat(0f, 360f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "rot")
    val wave = inf?.animateFloat(0f, TWO_PI, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "wave")
    val orbit = inf?.animateFloat(0f, TWO_PI, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "orbit")
    val glow = inf?.animateFloat(0.16f, 0.36f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val breathe = inf?.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")

    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val half = this.size.minDimension / 2f
        val e = entrance.value
        // توهّج خلفي
        drawCircle(
            Brush.radialGradient(listOf(ClaudeRay.copy(alpha = (glow?.value ?: 0.22f) * e), Color.Transparent), c, half),
            radius = half, center = c
        )
        val body = half * 0.78f * (breathe?.value ?: 1f)
        rotate(rot?.value ?: 0f, c) {
            val rays = 12
            for (i in 0 until rays) {
                // ONCE/LOOP: كل شعاع يبدأ نموه بعد سابقه.
                val p = ((e * (rays + 3) - i) / 3f).coerceIn(0f, 1f)
                val base = if (i % 2 == 0) body else body * 0.82f
                val w = if (wave != null) 1f + 0.10f * sin(wave.value + i * 0.9f) else 1f
                val len = base * w * p
                val inner = body * 0.2f
                val a = Math.toRadians(i * 360.0 / rays - 90.0)
                drawLine(
                    ClaudeRay,
                    Offset(c.x + (inner * cos(a)).toFloat(), c.y + (inner * sin(a)).toFloat()),
                    Offset(c.x + ((inner + (len - inner).coerceAtLeast(0f)) * cos(a)).toFloat(),
                        c.y + ((inner + (len - inner).coerceAtLeast(0f)) * sin(a)).toFloat()),
                    strokeWidth = size.toPx() * 0.055f, cap = StrokeCap.Round, alpha = p
                )
            }
        }
        // جسيمات تدور (LOOP فقط)
        if (orbit != null) {
            for (k in 0 until 3) {
                val a = orbit.value + k * TWO_PI / 3f
                val r = half * (0.93f + 0.05f * sin(orbit.value * 2f + k))
                val tw = 0.5f + 0.5f * sin(orbit.value * 3f + k * 2f)
                drawCircle(ClaudeRay.copy(alpha = 0.35f + 0.55f * tw), size.toPx() * (0.014f + 0.01f * tw), Offset(c.x + r * cos(a), c.y + r * sin(a)))
            }
        }
    }
}

/**
 * شعار المطوّر: مربع بتدرّج، حرف S بين قوسين برمجيين ونقطة برتقالية.
 * LOOP: طفو + لمعة تعبر المربع + القوسان ينبضان + النقطة تومض.
 * ONCE: قفزة دخول نابضة.  NONE: ثابت.
 */
@Composable
fun DeveloperLogo(modifier: Modifier = Modifier, size: Dp = 104.dp, motion: LogoMotion = rememberLogoMotion()) {
    val pop = remember { Animatable(if (motion == LogoMotion.NONE) 1f else 0.6f) }
    LaunchedEffect(motion) {
        if (motion == LogoMotion.NONE) pop.snapTo(1f)
        else pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }
    val inf = if (motion == LogoMotion.LOOP) rememberInfiniteTransition(label = "dev") else null
    val floatY = inf?.animateFloat(-3f, 3f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")
    val shine = inf?.animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "shine")
    val pulse = inf?.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")

    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val s = pop.value
                scaleX = s; scaleY = s; alpha = ((s - 0.6f) / 0.4f).coerceIn(0f, 1f)
                translationY = (floatY?.value ?: 0f).dp.toPx()
            }
            .clip(RoundedCornerShape(size * 0.29f))
            .background(Brush.linearGradient(listOf(DevTop, DevBottom))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val sw = w * 0.04f
            val p = pulse?.value ?: 0f
            val dx = w * 0.025f * p // القوسان يتحركان للداخل/الخارج
            drawLine(DevChevron, Offset(w * 0.20f - dx, h * 0.50f), Offset(w * 0.30f - dx, h * 0.40f), sw, StrokeCap.Round)
            drawLine(DevChevron, Offset(w * 0.20f - dx, h * 0.50f), Offset(w * 0.30f - dx, h * 0.60f), sw, StrokeCap.Round)
            drawLine(DevChevron, Offset(w * 0.80f + dx, h * 0.50f), Offset(w * 0.70f + dx, h * 0.40f), sw, StrokeCap.Round)
            drawLine(DevChevron, Offset(w * 0.80f + dx, h * 0.50f), Offset(w * 0.70f + dx, h * 0.60f), sw, StrokeCap.Round)
            drawCircle(DevDot.copy(alpha = if (pulse != null) 0.55f + 0.45f * p else 1f), w * 0.043f, Offset(w * 0.70f, h * 0.20f))
            // لمعة قطرية تعبر المربع
            shine?.let { sh ->
                val x = w * sh.value
                drawLine(
                    Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                        Offset(x - w * 0.12f, 0f), Offset(x + w * 0.12f, 0f)),
                    Offset(x + h * 0.3f, 0f), Offset(x - h * 0.3f, h), strokeWidth = w * 0.22f
                )
            }
        }
        Text(
            "S",
            style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.SansSerif),
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/** خط الربط: LOOP = "حزمة" ضوء تنتقل بين الشعارين ذهاباً وإياباً؛ ONCE = تضيء النقاط بالتتابع مرة؛ NONE = نقاط ثابتة. */
@Composable
fun LinkDots(modifier: Modifier = Modifier, motion: LogoMotion = rememberLogoMotion()) {
    val once = remember { Animatable(if (motion == LogoMotion.NONE) 1f else 0f) }
    LaunchedEffect(motion) {
        if (motion == LogoMotion.NONE) once.snapTo(1f) else once.animateTo(1f, tween(1200, easing = LinearEasing))
    }
    val inf = if (motion == LogoMotion.LOOP) rememberInfiniteTransition(label = "link") else null
    val packet = inf?.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "packet")
    val track = MaterialTheme.colorScheme.outlineVariant
    val lit = Color(0xFF3B82F6)
    Canvas(modifier.width(60.dp).height(14.dp)) {
        val n = 3
        val gap = this.size.width / n
        for (i in 0 until n) {
            val x = gap * (i + 0.5f)
            val pos = (i + 0.5f) / n
            val d = when {
                packet != null -> (1f - abs(packet.value - pos) / 0.45f).coerceIn(0f, 1f)
                motion == LogoMotion.ONCE -> ((once.value * (n + 1) - i) / 1f).coerceIn(0f, 1f).let { if (once.value >= 1f) 0.6f else it }
                else -> 0.6f
            }
            val col = androidx.compose.ui.graphics.lerp(track, lit, d)
            drawCircle(col, this.size.height * (0.30f + 0.20f * d), Offset(x, this.size.height / 2f))
        }
        // حزمة مضيئة صغيرة بين النقاط
        packet?.let { p ->
            drawCircle(lit.copy(alpha = 0.85f), this.size.height * 0.16f, Offset(this.size.width * p.value, this.size.height / 2f))
        }
    }
}

/** أيقونة "اقتراح ذكي": ومضة تلألؤ لطيفة في المرتفع، ثابتة في غيره. */
@Composable
fun twinkleScale(motion: LogoMotion = rememberLogoMotion()): Float {
    if (motion != LogoMotion.LOOP) return 1f
    val inf = rememberInfiniteTransition(label = "twinkle")
    val s = inf.animateFloat(0.88f, 1.12f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tw")
    return s.value
}
