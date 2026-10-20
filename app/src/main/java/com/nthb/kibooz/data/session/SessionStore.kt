package com.nthb.kibooz.data.session

/**
 * Penyimpanan sesi dan `deviceToken` di perangkat. `deviceToken` disimpan per email dan sengaja
 * bertahan setelah [clearSession], karena server memakainya lagi di login berikutnya.
 */
interface SessionStore {
    suspend fun readSession(): Session?

    suspend fun writeSession(session: Session)

    /** Menghapus sesi saja, `deviceToken` tetap ada. */
    suspend fun clearSession()

    suspend fun readDeviceToken(email: String): String?

    suspend fun writeDeviceToken(email: String, deviceToken: String)
}
