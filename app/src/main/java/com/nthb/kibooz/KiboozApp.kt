package com.nthb.kibooz

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.data.session.SessionManager
import com.nthb.kibooz.notification.ScanReminderNotifier
import com.nthb.kibooz.notification.ScanReminderScheduler
import com.nthb.kibooz.ui.auth.SessionGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class KiboozApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    /** Hidup selama proses, untuk kerja yang tidak boleh ikut batal saat sebuah layar ditutup. */
    private val appScope: CoroutineScope = MainScope()

    val sessionManager: SessionManager by lazy { SessionManager(container.authRepository, appScope) }

    override fun onCreate() {
        super.onCreate()
        SessionGuard(container.authRepository, appScope).also {
            registerActivityLifecycleCallbacks(it)
            it.start()
        }
        startScanReminder()
    }

    /**
     * Pengingat sisa scan hanya untuk Wali. Mengikuti sesi, jadi login, bypass, logout, ganti kata sandi, dan
     * refresh yang gagal semuanya tertangani di satu tempat.
     */
    private fun startScanReminder() {
        ScanReminderNotifier.createChannel(this)
        appScope.launch {
            // Sesi dimuat dulu supaya nilai null awal tidak sempat membatalkan jadwal yang sudah ada
            container.authRepository.restore()
            container.authRepository.session
                .map { it?.user?.role == Role.WALI }
                .distinctUntilChanged()
                .collect { isWali ->
                    if (isWali) ScanReminderScheduler.schedule(this@KiboozApp)
                    else ScanReminderScheduler.cancel(this@KiboozApp)
                }
        }

        // ON_STOP datang saat activity terakhir tidak tampil lagi (Home, pindah aplikasi, atau ditutup dari recent),
        // dan tidak terpicu oleh rotasi layar. ON_START datang saat aplikasi tampil lagi.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (container.authRepository.session.value?.user?.role == Role.WALI) {
                    ScanReminderScheduler.scheduleOnClose(this@KiboozApp)
                }
            }

            override fun onStart(owner: LifecycleOwner) {
                ScanReminderScheduler.cancelOnClose(this@KiboozApp)
            }
        })
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as KiboozApp).container

val Context.sessionManager: SessionManager
    get() = (applicationContext as KiboozApp).sessionManager
