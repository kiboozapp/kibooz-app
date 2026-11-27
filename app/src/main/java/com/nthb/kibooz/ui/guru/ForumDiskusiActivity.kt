package com.nthb.kibooz.ui.guru

import android.graphics.Typeface
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
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.data.session.SessionUser
import com.nthb.kibooz.databinding.ActivityForumDiskusiBinding
import com.nthb.kibooz.util.ClassTeachers
import com.nthb.kibooz.util.ForumFilter
import com.nthb.kibooz.util.ForumSubjects
import com.nthb.kibooz.util.InsetsUtil
import com.nthb.kibooz.util.TimeFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ForumDiskusiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForumDiskusiBinding
    private var activeClassId: String? = null
    private var activeClassName: String? = null
    private var activeFilter: ForumFilter = ForumFilter.All
    private var cachedStudentCount: Int = 1
    private var cachedTeacherCount: Int = 1
    private var cachedCurrentUser: SessionUser? = null
    private var threadsLoadJob: Job? = null
    private val chipViews = linkedMapOf<ForumFilter, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForumDiskusiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar,
            bottomBar = binding.clInputBar
        )

        val rawRombelName = intent.getStringExtra(EXTRA_ROMBEL_NAME)?.takeIf { it.isNotBlank() }
        val rawNamaKelas = intent.getStringExtra(EXTRA_NAMA_KELAS)?.takeIf { it.isNotBlank() }
        activeClassName = rawRombelName ?: rawNamaKelas?.takeIf { ForumSubjects.fromName(it) == null }
        activeClassId = intent.getStringExtra(EXTRA_CLASS_ID)
        activeFilter = ForumSubjects.initialFilter(
            mapelCode = intent.getStringExtra(ForumSubjects.EXTRA_MAPEL_CODE),
            subjectName = intent.getStringExtra(ForumSubjects.EXTRA_MAPEL_NAME) ?: rawNamaKelas
        )
        binding.tvForumTitle.text = formatForumHeaderTitle(activeClassName)

        setupSubjectFilterChips()
        applyFilterPresentation()
        setupListeners()
        resolveClassAndLoadThreads()
    }

    private fun setupSubjectFilterChips() {
        val density = resources.displayMetrics.density
        val hPad = (14 * density).toInt()
        val vPad = (7 * density).toInt()
        val endMargin = (8 * density).toInt()
        val balooFont = ResourcesCompat.getFont(this, R.font.baloo2)

        binding.llSubjectChips.removeAllViews()
        chipViews.clear()

        ForumSubjects.filterOptions.forEach { option ->
            val chip = TextView(this).apply {
                text = option.chipLabel
                textSize = 13f
                typeface = balooFont
                paint.isFakeBoldText = true
                setPadding(hPad, vPad, hPad, vPad)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = endMargin
                }
                setOnClickListener { selectFilter(option) }
            }
            chipViews[option] = chip
            binding.llSubjectChips.addView(chip)
        }
        updateFilterChipStyles()
        scrollToActiveChip()
    }

    private fun selectFilter(option: ForumFilter) {
        activeFilter = option
        updateFilterChipStyles()
        applyFilterPresentation()
        scrollToActiveChip()
        showForumPlaceholder(LOADING_FORUM_TEXT)
        loadFilteredThreads()
    }

    private fun updateFilterChipStyles() {
        val selectedText = ContextCompat.getColor(this, R.color.kibooz_white)
        val idleText = ContextCompat.getColor(this, R.color.kibooz_primary_purple)
        chipViews.forEach { (option, view) ->
            val isSelected = option == activeFilter
            view.setBackgroundResource(if (isSelected) R.drawable.bg_pill_date_active else R.drawable.bg_tag_lavender)
            view.setTextColor(if (isSelected) selectedText else idleText)
        }
    }

    private fun scrollToActiveChip() {
        val activeChip = chipViews[activeFilter] ?: return
        binding.hsvSubjectFilters.post {
            val targetX = (activeChip.left - binding.hsvSubjectFilters.paddingStart).coerceAtLeast(0)
            binding.hsvSubjectFilters.smoothScrollTo(targetX, 0)
        }
    }

    private fun applyFilterPresentation() {
        binding.tvSubtitleForum.text = ForumSubjects.subtitle(activeFilter, cachedStudentCount, cachedTeacherCount)
        binding.etMessage.hint = ForumSubjects.inputHint(activeFilter)
        binding.tvEmptyForum.text = ForumSubjects.emptyMessage(activeFilter)
    }

    private fun resolveClassAndLoadThreads() {
        lifecycleScope.launch {
            val session = appContainer.sessionStore.readSession()
            cachedCurrentUser = session?.user
            val isWali = cachedCurrentUser?.role == Role.WALI
            ensureActiveClassMetadata(session == null, isWali)
            binding.tvForumTitle.text = formatForumHeaderTitle(activeClassName)

            val classId = activeClassId ?: return@launch
            cachedStudentCount = fetchStudentCount(classId, isWali)
            cachedTeacherCount = fetchTeacherCount(classId, isWali)
            applyFilterPresentation()
            loadFilteredThreads()
        }
    }

    private fun loadFilteredThreads() {
        val classId = activeClassId ?: return
        val requestedFilter = activeFilter
        threadsLoadJob?.cancel()
        threadsLoadJob = lifecycleScope.launch {
            val result = appContainer.forumRepository.threads(
                classId = classId,
                subjectCode = requestedFilter.subjectCodeQuery,
                generalOnly = requestedFilter.generalOnlyQuery
            )
            if (requestedFilter != activeFilter) return@launch
            if (result is ApiResult.Success) {
                renderThreads(result.data.threads, cachedCurrentUser)
            } else {
                showForumPlaceholder(FAILED_FORUM_TEXT)
                Toast.makeText(this@ForumDiskusiActivity, "Gagal memuat forum diskusi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showForumPlaceholder(message: String) {
        binding.llChatMessages.removeAllViews()
        binding.llChatMessages.addView(binding.tvEmptyForum)
        binding.tvEmptyForum.text = message
        binding.tvEmptyForum.visibility = View.VISIBLE
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

    private suspend fun resolveFromChildren(classRepo: ClassRepository) {
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

    private suspend fun resolveFromTeacherClasses(classRepo: ClassRepository) {
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

    private fun renderThreads(threads: List<ForumThreadItem>, currentUser: SessionUser?) {
        binding.llChatMessages.removeAllViews()

        if (threads.isEmpty()) {
            showForumPlaceholder(ForumSubjects.emptyMessage(activeFilter))
            return
        }

        binding.tvEmptyForum.visibility = View.GONE

        // Endpoint backend mengurutkan thread terbaru terlebih dahulu (DESC).
        // Tampilan chat menata kronologis dari terlama di atas ke terbaru di bawah.
        val chronologicalThreads = threads.sortedBy { it.createdAt }
        val showTopicBadge = activeFilter == ForumFilter.All

        chronologicalThreads.forEach { thread ->
            val isSelf = currentUser != null && (
                thread.author.id == currentUser.id ||
                    thread.author.fullName.equals(currentUser.fullName, ignoreCase = true)
                )
            val roleLabel = if (thread.author.role.equals(ROLE_GURU, ignoreCase = true)) {
                ROLE_LABEL_GURU
            } else {
                ROLE_LABEL_WALI
            }
            val senderLabel = if (isSelf) "Anda ($roleLabel)" else "${thread.author.fullName} ($roleLabel)"
            val badgeLabel = if (showTopicBadge) ForumSubjects.threadBadge(thread.subjectCode) else null

            addMessageBubble(
                senderName = senderLabel,
                content = thread.body,
                time = TimeFormat.localTime(thread.createdAt) ?: DEFAULT_TIME_LABEL,
                isSelf = isSelf,
                subjectBadge = badgeLabel
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
        val targetFilter = activeFilter

        lifecycleScope.launch {
            val session = appContainer.sessionStore.readSession()
            val userRole = if (session?.user?.role == Role.GURU) ROLE_LABEL_GURU else ROLE_LABEL_WALI
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val badgeLabel = if (targetFilter == ForumFilter.All) {
                ForumSubjects.threadBadge(targetFilter.postSubjectCode)
            } else {
                null
            }

            if (targetFilter == activeFilter) {
                binding.tvEmptyForum.visibility = View.GONE
                addMessageBubble(
                    senderName = "Anda ($userRole)",
                    content = content,
                    time = currentTime,
                    isSelf = true,
                    subjectBadge = badgeLabel
                )
            }

            if (!classId.isNullOrBlank()) {
                val result = forumRepo.createThread(
                    classId = classId,
                    title = ForumSubjects.threadTitle(targetFilter),
                    body = content,
                    subjectCode = targetFilter.postSubjectCode
                )
                when (result) {
                    is ApiResult.Success<*> -> loadFilteredThreads()
                    is ApiResult.Failure -> Toast.makeText(
                        this@ForumDiskusiActivity,
                        result.message,
                        Toast.LENGTH_SHORT
                    ).show()
                    is ApiResult.NetworkError -> Toast.makeText(
                        this@ForumDiskusiActivity,
                        R.string.error_network,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private data class BubbleStyle(
        val backgroundRes: Int,
        val senderColor: Int,
        val bodyColor: Int,
        val timeColor: Int
    )

    private fun bubbleStyle(isSelf: Boolean): BubbleStyle = if (isSelf) {
        BubbleStyle(
            backgroundRes = R.drawable.bg_button_purple,
            senderColor = ContextCompat.getColor(this, R.color.kibooz_yellow_gold),
            bodyColor = ContextCompat.getColor(this, R.color.kibooz_white),
            timeColor = ContextCompat.getColor(this, R.color.kibooz_lavender_surface)
        )
    } else {
        BubbleStyle(
            backgroundRes = R.drawable.bg_input_field,
            senderColor = ContextCompat.getColor(this, R.color.kibooz_primary_purple),
            bodyColor = ContextCompat.getColor(this, R.color.kibooz_text_dark),
            timeColor = ContextCompat.getColor(this, R.color.kibooz_text_muted)
        )
    }

    private fun createSubjectBadgeView(badgeText: String, balooFont: Typeface?, density: Float): TextView =
        TextView(this).apply {
            text = badgeText
            textSize = 10f
            typeface = balooFont
            paint.isFakeBoldText = true
            setBackgroundResource(R.drawable.bg_tag_lavender)
            setTextColor(ContextCompat.getColor(context, R.color.kibooz_primary_purple))
            setPadding((8 * density).toInt(), (2 * density).toInt(), (8 * density).toInt(), (2 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (4 * density).toInt()
            }
        }

    private fun createBubbleContainer(isSelf: Boolean, topMarginPx: Int): LinearLayout {
        val row = LinearLayout(this)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = topMarginPx
        row.layoutParams = params
        row.gravity = if (isSelf) Gravity.END else Gravity.START
        row.orientation = LinearLayout.VERTICAL
        return row
    }

    private fun createBubbleBox(isSelf: Boolean, backgroundRes: Int, density: Float): LinearLayout {
        val box = LinearLayout(this)
        val sideMargin = (64 * density).toInt()
        val hPad = (16 * density).toInt()
        val vPad = (12 * density).toInt()
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        if (isSelf) {
            params.marginStart = sideMargin
        } else {
            params.marginEnd = sideMargin
        }
        box.layoutParams = params
        box.setBackgroundResource(backgroundRes)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(hPad, vPad, hPad, vPad)
        return box
    }

    private fun createBubbleText(
        content: String,
        textColor: Int,
        sizeSp: Float,
        balooFont: Typeface?,
        isBold: Boolean,
        isEndAligned: Boolean
    ): TextView {
        val label = TextView(this)
        label.text = content
        label.setTextColor(textColor)
        label.textSize = sizeSp
        label.typeface = balooFont
        if (isBold) label.paint.isFakeBoldText = true
        if (isEndAligned) label.gravity = Gravity.END
        return label
    }

    private fun addMessageBubble(
        senderName: String,
        content: String,
        time: String,
        isSelf: Boolean,
        subjectBadge: String? = null
    ) {
        val density = resources.displayMetrics.density
        val style = bubbleStyle(isSelf)
        val balooFont = ResourcesCompat.getFont(this, R.font.baloo2)
        val row = createBubbleContainer(isSelf, (12 * density).toInt())
        val bubble = createBubbleBox(isSelf, style.backgroundRes, density)

        if (!subjectBadge.isNullOrBlank()) {
            bubble.addView(createSubjectBadgeView(subjectBadge, balooFont, density))
        }
        bubble.addView(
            createBubbleText(senderName, style.senderColor, 12f, balooFont, isBold = true, isEndAligned = false)
        )
        bubble.addView(
            createBubbleText(content, style.bodyColor, 14f, balooFont, isBold = false, isEndAligned = false)
        )
        bubble.addView(
            createBubbleText(time, style.timeColor, 10f, balooFont, isBold = false, isEndAligned = true)
        )

        row.addView(bubble)
        binding.llChatMessages.addView(row)
        binding.nsvChat.post { binding.nsvChat.fullScroll(View.FOCUS_DOWN) }
    }

    private companion object {
        const val EXTRA_ROMBEL_NAME = "ROMBEL_NAME"
        const val EXTRA_NAMA_KELAS = "NAMA_KELAS"
        const val EXTRA_CLASS_ID = "CLASS_ID"
        const val ROLE_GURU = "GURU"
        const val ROLE_LABEL_GURU = "Guru"
        const val ROLE_LABEL_WALI = "Wali Murid"
        const val DEFAULT_TIME_LABEL = "09:00"
        const val LOADING_FORUM_TEXT = "Memuat diskusi..."
        const val FAILED_FORUM_TEXT = "Gagal memuat diskusi. Ketuk filter di atas untuk mencoba lagi."
    }
}

/** Menampilkan nama kelas aktif pada header forum karena topik umum & seluruh mapel berada dalam 1 kelas. */
fun formatForumHeaderTitle(className: String?): String = ForumSubjects.headerTitle(className)
