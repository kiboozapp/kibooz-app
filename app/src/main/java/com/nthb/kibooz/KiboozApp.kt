package com.nthb.kibooz

import android.app.Application
import android.content.Context
import com.nthb.kibooz.ui.auth.SessionGuard
import kotlinx.coroutines.MainScope

class KiboozApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        SessionGuard(container.authRepository, MainScope()).also {
            registerActivityLifecycleCallbacks(it)
            it.start()
        }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as KiboozApp).container
