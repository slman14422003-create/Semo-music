package com.shopmanager.app.data.updates

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * BUG FIXED ("لازم التحميل ينحفظ ... مو كل ما بدي اطلع وفوت اعيد تحميلة"،
 * "والتحميل يكون في الخلفية"): where the in-app update download's progress
 * actually lives now — a plain process-wide singleton, not `remember`ed
 * state inside SettingsScreen/ForceUpdateScreen. Before, `isDownloadingUpdate`/
 * `downloadPercent`/`downloadedApk` were local to whichever Composable
 * started the download, and the download itself ran inside that
 * Composable's `rememberCoroutineScope()` — the moment the person left that
 * screen (even just backgrounding the app), Compose disposed the
 * composition, its scope was cancelled along with it, and the whole
 * download died mid-stream. Coming back showed the dialog again from 0%
 * because the `remember` vars had reset too.
 *
 * Now [UpdateDownloadService] (a real foreground service — see its own doc)
 * is the only thing that ever writes here, and it keeps running regardless
 * of what's on screen or whether the app is in the foreground at all.
 * SettingsScreen's dialog and ForceUpdateScreen both just [collectAsState]
 * on [phase] and render whatever it currently says — so leaving the
 * screen, rotating, or switching to another app no longer touches the
 * download; only it finishing, failing, or actually being cancelled does.
 */
sealed class UpdateDownloadPhase {
    data object Idle : UpdateDownloadPhase()
    data class InProgress(val percent: Int) : UpdateDownloadPhase()
    data class Done(val file: File) : UpdateDownloadPhase()
    data class Error(val message: String) : UpdateDownloadPhase()
}

object UpdateDownloadState {
    private val _phase = MutableStateFlow<UpdateDownloadPhase>(UpdateDownloadPhase.Idle)
    val phase: StateFlow<UpdateDownloadPhase> = _phase

    /** The manifest currently downloading (or last downloaded), so a
     * screen that (re)composes mid-download — e.g. the person left
     * Settings and came back — can re-show the right version info instead
     * of just a bare progress bar. Cleared on [reset]. */
    @Volatile
    var activeManifest: UpdateManifest? = null
        private set

    fun start(manifest: UpdateManifest) {
        activeManifest = manifest
        _phase.value = UpdateDownloadPhase.InProgress(0)
    }

    fun progress(percent: Int) {
        _phase.value = UpdateDownloadPhase.InProgress(percent)
    }

    fun done(file: File) {
        _phase.value = UpdateDownloadPhase.Done(file)
    }

    fun error(message: String) {
        _phase.value = UpdateDownloadPhase.Error(message)
    }

    /** Back to idle — after a successful install hand-off, or once the
     * person has seen/dismissed a failed download. */
    fun reset() {
        _phase.value = UpdateDownloadPhase.Idle
        activeManifest = null
    }
}
