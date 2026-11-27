package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.CreateForumReplyRequest
import com.nthb.kibooz.data.remote.CreateForumThreadRequest
import com.nthb.kibooz.data.remote.ForumApi
import com.nthb.kibooz.data.remote.ForumReplyItem
import com.nthb.kibooz.data.remote.ForumReplyList
import com.nthb.kibooz.data.remote.ForumThreadItem
import com.nthb.kibooz.data.remote.ForumThreadList
import com.nthb.kibooz.data.remote.newIdempotencyKey
import com.nthb.kibooz.data.remote.safeApiCall
import com.squareup.moshi.Moshi

class ForumRepository(private val api: ForumApi, private val moshi: Moshi) {

    suspend fun threads(
        classId: String,
        subjectCode: String? = null,
        generalOnly: Boolean? = null,
        page: Int = 1,
        limit: Int = 20
    ): ApiResult<ForumThreadList> {
        val cleanSubjectCode = subjectCode?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedGeneralOnly = if (cleanSubjectCode == null) generalOnly else null
        return safeApiCall(moshi) {
            api.listThreads(
                classId = classId,
                subjectCode = cleanSubjectCode,
                generalOnly = resolvedGeneralOnly,
                page = page,
                limit = limit
            )
        }
    }

    suspend fun createThread(
        classId: String,
        title: String,
        body: String,
        subjectCode: String? = null
    ): ApiResult<ForumThreadItem> =
        safeApiCall(moshi) {
            val key = newIdempotencyKey()
            val cleanSubjectCode = subjectCode?.trim()?.takeIf { it.isNotEmpty() }
            api.createThread(
                classId,
                key,
                CreateForumThreadRequest(
                    title = title,
                    body = body,
                    subjectCode = cleanSubjectCode
                )
            )
        }

    suspend fun replies(classId: String, postId: String, page: Int = 1, limit: Int = 20): ApiResult<ForumReplyList> =
        safeApiCall(moshi) { api.listReplies(classId, postId, page, limit) }

    suspend fun createReply(classId: String, postId: String, body: String): ApiResult<ForumReplyItem> =
        safeApiCall(moshi) {
            val key = newIdempotencyKey()
            api.createReply(classId, postId, key, CreateForumReplyRequest(body))
        }
}
