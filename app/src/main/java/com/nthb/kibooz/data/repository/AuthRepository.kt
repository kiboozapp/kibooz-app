package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.AuthApi
import com.nthb.kibooz.data.remote.AuthData
import com.nthb.kibooz.data.remote.ChangePasswordRequest
import com.nthb.kibooz.data.remote.LoginRequest
import com.nthb.kibooz.data.remote.LogoutRequest
import com.nthb.kibooz.data.remote.RefreshRequest
import com.nthb.kibooz.data.remote.RegisterRequest
import com.nthb.kibooz.data.remote.TokenProvider
import com.nthb.kibooz.data.remote.safeApiCall
import com.nthb.kibooz.data.remote.safeApiCallEmpty
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.data.session.Session
import com.nthb.kibooz.data.session.SessionStore
import com.nthb.kibooz.data.session.SessionUser
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * Login, refresh dan logout, plus sesi yang sedang aktif. Refresh token sekali pakai dan berputar,
 * jadi pasangan token baru ditulis ke [store] sebelum dipakai dan hanya satu refresh berjalan pada
 * satu waktu. Replay token lama mengakhiri seluruh sesi di server.
 */
class AuthRepository(
    private val api: AuthApi,
    private val store: SessionStore,
    private val moshi: Moshi
) : TokenProvider {

    private val _session = MutableStateFlow<Session?>(null)

    /** Sesi saat ini. Menjadi null saat logout atau saat server mengakhiri sesi. Panggil [restore] dulu. */
    val session: StateFlow<Session?> = _session

    @Volatile
    private var loaded = false
    private val loadMutex = Mutex()
    private val refreshMutex = Mutex()

    /** Memuat sesi dari penyimpanan sekali saja, lalu mengembalikan sesi aktif. */
    suspend fun restore(): Session? = loadMutex.withLock {
        if (!loaded) {
            _session.value = store.readSession()
            loaded = true
        }
        _session.value
    }

    /** Dipanggil di thread OkHttp. Hanya memblokir satu kali, saat sesi belum dimuat dari penyimpanan. */
    override fun accessToken(): String? {
        if (!loaded) runBlocking { restore() }
        return _session.value?.accessToken
    }

    suspend fun login(email: String, password: String, role: Role): ApiResult<SessionUser> {
        val cleanEmail = email.trim()
        val deviceToken = store.readDeviceToken(cleanEmail)

        return startSession(cleanEmail) { api.login(LoginRequest(cleanEmail, password, role, deviceToken)) }
    }

    /** Shortcut bypass pengujian manual untuk login cepat sebagai Guru atau Wali. */
    suspend fun bypassLogin(role: Role): SessionUser {
        val user = if (role == Role.GURU) {
            SessionUser(
                id = "guru-bypass-1",
                email = "guru.tika@kibooz.com",
                fullName = "Tika Sartika, S.Pd",
                role = Role.GURU,
                avatarUrl = null
            )
        } else {
            SessionUser(
                id = "wali-bypass-1",
                email = "budi.santoso@kibooz.com",
                fullName = "Budi Santoso",
                role = Role.WALI,
                avatarUrl = null
            )
        }
        val session = Session(
            accessToken = "mock-bypass-token",
            refreshToken = "mock-bypass-refresh-token",
            user = user
        )
        persist(session)
        return user
    }

    /**
     * Mendaftar lalu langsung masuk: server membalas pasangan token seperti login, jadi sesinya disimpan
     * dengan cara yang sama dan `deviceToken` dari balasan diingat untuk email ini.
     */
    suspend fun register(request: RegisterRequest): ApiResult<SessionUser> {
        val clean = request.copy(email = request.email.trim())
        return startSession(clean.email) { api.register(clean) }
    }

    private suspend fun startSession(
        email: String,
        call: suspend () -> Response<ApiEnvelope<AuthData>>
    ): ApiResult<SessionUser> =
        when (val result = safeApiCall(moshi, call)) {
            is ApiResult.Success -> {
                result.data.deviceToken?.let { store.writeDeviceToken(email, it) }
                val session = result.data.toSession()
                persist(session)
                ApiResult.Success(session.user, result.message)
            }
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }

    /**
     * Menukar refresh token setelah [failedAccessToken] ditolak server dan mengembalikan access token
     * baru. Jika pemanggil lain sudah memperbarui sesi, token terbaru dikembalikan tanpa memanggil
     * server lagi. Hasil null berarti sesi tidak bisa diperbarui: 401 dari server mengakhiri sesi,
     * sedangkan gangguan jaringan atau 429 membiarkannya utuh supaya bisa dicoba lagi.
     */
    suspend fun refresh(failedAccessToken: String): String? = refreshMutex.withLock {
        val current = restore() ?: return null
        if (current.accessToken != failedAccessToken) return current.accessToken

        when (val result = safeApiCall(moshi) { withContext(Dispatchers.IO) { api.refreshToken(RefreshRequest(current.refreshToken)).execute() } }) {
            is ApiResult.Success -> result.data.toSession().also { persist(it) }.accessToken
            is ApiResult.Failure -> {
                if (result.status == 401 && !current.refreshToken.startsWith("mock-")) endSession()
                null
            }
            is ApiResult.NetworkError -> null
        }
    }

    /** Mencabut refresh token di server sebisanya, lalu selalu menghapus sesi lokal. */
    suspend fun logout() = refreshMutex.withLock {
        restore()?.let { current ->
            safeApiCallEmpty(moshi) { withContext(Dispatchers.IO) { api.logout(LogoutRequest(current.refreshToken)).execute() } }
        }
        endSession()
    }

    /**
     * Mengganti kata sandi. Saat berhasil server mengakhiri semua sesi akun ini, termasuk yang di perangkat ini,
     * dan refresh token-nya tidak berlaku lagi. Karena itu sesi lokal langsung dihapus tanpa memanggil logout,
     * dan pengguna masuk lagi dengan kata sandi baru. Saat gagal sesi dibiarkan utuh.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): ApiResult<Unit> =
        when (val result = safeApiCallEmpty(moshi) { api.changePassword(ChangePasswordRequest(currentPassword, newPassword)) }) {
            is ApiResult.Success -> {
                refreshMutex.withLock { endSession() }
                result
            }
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }

    /** Memperbarui nama atau avatar akun aktif di memori dan SessionStore lokal setelah edit/unggah berhasil. */
    suspend fun updateSessionUser(fullName: String? = null, avatarUrl: String? = null) {
        val current = restore() ?: return
        val updatedUser = current.user.copy(
            fullName = fullName ?: current.user.fullName,
            avatarUrl = avatarUrl ?: current.user.avatarUrl
        )
        if (updatedUser != current.user) {
            persist(current.copy(user = updatedUser))
        }
    }

    private suspend fun persist(session: Session) {
        try {
            store.writeSession(session)
        } finally {
            _session.value = session
            loaded = true
        }
    }

    private suspend fun endSession() {
        try {
            store.clearSession()
        } finally {
            _session.value = null
        }
    }

    private fun AuthData.toSession() = Session(
        accessToken = token,
        refreshToken = refreshToken,
        user = SessionUser(user.id, user.email, user.fullName, user.role, user.avatarUrl)
    )
}
