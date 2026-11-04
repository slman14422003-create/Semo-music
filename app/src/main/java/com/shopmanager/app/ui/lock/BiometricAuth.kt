package com.shopmanager.app.ui.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * FEATURE ADDED ("فتح القفل بالبصمة والوجه"): thin wrapper over
 * androidx.biometric so LockScreen/SettingsScreen never touch the
 * BiometricPrompt API directly.
 *
 * BIOMETRIC_WEAK (class 2) is used on purpose: it covers fingerprint AND
 * face unlock — most phones' face unlock is only class 2, so asking for
 * STRONG would hide it. That's acceptable here because this is only a
 * convenience shortcut for the same local app-lock the PIN protects (no
 * crypto keys are bound to it); the PIN always remains available.
 */
object BiometricAuth {

    /** True when the device has hardware AND at least one enrolled fingerprint/face. */
    fun isAvailable(context: Context): Boolean =
        runCatching {
            BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)

    /** BiometricPrompt needs a FragmentActivity; walks the ContextWrapper chain Compose hands us. */
    fun findActivity(context: Context): FragmentActivity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is FragmentActivity) return c
            c = c.baseContext
        }
        return null
    }

    /**
     * Shows the system prompt. [onFailure] gets `true` when the person
     * deliberately dismissed it / tapped the PIN button (so the caller
     * shouldn't nag again), `false` for a system error (lockout, canceled
     * because the app went to the background, ...).
     */
    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeText: String,
        onSuccess: () -> Unit,
        onFailure: (userDismissed: Boolean) -> Unit
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure(
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                )
            }
            // onAuthenticationFailed (a single unrecognised finger/face) is
            // deliberately ignored: the system dialog stays open and lets the
            // person retry by itself.
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .setConfirmationRequired(false) // face unlock: no extra "confirm" tap
            .build()
        runCatching {
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
        }.onFailure { onFailure(false) }
    }
}
