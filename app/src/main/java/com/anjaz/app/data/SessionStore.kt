package com.anjaz.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anjaz.app.domain.Profile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.sessionStore by preferencesDataStore("session")

@Singleton
class SessionStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val key = stringPreferencesKey("profile")
    private val json = Json { ignoreUnknownKeys = true }

    val profile: Flow<Profile?> = context.sessionStore.data.map { p ->
        p[key]?.let { runCatching { json.decodeFromString<Profile>(it) }.getOrNull() }
    }

    suspend fun save(profile: Profile) {
        context.sessionStore.edit { it[key] = json.encodeToString(profile) }
    }

    suspend fun clear() {
        context.sessionStore.edit { it.remove(key) }
    }
}
