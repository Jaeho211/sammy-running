package com.sammy.running.health

import android.app.Activity
import com.sammy.running.core.RawRun

data class AccessState(val exercise: Boolean, val route: Boolean)
data class RecentRuns(val runs: List<RawRun>, val routeAllowed: Boolean)

interface SamsungHealthRepository {
    val isDemo: Boolean
    fun errorMessage(error: Exception): String = "연결을 완료하지 못했어요. 잠시 후 다시 시도해 주세요."
    suspend fun permissions(): AccessState
    suspend fun requestPermissions(activity: Activity): AccessState
    /** Last 90 days, newest first. Does not persist or publish source records. */
    suspend fun recentRuns(): RecentRuns
}
