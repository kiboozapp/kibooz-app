package com.nthb.kibooz

import android.app.Application
import android.content.Context
import com.nthb.kibooz.data.session.SessionManager
import com.nthb.kibooz.ui.auth.SessionGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

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
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as KiboozApp).container

val Context.sessionManager: SessionManager
    get() = (applicationContext as KiboozApp).sessionManager
