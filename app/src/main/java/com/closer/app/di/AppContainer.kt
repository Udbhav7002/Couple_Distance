package com.closer.app.di

import android.content.Context
import com.closer.app.backend.Backend
import com.closer.app.backend.FirebaseBackend
import com.closer.app.backend.MockBackend
import com.closer.app.data.SettingsStore
import com.closer.app.data.SnapshotStore
import com.closer.app.location.LocationEngine
import com.closer.app.notify.Notifier
import com.google.firebase.FirebaseApp

/**
 * Deliberately tiny manual DI — one container, one locator, no framework.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val settings = SettingsStore(appContext)
    val snapshots = SnapshotStore(appContext)
    val locationEngine = LocationEngine(appContext)
    val notifier = Notifier(appContext)

    /** Firebase when configured (google-services.json present), mock otherwise. */
    val backend: Backend =
        if (FirebaseApp.getApps(appContext).isNotEmpty()) FirebaseBackend()
        else MockBackend(appContext)

    val mock: MockBackend? get() = backend as? MockBackend

    val usingFirebase: Boolean get() = backend is FirebaseBackend
}

object Locator {
    lateinit var container: AppContainer
        private set

    fun init(context: Context): AppContainer {
        if (!::container.isInitialized) container = AppContainer(context)
        return container
    }
}
