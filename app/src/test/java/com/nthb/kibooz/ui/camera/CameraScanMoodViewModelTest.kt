package com.nthb.kibooz.ui.camera

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ChildList
import com.nthb.kibooz.data.remote.ClassApi
import com.nthb.kibooz.data.remote.ClassList
import com.nthb.kibooz.data.remote.ClassStudent
import com.nthb.kibooz.data.remote.ClassStudentList
import com.nthb.kibooz.data.remote.ClassSummary
import com.nthb.kibooz.data.repository.ClassRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class CameraScanMoodViewModelTest {

    private class FakeClassApi : ClassApi {
        var classes: List<ClassSummary> = emptyList()
        var studentsByClass: Map<String, List<ClassStudent>> = emptyMap()
        val studentRequests = mutableListOf<String>()
        var classRequests = 0

        /** Jika terisi, permintaan berikutnya gagal dengan ini sekali saja. */
        var nextFailure: (() -> Response<*>)? = null

        override suspend fun listClasses(page: Int, limit: Int): Response<ApiEnvelope<ClassList>> {
            classRequests++
            nextFailure?.let { failure -> nextFailure = null; @Suppress("UNCHECKED_CAST") return failure() as Response<ApiEnvelope<ClassList>> }
            return Response.success(ApiEnvelope(true, null, ClassList(page, limit, classes.size, classes)))
        }

        override suspend fun listStudents(classId: String, page: Int, limit: Int): Response<ApiEnvelope<ClassStudentList>> {
            studentRequests += classId
            nextFailure?.let { failure -> nextFailure = null; @Suppress("UNCHECKED_CAST") return failure() as Response<ApiEnvelope<ClassStudentList>> }
            val students = studentsByClass[classId].orEmpty()
            return Response.success(ApiEnvelope(true, null, ClassStudentList(page, limit, students.size, students)))
        }

        override suspend fun listChildren(): Response<ApiEnvelope<ChildList>> = error("tidak dipakai")
    }

    private val api = FakeClassApi()
    private lateinit var viewModel: CameraScanMoodViewModel

    private fun summary(id: String, name: String) =
        ClassSummary(id, name, "Class A", "SD Kibooz", "2026/2027", "ABC23456", true, 2, "2026-07-01T00:00:00Z")

    private fun student(id: String, name: String) =
        ClassStudent(id, name, "00$id", null, 0, 0, 1, null)

    private fun httpError(code: Int, errorCode: String): () -> Response<*> = {
        Response.error<Any>(
            code,
            """{"success":false,"message":"x","errorCode":"$errorCode"}""".toResponseBody("application/json".toMediaType())
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        viewModel = CameraScanMoodViewModel(ClassRepository(api, moshi))
        api.studentsByClass = mapOf(
            "c1" to listOf(student("s1", "Amelia"), student("s2", "Farhan")),
            "c2" to listOf(student("s3", "Kenzo"))
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a class from the launcher is used without asking for the class list`() {
        viewModel.start("c1")

        val state = viewModel.state.value
        assertEquals("c1", state.classId)
        assertEquals(listOf("Amelia", "Farhan"), state.students.map { it.name })
        assertEquals(listOf("s1", "s2"), state.students.map { it.id })
        assertFalse(state.loading)
        assertEquals(0, api.classRequests)
    }

    @Test
    fun `a teacher with one class gets it selected and its students loaded`() {
        api.classes = listOf(summary("c1", "Bunga"))

        viewModel.start(null)

        val state = viewModel.state.value
        assertEquals("c1", state.classId)
        assertEquals(2, state.students.size)
        assertTrue(state.classChoices.isEmpty())
    }

    @Test
    fun `a teacher with several classes has to choose and no students load before that`() {
        api.classes = listOf(summary("c1", "Bunga"), summary("c2", "Mawar"))

        viewModel.start("  ")

        val state = viewModel.state.value
        assertEquals(listOf("Bunga", "Mawar"), state.classChoices.map { it.name })
        assertNull(state.classId)
        assertTrue(api.studentRequests.isEmpty())
        assertFalse(state.loading)
    }

    @Test
    fun `choosing a class loads its students and clears the choices`() {
        api.classes = listOf(summary("c1", "Bunga"), summary("c2", "Mawar"))
        viewModel.start(null)

        viewModel.selectClass("c2")

        val state = viewModel.state.value
        assertEquals("c2", state.classId)
        assertEquals(listOf("Kenzo"), state.students.map { it.name })
        assertTrue(state.classChoices.isEmpty())
    }

    @Test
    fun `a teacher without classes is told so`() {
        viewModel.start(null)

        val state = viewModel.state.value
        assertEquals(R.string.kelas_kosong, state.error)
        assertNull(state.classId)
        assertFalse(state.loading)
    }

    @Test
    fun `a failed class list can be retried`() {
        api.classes = listOf(summary("c1", "Bunga"))
        api.nextFailure = { throw IOException("offline") }

        viewModel.start(null)
        assertEquals(R.string.error_network, viewModel.state.value.error)

        viewModel.retry()

        assertNull(viewModel.state.value.error)
        assertEquals("c1", viewModel.state.value.classId)
        assertEquals(2, viewModel.state.value.students.size)
    }

    @Test
    fun `a failed student list keeps the class and retries only the students`() {
        api.nextFailure = httpError(429, "RATE_LIMITED")

        viewModel.start("c1")
        val failed = viewModel.state.value
        assertEquals(R.string.error_rate_limited, failed.error)
        assertEquals("c1", failed.classId)
        assertTrue(failed.students.isEmpty())

        viewModel.retry()

        assertNull(viewModel.state.value.error)
        assertEquals(2, viewModel.state.value.students.size)
        assertEquals(0, api.classRequests)
        assertEquals(listOf("c1", "c1"), api.studentRequests)
    }

    @Test
    fun `a teacher who does not teach the launched class gets the forbidden message`() {
        api.nextFailure = httpError(403, "AUTH_FORBIDDEN")

        viewModel.start("other")

        assertEquals(R.string.error_forbidden, viewModel.state.value.error)
    }

    @Test
    fun `start only works once so recreating the screen does not reload`() {
        viewModel.start("c1")
        viewModel.start("c2")

        assertEquals("c1", viewModel.state.value.classId)
        assertEquals(listOf("c1"), api.studentRequests)
    }

    @Test
    fun `a class with no students gives an empty list and no error`() {
        api.studentsByClass = mapOf("c1" to emptyList())

        viewModel.start("c1")

        val state = viewModel.state.value
        assertTrue(state.students.isEmpty())
        assertNull(state.error)
        assertEquals("c1", state.classId)
    }
}
