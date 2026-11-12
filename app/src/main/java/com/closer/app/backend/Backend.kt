package com.closer.app.backend

import com.closer.app.data.CoupleState
import com.closer.app.data.LocationSample
import kotlinx.coroutines.flow.Flow

data class MemberInit(val name: String, val colorIndex: Int)

class PairingException(message: String) : Exception(message)

interface Backend {
    /** Anonymous account per device; returns the stable uid. */
    suspend fun ensureAuth(): String

    suspend fun createCouple(me: MemberInit): CoupleState
    suspend fun joinCouple(code: String, me: MemberInit): CoupleState

    /** Live stream of the couple (both members, radius); null until pairing exists. */
    fun observeCouple(coupleId: String, myUid: String): Flow<CoupleState?>

    suspend fun pushLocation(coupleId: String, uid: String, sample: LocationSample)
    suspend fun setSharing(coupleId: String, uid: String, on: Boolean)
    suspend fun setRadius(coupleId: String, radiusM: Int)
    suspend fun updateMe(coupleId: String, uid: String, name: String, colorIndex: Int)
}
