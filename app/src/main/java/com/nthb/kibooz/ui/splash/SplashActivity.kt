package com.nthb.kibooz.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.databinding.ActivitySplashBinding
import com.nthb.kibooz.ui.guru.DashboardGuruActivity
import com.nthb.kibooz.ui.onboarding.OnboardingActivity
import com.nthb.kibooz.ui.wali.DashboardWaliActivity
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val binding by lazy { ActivitySplashBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // Coroutine ikut berhenti saat layar ditutup, jadi tidak ada navigasi yang tertinggal
        lifecycleScope.launch {
            val started = SystemClock.elapsedRealtime()
            val session = try {
                appContainer.authRepository.restore()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Penyimpanan rusak atau tidak terbaca, mulai dari awal seperti pengguna baru
                null
            }

            // Maskot tetap terlihat sebentar walaupun sesi terbaca cepat
            delay(MIN_VISIBLE_MILLIS - (SystemClock.elapsedRealtime() - started))
            open(routeFor(session?.user?.role))
        }
    }

    private fun open(route: SplashRoute) {
        val intent = when (route) {
            SplashRoute.ONBOARDING -> Intent(this, OnboardingActivity::class.java)
            // Dasbor menjadi layar paling bawah, jadi tombol kembali tidak keluar ke splash
            SplashRoute.DASHBOARD_GURU -> Intent(this, DashboardGuruActivity::class.java).clearTask()
            SplashRoute.DASHBOARD_WALI -> Intent(this, DashboardWaliActivity::class.java).clearTask()
        }
        startActivity(intent)
        finish()
    }

    private fun Intent.clearTask() = apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }

    private companion object {
        const val MIN_VISIBLE_MILLIS = 1_000L
    }
}
