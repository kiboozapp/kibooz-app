package com.nthb.kibooz.data.remote

import com.nthb.kibooz.data.repository.ForumRepository
import com.nthb.kibooz.util.ForumSubject
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class DevBypassInterceptorForumTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private fun createBypassRepository(): ForumRepository {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val client = OkHttpClient.Builder()
            .addInterceptor(DevBypassInterceptor(object : TokenProvider {
                override fun accessToken(): String = "mock-bypass-token"
            }))
            .build()
        val api = Retrofit.Builder()
            .baseUrl("https://kibooz.invalid/api/v1/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ForumApi::class.java)
        return ForumRepository(api, moshi)
    }

    @Test
    fun `mock forum applies subjectCode and generalOnly query filters consistently`() = runBlocking {
        val repo = createBypassRepository()
        val classId = "cls-1"
        val all = repo.threads(classId)
        assertTrue(all is ApiResult.Success)
        assertEquals(2, (all as ApiResult.Success).data.total)

        val seniCode = ForumSubject.SENI.code
        val seni = repo.threads(classId, subjectCode = seniCode)
        assertTrue(seni is ApiResult.Success)
        assertEquals(1, (seni as ApiResult.Success).data.total)
        assertEquals(seniCode, seni.data.threads.first().subjectCode)

        val logikaCode = ForumSubject.LOGIKA.code
        val logika = repo.threads(classId, subjectCode = logikaCode)
        assertTrue(logika is ApiResult.Success)
        assertEquals(0, (logika as ApiResult.Success).data.total)

        val general = repo.threads(classId, generalOnly = true)
        assertTrue(general is ApiResult.Success)
        assertEquals(1, (general as ApiResult.Success).data.total)
        assertNull(general.data.threads.first().subjectCode)

        val created = repo.createThread(classId, "Logika", "Belajar pola", subjectCode = logikaCode)
        assertTrue(created is ApiResult.Success)
        val logikaAfter = repo.threads(classId, subjectCode = logikaCode)
        assertEquals(1, (logikaAfter as ApiResult.Success).data.total)
    }
}
