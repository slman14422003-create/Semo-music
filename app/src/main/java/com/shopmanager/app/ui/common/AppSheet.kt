package com.shopmanager.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * ورقة سفلية عامة بنفس شكل أوراق الإعدادات (عنوان سيريف 24sp، خلفية surface،
 * زوايا علوية 28dp، محتوى بمسافات [AppScreenPadding]) لكن بإمكان استعمالها خارج
 * شاشة الإعدادات (مثل مربع التحديث عند فتح التطبيق).
 *
 * [dismissible] = false يمنع الإغلاق بالسحب أو باللمس خارج الورقة (أثناء تنزيل
 * التحديث مثلاً). [content] يستلم دالة close تغلق الورقة بحركة الانزلاق الكاملة
 * ثم تستدعي [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    title: String,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit
) {
    // dismissible قد يتغير أثناء عرض الورقة (يبدأ التنزيل بعد فتحها)، فتُقرأ قيمته
    // الحالية وقت الاستدعاء بدل القيمة الملتقطة عند أول رسم.
    val dismissibleNow = rememberUpdatedState(dismissible)
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> dismissibleNow.value || target != SheetValue.Hidden }
    )
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }
    val glass = blurEnabled()
    ModalBottomSheet(
        onDismissRequest = { if (dismissible) onDismiss() },
        sheetState = state,
        dragHandle = {
            Box(Modifier.fillMaxWidth()) {
                if (glass) {
                    BlurBackdrop(
                        Modifier.matchParentSize(),
                        tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
                        radius = 16f
                    )
                }
                BottomSheetDefaults.DragHandle(Modifier.align(Alignment.Center))
            }
        },
        containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
        if (glass) {
            BlurBackdrop(
                Modifier.matchParentSize(),
                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
                radius = 16f
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = AppScreenPadding, end = AppScreenPadding, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(AppScreenPadding)
        ) {
            Text(
                title,
                modifier = Modifier.padding(horizontal = 6.dp),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            content(close)
        }
        }
    }
}

/** صف معلومة مسطّح (عنوان في البداية وقيمة في النهاية) بتعبئة صفوف الإعدادات. */
@Composable
fun AppInfoRow(
    label: String,
    value: String,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    AppRowSurface(shape = shape, modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
