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

    suspend fun threads(classId: String, page: Int = 1, limit: Int = 20): ApiResult<ForumThreadList> =
        safeApiCall(moshi) { api.listThreads(classId, page, limit) }

    suspend fun createThread(classId: String, title: String, body: String): ApiResult<ForumThreadItem> =
        safeApiCall(moshi) {
            val key = newIdempotencyKey()
            api.createThread(classId, key, CreateForumThreadRequest(title, body))
        }

    suspend fun replies(classId: String, postId: String, page: Int = 1, limit: Int = 20): ApiResult<ForumReplyList> =
        safeApiCall(moshi) { api.listReplies(classId, postId, page, limit) }

    suspend fun createReply(classId: String, postId: String, body: String): ApiResult<ForumReplyItem> =
        safeApiCall(moshi) {
            val key = newIdempotencyKey()
            api.createReply(classId, postId, key, CreateForumReplyRequest(body))
        }
}
