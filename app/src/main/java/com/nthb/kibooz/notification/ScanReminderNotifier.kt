package com.nthb.kibooz.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nthb.kibooz.R
import com.nthb.kibooz.ui.wali.DashboardWaliActivity

/** Pengingat siang untuk Wali bahwa anaknya masih punya sisa scan sampah hari ini. */
object ScanReminderNotifier {
    const val CHANNEL_ID = "scan_reminder"

    // ID tetap supaya pengingat yang sempat terkirim dua kali saling menggantikan, tidak menumpuk
    private const val NOTIFICATION_ID = 1200

    /** Aman dipanggil berulang kali, channel yang sudah ada tidak dibuat ulang. */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_scan_channel_nama),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.notif_scan_channel_deskripsi) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** [children] berisi pasangan nama anak dan sisa scan-nya hari ini. Diam saja jika izin notifikasi tidak ada. */
    fun show(context: Context, children: List<Pair<String, Int>>) {
        if (children.isEmpty()) return
        // Diperiksa langsung di sini, bukan lewat hasPermission(), supaya lint melihatnya sebelum notify()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, DashboardWaliActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val body = children.joinToString("\n") { (name, remaining) ->
            context.getString(R.string.notif_scan_baris, name, remaining)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_scan_judul))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
