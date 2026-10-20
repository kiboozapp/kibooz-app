package com.nthb.kibooz.data.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nthb.kibooz.data.sampleSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSessionStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: DataStoreSessionStore

    @Before
    fun setUp() {
        val file = folder.newFile("session.preferences_pb")
        file.delete()
        store = DataStoreSessionStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `reads nothing before a session was written`() = runBlocking {
        assertNull(store.readSession())
    }

    @Test
    fun `reads back the session that was written`() = runBlocking {
        store.writeSession(sampleSession)

        assertEquals(sampleSession, store.readSession())
    }

    @Test
    fun `an avatar url is stored and then removed when the next session has none`() = runBlocking {
        val withAvatar = sampleSession.copy(user = sampleSession.user.copy(avatarUrl = "https://x/a.png"))
        store.writeSession(withAvatar)
        assertEquals("https://x/a.png", store.readSession()?.user?.avatarUrl)

        store.writeSession(sampleSession)

        assertNull(store.readSession()?.user?.avatarUrl)
    }

    @Test
    fun `the next write replaces both tokens`() = runBlocking {
        store.writeSession(sampleSession)

        store.writeSession(sampleSession.copy(accessToken = "new-access", refreshToken = "new-refresh"))

        val read = store.readSession()!!
        assertEquals("new-access", read.accessToken)
        assertEquals("new-refresh", read.refreshToken)
    }

    @Test
    fun `clearing removes the session but keeps device tokens`() = runBlocking {
        store.writeSession(sampleSession)
        store.writeDeviceToken("ani@example.com", "device-1")

        store.clearSession()

        assertNull(store.readSession())
        assertEquals("device-1", store.readDeviceToken("ani@example.com"))
    }

    @Test
    fun `device tokens are kept per email and ignore case and spaces`() = runBlocking {
        store.writeDeviceToken("Ani@Example.com ", "device-ani")
        store.writeDeviceToken("budi@example.com", "device-budi")

        assertEquals("device-ani", store.readDeviceToken("ani@example.com"))
        assertEquals("device-budi", store.readDeviceToken("budi@example.com"))
        assertNull(store.readDeviceToken("siti@example.com"))
    }
}
