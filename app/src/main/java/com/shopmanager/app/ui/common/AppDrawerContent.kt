package com.shopmanager.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.BuildConfig
import com.shopmanager.app.data.performance.MotionSpecs

/**
 * القائمة الجانبية — أُعيد تصميمها لتتبع نفس لغة بقية التطبيق (الإعدادات
 * والبطاقات): صفوف مجمّعة بزوايا 20/4 وفواصل 3dp بدل صفوف عائمة بلا خلفية،
 * وأيقونة داخل شارة دائرية لكل وجهة، ورأس بشعار المتجر واسم التطبيق والإصدار.
 *
 * الحالة المحددة بدون أي ألوان زائدة (التطبيق أحادي: أبيض/أسود/رمادي):
 * الصف يأخذ خلفية أعلى درجة، والشارة تنقلب إلى عكس السمة (دائرة بلون النص
 * وأيقونة بلون الخلفية)، ونقطة صغيرة بنهاية الصف — فيُعرف التبويب الحالي
 * بنظرة واحدة حتى مع الإضاءة العالية، وبدون الاعتماد على اللون وحده.
 *
 * العرض ثابت 316dp بدل ملء ~88% من الشاشة (الافتراضي كان 360dp)، فيبقى
 * جزء مقروء من الشاشة خلفه بدل شريط رفيع من البطاقات المعتمة على الحافة،
 * والزاوية الداخلية بانحناء 28dp لتطابق نوافذ الحوار.
 *
 * ترتيب الوجهات (الرئيسية، الديون، المواد، الملاحظات) وزر الإعدادات وزر لوحة
 * المسؤول ومنطق الاستدعاءات كلها كما كانت — تغيير بصري فقط.
 */
private data class DrawerNavItem(val icon: ImageVector, val label: String, val page: Int)

private val drawerNavItems = listOf(
    DrawerNavItem(Icons.Default.Home, "الشاشة الرئيسية", page = 0),
    DrawerNavItem(Icons.Default.AttachMoney, "الديون", page = 1),
    DrawerNavItem(Icons.Default.Inventory2, "المواد والأسعار", page = 2),
    DrawerNavItem(Icons.Default.Notes, "ملاحظات هامة", page = 3)
)

private val DrawerWidth = 316.dp
private val DrawerCornerRadius = 28.dp
private val BadgeSize = 40.dp

@Composable
fun AppDrawerContent(
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdmin: () -> Unit = {},
    onOpenMissingFinder: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme

    ModalDrawerSheet(
        modifier = Modifier.width(DrawerWidth),
        drawerShape = RoundedCornerShape(topEnd = DrawerCornerRadius, bottomEnd = DrawerCornerRadius),
        drawerContainerColor = cs.surface
    ) {
        Column(Modifier.fillMaxHeight()) {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))

            DrawerHeader()

            Column(
                Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(AppGroupGap)
            ) {
                val lastIndex = drawerNavItems.lastIndex
                drawerNavItems.forEachIndexed { index, item ->
                    DrawerRow(
                        icon = item.icon,
                        label = item.label,
                        selected = item.page == selectedPage,
                        shape = groupedRowShape(index, lastIndex),
                        onClick = { onSelectPage(item.page) }
                    )
                }
            }

            // يدفع الإعدادات/لوحة المسؤول لأسفل القائمة مهما كان عدد الصفوف.
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                DrawerRow(
                    icon = Icons.Default.AutoAwesome,
                    label = "إيجاد المواد الناقصة",
                    selected = false,
                    shape = RoundedCornerShape(AppGroupLargeRadius),
                    onClick = onOpenMissingFinder,
                    badge = "AI"
                )
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppGroupGap + 3.dp)
            ) {
                DrawerRow(
                    icon = Icons.Default.Settings,
                    label = "الإعدادات",
                    selected = false,
                    shape = RoundedCornerShape(AppGroupLargeRadius),
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f)
                )
                AdminButton(onClick = onOpenAdmin)
            }
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** شعار المتجر + اسم التطبيق + رقم الإصدار (من BuildConfig، فيتحدث تلقائيًا). */
@Composable
private fun DrawerHeader() {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(cs.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Storefront,
                contentDescription = null,
                tint = cs.onSurface,
                modifier = Modifier.size(26.dp)
            )
        }
        Column {
            Text(
                "إدارة المحل",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
            Text(
                "الإصدار ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    val cs = MaterialTheme.colorScheme

    // انتقال لوني قصير بين المحدد وغير المحدد بدل القفزة المباشرة.
    val rowColor by animateColorAsState(
        targetValue = if (selected) cs.surfaceContainerHighest else cs.surfaceContainerHigh,
        animationSpec = MotionSpecs.quickSpring(),
        label = "drawerRowColor"
    )
    val badgeColor by animateColorAsState(
        targetValue = if (selected) cs.onSurface else cs.surfaceContainerHighest,
        animationSpec = MotionSpecs.quickSpring(),
        label = "drawerBadgeColor"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) cs.surface else cs.onSurface,
        animationSpec = MotionSpecs.quickSpring(),
        label = "drawerIconColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(rowColor)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .size(BadgeSize)
                .clip(CircleShape)
                .background(badgeColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = cs.onSurface
        )
        if (badge != null) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(cs.onSurface)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(badge, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = cs.surface)
            }
        }
        if (selected) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(cs.onSurface)
            )
        }
    }
}

/** زر لوحة المسؤول: دائرة بنفس ارتفاع صف الإعدادات (64dp) وبنفس لون الصفوف. */
@Composable
private fun AdminButton(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(AppGroupLargeRadius))
            .background(cs.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.AdminPanelSettings,
            contentDescription = "لوحة المسؤول",
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}
