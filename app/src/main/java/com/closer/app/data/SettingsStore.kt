package com.closer.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "closer_settings")

data class Pairing(val coupleId: String, val code: String, val uid: String)

class SettingsStore(private val context: Context) {

    private object K {
        val myName = stringPreferencesKey("my_name")
        val myColor = intPreferencesKey("my_color")
        val coupleId = stringPreferencesKey("couple_id")
        val coupleCode = stringPreferencesKey("couple_code")
        val uid = stringPreferencesKey("uid")
        val mode = stringPreferencesKey("mode")
        val quietFrom = intPreferencesKey("quiet_from")
        val quietTo = intPreferencesKey("quiet_to")
        val nearAlerts = booleanPreferencesKey("near_alerts")
        val sharingOn = booleanPreferencesKey("sharing_on")
        val onboarded = booleanPreferencesKey("onboarded")
    }

    val myName: Flow<String> = context.settingsDataStore.data.map { it[K.myName] ?: "" }
    val myColorIndex: Flow<Int> = context.settingsDataStore.data.map { it[K.myColor] ?: 0 }
    val pairing: Flow<Pairing?> = context.settingsDataStore.data.map {
        val id = it[K.coupleId]
        if (id.isNullOrEmpty()) null else Pairing(id, it[K.coupleCode] ?: "", it[K.uid] ?: "")
    }
    val mode: Flow<ShareMode> = context.settingsDataStore.data.map {
        if (it[K.mode] == ShareMode.LIVE.name) ShareMode.LIVE else ShareMode.ECO
    }
    val nearAlerts: Flow<Boolean> = context.settingsDataStore.data.map { it[K.nearAlerts] ?: true }
    val sharingOn: Flow<Boolean> = context.settingsDataStore.data.map { it[K.sharingOn] ?: true }
    val quietFrom: Flow<Int> = context.settingsDataStore.data.map { it[K.quietFrom] ?: 23 }
    val quietTo: Flow<Int> = context.settingsDataStore.data.map { it[K.quietTo] ?: 7 }
    val onboarded: Flow<Boolean> = context.settingsDataStore.data.map { it[K.onboarded] ?: false }

    suspend fun setProfile(name: String, colorIndex: Int) = context.settingsDataStore.edit {
        it[K.myName] = name
        it[K.myColor] = colorIndex
    }

    suspend fun setPairing(p: Pairing?) = context.settingsDataStore.edit {
        if (p == null) {
            it.remove(K.coupleId); it.remove(K.coupleCode); it.remove(K.uid)
        } else {
            it[K.coupleId] = p.coupleId
            it[K.coupleCode] = p.code
            it[K.uid] = p.uid
        }
    }

    suspend fun setMode(mode: ShareMode) = context.settingsDataStore.edit {
        it[K.mode] = mode.name
    }

    suspend fun setNearAlerts(on: Boolean) = context.settingsDataStore.edit {
        it[K.nearAlerts] = on
    }

    suspend fun setSharing(on: Boolean) = context.settingsDataStore.edit {
        it[K.sharingOn] = on
    }

    suspend fun setQuietHours(from: Int, to: Int) = context.settingsDataStore.edit {
        it[K.quietFrom] = from
        it[K.quietTo] = to
    }

    suspend fun setOnboarded(on: Boolean = true) = context.settingsDataStore.edit {
        if (on) it[K.onboarded] = true else it.remove(K.onboarded)
    }

    suspend fun snapshotPairing(): Pairing? = pairing.first()
}
