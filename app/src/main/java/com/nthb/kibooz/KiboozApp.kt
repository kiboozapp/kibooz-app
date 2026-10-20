package com.nthb.kibooz

import android.app.Application
import android.content.Context

class KiboozApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as KiboozApp).container
