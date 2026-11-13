package com.closer.app

import android.app.Application
import com.closer.app.di.Locator
import com.closer.app.refresh.PeriodicWorker

class CloserApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Locator.init(this)
        try {
            PeriodicWorker.ensure(this)
        } catch (_: Throwable) {
            // Safe in unit-test environments without WorkManager runtime
        }
    }
}
