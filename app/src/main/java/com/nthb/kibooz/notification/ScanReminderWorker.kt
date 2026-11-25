package com.nthb.kibooz.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.session.Role

/**
 * Dijalankan sekitar pukul 12.00 oleh [ScanReminderScheduler]. Memeriksa sisa scan sampah setiap anak Wali dan
 * menampilkan pengingat jika masih ada yang tersisa. Proses bisa dihidupkan WorkManager tanpa layar apa pun,
 * jadi sesi dibaca dulu dari penyimpanan.
 */
class ScanReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!ScanReminderNotifier.hasPermission(applicationContext)) return Result.success()

        val container = applicationContext.appContainer
        val session = container.authRepository.restore() ?: return Result.success()
        if (session.user.role != Role.WALI) return Result.success()

        val children = when (val result = container.classRepository.children()) {
            is ApiResult.Success -> result.data
            // Galat dari server (misalnya belum punya anak) tidak akan berubah jika diulang
            is ApiResult.Failure -> return Result.success()
            // Dibatasi supaya pengingat siang tidak baru muncul sore hari
            is ApiResult.NetworkError -> return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        }

        // Per anak, karena tanpa studentId server hanya menjawab untuk anak yang paling lama terdaftar
        val remaining = children.mapNotNull { child ->
            (container.trashRepository.stats(child.id) as? ApiResult.Success)?.data
                ?.remainingDailyScans
                ?.takeIf { it > 0 }
                ?.let { child.fullName to it }
        }

        ScanReminderNotifier.show(applicationContext, remaining)
        return Result.success()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
