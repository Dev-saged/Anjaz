package com.anjaz.app.push

import com.anjaz.app.cloud.CloudState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class DeviceTokens @Inject constructor(private val cloud: CloudState) {
    suspend fun register() {
        if (!cloud.available) return
        runCatching { store(FirebaseMessaging.getInstance().token.await()) }
    }

    companion object {
        suspend fun store(token: String) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance().collection("devices").document(uid)
                .set(mapOf("token" to token, "updatedAt" to System.currentTimeMillis())).await()
        }
    }
}
