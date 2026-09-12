package com.sammy.running

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.sammy.running.core.RawRun
import com.sammy.running.core.GitHubPublisher
import com.sammy.running.core.GitHubDeviceAuthClient
import com.sammy.running.health.RepositoryFactory

class RunViewModel(application: Application) : AndroidViewModel(application) {
    val repository = RepositoryFactory.create(application)
    var runs: List<RawRun> = emptyList()
    var selectedId: String? = null
    var routeAllowed = false
    var hasLoaded = false
    val settings = GitHubSettingsStore(application)
    val publishedRuns = PublishedRunStore(application)
    val publisher = GitHubPublisher()
    val deviceAuth = GitHubDeviceAuthClient()
    var publishingId: String? = null
    var publishMessage: String? = null
    var showingSettings = false
}
