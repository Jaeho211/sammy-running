package com.sammy.running

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.sammy.running.core.RawRun
import com.sammy.running.health.RepositoryFactory

class RunViewModel(application: Application) : AndroidViewModel(application) {
    val repository = RepositoryFactory.create(application)
    var runs: List<RawRun> = emptyList()
    var selectedId: String? = null
    var toleranceMeters = 5.0
    var routeAllowed = false
    var hasLoaded = false
}
