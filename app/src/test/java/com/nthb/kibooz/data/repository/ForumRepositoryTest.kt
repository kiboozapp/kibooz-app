package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ForumApi
import com.nthb.kibooz.util.ForumSubject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class ForumRepositoryTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var repository: ForumRepository

    private val threadJson = """
        {
            "success": true,
            "data": {
                "id": "th-1",
                "title": "Diskusi ${ForumSubject.SENI.displayName}",
                "body": "Bawa krayon besok ya",
                "$SUBJECT_FIELD": "${ForumSubject.SENI.code}",
                "author": {
                    "id": "g-1",
                    "fullName": "Siti Rahayu",
                    "role": "GURU",
                    "avatarUrl": null
                },
                "replyCount": 0,
                "createdAt": "2026-11-27T08:00:00Z",
                "editedAt": null
            }
        }
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = testClient(server)
        repository = ForumRepository(client.create(ForumApi::class.java), client.moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `threads forwards subjectCode or generalOnly query filters mutually exclusively`() = runBlocking {
        val seniCode = ForumSubject.SENI.code
        val listBody = """
            {
                "success": true,
                "data": {
                    "page": 1,
                    "limit": 20,
                    "total": 1,
                    "threads": [
                        {
                            "id": "th-1",
                            "title": "Diskusi Seni",
                            "body": "Contoh",
                            "$SUBJECT_FIELD": "$seniCode",
                            "author": {
                                "id": "g-1",
                                "fullName": "Siti Rahayu",
                                "role": "GURU",
                                "avatarUrl": null
                            },
                            "replyCount": 2,
                            "createdAt": "2026-11-27T08:00:00Z",
                            "editedAt": null
                        }
                    ]
                }
            }
        """.trimIndent()
        repeat(4) { server.enqueue(jsonResponse(listBody)) }

        val bySubject = repository.threads(CLASS_ID, subjectCode = " $seniCode ", generalOnly = true)
        assertTrue(bySubject is ApiResult.Success)
        assertEquals(seniCode, (bySubject as ApiResult.Success).data.threads.first().subjectCode)
        assertEquals(
            "$FORUM_PATH?$SUBJECT_FIELD=$seniCode&$PAGE_QUERY",
            server.takeRequest().path
        )

        repository.threads(CLASS_ID, subjectCode = "  ", generalOnly = true)
        assertEquals("$FORUM_PATH?generalOnly=true&$PAGE_QUERY", server.takeRequest().path)

        repository.threads(CLASS_ID, generalOnly = true)
        assertEquals("$FORUM_PATH?generalOnly=true&$PAGE_QUERY", server.takeRequest().path)

        repository.threads(CLASS_ID)
        assertEquals("$FORUM_PATH?$PAGE_QUERY", server.takeRequest().path)
    }

    @Test
    fun `createThread serializes subjectCode when provided and omits null when omitted`() = runBlocking {
        val logikaCode = ForumSubject.LOGIKA.code
        server.enqueue(jsonResponse(threadJson, code = 201))
        val tagged = repository.createThread(CLASS_ID, "Judul", "Isi", subjectCode = " $logikaCode ")
        assertTrue(tagged is ApiResult.Success)
        val req1 = server.takeRequest()
        assertTrue(req1.body.readUtf8().contains(""""$SUBJECT_FIELD":"$logikaCode""""))

        server.enqueue(
            jsonResponse(threadJson.replace(""""${ForumSubject.SENI.code}"""", "null"), code = 201)
        )
        val general = repository.createThread(CLASS_ID, "Umum", "Isi umum")
        assertTrue(general is ApiResult.Success)
        assertNull((general as ApiResult.Success).data.subjectCode)
        val body2 = server.takeRequest().body.readUtf8()
        assertTrue(!body2.contains(SUBJECT_FIELD))
    }

    private companion object {
        const val CLASS_ID = "cls-1"
        const val SUBJECT_FIELD = "subjectCode"
        const val FORUM_PATH = "/api/v1/classes/$CLASS_ID/forum"
        const val PAGE_QUERY = "page=1&limit=20"
    }
}
