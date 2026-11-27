package com.nthb.kibooz.data.remote

data class ForumAuthor(
    val id: String,
    val fullName: String,
    val role: String,
    val avatarUrl: String? = null
)

data class ForumThreadItem(
    val id: String,
    val title: String,
    val body: String,
    val subjectCode: String? = null,
    val author: ForumAuthor,
    val replyCount: Int = 0,
    val createdAt: String,
    val editedAt: String? = null
)

data class ForumThreadList(
    val threads: List<ForumThreadItem> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0
)

data class CreateForumThreadRequest(
    val title: String,
    val body: String,
    val subjectCode: String? = null
)

data class ForumReplyItem(
    val id: String,
    val body: String,
    val author: ForumAuthor,
    val createdAt: String,
    val editedAt: String? = null
)

data class ForumReplyList(
    val replies: List<ForumReplyItem> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0
)

data class CreateForumReplyRequest(
    val body: String
)
