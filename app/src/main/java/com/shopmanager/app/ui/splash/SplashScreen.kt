package com.shopmanager.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.animateFloat
import com.shopmanager.app.ui.common.LogoMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmanager.app.R
import com.shopmanager.app.data.performance.MotionSpecs

/**
 * شاشة البداية بلغة التصميم الموحّدة (نفس الإعدادات والحوارات):
 *  - خلفية surface مسطّحة بلا توهّج ولا تدرجات ولا دوران.
 *  - أيقونة التطبيق داخل لوح مسطّح بزوايا 28dp على تعبئة surfaceContainerHigh،
 *    تظهر بتكبير خفيف (0.9 ← 1) مع تلاشٍ، بنفس منحنى الحركة المستعمل بالتطبيق.
 *  - اسم التطبيق بخط السيريف (نفس عناوين الشاشات) ينزلق من تحت قليلاً.
 *  - الاعتماد "SEMO STUDIO" في حبّة مسطّحة صغيرة (نفس شارة الإصدار) تظهر أخيراً.
 *
 * حركة واحدة تُشغَّل مرة وحدة: لا تكرار ولا حلقات، وتتبع [MotionSpecs.durationScale]
 * بحيث تقصر على الشاشات 60Hz. الألوان كلها من MaterialTheme لتتبع وضع التطبيق
 * (فاتح/داكن) المختار من الإعدادات وليس وضع النظام فقط.
 */
/** Whole splash animation length (ms) before the 60Hz scale; kept under ~600ms. */
private const val SPLASH_ANIM_MS = 600

@Composable
fun AppSplashScreen(modifier: Modifier = Modifier, motion: LogoMotion = LogoMotion.ONCE) {
    // One progress value (0..1) drives the whole splash; tile, title and credit are
    // staggered slices of it, so there is a single animation to run and to hand off from.
    val progress = remember { Animatable(0f) }
    val reduce = MotionSpecs.reduceMotion()
    val scale = MotionSpecs.durationScale()
    LaunchedEffect(reduce, motion) {
        if (reduce || motion == LogoMotion.NONE) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(
                1f,
                animationSpec = tween(
                    durationMillis = (SPLASH_ANIM_MS * scale).toInt(),
                    easing = MotionSpecs.emphasizedEasing
                )
            )
        }
    }
    fun slice(from: Float, to: Float): Float = ((progress.value - from) / (to - from)).coerceIn(0f, 1f)

    val cs = MaterialTheme.colorScheme

    // رمز التطبيق: اقتصادي = ثابت، متوازن = قفزة دخول نابضة، مرتفع = + نبضة حلقة مستمرة.
    val pop = remember { Animatable(if (motion == LogoMotion.NONE) 1f else 0.7f) }
    val popSpec = MotionSpecs.popInSpring()
    LaunchedEffect(motion, reduce) {
        if (motion == LogoMotion.NONE || reduce) pop.snapTo(1f)
        else pop.animateTo(1f, popSpec)
    }
    val ring = if (motion == LogoMotion.LOOP) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "splashRing").animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(tween(MotionSpecs.floatMs, easing = androidx.compose.animation.core.LinearEasing)),
            label = "ring"
        )
    } else null
    val ringColor = cs.primary

    Box(
        modifier
            .fillMaxSize()
            .background(cs.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // لوح مسطّح بتعبئة الصفوف خلف أيقونة التطبيق الحقيقية.
            Box(
                Modifier
                    .size(124.dp)
                    .drawBehind {
                        ring?.let { r ->
                            val t = r.value
                            drawCircle(ringColor.copy(alpha = 0.28f * (1f - t)), radius = size.minDimension / 2f * (1f + 0.55f * t))
                        }
                    }
                    .graphicsLayer {
                        val tileProgress = slice(0f, 0.7f)
                        val s = (0.9f + 0.1f * tileProgress) * pop.value
                        scaleX = s
                        scaleY = s
                        alpha = tileProgress
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .background(cs.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_splash_mark),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(20.dp))
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "إدارة المحل",
                color = cs.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp, lineHeight = 34.sp),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    val titleProgress = slice(0.2f, 0.85f)
                    alpha = titleProgress
                    translationY = (1f - titleProgress) * 12.dp.toPx()
                }
            )
        }

        Surface(
            shape = CircleShape,
            color = cs.surfaceContainerHigh,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp)
                .graphicsLayer { alpha = slice(0.4f, 1f) }
        ) {
            Text(
                "SEMO STUDIO",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
