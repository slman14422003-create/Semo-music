package com.shopmanager.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.performance.MotionSpecs

/**
 * حقل إدخال بلغة تصميم الإعدادات: صفٌّ مسطّح على تعبئة الصفوف، بلا حدود دائمة،
 * العنوان الصغير الرمادي داخل الصف نفسه فوق القيمة، والصف كله قابل للّمس.
 *
 * - [shape] يسمح بتجميع عدة حقول في مجموعة واحدة متلاصقة (أول/وسط/أخير) عبر
 *   [groupedRowShape] مع فاصل [AppGroupGap] بينها، تماماً مثل صفوف الإعدادات.
 * - [containerColor] الافتراضي surfaceContainerHighest ليظهر الحقل فوق أي بطاقة
 *   أو حوار (surfaceContainerHigh فما دون)، ويمكن تمرير لون آخر عند الحاجة.
 * - عند التركيز يظهر إطار رفيع بلون onSurface، وعند الخطأ بلون error.
 *
 * التواقيع القديمة محفوظة، فكل الاستدعاءات الحالية تعمل بدون تعديل.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: ImageVector? = null,
    isError: Boolean = false,
    showLabel: Boolean = true,
    shape: Shape = RoundedCornerShape(AppGroupLargeRadius),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val targetRim = when {
        isError -> cs.error.copy(alpha = 0.75f)
        focused -> cs.onSurface.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    val rim by animateColorAsState(
        targetValue = targetRim,
        animationSpec = MotionSpecs.quickSpring(),
        label = "appTextFieldRim"
    )

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(1.dp, rim, shape)
            .alpha(if (enabled) 1f else 0.6f)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
            cursorBrush = SolidColor(cs.onSurface),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            leadingIcon,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        if (showLabel) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isError) cs.error else cs.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Box {
                            if (value.isEmpty() && placeholder != null) {
                                Text(
                                    placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = cs.onSurfaceVariant.copy(alpha = 0.9f)
                                )
                            }
                            innerTextField()
                        }
                    }
                }
            }
        )
    }
}
