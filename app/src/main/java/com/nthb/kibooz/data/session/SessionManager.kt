package com.nthb.kibooz.data.session

import com.nthb.kibooz.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Satu pintu untuk keluar dari akun. [signOut] mencabut refresh token di server sebisanya lalu selalu
 * menghapus sesi lokal, `deviceToken` tetap tersimpan. Kerjanya berjalan di [scope] milik proses, jadi
 * tidak batal kalau layar ditutup di tengah jalan. Layar tidak perlu berpindah sendiri karena
 * [com.nthb.kibooz.ui.auth.SessionGuard] membawa layar yang sedang tampil ke pilihan peran begitu sesinya hilang.
 */
class SessionManager(
    private val repository: AuthRepository,
    private val scope: CoroutineScope
) {
    fun signOut() {
        scope.launch { repository.logout() }
    }
}
