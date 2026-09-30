package com.valentinusmarvel.kibooz.ui.guru

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.valentinusmarvel.kibooz.R
import com.valentinusmarvel.kibooz.databinding.ActivityForumDiskusiBinding
import com.valentinusmarvel.kibooz.util.InsetsUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ForumDiskusiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForumDiskusiBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForumDiskusiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar,
            bottomBar = binding.clInputBar
        )

        val namaKelas = intent.getStringExtra("NAMA_KELAS") ?: "Seni & Kreativitas"
        binding.tvForumTitle.text = "Forum $namaKelas"

        setupListeners()
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                binding.etMessage.text.clear()
            } else {
                Toast.makeText(this, "Tulis pesan terlebih dahulu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendMessage(content: String) {
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val container = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
            }
            gravity = Gravity.END
            orientation = LinearLayout.VERTICAL
        }

        val bubble = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 120
            }
            setBackgroundResource(R.drawable.bg_button_purple)
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        val senderTv = TextView(this).apply {
            text = "Anda (Ibu Siti Rahayu)"
            setTextColor(ContextCompat.getColor(context, R.color.kibooz_yellow_gold))
            textSize = 12f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.baloo2)
        }

        val messageTv = TextView(this).apply {
            text = content
            setTextColor(ContextCompat.getColor(context, R.color.kibooz_white))
            textSize = 14f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.baloo2)
        }

        val timeTv = TextView(this).apply {
            text = currentTime
            setTextColor(ContextCompat.getColor(context, R.color.kibooz_lavender_surface))
            textSize = 10f
            gravity = Gravity.END
            typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.baloo2)
        }

        bubble.addView(senderTv)
        bubble.addView(messageTv)
        bubble.addView(timeTv)
        container.addView(bubble)

        binding.llChatMessages.addView(container)

        binding.nsvChat.post {
            binding.nsvChat.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }
}
