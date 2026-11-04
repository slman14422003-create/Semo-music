package com.shopmanager.app.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.ui.common.AppTextField
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.shopmanager.app.ui.common.AppGroupLargeRadius
import com.shopmanager.app.ui.common.AppPillButton
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon

/**
 * SECURITY FIX: a 4-6 digit PIN used to have no limit on wrong guesses —
 * anyone with the phone in hand could just keep tapping "دخول" until they
 * landed on the right one. `settings.verifyPin` now enforces a real
 * escalating lockout (see PinAttemptThrottle/SettingsRepository); this
 * screen surfaces that lockout instead of only reflecting a right/wrong
 * result — the field and button disable and a countdown shows while
 * locked, so it's visibly a real cooldown rather than the app just
 * silently rejecting the correct PIN.
 *
 * REDESIGN (طلب "اعادة تلوين كل الواجهة حسب الوضع الموجود... حرفيا كلشيء"):
 * this was the one screen in the whole app that never picked up any of the
 * app's own design system — a bare centered [Column] on the plain Material
 * background, a stock [Icon]/[Button], no brand gradient, no glass. Every
 * other screen (the splash, every header, every dialog) already reads as
 * this app's own product; the PIN screen alone looked like unstyled
 * scaffolding, and it's the very first thing a returning person sees.
 * Rebuilt on the exact same language [AppSplashScreen] already
 * established — [BrandGradient] behind a glass badge
 * — so unlocking the app now visually continues straight out of the same
 * screen the splash just handed off from, instead of cutting to a
 * completely different, unbranded look. The actual PIN entry sits inside
 * its own [liquidGlassSurface] card, tinted toward this mode's own
 * primary/tertiary the same way [GlassAlertDialog] blends its panel — so
 * the field and error text keep reading from the ordinary
 * onSurface/onSurfaceVariant roles that already look correct in every
 * color mode, no one-off color logic of their own needed.
 *
 * Status bar/nav bar icon color for this screen is handled in
 * MainActivity, not here — see its own note on why this full-bleed
 * gradient background means this screen now follows the exact same
 * "always white icons" rule the splash and every unlocked screen already
 * use, rather than the light/dark-background rule a plain screen would.
 */
@Composable
fun LockScreen(settings: SettingsRepository, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var lockRemaining by remember { mutableLongStateOf(settings.pinLockRemainingSeconds()) }
    val isLocked = lockRemaining > 0

    // UI FIX: one shared submit path for both the "دخول" button and the
    // keyboard's Done key (before, the keyboard had no action at all, so
    // people had to dismiss it and hunt for the button). A wrong PIN now
    // also clears the field instead of leaving the old digits to erase.
    val submit: () -> Unit = {
        if (!isLocked && pin.length >= 4) {
            if (settings.verifyPin(pin)) {
                onUnlocked()
            } else {
                error = true
                pin = ""
                lockRemaining = settings.pinLockRemainingSeconds()
            }
        }
    }

    // FEATURE ADDED ("فتح بالبصمة/الوجه"): shown only when the person
    // hasn't turned it off in Settings AND the phone really has an enrolled
    // fingerprint/face. Prompts once automatically as soon as the screen is
    // resumed (BiometricPrompt can't run before the Activity is RESUMED);
    // after a cancel, the button below re-opens it on demand.
    val context = LocalContext.current
    val activity = remember(context) { BiometricAuth.findActivity(context) }
    val biometricReady = remember(activity) {
        activity != null && settings.biometricEnabled && BiometricAuth.isAvailable(context)
    }
    var biometricBusy by remember { mutableStateOf(false) }
    val launchBiometric: () -> Unit = launch@{
        val a = activity ?: return@launch
        if (biometricBusy) return@launch
        biometricBusy = true
        BiometricAuth.prompt(
            activity = a,
            title = "فتح إدارة المحل",
            subtitle = "استخدم بصمتك أو وجهك",
            negativeText = "استخدام PIN",
            onSuccess = { biometricBusy = false; onUnlocked() },
            onFailure = { biometricBusy = false }
        )
    }
    // repeatOnLifecycle (not a one-shot): every time the app comes back to
    // the foreground while still locked (screen turned back on, returned from
    // another app) the prompt is shown again, so the person never has to
    // tap anything to unlock with a fingerprint/face.
    LaunchedEffect(biometricReady) {
        val lifecycle = activity?.lifecycle
        if (biometricReady && lifecycle != null) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { launchBiometric() }
        }
    }

    // Ticks the visible countdown once a second while locked, and clears
    // itself the moment the lockout actually expires — no manual "try
    // again" tap needed just to find out it's over.
    LaunchedEffect(isLocked) {
        while (lockRemaining > 0) {
            delay(1000)
            lockRemaining = settings.pinLockRemainingSeconds()
        }
    }

    // Claude-app style ("والهيكل"): a plain flat background instead of a
    // full-screen colored gradient wash — Claude's own screens never paint
    // the whole page in the accent color, only small elements (like the
    // icon badge below) carry it. The card no longer blends toward
    // primary/tertiary either; same flat surfaceContainerHigh panel every
    // other dialog/card in the app now uses.
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Same fixed, unblurred light pool [AppSplashScreen] uses — one
        // light source instead of a flat wash, drawn once and never
        // animated. Tinted toward the accent color now instead of plain
        // white, since it's sitting on a neutral background instead of an
        // already-colored one.
        // الهالة الشعاعية (420dp، shader لكل بكسل) للأداء القوي فقط.
        if (com.shopmanager.app.data.performance.LocalPerformanceTier.current ==
            com.shopmanager.app.data.performance.PerformanceTier.STANDARD
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-90).dp)
                    .size(420.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), Color.Transparent)
                        )
                    )
            )
        }

        // UI FIX: this column used to be a fixed, non-scrolling block — on a
        // small phone (or landscape) with the keyboard open, adjustResize
        // shrank the window and the PIN card/button got clipped off-screen.
        // Now it scrolls and keeps clear of the keyboard and system bars.
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Same glow + glass-circle language as the splash logo (see
            // AppSplashScreen's LiquidGlassLogo), just holding a lock glyph
            // instead of the storefront mark — reads as a continuation of
            // the same screen the splash just faded from, not a hand-off
            // to a different design.
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(38.dp))
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "إدارة المحل مقفلة",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .clip(RoundedCornerShape(AppGroupLargeRadius))
                    .background(cardColor)
                    .padding(24.dp)
            ) {
                AppTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6); error = false },
                    label = "أدخل رمز PIN",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    isError = error,
                    enabled = !isLocked,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isLocked) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "محاولات كثيرة خاطئة — حاول بعد $lockRemaining ثانية",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                } else if (error) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "رمز خاطئ، حاول مجدداً",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(Modifier.height(18.dp))
                AppPillButton(
                    label = "دخول",
                    enabled = !isLocked && pin.length >= 4,
                    onClick = { submit() },
                    modifier = Modifier.fillMaxWidth()
                )
                if (biometricReady) {
                    Spacer(Modifier.height(10.dp))
                    AppPillButton(
                        label = "فتح بالبصمة أو الوجه",
                        icon = Icons.Default.Fingerprint,
                        enabled = !biometricBusy,
                        tonal = true,
                        onClick = { launchBiometric() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
