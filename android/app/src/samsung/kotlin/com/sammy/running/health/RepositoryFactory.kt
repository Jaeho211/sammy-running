package com.sammy.running.health

import android.content.Context

object RepositoryFactory {
    fun create(context: Context): SamsungHealthRepository = SdkSamsungHealthRepository(context.applicationContext)
}
