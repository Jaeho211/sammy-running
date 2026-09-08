package com.sammy.running.health

import android.app.Activity
import android.content.Context
import com.sammy.running.core.Fixtures

object RepositoryFactory {
    fun create(context: Context): SamsungHealthRepository = FixtureSamsungHealthRepository()
}

class FixtureSamsungHealthRepository : SamsungHealthRepository {
    override val isDemo = true
    override suspend fun permissions() = AccessState(true, true)
    override suspend fun requestPermissions(activity: Activity) = permissions()
    override suspend fun recentRuns() = RecentRuns(Fixtures.runs().sortedByDescending { it.startTime }, true)
}
