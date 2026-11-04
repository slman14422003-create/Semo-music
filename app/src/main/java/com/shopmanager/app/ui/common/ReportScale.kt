package com.shopmanager.app.ui.common

import android.content.Context
import com.shopmanager.app.data.performance.DevicePerformance
import com.shopmanager.app.data.performance.PerformanceTier

object ReportScale {

    fun factor(context: Context): Float =
        if (DevicePerformance.detectTier(context) == PerformanceTier.LOW) 0.67f else 1f
}
