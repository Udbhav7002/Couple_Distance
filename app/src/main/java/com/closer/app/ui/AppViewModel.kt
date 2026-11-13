package com.closer.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.closer.app.backend.MemberInit
import com.closer.app.backend.PairingException
import com.closer.app.data.CoupleState
import com.closer.app.data.Pairing
import com.closer.app.data.ShareMode
import com.closer.app.data.WidgetSnapshot
import com.closer.app.di.Locator
import com.closer.app.location.LiveModeService
import com.closer.app.notify.ProximityChecker
import com.closer.app.refresh.Refresher
import com.closer.app.widget.WidgetMapper
import com.closer.app.widget.WidgetUpdater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val c = Locator.init(app)
    private val context: Application get() = getApplication()

    val onboarded: StateFlow<Boolean> =
        c.settings.onboarded.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val couple: StateFlow<CoupleState?> =
        c.settings.pairing.flatMapLatest { p ->
            if (p == null) flowOf(null)
            else c.backend.observeCouple(p.coupleId, p.uid).catch { emit(null) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val myName: StateFlow<String> =
        c.settings.myName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val myColorIndex: StateFlow<Int> =
        c.settings.myColorIndex.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val mode: StateFlow<ShareMode> =
        c.settings.mode.stateIn(viewModelScope, SharingStarted.Eagerly, ShareMode.ECO)
    val nearAlerts: StateFlow<Boolean> =
        c.settings.nearAlerts.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val quietFrom: StateFlow<Int> =
        c.settings.quietFrom.stateIn(viewModelScope, SharingStarted.Eagerly, 23)
    val quietTo: StateFlow<Int> =
        c.settings.quietTo.stateIn(viewModelScope, SharingStarted.Eagerly, 7)

    val refreshing = MutableStateFlow(false)
    val lastError = MutableStateFlow<String?>(null)

    fun clearError() { lastError.value = null }

    init {
        // Refresh both sides on app open.
        viewModelScope.launch { runCatching { Refresher.refresh(context) } }
        // Keep the widget in lock-step with live updates while the app is open,
        // and run the nearby-alert edge trigger on every live change too.
        viewModelScope.launch {
            couple.collect { state ->
                if (state != null) {
                    val snap = WidgetMapper.fromState(state)
                    c.snapshots.save(snap)
                    WidgetUpdater.renderAll(context, snap)
                    ProximityChecker.check(c, state, snap)
                }
            }
        }
    }

    fun createCouple(name: String, colorIndex: Int, onDone: () -> Unit) = viewModelScope.launch {
        runCatching {
            val uid = c.backend.ensureAuth()
            val state = c.backend.createCouple(MemberInit(name, colorIndex))
            c.settings.setProfile(name, colorIndex)
            c.settings.setPairing(Pairing(state.coupleId, state.code, uid))
            c.settings.setOnboarded()
            Refresher.refresh(context)
        }.onSuccess { onDone() }
            .onFailure { lastError.value = it.message ?: "Could not create the couple" }
    }

    fun joinCouple(code: String, name: String, colorIndex: Int, onDone: () -> Unit) =
        viewModelScope.launch {
            runCatching {
                val uid = c.backend.ensureAuth()
                val state = c.backend.joinCouple(code, MemberInit(name, colorIndex))
                c.settings.setProfile(name, colorIndex)
                c.settings.setPairing(Pairing(state.coupleId, state.code, uid))
                c.settings.setOnboarded()
                Refresher.refresh(context)
            }.onSuccess { onDone() }
                .onFailure {
                    lastError.value =
                        if (it is PairingException) it.message else (it.message ?: "Could not join")
                }
        }

    fun refresh() = viewModelScope.launch {
        refreshing.value = true
        runCatching { Refresher.refresh(context) }
            .onFailure { lastError.value = it.message ?: "Refresh failed" }
        refreshing.value = false
    }

    fun setRadius(radiusM: Int) = viewModelScope.launch {
        couple.value?.let { runCatching { c.backend.setRadius(it.coupleId, radiusM) } }
    }

    fun setMode(newMode: ShareMode) = viewModelScope.launch {
        c.settings.setMode(newMode)
        runCatching {
            if (newMode == ShareMode.LIVE) LiveModeService.start(context) else LiveModeService.stop(context)
        }.onFailure { lastError.value = "Live mode needs location permission" }
    }

    fun setSharing(on: Boolean) = viewModelScope.launch {
        c.settings.setSharing(on)
        couple.value?.let { runCatching { c.backend.setSharing(it.coupleId, it.me.uid, on) } }
    }

    fun setNearAlerts(on: Boolean) = viewModelScope.launch { c.settings.setNearAlerts(on) }

    /** Offers the system dialog to pin the spectacles widget to the home screen. */
    fun pinWidget() {
        val mgr = android.appwidget.AppWidgetManager.getInstance(context)
        val provider = android.content.ComponentName(context, com.closer.app.widget.SpectaclesWidgetProvider::class.java)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(provider, null, null)
        }
    }

    fun setQuietHours(from: Int, to: Int) = viewModelScope.launch { c.settings.setQuietHours(from, to) }

    fun updateProfile(name: String, colorIndex: Int) = viewModelScope.launch {
        c.settings.setProfile(name, colorIndex)
        couple.value?.let {
            runCatching { c.backend.updateMe(it.coupleId, it.me.uid, name, colorIndex) }
        }
    }

    fun unpair() = viewModelScope.launch {
        c.settings.setPairing(null)
        c.snapshots.save(WidgetSnapshot())
        WidgetUpdater.renderAll(context)
    }

    /** Back to the pairing flow (keeps name/color) — used when the link is missing. */
    fun repairPairing() = viewModelScope.launch {
        c.settings.setPairing(null)
        c.settings.setOnboarded(false)
    }
}
