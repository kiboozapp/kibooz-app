package com.nthb.kibooz.ui.auth

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.nthb.kibooz.data.repository.AuthRepository
import java.lang.ref.WeakReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Menjaga layar yang butuh login. Setiap layar yang tampil diperiksa terhadap sesi dan perannya
 * ([accessFor]), dan layar yang sedang tampil diperiksa lagi saat sesi berakhir (logout atau ditolak
 * server). Yang tidak boleh masuk dikembalikan ke pilihan peran dengan seluruh tumpukan layar dibersihkan.
 * Layar bisa terlihat sekejap sebelum sesi selesai dibaca, jadi data sensitif tidak boleh dimuat di
 * `onCreate` tanpa menunggu sesi.
 */
class SessionGuard(
    private val repository: AuthRepository,
    private val scope: CoroutineScope,
    private val onDenied: (Activity) -> Unit = ::sendToRoleSelection
) : Application.ActivityLifecycleCallbacks {

    private var resumed: WeakReference<Activity>? = null

    /** Mulai memantau sesi. Panggil sekali setelah guard didaftarkan ke Application. */
    fun start() {
        scope.launch {
            repository.session.collect { resumed?.get()?.let(::check) }
        }
    }

    private fun check(activity: Activity) {
        val access = accessFor(activity.javaClass.name)
        if (access == Access.PUBLIC) return

        scope.launch {
            if (!access.allows(currentRole()) && !activity.isFinishing) onDenied(activity)
        }
    }

    private suspend fun currentRole() = try {
        repository.restore()?.user?.role
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
        check(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

private fun sendToRoleSelection(activity: Activity) {
    activity.startActivity(
        Intent(activity, RoleSelectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
    )
    activity.finish()
}
