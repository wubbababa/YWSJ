package com.example.apps

import org.junit.Assert.assertTrue
import org.junit.Test

class AppRankingTest {
    @Test
    fun higherLaunchCountRanksFirst() {
        val result = compareLauncherRank(
            firstLaunchCount = 5,
            firstUsageTimeMillis = 1_000L,
            firstLastUsedTime = 1_000L,
            firstLabel = "A",
            secondLaunchCount = 2,
            secondUsageTimeMillis = 99_000L,
            secondLastUsedTime = 99_000L,
            secondLabel = "B"
        )

        assertTrue(result < 0)
    }

    @Test
    fun usageTimeBreaksLaunchCountTie() {
        val result = compareLauncherRank(
            firstLaunchCount = 3,
            firstUsageTimeMillis = 60_000L,
            firstLastUsedTime = 1_000L,
            firstLabel = "A",
            secondLaunchCount = 3,
            secondUsageTimeMillis = 10_000L,
            secondLastUsedTime = 99_000L,
            secondLabel = "B"
        )

        assertTrue(result < 0)
    }

    @Test
    fun lastUsedTimeThenLabelBreakRemainingTies() {
        val lastUsedResult = compareLauncherRank(
            firstLaunchCount = 3,
            firstUsageTimeMillis = 60_000L,
            firstLastUsedTime = 20_000L,
            firstLabel = "B",
            secondLaunchCount = 3,
            secondUsageTimeMillis = 60_000L,
            secondLastUsedTime = 10_000L,
            secondLabel = "A"
        )
        val labelResult = compareLauncherRank(
            firstLaunchCount = 3,
            firstUsageTimeMillis = 60_000L,
            firstLastUsedTime = 20_000L,
            firstLabel = "A",
            secondLaunchCount = 3,
            secondUsageTimeMillis = 60_000L,
            secondLastUsedTime = 20_000L,
            secondLabel = "B"
        )

        assertTrue(lastUsedResult < 0)
        assertTrue(labelResult < 0)
    }
}