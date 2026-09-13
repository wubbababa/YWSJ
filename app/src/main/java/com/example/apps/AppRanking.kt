package com.example.apps

import java.util.Locale

internal fun compareLauncherRank(
    firstLaunchCount: Int,
    firstUsageTimeMillis: Long,
    firstLastUsedTime: Long,
    firstLabel: String,
    secondLaunchCount: Int,
    secondUsageTimeMillis: Long,
    secondLastUsedTime: Long,
    secondLabel: String
): Int {
    val launchCountComparison = secondLaunchCount.compareTo(firstLaunchCount)
    if (launchCountComparison != 0) return launchCountComparison

    val usageTimeComparison = secondUsageTimeMillis.compareTo(firstUsageTimeMillis)
    if (usageTimeComparison != 0) return usageTimeComparison

    val lastUsedComparison = secondLastUsedTime.compareTo(firstLastUsedTime)
    if (lastUsedComparison != 0) return lastUsedComparison

    return firstLabel.lowercase(Locale.getDefault())
        .compareTo(secondLabel.lowercase(Locale.getDefault()))
}