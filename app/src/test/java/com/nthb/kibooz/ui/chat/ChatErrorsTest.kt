package com.nthb.kibooz.ui.chat

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.messageRes
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.ui.auth.Access
import com.nthb.kibooz.ui.auth.accessFor
import com.nthb.kibooz.ui.auth.allows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatErrorsTest {

    private fun failure(status: Int, errorCode: String?) = ApiResult.Failure(status = status, errorCode = errorCode, message = null)

    @Test
    fun `each chat error code has its own message whatever the status is`() {
        assertEquals(R.string.error_chat_contact_not_allowed, failure(403, "CHAT_CONTACT_NOT_ALLOWED").messageRes())
        assertEquals(R.string.error_chat_conversation_not_found, failure(404, "CHAT_CONVERSATION_NOT_FOUND").messageRes())
        assertEquals(R.string.error_chat_message_not_found, failure(404, "CHAT_MESSAGE_NOT_FOUND").messageRes())
        assertEquals(R.string.error_chat_sending_closed, failure(409, "CHAT_SENDING_CLOSED").messageRes())
    }

    @Test
    fun `the idempotency and rate limit codes of a send keep their messages`() {
        assertEquals(R.string.error_request_in_progress, failure(409, "IDEMPOTENCY_IN_PROGRESS").messageRes())
        assertEquals(R.string.error_key_reused, failure(422, "IDEMPOTENCY_KEY_REUSED").messageRes())
        assertEquals(R.string.error_rate_limited, failure(429, "RATE_LIMITED").messageRes())
    }

    @Test
    fun `the chat screens need a session and are open to both roles`() {
        listOf("ChatListActivity", "ChatActivity").forEach { name ->
            val access = accessFor("com.nthb.kibooz.ui.chat.$name")

            assertEquals(name, Access.ANY_ROLE, access)
            assertTrue(access.allows(Role.GURU))
            assertTrue(access.allows(Role.WALI))
            assertFalse(access.allows(null))
        }
    }
}
