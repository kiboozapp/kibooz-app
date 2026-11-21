package com.nthb.kibooz.ui.camera

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ChildList
import com.nthb.kibooz.data.remote.ClassApi
import com.nthb.kibooz.data.remote.ClassList
import com.nthb.kibooz.data.remote.ClassStudent
import com.nthb.kibooz.data.remote.ClassStudentList
import com.nthb.kibooz.data.repository.ClassRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewRosterViewModelTest {

    private class FakeClassApi : ClassApi {
        var students = listOf(student("s1", "Ani"), student("s2", "Budi"), student("s3", "Citra"))
        var fail = false
        val asked = mutableListOf<String>()

        override suspend fun listStudents(classId: String, page: Int, limit: Int): Response<ApiEnvelope<ClassStudentList>> {
            asked += classId
            if (fail) throw IOException("offline")
            return Response.success(ApiEnvelope(true, null, ClassStudentList(page, limit, students.size, students)))
        }

        override suspend fun listClasses(page: Int, limit: Int): Response<ApiEnvelope<ClassList>> = error("not used")

        override suspend fun listChildren(): Response<ApiEnvelope<ChildList>> = error("not used")

        companion object {
            fun student(id: String, name: String) = ClassStudent(id, name, "00$id", null, 0, 0, 1, null)
        }
    }

    private val api = FakeClassApi()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private fun viewModel(classId: String? = "c1") = ReviewRosterViewModel(ClassRepository(api, moshi), classId)

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the roster of the scanned class loads once on creation`() {
        val state = viewModel().state.value

        assertTrue(state.loaded)
        assertFalse(state.loading)
        assertEquals(listOf("Ani", "Budi", "Citra"), state.students.map { it.name })
        assertEquals(listOf("c1"), api.asked)
    }

    @Test
    fun `students already on the review list cannot be added again`() {
        val viewModel = viewModel()

        assertEquals(listOf("s2"), viewModel.addable(setOf("s1", "s3")).map { it.id })
        assertTrue(viewModel.addable(setOf("s1", "s2", "s3")).isEmpty())
    }

    @Test
    fun `without a class nothing is requested`() {
        val state = viewModel(classId = null).state.value

        assertEquals(R.string.review_kelas_belum_dipilih, state.error)
        assertFalse(state.loaded)
        assertTrue(api.asked.isEmpty())
    }

    @Test
    fun `a failed load can be retried`() {
        api.fail = true
        val viewModel = viewModel()
        assertEquals(R.string.error_network, viewModel.state.value.error)
        assertFalse(viewModel.state.value.loaded)

        viewModel.errorShown()
        assertNull(viewModel.state.value.error)

        api.fail = false
        viewModel.load()
        assertTrue(viewModel.state.value.loaded)
        assertEquals(3, viewModel.state.value.students.size)
    }
}
