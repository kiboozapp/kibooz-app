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

    @Test
    fun `loadPeserta successfully populates students from class repository`() {
        val json = """
            {
              "success": true,
              "data": {
                "page": 1,
                "limit": 50,
                "total": 2,
                "students": [
                  {
                    "id": "6f1c2a52-0d4b-4a7e-9c11-3b8e5d2f0a01",
                    "fullName": "Amelia Siti Zahra",
                    "nisn": "3124567890",
                    "avatarUrl": null,
                    "currentPoints": 150,
                    "seasonPoints": 150,
                    "classRank": 1,
                    "todayMood": "SENANG"
                  },
                  {
                    "id": "6f1c2a52-0d4b-4a7e-9c11-3b8e5d2f0a02",
                    "fullName": "Farhan Al-Fatih",
                    "nisn": "3124567891",
                    "avatarUrl": null,
                    "currentPoints": 120,
                    "seasonPoints": 120,
                    "classRank": 2,
                    "todayMood": "SEDIH"
                  }
                ]
              }
            }
        """.trimIndent()
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
        val initialRoster = """
            {
              "success": true,
              "data": {
                "page": 1,
                "limit": 50,
                "total": 2,
                "students": [
                  {"id":"s1","fullName":"Bella Saphira","nisn":"0012345672","nisnVerified":false,"nisnNeedsUpdate":false,"avatarUrl":null,"currentPoints":10,"seasonPoints":10,"classRank":1,"todayMood":"SENANG"},
                  {"id":"s2","fullName":"Dimas Anggara","nisn":"0012345672","nisnVerified":false,"nisnNeedsUpdate":false,"avatarUrl":null,"currentPoints":5,"seasonPoints":5,"classRank":2,"todayMood":null}
                ]
              }
            }
        """.trimIndent()
        val refreshedRoster = """
            {
              "success": true,
              "data": {
                "page": 1,
                "limit": 50,
                "total": 2,
                "students": [
                  {"id":"s1","fullName":"Bella Saphira","nisn":"0012345672","nisnVerified":true,"nisnNeedsUpdate":false,"avatarUrl":null,"currentPoints":10,"seasonPoints":10,"classRank":1,"todayMood":"SENANG"},
                  {"id":"s2","fullName":"Dimas Anggara","nisn":"","nisnVerified":false,"nisnNeedsUpdate":true,"avatarUrl":null,"currentPoints":5,"seasonPoints":5,"classRank":2,"todayMood":null}
                ]
              }
            }
        """.trimIndent()

        server.enqueue(jsonResponse(initialRoster))
        server.enqueue(jsonResponse("""{"success":true,"data":{"nisnVerified":true,"displaced":1}}"""))
        server.enqueue(jsonResponse(refreshedRoster))

        val viewModel = PesertaKelasViewModel(classRepository, classManagementRepository = managementRepository)
        viewModel.loadPeserta(initialClassId = "class-1", defaultClassName = "Kelas A")

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
        val roster = """
            {
              "success": true,
              "data": {
                "page": 1,
                "limit": 50,
                "total": 1,
                "students": [
                  {"id":"s1","fullName":"Adit Pratama","nisn":"0012345671","nisnVerified":true,"nisnNeedsUpdate":false,"avatarUrl":null,"currentPoints":100,"seasonPoints":100,"classRank":1,"todayMood":"SENANG"}
                ]
              }
            }
        """.trimIndent()

        server.enqueue(jsonResponse(roster))
        server.enqueue(jsonResponse("""{"success":true,"data":{"nisnVerified":false,"displaced":0}}"""))

        val viewModel = PesertaKelasViewModel(classRepository, classManagementRepository = managementRepository)
        viewModel.loadPeserta(initialClassId = "class-1")
        val loaded = settled(viewModel)

        viewModel.unverifyStudentNisn(loaded.students[0])

        val updated = actionSettled(viewModel)
        assertFalse(updated.students[0].nisnVerified)
        assertEquals(R.string.peserta_nisn_sukses_batal, updated.actionMessage?.resId)
        assertEquals("Adit Pratama", updated.actionMessage?.studentName)
    }

    @Test
    fun `verifyStudentNisn handles NISN_ALREADY_REGISTERED, CLASS_ARCHIVED, and blank NISN guard`() {
        val roster = """
            {
              "success": true,
              "data": {
                "page": 1,
                "limit": 50,
                "total": 2,
                "students": [
                  {"id":"s1","fullName":"Bella","nisn":"0012345672","nisnVerified":false,"nisnNeedsUpdate":false,"avatarUrl":null,"currentPoints":10,"seasonPoints":10,"classRank":1,"todayMood":null},
                  {"id":"s2","fullName":"Dimas","nisn":"","nisnVerified":false,"nisnNeedsUpdate":true,"avatarUrl":null,"currentPoints":0,"seasonPoints":0,"classRank":2,"todayMood":null}
                ]
              }
            }
        """.trimIndent()

        server.enqueue(jsonResponse(roster))
        server.enqueue(jsonResponse(failureJson("NISN_ALREADY_REGISTERED"), 409))
        server.enqueue(jsonResponse(failureJson("CLASS_ARCHIVED"), 409))

        val viewModel = PesertaKelasViewModel(classRepository, classManagementRepository = managementRepository)
        viewModel.loadPeserta(initialClassId = "class-1")
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
        val roster = """
            {
              "success": true,
              "data": {
                "page": 1,
                "limit": 50,
                "total": 2,
                "students": [
                  {"id":"s1","fullName":"Adit Pratama","nisn":"0012345671","nisnVerified":true,"nisnNeedsUpdate":false,"avatarUrl":null,"currentPoints":100,"seasonPoints":100,"classRank":1,"todayMood":"SENANG"},
                  {"id":"s2","fullName":"Cahya Putra","nisn":"0012345673","nisnVerified":false,"nisnNeedsUpdate":false,"avatarUrl":null,"currentPoints":80,"seasonPoints":80,"classRank":2,"todayMood":null}
                ]
              }
            }
        """.trimIndent()

        server.enqueue(jsonResponse(roster))
        server.enqueue(jsonResponse("""{"success":true,"message":"Student removed","data":null}"""))

        val viewModel = PesertaKelasViewModel(classRepository, classManagementRepository = managementRepository)
        viewModel.loadPeserta(initialClassId = "class-1")
        val loaded = settled(viewModel)
        assertEquals(2, loaded.students.size)

        viewModel.removeStudent(loaded.students[1])

        val afterRemove = actionSettled(viewModel)
        assertEquals(1, afterRemove.students.size)
        assertEquals("s1", afterRemove.students.first().id)
        assertEquals(R.string.peserta_keluarkan_sukses, afterRemove.actionMessage?.resId)
        assertEquals("Cahya Putra", afterRemove.actionMessage?.studentName)
    }

    @Test
    fun `removeStudent refreshes roster on STUDENT_NOT_FOUND and locks on CLASS_ARCHIVED`() {
        val initialRoster = """
            {
              "success": true,
              "data": {
                "page": 1,
                "limit": 50,
                "total": 2,
                "students": [
                  {"id":"s1","fullName":"Adit Pratama","nisn":"0012345671","nisnVerified":true,"nisnNeedsUpdate":false,"avatarUrl":null,"currentPoints":100,"seasonPoints":100,"classRank":1,"todayMood":null},
                  {"id":"s2","fullName":"Bella","nisn":"0012345672","nisnVerified":false,"nisnNeedsUpdate":false,"avatarUrl":null,"currentPoints":80,"seasonPoints":80,"classRank":2,"todayMood":null}
                ]
              }
            }
        """.trimIndent()
        val updatedRoster = """
            {
              "success": true,
              "data": {
                "page": 1,
                "limit": 50,
                "total": 1,
                "students": [
                  {"id":"s1","fullName":"Adit Pratama","nisn":"0012345671","nisnVerified":true,"nisnNeedsUpdate":false,"avatarUrl":null,"currentPoints":100,"seasonPoints":100,"classRank":1,"todayMood":null}
                ]
              }
            }
        """.trimIndent()

        server.enqueue(jsonResponse(initialRoster))
        server.enqueue(jsonResponse(failureJson("STUDENT_NOT_FOUND"), 404))
        server.enqueue(jsonResponse(updatedRoster))
        server.enqueue(jsonResponse(failureJson("CLASS_ARCHIVED"), 409))

        val viewModel = PesertaKelasViewModel(classRepository, classManagementRepository = managementRepository)
        viewModel.loadPeserta(initialClassId = "class-1")
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
        val classesJson = """
            {
              "success": true,
              "data": {
                "page": 1,
                "limit": 50,
                "total": 1,
                "classes": [
                  {"id":"c-matahari","name":"Kelas Matahari","gradeLevel":"TK B","schoolName":"TK Kibooz","academicYear":"2026/2027","joinCode":"MTH123","joinOpen":true,"totalStudents":1,"createdAt":"2026-08-01T00:00:00Z"}
                ]
              }
            }
        """.trimIndent()
        val teachersJson = """
            {
              "success": true,
              "data": {
                "teachers": [
                  {"id":"t2","userId":"u2","isSelf":false,"fullName":"Pak Budi","nip":null,"avatarUrl":null,"isOwner":false,"joinedAt":"2026-08-02T00:00:00Z"},
                  {"id":"t1","userId":"u1","isSelf":true,"fullName":"Ibu Siti Aminah, S.Pd","nip":"123","avatarUrl":"https://cdn/siti.png","isOwner":true,"joinedAt":"2026-08-01T00:00:00Z"}
                ]
              }
            }
        """.trimIndent()
        val rosterJson = """
            {
              "success": true,
              "data": {
                "page": 1,
                "limit": 50,
                "total": 1,
                "students": [
                  {"id":"s1","fullName":"Adit Pratama","nisn":"0012345671","nisnVerified":true,"nisnNeedsUpdate":false,"avatarUrl":null,"currentPoints":100,"seasonPoints":100,"classRank":1,"todayMood":"SENANG"}
                ]
              }
            }
        """.trimIndent()

        server.enqueue(jsonResponse(classesJson))
        server.enqueue(jsonResponse(teachersJson))
        server.enqueue(jsonResponse(rosterJson))

        val authRepo = signedInAuth(Role.GURU, "Ibu Siti Aminah, S.Pd", "https://cdn/self.png")
        val viewModel = PesertaKelasViewModel(
            classRepository = classRepository,
            authRepository = authRepo,
            classManagementRepository = managementRepository
        )
        viewModel.loadPeserta(initialClassId = "c-matahari", defaultClassName = "")

        val state = settled(viewModel)
        assertTrue(state.isTeacher)
        assertEquals("Kelas Matahari", state.className)
        assertEquals("Ibu Siti Aminah, S.Pd, Pak Budi", state.teacherName)
        assertEquals("https://cdn/siti.png", state.teacherAvatarUrl)
        assertEquals("2 Guru Pengajar • Kelas Matahari", state.teacherRole)
        assertEquals(2, state.teacherCount)
    }

    @Test
    fun `loadPeserta Guru falls back to session user name when teachers endpoint fails`() {
        val rosterJson = """
            {
              "success": true,
              "data": {
                "page": 1,
                "limit": 50,
                "total": 1,
                "students": [
                  {"id":"s1","fullName":"Adit Pratama","nisn":"0012345671","nisnVerified":true,"nisnNeedsUpdate":false,"avatarUrl":null,"currentPoints":100,"seasonPoints":100,"classRank":1,"todayMood":"SENANG"}
                ]
              }
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"success":false,"message":"error"}"""))
        server.enqueue(jsonResponse(rosterJson))

        val authRepo = signedInAuth(Role.GURU, "Ibu Siti Aminah, S.Pd", "https://cdn/self.png")
        val viewModel = PesertaKelasViewModel(
            classRepository = classRepository,
            authRepository = authRepo,
            classManagementRepository = managementRepository
        )
        viewModel.loadPeserta(initialClassId = "c-matahari", defaultClassName = "Kelas Matahari")

        val state = settled(viewModel)
        assertEquals("Ibu Siti Aminah, S.Pd", state.teacherName)
        assertEquals("https://cdn/self.png", state.teacherAvatarUrl)
        assertEquals("Guru Pengajar • Kelas Matahari", state.teacherRole)
    }

    @Test
    fun `loadPeserta Wali resolves class teacher from chat contacts`() {
        val childrenJson = """
            {
              "success": true,
              "data": {
                "children": [
                  {"id":"s1","fullName":"Adit Pratama","nisn":"0012345671","avatarUrl":null,"classId":"c-matahari","className":"Kelas Matahari"}
                ]
              }
            }
        """.trimIndent()
        val contactsJson = """
            {
              "success": true,
              "data": {
                "page": 1,
                "limit": 50,
                "total": 3,
                "contacts": [
                  {"id":"g-other","fullName":"Guru Kelas Lain","role":"GURU","avatarUrl":null,"deleted":false,"classes":[{"id":"c-bulan","name":"Kelas Bulan"}]},
                  {"id":"w-self","fullName":"Bapak Hendra","role":"WALI","avatarUrl":null,"deleted":false,"classes":[{"id":"c-matahari","name":"Kelas Matahari"}]},
                  {"id":"g-matahari","fullName":"Ibu Siti Aminah, S.Pd","role":"GURU","avatarUrl":"https://cdn/siti.png","deleted":false,"classes":[{"id":"c-matahari","name":"Kelas Matahari"}]}
                ]
              }
            }
        """.trimIndent()
        val leaderboardJson = """
            {
              "success": true,
              "data": {
                "podium": [{"rank":1,"studentName":"Adit Pratama","points":120,"avatarUrl":null}],
                "rankings": []
              }
            }
        """.trimIndent()

        server.enqueue(jsonResponse(childrenJson))
        server.enqueue(jsonResponse(contactsJson))
        server.enqueue(jsonResponse(leaderboardJson))

        val client = testClient(server)
        val authRepo = signedInAuth(Role.WALI, "Bapak Hendra Gunawan")
        val viewModel = PesertaKelasViewModel(
            classRepository = classRepository,
            leaderboardRepository = LeaderboardRepository(client.create(LeaderboardApi::class.java), client.moshi),
            authRepository = authRepo,
            chatRepository = ChatRepository(client.create(ChatApi::class.java), client.moshi)
        )
        viewModel.loadPeserta(initialClassId = "c-matahari", defaultClassName = "")

        val state = settled(viewModel)
        assertFalse(state.isTeacher)
        assertEquals("Kelas Matahari", state.className)
        assertEquals("Ibu Siti Aminah, S.Pd", state.teacherName)
        assertEquals("https://cdn/siti.png", state.teacherAvatarUrl)
        assertEquals("Guru Pengajar • Kelas Matahari", state.teacherRole)
        assertEquals(1, state.students.size)
    }
}
