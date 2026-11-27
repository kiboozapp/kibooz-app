package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.R
import com.nthb.kibooz.data.InMemorySessionStore
import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.AuthApi
import com.nthb.kibooz.data.remote.ChatApi
import com.nthb.kibooz.data.remote.ClassApi
import com.nthb.kibooz.data.remote.GuruClassApi
import com.nthb.kibooz.data.remote.LeaderboardApi
import com.nthb.kibooz.data.repository.AuthRepository
import com.nthb.kibooz.data.repository.ChatRepository
import com.nthb.kibooz.data.repository.ClassManagementRepository
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.repository.LeaderboardRepository
import com.nthb.kibooz.data.repository.jsonResponse
import com.nthb.kibooz.data.repository.testClient
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.data.session.Session
import com.nthb.kibooz.data.session.SessionUser
import com.nthb.kibooz.ml.Emotion
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

@OptIn(ExperimentalCoroutinesApi::class)
class PesertaKelasViewModelTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var classRepository: ClassRepository
    private lateinit var managementRepository: ClassManagementRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        server = MockWebServer().apply { start() }
        val client = testClient(server)
        classRepository = ClassRepository(client.create(ClassApi::class.java), client.moshi)
        managementRepository = ClassManagementRepository(classRepository, client.create(GuruClassApi::class.java), client.moshi)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun settled(viewModel: PesertaKelasViewModel): PesertaUiState = runBlocking {
        viewModel.uiState.first { !it.isLoading }
    }

    private fun actionSettled(viewModel: PesertaKelasViewModel): PesertaUiState = runBlocking {
        viewModel.uiState.first { it.busyStudentId == null && it.actionMessage != null }
    }

    private fun rosterEnvelope(vararg items: String): String =
        """{"success":true,"data":{"page":1,"limit":50,"total":${items.size},"students":[${items.joinToString(",")}]}}"""

    private fun studentItem(
        id: String,
        fullName: String,
        nisn: String,
        nisnVerified: Boolean = false,
        nisnNeedsUpdate: Boolean = false,
        points: Int = 100,
        rank: Int = 1,
        mood: String? = null
    ): String {
        val moodJson = mood?.let { "\"$it\"" } ?: "null"
        return """{"id":"$id","fullName":"$fullName","nisn":"$nisn","nisnVerified":$nisnVerified,"nisnNeedsUpdate":$nisnNeedsUpdate,"avatarUrl":null,"currentPoints":$points,"seasonPoints":$points,"classRank":$rank,"todayMood":$moodJson}"""
    }

    private fun classesEnvelope(): String =
        """{"success":true,"data":{"page":1,"limit":50,"total":1,"classes":[{"id":"$CLASS_MATAHARI_ID","name":"$CLASS_MATAHARI_NAME","gradeLevel":"TK B","schoolName":"TK Kibooz","academicYear":"2026/2027","joinCode":"MTH123","joinOpen":true,"totalStudents":1,"createdAt":"2026-08-01T00:00:00Z"}]}}"""

    private fun singleChildEnvelope(): String =
        """{"success":true,"data":{"children":[{"id":"$ID_S1","fullName":"$CHILD_ADIT","nisn":"$NISN_ADIT","avatarUrl":null,"classId":"$CLASS_MATAHARI_ID","className":"$CLASS_MATAHARI_NAME"}]}}"""

    private fun emptyContactsEnvelope(): String =
        """{"success":true,"data":{"page":1,"limit":50,"total":0,"contacts":[]}}"""

    private fun leaderboardEnvelope(): String =
        """{"success":true,"data":{"podium":[{"rank":1,"studentName":"$CHILD_ADIT","points":120,"avatarUrl":null}],"rankings":[]}}"""

    private companion object {
        const val CLASS_1 = "class-1"
        const val CLASS_MATAHARI_ID = "c-matahari"
        const val CLASS_MATAHARI_NAME = "Kelas Matahari"
        const val TEACHER_SITI = "Ibu Siti Aminah, S.Pd"
        const val TEACHER_BUDI = "Pak Budi"
        const val DEFAULT_TEACHER = "Guru Kelas"
        const val CHILD_ADIT = "Adit Pratama"
        const val URL_SITI = "https://cdn/siti.png"
        const val URL_SELF = "https://cdn/self.png"
        const val NISN_ADIT = "0012345671"
        const val NISN_BELLA = "0012345672"
        const val ID_S1 = "s1"
        const val ID_S2 = "s2"
        const val MOOD_SENANG = "SENANG"
    }

    @Test
    fun `loadPeserta successfully populates students from class repository`() {
        val json = rosterEnvelope(
            studentItem("6f1c2a52-0d4b-4a7e-9c11-3b8e5d2f0a01", "Amelia Siti Zahra", "3124567890", points = 150, rank = 1, mood = MOOD_SENANG),
            studentItem("6f1c2a52-0d4b-4a7e-9c11-3b8e5d2f0a02", "Farhan Al-Fatih", "3124567891", points = 120, rank = 2, mood = "SEDIH")
        )
        server.enqueue(jsonResponse(json))

        val viewModel = PesertaKelasViewModel(classRepository)
        viewModel.loadPeserta(initialClassId = "class-123", defaultClassName = "Kelas Melati")

        val state = settled(viewModel)
        assertFalse(state.isLoading)
        assertEquals("Guru Kelas", state.teacherName)
        assertEquals("Guru Pengajar • Kelas Melati", state.teacherRole)
        assertEquals(2, state.students.size)
        assertEquals("Amelia Siti Zahra", state.students[0].fullName)
        assertEquals(Emotion.SENANG, state.students[0].todayMood)
        assertEquals(150, state.students[0].currentPoints)
        assertEquals("Farhan Al-Fatih", state.students[1].fullName)
        assertEquals(Emotion.SEDIH, state.students[1].todayMood)
    }

    @Test
    fun `loadPeserta handles error when class not found or failure returned`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"success":false,"message":"Kelas tidak ditemukan"}"""))

        val viewModel = PesertaKelasViewModel(classRepository)
        viewModel.loadPeserta(initialClassId = "invalid-class")

        val state = settled(viewModel)
        assertFalse(state.isLoading)
        assertTrue(state.students.isEmpty())
        assertNotNull(state.errorMessage)
    }

    @Test
    fun `verifyStudentNisn updates student, reloads displaced peers, and emits displaced message`() {
        val initialRoster = rosterEnvelope(
            studentItem(ID_S1, "Bella Saphira", NISN_BELLA, points = 10, rank = 1, mood = MOOD_SENANG),
            studentItem(ID_S2, "Dimas Anggara", NISN_BELLA, points = 5, rank = 2)
        )
        val refreshedRoster = rosterEnvelope(
            studentItem(ID_S1, "Bella Saphira", NISN_BELLA, nisnVerified = true, points = 10, rank = 1, mood = MOOD_SENANG),
            studentItem(ID_S2, "Dimas Anggara", "", nisnNeedsUpdate = true, points = 5, rank = 2)
        )

        server.enqueue(jsonResponse(initialRoster))
        server.enqueue(jsonResponse("""{"success":true,"data":{"nisnVerified":true,"displaced":1}}"""))
        server.enqueue(jsonResponse(refreshedRoster))

        val viewModel = PesertaKelasViewModel(classRepository, classManagementRepository = managementRepository)
        viewModel.loadPeserta(initialClassId = CLASS_1, defaultClassName = "Kelas A")

        val initialState = settled(viewModel)
        assertTrue(initialState.isTeacher)
        assertFalse(initialState.students[0].nisnVerified)

        viewModel.verifyStudentNisn(initialState.students[0])

        val afterVerify = actionSettled(viewModel)
        assertTrue(afterVerify.students[0].nisnVerified)
        assertFalse(afterVerify.students[0].nisnNeedsUpdate)
        assertEquals("", afterVerify.students[1].nisn)
        assertTrue(afterVerify.students[1].nisnNeedsUpdate)
        assertEquals(R.string.peserta_nisn_sukses_verif_geser, afterVerify.actionMessage?.resId)
        assertEquals(1, afterVerify.actionMessage?.count)
    }

    @Test
    fun `unverifyStudentNisn clears verified flag and shows confirmation message`() {
        val roster = rosterEnvelope(
            studentItem(ID_S1, CHILD_ADIT, NISN_ADIT, nisnVerified = true, mood = MOOD_SENANG)
        )

        server.enqueue(jsonResponse(roster))
        server.enqueue(jsonResponse("""{"success":true,"data":{"nisnVerified":false,"displaced":0}}"""))

        val viewModel = PesertaKelasViewModel(classRepository, classManagementRepository = managementRepository)
        viewModel.loadPeserta(initialClassId = CLASS_1)
        val loaded = settled(viewModel)

        viewModel.unverifyStudentNisn(loaded.students[0])

        val updated = actionSettled(viewModel)
        assertFalse(updated.students[0].nisnVerified)
        assertEquals(R.string.peserta_nisn_sukses_batal, updated.actionMessage?.resId)
        assertEquals(CHILD_ADIT, updated.actionMessage?.studentName)
    }

    @Test
    fun `verifyStudentNisn handles NISN_ALREADY_REGISTERED, CLASS_ARCHIVED, and blank NISN guard`() {
        val roster = rosterEnvelope(
            studentItem(ID_S1, "Bella", NISN_BELLA, points = 10, rank = 1),
            studentItem(ID_S2, "Dimas", "", nisnNeedsUpdate = true, points = 0, rank = 2)
        )

        server.enqueue(jsonResponse(roster))
        server.enqueue(jsonResponse(failureJson("NISN_ALREADY_REGISTERED"), 409))
        server.enqueue(jsonResponse(failureJson("CLASS_ARCHIVED"), 409))

        val viewModel = PesertaKelasViewModel(classRepository, classManagementRepository = managementRepository)
        viewModel.loadPeserta(initialClassId = CLASS_1)
        val loaded = settled(viewModel)

        // Siswa dengan NISN kosong langsung ditahan tanpa request ke server
        viewModel.verifyStudentNisn(loaded.students[1])
        assertEquals(R.string.error_nisn_not_set, viewModel.uiState.value.actionMessage?.resId)
        viewModel.actionMessageShown()

        // Konflik NISN sudah dipakai anak lain
        viewModel.verifyStudentNisn(loaded.students[0])
        assertEquals(R.string.peserta_nisn_error_sudah_dipakai, actionSettled(viewModel).actionMessage?.resId)
        viewModel.actionMessageShown()

        // Kelas diarsipkan mengunci aksi berikutnya
        viewModel.verifyStudentNisn(loaded.students[0])
        val archivedState = actionSettled(viewModel)
        assertEquals(R.string.error_class_archived, archivedState.actionMessage?.resId)
        assertTrue(archivedState.classArchived)
    }

    @Test
    fun `removeStudent deletes student from list and emits confirmation message`() {
        val roster = rosterEnvelope(
            studentItem(ID_S1, CHILD_ADIT, NISN_ADIT, nisnVerified = true, mood = MOOD_SENANG),
            studentItem(ID_S2, "Cahya Putra", "0012345673", points = 80, rank = 2)
        )

        server.enqueue(jsonResponse(roster))
        server.enqueue(jsonResponse("""{"success":true,"message":"Student removed","data":null}"""))

        val viewModel = PesertaKelasViewModel(classRepository, classManagementRepository = managementRepository)
        viewModel.loadPeserta(initialClassId = CLASS_1)
        val loaded = settled(viewModel)
        assertEquals(2, loaded.students.size)

        viewModel.removeStudent(loaded.students[1])

        val afterRemove = actionSettled(viewModel)
        assertEquals(1, afterRemove.students.size)
        assertEquals(ID_S1, afterRemove.students.first().id)
        assertEquals(R.string.peserta_keluarkan_sukses, afterRemove.actionMessage?.resId)
        assertEquals("Cahya Putra", afterRemove.actionMessage?.studentName)
    }

    @Test
    fun `removeStudent refreshes roster on STUDENT_NOT_FOUND and locks on CLASS_ARCHIVED`() {
        val initialRoster = rosterEnvelope(
            studentItem(ID_S1, CHILD_ADIT, NISN_ADIT, nisnVerified = true),
            studentItem(ID_S2, "Bella", NISN_BELLA, points = 80, rank = 2)
        )
        val updatedRoster = rosterEnvelope(
            studentItem(ID_S1, CHILD_ADIT, NISN_ADIT, nisnVerified = true)
        )

        server.enqueue(jsonResponse(initialRoster))
        server.enqueue(jsonResponse(failureJson("STUDENT_NOT_FOUND"), 404))
        server.enqueue(jsonResponse(updatedRoster))
        server.enqueue(jsonResponse(failureJson("CLASS_ARCHIVED"), 409))

        val viewModel = PesertaKelasViewModel(classRepository, classManagementRepository = managementRepository)
        viewModel.loadPeserta(initialClassId = CLASS_1)
        val loaded = settled(viewModel)

        viewModel.removeStudent(loaded.students[1])
        val afterNotFound = actionSettled(viewModel)
        assertEquals(R.string.error_not_found, afterNotFound.actionMessage?.resId)
        assertEquals(1, afterNotFound.students.size)
        viewModel.actionMessageShown()

        viewModel.removeStudent(afterNotFound.students[0])
        val afterArchived = actionSettled(viewModel)
        assertEquals(R.string.error_class_archived, afterArchived.actionMessage?.resId)
        assertTrue(afterArchived.classArchived)
    }

    private fun signedInAuth(role: Role, fullName: String, avatarUrl: String? = null): AuthRepository {
        val client = testClient(server)
        val user = SessionUser(
            id = "u-${role.name.lowercase()}",
            email = "${role.name.lowercase()}@kibooz.id",
            fullName = fullName,
            role = role,
            avatarUrl = avatarUrl
        )
        val store = InMemorySessionStore(Session("acc", "ref", user))
        return AuthRepository(client.create(AuthApi::class.java), store, client.moshi).also {
            runBlocking { it.restore() }
        }
    }

    @Test
    fun `loadPeserta resolves real rombel name and class teachers for Guru`() {
        val teachersJson = """{"success":true,"data":{"teachers":[{"id":"t2","userId":"u2","isSelf":false,"fullName":"Pak Budi","nip":null,"avatarUrl":null,"isOwner":false,"joinedAt":"2026-08-02T00:00:00Z"},{"id":"t1","userId":"u1","isSelf":true,"fullName":"$TEACHER_SITI","nip":"123","avatarUrl":"$URL_SITI","isOwner":true,"joinedAt":"2026-08-01T00:00:00Z"}]}}"""
        val rosterJson = rosterEnvelope(studentItem(ID_S1, CHILD_ADIT, NISN_ADIT, nisnVerified = true, mood = MOOD_SENANG))

        server.enqueue(jsonResponse(classesEnvelope()))
        server.enqueue(jsonResponse(teachersJson))
        server.enqueue(jsonResponse(rosterJson))

        val authRepo = signedInAuth(Role.GURU, TEACHER_SITI, URL_SELF)
        val viewModel = PesertaKelasViewModel(
            classRepository = classRepository,
            authRepository = authRepo,
            classManagementRepository = managementRepository
        )
        viewModel.loadPeserta(initialClassId = CLASS_MATAHARI_ID, defaultClassName = "")

        val state = settled(viewModel)
        assertTrue(state.isTeacher)
        assertEquals(CLASS_MATAHARI_NAME, state.className)
        assertEquals("$TEACHER_SITI, Pak Budi", state.teacherName)
        assertEquals(URL_SITI, state.teacherAvatarUrl)
        assertEquals("2 Guru Pengajar • $CLASS_MATAHARI_NAME", state.teacherRole)
        assertEquals(2, state.teacherCount)
    }

    @Test
    fun `loadPeserta Guru falls back to session user name when teachers endpoint fails`() {
        val rosterJson = rosterEnvelope(studentItem(ID_S1, CHILD_ADIT, NISN_ADIT, nisnVerified = true, mood = MOOD_SENANG))

        server.enqueue(jsonResponse(classesEnvelope()))
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"success":false,"message":"error"}"""))
        server.enqueue(jsonResponse(rosterJson))

        val authRepo = signedInAuth(Role.GURU, TEACHER_SITI, URL_SELF)
        val viewModel = PesertaKelasViewModel(
            classRepository = classRepository,
            authRepository = authRepo,
            classManagementRepository = managementRepository
        )
        viewModel.loadPeserta(initialClassId = CLASS_MATAHARI_ID, defaultClassName = CLASS_MATAHARI_NAME)

        val state = settled(viewModel)
        assertEquals(TEACHER_SITI, state.teacherName)
        assertEquals(URL_SELF, state.teacherAvatarUrl)
        assertEquals("Guru Pengajar • $CLASS_MATAHARI_NAME", state.teacherRole)
    }

    @Test
    fun `loadPeserta resolves archived status even when display name was pre-supplied`() {
        val archivedClassJson = """{"success":true,"data":{"page":1,"limit":50,"total":1,"classes":[{"id":"$CLASS_MATAHARI_ID","name":"$CLASS_MATAHARI_NAME","gradeLevel":"TK B","schoolName":"TK Kibooz","academicYear":"2026/2027","joinCode":"MTH123","joinOpen":false,"totalStudents":1,"createdAt":"2026-08-01T00:00:00Z","archivedAt":"2026-10-01T00:00:00Z"}]}}"""
        val rosterJson = rosterEnvelope(studentItem(ID_S1, CHILD_ADIT, NISN_ADIT, nisnVerified = true))

        server.enqueue(jsonResponse(archivedClassJson))
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"success":false,"message":"err"}"""))
        server.enqueue(jsonResponse(rosterJson))

        val authRepo = signedInAuth(Role.GURU, TEACHER_SITI, URL_SELF)
        val viewModel = PesertaKelasViewModel(
            classRepository = classRepository,
            authRepository = authRepo,
            classManagementRepository = managementRepository
        )
        viewModel.loadPeserta(initialClassId = CLASS_MATAHARI_ID, defaultClassName = CLASS_MATAHARI_NAME)

        val state = settled(viewModel)
        assertTrue(state.classArchived)
        assertEquals(CLASS_MATAHARI_NAME, state.className)
    }

    @Test
    fun `loadPeserta Wali resolves class teacher from chat contacts`() {
        val contactsJson = """{"success":true,"data":{"page":1,"limit":50,"total":3,"contacts":[{"id":"g-other","fullName":"Guru Kelas Lain","role":"GURU","avatarUrl":null,"deleted":false,"classes":[{"id":"c-bulan","name":"Kelas Bulan"}]},{"id":"w-self","fullName":"Bapak Hendra","role":"WALI","avatarUrl":null,"deleted":false,"classes":[{"id":"$CLASS_MATAHARI_ID","name":"$CLASS_MATAHARI_NAME"}]},{"id":"g-matahari","fullName":"$TEACHER_SITI","role":"GURU","avatarUrl":"$URL_SITI","deleted":false,"classes":[{"id":"$CLASS_MATAHARI_ID","name":"$CLASS_MATAHARI_NAME"}]}]}}"""

        server.enqueue(jsonResponse(singleChildEnvelope()))
        server.enqueue(jsonResponse(contactsJson))
        server.enqueue(jsonResponse(leaderboardEnvelope()))

        val client = testClient(server)
        val authRepo = signedInAuth(Role.WALI, "Bapak Hendra Gunawan")
        val viewModel = PesertaKelasViewModel(
            classRepository = classRepository,
            leaderboardRepository = LeaderboardRepository(client.create(LeaderboardApi::class.java), client.moshi),
            authRepository = authRepo,
            chatRepository = ChatRepository(client.create(ChatApi::class.java), client.moshi)
        )
        viewModel.loadPeserta(initialClassId = CLASS_MATAHARI_ID, defaultClassName = "")

        val state = settled(viewModel)
        assertFalse(state.isTeacher)
        assertEquals(CLASS_MATAHARI_NAME, state.className)
        assertEquals(TEACHER_SITI, state.teacherName)
        assertEquals(URL_SITI, state.teacherAvatarUrl)
        assertEquals("Guru Pengajar • $CLASS_MATAHARI_NAME", state.teacherRole)
        assertEquals(1, state.students.size)
    }

    @Test
    fun `loadPeserta retains teachers during same-class failure, applies empty success, and resets on class change`() {
        val secondClassId = "c-bulan"
        val childrenJson = """{"success":true,"data":{"children":[{"id":"$ID_S1","fullName":"$CHILD_ADIT","nisn":"$NISN_ADIT","avatarUrl":null,"classId":"$CLASS_MATAHARI_ID","className":"$CLASS_MATAHARI_NAME"},{"id":"$ID_S2","fullName":"Bima","nisn":"$NISN_BELLA","avatarUrl":null,"classId":"$secondClassId","className":"Kelas Bulan"}]}}"""
        val twoTeachersJson = """{"success":true,"data":{"page":1,"limit":50,"total":2,"contacts":[{"id":"g1","fullName":"$TEACHER_SITI","role":"GURU","avatarUrl":"$URL_SITI","deleted":false,"classes":[{"id":"$CLASS_MATAHARI_ID","name":"$CLASS_MATAHARI_NAME"}]},{"id":"g2","fullName":"$TEACHER_BUDI","role":"GURU","avatarUrl":null,"deleted":false,"classes":[{"id":"$CLASS_MATAHARI_ID","name":"$CLASS_MATAHARI_NAME"}]}]}}"""
        fun serverError() = MockResponse().setResponseCode(500).setBody("""{"success":false,"message":"server err"}""")
        fun enqueueWaliLoad(contactsResponse: MockResponse) {
            server.enqueue(jsonResponse(childrenJson))
            server.enqueue(contactsResponse)
            server.enqueue(jsonResponse(leaderboardEnvelope()))
        }

        val client = testClient(server)
        val viewModel = PesertaKelasViewModel(
            classRepository = classRepository,
            leaderboardRepository = LeaderboardRepository(client.create(LeaderboardApi::class.java), client.moshi),
            authRepository = signedInAuth(Role.WALI, "Wali Anak"),
            chatRepository = ChatRepository(client.create(ChatApi::class.java), client.moshi)
        )

        enqueueWaliLoad(jsonResponse(twoTeachersJson))
        viewModel.loadPeserta(initialClassId = CLASS_MATAHARI_ID, defaultClassName = "")
        val initial = settled(viewModel)
        assertEquals(2, initial.teacherCount)

        enqueueWaliLoad(serverError())
        viewModel.loadPeserta(initialClassId = CLASS_MATAHARI_ID, defaultClassName = "")
        val afterFailure = settled(viewModel)
        assertEquals(2, afterFailure.teacherCount)
        assertEquals("$TEACHER_SITI, $TEACHER_BUDI", afterFailure.teacherName)
        assertEquals(URL_SITI, afterFailure.teacherAvatarUrl)

        enqueueWaliLoad(jsonResponse(emptyContactsEnvelope()))
        viewModel.loadPeserta(initialClassId = CLASS_MATAHARI_ID, defaultClassName = "")
        val afterEmptySuccess = settled(viewModel)
        assertEquals(1, afterEmptySuccess.teacherCount)
        assertEquals(DEFAULT_TEACHER, afterEmptySuccess.teacherName)
        assertNull(afterEmptySuccess.teacherAvatarUrl)

        enqueueWaliLoad(jsonResponse(twoTeachersJson))
        viewModel.loadPeserta(initialClassId = CLASS_MATAHARI_ID, defaultClassName = "")
        assertEquals(2, settled(viewModel).teacherCount)

        enqueueWaliLoad(serverError())
        viewModel.loadPeserta(initialClassId = secondClassId, defaultClassName = "")
        val switchedClass = settled(viewModel)
        assertEquals(1, switchedClass.teacherCount)
        assertEquals(DEFAULT_TEACHER, switchedClass.teacherName)
        assertNull(switchedClass.teacherAvatarUrl)
    }

    @Test
    fun `loadPeserta preserves seeded teacher metadata when initial contacts lookup fails`() {
        val seededName = "Ibu Rina Puspita"

        server.enqueue(jsonResponse(singleChildEnvelope()))
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"success":false,"message":"offline"}"""))
        server.enqueue(jsonResponse(leaderboardEnvelope()))
        server.enqueue(jsonResponse(singleChildEnvelope()))
        server.enqueue(jsonResponse(emptyContactsEnvelope()))
        server.enqueue(jsonResponse(leaderboardEnvelope()))

        val client = testClient(server)
        val viewModel = PesertaKelasViewModel(
            classRepository = classRepository,
            leaderboardRepository = LeaderboardRepository(client.create(LeaderboardApi::class.java), client.moshi),
            authRepository = signedInAuth(Role.WALI, "Wali Pertama"),
            chatRepository = ChatRepository(client.create(ChatApi::class.java), client.moshi)
        )

        viewModel.loadPeserta(
            initialClassId = CLASS_MATAHARI_ID,
            defaultClassName = CLASS_MATAHARI_NAME,
            initialTeacherName = seededName,
            initialTeacherAvatarUrl = URL_SITI
        )
        val failedInitial = settled(viewModel)
        assertEquals(seededName, failedInitial.teacherName)
        assertEquals(URL_SITI, failedInitial.teacherAvatarUrl)

        viewModel.loadPeserta(
            initialClassId = CLASS_MATAHARI_ID,
            defaultClassName = CLASS_MATAHARI_NAME,
            initialTeacherName = seededName,
            initialTeacherAvatarUrl = URL_SITI
        )
        val refreshedEmpty = settled(viewModel)
        assertEquals(DEFAULT_TEACHER, refreshedEmpty.teacherName)
        assertNull(refreshedEmpty.teacherAvatarUrl)
    }
}
