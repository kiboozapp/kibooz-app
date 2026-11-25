package com.nthb.kibooz.ui.common

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.NotificationItem
import com.nthb.kibooz.util.TimeFormat
import kotlinx.coroutines.launch

/** Dialog inbox notifikasi in-app bersama untuk akun Guru dan Wali. */
object NotificationInboxDialog {

    fun show(activity: AppCompatActivity) {
        val density = activity.resources.displayMetrics.density
        val pad = (18 * density).toInt()
        val font = ResourcesCompat.getFont(activity, R.font.baloo2)
        val primaryPurple = ContextCompat.getColor(activity, R.color.kibooz_primary_purple)

        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, (10 * density).toInt())
        }

        val headerTitle = TextView(activity).apply {
            text = "Kotak Masuk Notifikasi"
            textSize = 18f
            typeface = font
            paint.isFakeBoldText = true
            setTextColor(Color.parseColor("#242038"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val unreadChip = TextView(activity).apply {
            text = "Memuat..."
            textSize = 12f
            typeface = font
            paint.isFakeBoldText = true
            setTextColor(primaryPurple)
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(Color.parseColor("#F1EEFD"))
            }
        }

        headerRow.addView(headerTitle)
        headerRow.addView(unreadChip)

        val statusView = TextView(activity).apply {
            text = "Mengambil notifikasi terbaru..."
            textSize = 13f
            typeface = font
            setTextColor(Color.parseColor("#75718E"))
            setPadding(pad, (12 * density).toInt(), pad, (16 * density).toInt())
        }

        val listContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (4 * density).toInt(), pad, (16 * density).toInt())
        }

        val scrollView = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (360 * density).toInt()
            )
            addView(listContainer)
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerRow)
            addView(statusView)
            addView(scrollView)
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(root)
            .setPositiveButton("Tutup", null)
            .setNeutralButton("Tandai Semua Dibaca", null)
            .create()

        fun renderItems(items: List<NotificationItem>, unreadCount: Int) {
            unreadChip.text = "$unreadCount Belum Dibaca"
            listContainer.removeAllViews()

            if (items.isEmpty()) {
                statusView.visibility = View.VISIBLE
                statusView.text = "Belum ada notifikasi saat ini."
                return
            }

            statusView.visibility = View.GONE
            items.forEach { item ->
                listContainer.addView(
                    buildNotificationCard(
                        activity = activity,
                        item = item,
                        font = font,
                        density = density,
                        primaryPurple = primaryPurple
                    ) { clicked ->
                        if (clicked.readAt == null) {
                            activity.lifecycleScope.launch {
                                when (activity.appContainer.notificationRepository.markRead(clicked.id)) {
                                    is ApiResult.Success -> {
                                        val refreshed = activity.appContainer.notificationRepository.notifications()
                                        if (refreshed is ApiResult.Success) {
                                            renderItems(refreshed.data.notifications, refreshed.data.unreadCount)
                                        }
                                    }
                                    is ApiResult.Failure -> {
                                        Toast.makeText(activity, "Gagal menandai notifikasi", Toast.LENGTH_SHORT).show()
                                    }
                                    is ApiResult.NetworkError -> {
                                        Toast.makeText(activity, "Jaringan tidak tersedia", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }

        fun loadInbox() {
            activity.lifecycleScope.launch {
                when (val res = activity.appContainer.notificationRepository.notifications()) {
                    is ApiResult.Success -> {
                        renderItems(res.data.notifications, res.data.unreadCount)
                    }
                    is ApiResult.Failure -> {
                        unreadChip.text = "0 Baru"
                        listContainer.removeAllViews()
                        statusView.visibility = View.VISIBLE
                        statusView.text = if (res.status == 404) {
                            "Belum ada notifikasi baru."
                        } else {
                            res.message
                        }
                    }
                    is ApiResult.NetworkError -> {
                        unreadChip.text = "0 Baru"
                        listContainer.removeAllViews()
                        statusView.visibility = View.VISIBLE
                        statusView.text = "Tidak dapat terhubung ke server."
                    }
                }
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                activity.lifecycleScope.launch {
                    when (val res = activity.appContainer.notificationRepository.markAllRead()) {
                        is ApiResult.Success -> {
                            Toast.makeText(activity, "Semua notifikasi ditandai sudah dibaca", Toast.LENGTH_SHORT).show()
                            loadInbox()
                        }
                        is ApiResult.Failure -> {
                            Toast.makeText(activity, res.message, Toast.LENGTH_SHORT).show()
                        }
                        is ApiResult.NetworkError -> {
                            Toast.makeText(activity, "Jaringan tidak tersedia", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            loadInbox()
        }

        dialog.show()
    }

    private fun buildNotificationCard(
        activity: AppCompatActivity,
        item: NotificationItem,
        font: Typeface?,
        density: Float,
        primaryPurple: Int,
        onClick: (NotificationItem) -> Unit
    ): MaterialCardView {
        val isUnread = item.readAt == null
        val card = MaterialCardView(activity).apply {
            radius = 14 * density
            cardElevation = 1 * density
            setCardBackgroundColor(if (isUnread) Color.parseColor("#F8F6FF") else Color.WHITE)
            strokeWidth = (if (isUnread) 1.5f * density else 1f * density).toInt()
            strokeColor = if (isUnread) primaryPurple else Color.parseColor("#E5E1F2")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (10 * density).toInt()
            }
            setOnClickListener { onClick(item) }
        }

        val inner = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (12 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val topRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleView = TextView(activity).apply {
            text = item.title
            textSize = 14f
            typeface = font
            paint.isFakeBoldText = true
            setTextColor(Color.parseColor("#242038"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        topRow.addView(titleView)

        if (isUnread) {
            val dot = TextView(activity).apply {
                text = "Baru"
                textSize = 10f
                typeface = font
                paint.isFakeBoldText = true
                setTextColor(Color.WHITE)
                setPadding((7 * density).toInt(), (2 * density).toInt(), (7 * density).toInt(), (2 * density).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 8 * density
                    setColor(primaryPurple)
                }
            }
            topRow.addView(dot)
        }

        val bodyView = TextView(activity).apply {
            text = item.body
            textSize = 12f
            typeface = font
            setTextColor(Color.parseColor("#57536E"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * density).toInt()
            }
        }

        val timeText = TimeFormat.localDateTime(item.createdAt) ?: item.createdAt
        val timeView = TextView(activity).apply {
            text = timeText
            textSize = 11f
            typeface = font
            setTextColor(Color.parseColor("#8E8AA4"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6 * density).toInt()
            }
        }

        inner.addView(topRow)
        inner.addView(bodyView)
        inner.addView(timeView)
        card.addView(inner)
        return card
    }
}
