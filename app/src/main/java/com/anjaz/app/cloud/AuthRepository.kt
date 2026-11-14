package com.anjaz.app.cloud

import android.content.Context
import com.anjaz.app.BrandConfig
import com.anjaz.app.data.SessionStore
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Profile
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

@Singleton
class CloudState @Inject constructor(@ApplicationContext private val context: Context) {
    val available: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()
}

@Singleton
class AuthRepository @Inject constructor(
    private val cloud: CloudState,
    private val store: SessionStore,
) {
    val session: Flow<Profile?> = store.profile

    suspend fun restore(): Profile? {
        val cached = store.profile.first()
        if (!cloud.available) return cached ?: localProfile().also { store.save(it) }
        val signedIn = FirebaseAuth.getInstance().currentUser
        return if (signedIn != null && cached?.uid == signedIn.uid) cached else null
    }

    suspend fun signIn(username: String, password: String): Outcome<Profile> {
        val user = com.anjaz.app.domain.Username.normalize(username)
        if (user.isEmpty() || password.isEmpty()) return Outcome.Failure("أدخل اسم المستخدم وكلمة المرور")
        if (!cloud.available) return Outcome.Failure("لم يُربط الخادم بعد")
        return try {
            val auth = FirebaseAuth.getInstance()
            val result = auth.signInWithEmailAndPassword(com.anjaz.app.domain.Username.emailFor(user, BrandConfig.EMAIL_DOMAIN), password).await()
            val uid = result.user?.uid ?: return Outcome.Failure("تعذّر تسجيل الدخول")
            val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
            if (!doc.exists() || doc.getBoolean("active") != true) {
                auth.signOut()
                return Outcome.Failure("الحساب غير مفعّل")
            }
            val profile = Profile(
                uid = uid, username = doc.getString("username") ?: user,
                displayName = doc.getString("displayName") ?: user, role = doc.getString("role") ?: "FIELD_STAFF",
                regionId = doc.getString("regionId"), squareId = doc.getString("squareId"),
            )
            store.save(profile)
            Outcome.Ok(profile)
        } catch (e: FirebaseAuthInvalidUserException) {
            Outcome.Failure("بيانات الدخول غير صحيحة")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Outcome.Failure("بيانات الدخول غير صحيحة")
        } catch (e: Exception) {
            Outcome.Failure("تحقق من الاتصال بالإنترنت وأعد المحاولة")
        }
    }

    suspend fun signOut() {
        if (cloud.available) FirebaseAuth.getInstance().signOut()
        store.clear()
    }

    private fun localProfile() = Profile("local-admin", "admin", "المدير", "CENTRAL")
}
