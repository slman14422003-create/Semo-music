package com.shopmanager.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * REDESIGN ("شريط البحث بشكل جميل"): the old search UI in both المواد/الأسعار
 * and الديون was a bare [BasicTextField] with no fill/border of its own —
 * just text floating directly on the header background, with a hint that
 * was barely distinguishable from real input. This gives every screen's
 * search the same real, pill-shaped search field (leading magnifier glyph,
 * a filled rounded-full track, an inline clear glyph) — the same shape
 * language ChatGPT's own search field uses — instead of each screen
 * re-implementing its own undecorated field.
 *
 * A leading back arrow sits outside the pill (closes search entirely),
 * matching both screens' previous behavior.
 */
@Composable
fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    // MaterialsHeader has no TopAppBar of its own (it builds the whole row
    // by hand), so it needs this composable to draw its own leading back
    // arrow. DebtsScreen instead hosts this inside a real TopAppBar, which
    // already has its own navigationIcon slot for that same back arrow —
    // drawing a second one here would duplicate it, so that caller passes
    // false and only wants the pill field itself.
    showBackButton: Boolean = true
) {
    // UI FIX: the keyboard's Search key used to do nothing (onSearch = {}),
    // leaving the keyboard covering half the results. It now closes it.
    val keyboard = LocalSoftwareKeyboardController.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (showBackButton) {
            GradientIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "إغلاق البحث",
                onClick = onClose
            )
            Spacer(Modifier.width(10.dp))
        }
        Box(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp)
        ) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                )
                            }
                            inner()
                        }
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "مسح",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(34.dp) // was 18dp — far below a comfortable touch target
                            .clip(RoundedCornerShape(50))
                            .clickable { onQueryChange("") }
                            .wrapContentSize()
                            .size(18.dp)
                    )
                }
            }
        }
    }
}


/**
 * Always-visible capsule search field: a fully rounded track with a circular
 * search glyph on the leading edge, a hairline border that brightens on focus
 * and a circular clear button. Used as the primary search surface of the
 * list screens.
 */
@Composable
fun AppCapsuleSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val low = com.shopmanager.app.data.performance.LocalPerformanceTier.current ==
        com.shopmanager.app.data.performance.PerformanceTier.LOW
    val colorSpec = if (low) androidx.compose.animation.core.snap<androidx.compose.ui.graphics.Color>()
    else androidx.compose.animation.core.tween<androidx.compose.ui.graphics.Color>(com.shopmanager.app.data.performance.MotionSpecs.baseMs)
    val borderColor by animateColorAsState(
        targetValue = if (focused) cs.onSurface.copy(alpha = 0.55f) else cs.onSurface.copy(alpha = 0.08f),
        animationSpec = colorSpec, label = "capsuleSearchBorder"
    )
    val glyphBg by animateColorAsState(
        targetValue = if (focused) cs.onSurface else cs.surfaceContainerHighest,
        animationSpec = colorSpec, label = "capsuleSearchGlyphBg"
    )
    val glyphTint by animateColorAsState(
        targetValue = if (focused) cs.surface else cs.onSurfaceVariant,
        animationSpec = colorSpec, label = "capsuleSearchGlyphTint"
    )
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh)
            .border(BorderStroke(1.2.dp, borderColor), CircleShape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(glyphBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = glyphTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
                singleLine = true,
                interactionSource = interaction,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = cs.onSurface,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(cs.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurfaceVariant.copy(alpha = 0.9f)
                        )
                    }
                    inner()
                }
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceContainerHighest)
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "مسح",
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
