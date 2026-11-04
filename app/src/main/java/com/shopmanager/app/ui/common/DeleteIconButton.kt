package com.shopmanager.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shopmanager.app.ui.theme.LocalSemanticColors

/**
 * A small, clearly-marked circular "×" delete affordance shown next to a
 * list row. Used instead of swipe-to-delete: with swipe navigation between
 * top-level screens (see the HorizontalPager in MainActivity), a horizontal
 * swipe-to-delete gesture on each row would fight the page-swipe gesture for
 * the same drag axis. A tap target is unambiguous, works the same everywhere,
 * and never gets tangled up with page swiping.
 *
 * Now a thin wrapper around [ActionIconButton] so this is guaranteed to
 * match the green check button's size and press animation exactly — see
 * the fix note on [ActionIconButton] for why that used to drift apart.
 */
@Composable
fun DeleteIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String = "حذف") {
    ActionIconButton(
        icon = Icons.Default.Close,
        tint = LocalSemanticColors.current.danger,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier
    )
}
