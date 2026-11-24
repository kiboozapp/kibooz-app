package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.data.remote.ClassApi
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.repository.jsonResponse
import com.nthb.kibooz.data.repository.testClient
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

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        server = MockWebServer().apply { start() }
        val client = testClient(server)
        classRepository = ClassRepository(client.create(ClassApi::class.java), client.moshi)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun settled(viewModel: PesertaKelasViewModel): PesertaUiState = runBlocking {
        viewModel.uiState.first { !it.isLoading }
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
}
