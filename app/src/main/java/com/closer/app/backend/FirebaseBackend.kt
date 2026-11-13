package com.closer.app.backend

import com.closer.app.data.CoupleState
import com.closer.app.data.GeoPoint
import com.closer.app.data.LocationSample
import com.closer.app.data.MemberProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * Firestore-backed backend:
 *   couples/{coupleId}            { code, radiusM }
 *   couples/{coupleId}/members/{uid} { name, colorIndex, sharingOn, loc {lat,lng,acc,ts} }
 *
 * Security rules (set in the Firebase console) restrict every path to the two
 * member UIDs, so locations are visible to exactly the couple.
 */
class FirebaseBackend : Backend {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    init {
        val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(com.google.firebase.firestore.PersistentCacheSettings.newBuilder().build())
            .build()
        db.firestoreSettings = settings
    }

    override suspend fun ensureAuth(): String {
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().await().user!!.uid
    }

    override suspend fun createCouple(me: MemberInit): CoupleState {
        val uid = ensureAuth()
        val coupleRef = db.collection("couples").document()
        val code = MockBackend.generateCode()
        coupleRef.set(
            mapOf(
                "code" to code,
                "radiusM" to 200,
                "createdAt" to System.currentTimeMillis(),
                "members" to listOf(uid),
            )
        ).await()
        writeMember(coupleRef.id, uid, me.name, me.colorIndex, sharingOn = true)
        return CoupleState(coupleRef.id, code, 200, myProfile(coupleRef.id, uid, me.name, me.colorIndex), null)
    }

    override suspend fun joinCouple(code: String, me: MemberInit): CoupleState {
        val uid = ensureAuth()
        val snap = db.collection("couples").whereEqualTo("code", code.trim().uppercase())
            .limit(1).get().await()
        val doc = snap.documents.firstOrNull() ?: throw PairingException("No couple found for code $code")

        // Use a transaction to prevent two people joining simultaneously and creating a 3-member couple
        db.runTransaction { txn ->
            val freshDoc = txn.get(doc.reference)
            val members = (freshDoc.get("members") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (members.size >= 2 && uid !in members) throw PairingException("That couple already has two members")
            // Reject codes older than 24 hours
            val createdAt = freshDoc.getLong("createdAt") ?: 0L
            if (System.currentTimeMillis() - createdAt > 24 * 60 * 60 * 1000L) {
                throw PairingException("This pairing code has expired. Ask your partner to create a new couple.")
            }
            txn.update(doc.reference, "members", members + uid)
        }.await()

        writeMember(doc.id, uid, me.name, me.colorIndex, sharingOn = true)
        val radius = doc.getLong("radiusM")?.toInt() ?: 200
        val partnerUid = (doc.get("members") as? List<*>)?.filterIsInstance<String>()?.firstOrNull() ?: ""
        val partner = memberProfile(doc.id, partnerUid)
        return CoupleState(doc.id, code, radius, myProfile(doc.id, uid, me.name, me.colorIndex), partner)
    }

    override fun observeCouple(coupleId: String, myUid: String): Flow<CoupleState> =
        callbackFlow {
            val coupleRef = db.collection("couples").document(coupleId)
            var latestCouple: DocumentSnapshot? = null
            var latestMembers: Map<String, DocumentSnapshot> = emptyMap()

            var coupleReg: ListenerRegistration? = null
            var membersReg: ListenerRegistration? = null

            fun emit() {
                val c = latestCouple ?: return
                val code = c.getString("code") ?: ""
                val radius = c.getLong("radiusM")?.toInt() ?: 200
                val members = latestMembers.mapValues { (_, d) -> d.toProfile() }
                val meDoc = members[myUid]
                if (meDoc == null) return
                val partner = members.entries.firstOrNull { it.key != myUid }?.value
                trySend(
                    CoupleState(
                        coupleId = coupleId,
                        code = code,
                        radiusM = radius,
                        me = meDoc,
                        partner = partner,
                    )
                )
            }

            coupleReg = coupleRef.addSnapshotListener { snap, e ->
                if (e != null) { close(e); return@addSnapshotListener }
                latestCouple = snap; emit()
            }
            membersReg = coupleRef.collection("members").addSnapshotListener { snap, e ->
                if (e != null) { close(e); return@addSnapshotListener }
                latestMembers = snap?.documents?.associateBy { it.id } ?: emptyMap(); emit()
            }

            awaitClose { coupleReg?.remove(); membersReg?.remove() }
        }

    override suspend fun pushLocation(coupleId: String, uid: String, sample: LocationSample) {
        db.collection("couples").document(coupleId)
            .collection("members").document(uid)
            .update(
                "loc",
                mapOf(
                    "lat" to sample.point.lat,
                    "lng" to sample.point.lng,
                    "acc" to sample.accuracyM,
                    "ts" to sample.timestampMs,
                ),
                "updatedAt" to System.currentTimeMillis(),
            ).await()
    }

    override suspend fun setSharing(coupleId: String, uid: String, on: Boolean) {
        db.collection("couples").document(coupleId)
            .collection("members").document(uid)
            .update("sharingOn", on).await()
    }

    override suspend fun setRadius(coupleId: String, radiusM: Int) {
        db.collection("couples").document(coupleId).update("radiusM", radiusM).await()
    }

    override suspend fun updateMe(coupleId: String, uid: String, name: String, colorIndex: Int) {
        db.collection("couples").document(coupleId)
            .collection("members").document(uid)
            .update("name", name, "colorIndex", colorIndex.toLong()).await()
    }

    private suspend fun writeMember(
        coupleId: String, uid: String, name: String, colorIndex: Int, sharingOn: Boolean,
    ) {
        db.collection("couples").document(coupleId).collection("members").document(uid).set(
            mapOf(
                "uid" to uid,
                "name" to name,
                "colorIndex" to colorIndex.toLong(),
                "sharingOn" to sharingOn,
                "updatedAt" to System.currentTimeMillis(),
            )
        ).await()
    }

    private suspend fun myProfile(coupleId: String, uid: String, name: String, colorIndex: Int): MemberProfile {
        val doc = db.collection("couples").document(coupleId)
            .collection("members").document(uid).get().await()
        return doc.toProfile().let { if (it.name.isEmpty()) it.copy(name = name, colorIndex = colorIndex) else it }
    }

    private suspend fun memberProfile(coupleId: String, uid: String): MemberProfile? {
        if (uid.isEmpty()) return null
        val doc = db.collection("couples").document(coupleId)
            .collection("members").document(uid).get().await()
        return if (!doc.exists()) null else doc.toProfile()
    }

    private fun DocumentSnapshot.toProfile(): MemberProfile = MemberProfile(
        uid = id,
        name = getString("name") ?: "",
        colorIndex = (getLong("colorIndex") ?: 0L).toInt(),
        sharingOn = getBoolean("sharingOn") ?: true,
        lastLocation = (get("loc") as? Map<*, *>)?.let { loc ->
            LocationSample(
                point = GeoPoint(loc["lat"] as Double, loc["lng"] as Double),
                accuracyM = (loc["acc"] as? Double ?: 20.0).toFloat(),
                timestampMs = loc["ts"] as Long,
            )
        },
    )
}
