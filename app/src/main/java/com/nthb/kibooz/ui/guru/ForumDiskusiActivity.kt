package com.nthb.kibooz.ui.guru

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ForumThreadItem
import com.nthb.kibooz.databinding.ActivityForumDiskusiBinding
import com.nthb.kibooz.util.ClassTeachers
import com.nthb.kibooz.util.InsetsUtil
import com.nthb.kibooz.util.TimeFormat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ForumDiskusiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForumDiskusiBinding
    private var activeClassId: String? = null
    private var activeClassName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForumDiskusiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar,
            bottomBar = binding.clInputBar
        )

        activeClassName = intent.getStringExtra("ROMBEL_NAME")?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra("NAMA_KELAS")?.takeIf { it.isNotBlank() }
        activeClassId = intent.getStringExtra("CLASS_ID")
        binding.tvForumTitle.text = formatForumHeaderTitle(activeClassName)

        setupListeners()
        resolveClassAndLoadThreads()
    }

    private fun resolveClassAndLoadThreads() {
        lifecycleScope.launch {
            val session = appContainer.sessionStore.readSession()
            val isWali = session?.user?.role == com.nthb.kibooz.data.session.Role.WALI
            ensureActiveClassMetadata(session == null, isWali)
            binding.tvForumTitle.text = formatForumHeaderTitle(activeClassName)

            val classId = activeClassId ?: return@launch
            val studentCount = fetchStudentCount(classId, isWali)
            val teacherCount = fetchTeacherCount(classId, isWali)
            binding.tvSubtitleForum.text = "Forum Kelas • $studentCount Siswa • $teacherCount Guru Pengajar"

            val result = appContainer.forumRepository.threads(classId)
            if (result is ApiResult.Success) {
                renderThreads(result.data.threads, session?.user)
            }
        }
    }

    private suspend fun ensureActiveClassMetadata(hasNoSession: Boolean, isWali: Boolean) {
        if (!activeClassId.isNullOrBlank() && !activeClassName.isNullOrBlank()) return
        val classRepo = appContainer.classRepository
        if (hasNoSession || isWali) {
            resolveFromChildren(classRepo)
        }
        val shouldCheckTeacherClasses = (hasNoSession && activeClassId.isNullOrBlank()) ||
            (!isWali && (activeClassId.isNullOrBlank() || activeClassName.isNullOrBlank()))
        if (shouldCheckTeacherClasses) {
            resolveFromTeacherClasses(classRepo)
        }
    }

    private suspend fun resolveFromChildren(classRepo: com.nthb.kibooz.data.repository.ClassRepository) {
        val children = (classRepo.children() as? ApiResult.Success)?.data ?: return
        val requestedId = activeClassId?.takeIf { it.isNotBlank() }
        val matched = if (requestedId != null) {
            children.firstOrNull { it.classId == requestedId }
        } else {
            children.firstOrNull()
        } ?: return
        if (requestedId == null) activeClassId = matched.classId
        matched.className.takeIf { it.isNotBlank() }?.let { activeClassName = it }
    }

    private suspend fun resolveFromTeacherClasses(classRepo: com.nthb.kibooz.data.repository.ClassRepository) {
        val classes = (classRepo.teacherClasses() as? ApiResult.Success)?.data ?: return
        val requestedId = activeClassId?.takeIf { it.isNotBlank() }
        val matched = if (requestedId != null) {
            classes.firstOrNull { it.id == requestedId }
        } else {
            classes.firstOrNull()
        } ?: return
        if (requestedId == null) activeClassId = matched.id
        matched.name.takeIf { it.isNotBlank() }?.let { activeClassName = it }
    }

    private suspend fun fetchStudentCount(classId: String, isWali: Boolean): Int {
        if (!isWali) {
            val studentsRes = appContainer.classRepository.allStudents(classId)
            return if (studentsRes is ApiResult.Success) studentsRes.data.size else 1
        }
        val lbRes = appContainer.leaderboardRepository.leaderboard(classId)
        return if (lbRes is ApiResult.Success) {
            maxOf(1, lbRes.data.podium.size + lbRes.data.rankings.size)
        } else {
            1
        }
    }

    private suspend fun fetchTeacherCount(classId: String, isWali: Boolean): Int {
        if (isWali) {
            val contactsRes = appContainer.chatRepository.allContacts()
            return if (contactsRes is ApiResult.Success) {
                maxOf(1, ClassTeachers.fromChatContacts(contactsRes.data, classId).size)
            } else {
                1
            }
        }
        val teachersRes = appContainer.classManagementRepository.classTeachers(classId)
        return if (teachersRes is ApiResult.Success) {
            maxOf(1, ClassTeachers.fromClassTeachers(teachersRes.data).size)
        } else {
            1
        }
    }

    private fun renderThreads(threads: List<ForumThreadItem>, currentUser: com.nthb.kibooz.data.session.SessionUser?) {
        binding.llChatMessages.removeAllViews()

        if (threads.isEmpty()) {
            binding.tvEmptyForum.visibility = View.VISIBLE
            return
        }

        binding.tvEmptyForum.visibility = View.GONE

        // Balasan endpoint backend mengurutkan pesan terbaru di indeks awal (DESC).
        // Antarmuka chat harus mengalir secara kronologis: pesan terlama di atas, pesan terbaru di bawah.
        val chronologicalThreads = threads.sortedBy { it.createdAt }

        chronologicalThreads.forEach { thread ->
            val isSelf = currentUser != null && (
                thread.author.id == currentUser.id ||
                thread.author.fullName.equals(currentUser.fullName, ignoreCase = true)
            )
            val roleLabel = if (thread.author.role.equals("GURU", ignoreCase = true)) "Guru" else "Wali Murid"
            val senderLabel = if (isSelf) "Anda ($roleLabel)" else "${thread.author.fullName} ($roleLabel)"

            addMessageBubble(
                senderName = senderLabel,
                content = thread.body,
                time = TimeFormat.localTime(thread.createdAt) ?: "09:00",
                isSelf = isSelf
            )
        }
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                postMessage(text)
                binding.etMessage.text.clear()
            } else {
                Toast.makeText(this, "Tulis pesan terlebih dahulu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun postMessage(content: String) {
        val forumRepo = appContainer.forumRepository
        val classId = activeClassId

        lifecycleScope.launch {
            val session = appContainer.sessionStore.readSession()
            val userRole = if (session?.user?.role?.name?.equals("GURU", ignoreCase = true) == true) "Guru" else "Wali Murid"
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            binding.tvEmptyForum.visibility = View.GONE
            addMessageBubble(
                senderName = "Anda ($userRole)",
                content = content,
                time = currentTime,
                isSelf = true
            )

            if (!classId.isNullOrBlank()) {
                val result = forumRepo.createThread(classId = classId, title = "Diskusi Kelas", body = content)
                if (result is ApiResult.Success<*>) {
                    resolveClassAndLoadThreads()
                }
            }
        }
    }

    private fun addMessageBubble(senderName: String, content: String, time: String, isSelf: Boolean) {
        val density = resources.displayMetrics.density

        val container = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * density).toInt()
            }
            gravity = if (isSelf) Gravity.END else Gravity.START
            orientation = LinearLayout.VERTICAL
        }

        val bubble = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (isSelf) {
                    marginStart = (64 * density).toInt()
                } else {
                    marginEnd = (64 * density).toInt()
                }
            }
            setBackgroundResource(if (isSelf) R.drawable.bg_button_purple else R.drawable.bg_input_field)
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
        }

        val balooFont = ResourcesCompat.getFont(this, R.font.baloo2)

        val senderTv = TextView(this).apply {
            text = senderName
            setTextColor(
                if (isSelf) ContextCompat.getColor(context, R.color.kibooz_yellow_gold)
                else ContextCompat.getColor(context, R.color.kibooz_primary_purple)
            )
            textSize = 12f
            typeface = balooFont
            paint.isFakeBoldText = true
        }

        val messageTv = TextView(this).apply {
            text = content
            setTextColor(
                if (isSelf) ContextCompat.getColor(context, R.color.kibooz_white)
                else ContextCompat.getColor(context, R.color.kibooz_text_dark)
            )
            textSize = 14f
            typeface = balooFont
        }

        val timeTv = TextView(this).apply {
            text = time
            setTextColor(
                if (isSelf) ContextCompat.getColor(context, R.color.kibooz_lavender_surface)
                else ContextCompat.getColor(context, R.color.kibooz_text_muted)
            )
            textSize = 10f
            gravity = Gravity.END
            typeface = balooFont
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

/** Menampilkan nama kelas aktif pada header forum karena 1 forum dipakai bersama oleh seluruh mata pelajaran di kelas. */
fun formatForumHeaderTitle(className: String?): String =
    className?.trim()?.takeIf { it.isNotEmpty() } ?: "Forum Diskusi Kelas"
