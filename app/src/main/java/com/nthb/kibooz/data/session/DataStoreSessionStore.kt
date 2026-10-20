package com.nthb.kibooz.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * [SessionStore] di atas Preferences DataStore. Berkas berada di `files/datastore/` dan
 * dikeluarkan dari backup lewat `data_extraction_rules.xml`. Isinya belum dienkripsi.
 */
class DataStoreSessionStore(private val dataStore: DataStore<Preferences>) : SessionStore {

    constructor(context: Context) : this(context.applicationContext.sessionDataStore)

    override suspend fun readSession(): Session? {
        val prefs = dataStore.data.first()
        val access = prefs[ACCESS_TOKEN]
        val refresh = prefs[REFRESH_TOKEN]
        val id = prefs[USER_ID]
        val email = prefs[USER_EMAIL]
        val fullName = prefs[USER_NAME]
        val role = prefs[USER_ROLE]?.let { name -> Role.entries.firstOrNull { it.name == name } }

        if (access == null || refresh == null || id == null || email == null || fullName == null || role == null) {
            return null
        }

        return Session(access, refresh, SessionUser(id, email, fullName, role, prefs[USER_AVATAR]))
    }

    override suspend fun writeSession(session: Session) {
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = session.accessToken
            prefs[REFRESH_TOKEN] = session.refreshToken
            prefs[USER_ID] = session.user.id
            prefs[USER_EMAIL] = session.user.email
            prefs[USER_NAME] = session.user.fullName
            prefs[USER_ROLE] = session.user.role.name
            val avatar = session.user.avatarUrl
            if (avatar == null) prefs.remove(USER_AVATAR) else prefs[USER_AVATAR] = avatar
        }
    }

    override suspend fun clearSession() {
        dataStore.edit { prefs ->
            SESSION_KEYS.forEach { prefs.remove(it) }
        }
    }

    override suspend fun readDeviceToken(email: String): String? =
        dataStore.data.first()[deviceTokenKey(email)]

    override suspend fun writeDeviceToken(email: String, deviceToken: String) {
        dataStore.edit { it[deviceTokenKey(email)] = deviceToken }
    }

    private fun deviceTokenKey(email: String) =
        stringPreferencesKey("device_token_" + email.trim().lowercase())

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_ROLE = stringPreferencesKey("user_role")
        val USER_AVATAR = stringPreferencesKey("user_avatar")

        val SESSION_KEYS = listOf(
            ACCESS_TOKEN, REFRESH_TOKEN, USER_ID, USER_EMAIL, USER_NAME, USER_ROLE, USER_AVATAR
        )
    }
}
