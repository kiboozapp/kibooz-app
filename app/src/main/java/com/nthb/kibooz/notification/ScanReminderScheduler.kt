package com.nthb.kibooz.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Jadwal [ScanReminderWorker]: setiap hari sekitar pukul 12.00 waktu perangkat, ditambah pengingat demo
 * [DEMO_DELAY_MINUTES] menit setelah aplikasi ditutup. WorkManager tidak tepat ke menit (bisa mundur beberapa menit
 * karena Doze), tetapi tidak butuh izin alarm presisi.
 */
object ScanReminderScheduler {
    private const val WORK_NAME = "scan_reminder_harian"
    private const val DEMO_WORK_NAME = "scan_reminder_demo"
    private const val REMINDER_HOUR = 12

    /** Pengingat tambahan setelah aplikasi ditutup, untuk keperluan demo. Ubah ke false setelah demo selesai. */
    const val DEMO_ON_CLOSE = true
    private const val DEMO_DELAY_MINUTES = 1L

    private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** KEEP: jadwal yang sudah ada dibiarkan, supaya membuka aplikasi tidak menggeser jam pengingat. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ScanReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNoon(Calendar.getInstance()), TimeUnit.MILLISECONDS)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Membatalkan semua pengingat, misalnya saat logout. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(WORK_NAME)
            cancelUniqueWork(DEMO_WORK_NAME)
        }
    }

    /** Pengingat sekali jalan setelah aplikasi ditutup. REPLACE: menutup aplikasi lagi memulai hitungan dari awal. */
    fun scheduleOnClose(context: Context) {
        if (!DEMO_ON_CLOSE) return
        val request = OneTimeWorkRequestBuilder<ScanReminderWorker>()
            .setInitialDelay(DEMO_DELAY_MINUTES, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(DEMO_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Aplikasi dibuka lagi sebelum waktunya, jadi pengingat demo tidak perlu dikirim. */
    fun cancelOnClose(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DEMO_WORK_NAME)
    }

    /**
     * Milidetik dari [now] sampai pukul 12.00 berikutnya. Tepat pukul 12.00 dihitung ke besok. Memakai Calendar
     * karena java.time baru ada di API 26, sedangkan minSdk 24.
     */
    internal fun delayUntilNoon(now: Calendar): Long {
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
