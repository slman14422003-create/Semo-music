package com.shopmanager.app.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The one pull-to-refresh surface used across the whole app (Home, Debts,
 * Materials) so the gesture, the spinner, and its brand color are all
 * identical everywhere instead of each screen wiring its own — a single
 * shared wrapper is what makes it feel "منسق" (consistent) across tabs
 * rather than three separate implementations that could drift apart.
 *
 * Data on every screen already stays live via Firestore snapshot
 * listeners, so this isn't the only way new data appears — but the
 * familiar drag-down gesture (the "Facebook-style" refresh) gives people
 * an explicit, reassuring action, and it doubles as a real recovery path:
 * [onRefresh] forces a server round trip that can un-stick a listener that
 * silently stalled after a network drop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefreshContent(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isRefreshing,
                state = state,
                color = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        content()
    }
}
