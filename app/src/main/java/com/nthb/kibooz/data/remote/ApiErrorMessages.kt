package com.nthb.kibooz.data.remote

import androidx.annotation.StringRes
import com.nthb.kibooz.R

/**
 * Pesan untuk pengguna dari satu hasil API yang gagal. Percabangan memakai `errorCode`, dan kode status
 * hanya dipakai saat balasan bukan amplop JSON (misalnya dari proxy). Hasil [ApiResult.Success] tidak
 * punya pesan galat, jadi mengembalikan null.
 */
@StringRes
fun ApiResult<*>.errorMessageRes(): Int? = when (this) {
    is ApiResult.Success -> null
    is ApiResult.NetworkError -> R.string.error_network
    is ApiResult.Failure -> messageRes()
}

@StringRes
fun ApiResult.Failure.messageRes(): Int {
    errorCode?.let(::messageForCode)?.let { return it }

    return when {
        status == 401 -> R.string.error_session_ended
        status == 403 -> R.string.error_forbidden
        status == 404 -> R.string.error_not_found
        status == 413 -> R.string.error_file_too_large
        status == 429 -> R.string.error_rate_limited
        status == 500 || status in 502..504 -> R.string.error_unavailable
        else -> R.string.error_unknown
    }
}

@StringRes
private fun messageForCode(code: String): Int? = when (code) {
        "AUTH_INVALID_CREDENTIALS" -> R.string.error_invalid_credentials
        "AUTH_PASSWORD_INCORRECT" -> R.string.error_password_incorrect
        "AUTH_TOKEN_MISSING" -> R.string.error_session_ended
        "AUTH_TOKEN_INVALID" -> R.string.error_session_ended
        "AUTH_REFRESH_INVALID" -> R.string.error_session_ended
        "AUTH_FORBIDDEN" -> R.string.error_forbidden
        "EMAIL_ALREADY_REGISTERED" -> R.string.error_email_taken
        "NISN_ALREADY_REGISTERED" -> R.string.error_nisn_taken
        "NIP_ALREADY_REGISTERED" -> R.string.error_nip_taken
        "CHILD_LIMIT_REACHED" -> R.string.error_child_limit
        "CLASS_CODE_NOT_FOUND" -> R.string.error_class_code_not_found
        "CLASS_JOINING_CLOSED" -> R.string.error_class_joining_closed
        "CLASS_FULL" -> R.string.error_class_full
        "CLASS_LIMIT_REACHED" -> R.string.error_class_limit
        "CLASS_ARCHIVED" -> R.string.error_class_archived
        "CLASS_NOT_ARCHIVED" -> R.string.error_class_not_archived
        "CLASS_NO_ACTIVE_TEACHER" -> R.string.error_class_no_teacher
        "CLASS_NOT_FOUND" -> R.string.error_class_gone
        "ALREADY_IN_CLASS" -> R.string.error_already_in_class
        "MOVE_REQUEST_PENDING" -> R.string.kelas_gabung_menunggu
        "MOVE_REQUEST_NOT_FOUND" -> R.string.error_move_request_not_found
        "NO_CHILD_REGISTERED" -> R.string.error_no_child
        "STUDENT_NOT_FOUND" -> R.string.error_not_found
        "PROFILE_NOT_FOUND" -> R.string.error_not_found
        "GUIDANCE_NOT_FOUND" -> R.string.error_not_found
        "FORUM_POST_NOT_FOUND" -> R.string.error_not_found
        "VIDEO_NOT_FOUND" -> R.string.error_not_found
        "TEACHER_NOT_FOUND" -> R.string.error_not_found
        "NOT_FOUND" -> R.string.error_not_found
        "TRASH_DAILY_LIMIT_REACHED" -> R.string.error_trash_daily_limit
        "MOOD_LOG_LIMIT_REACHED" -> R.string.error_mood_log_limit
        "RATE_LIMITED" -> R.string.error_rate_limited
        "IDEMPOTENCY_IN_PROGRESS" -> R.string.error_request_in_progress
        "IDEMPOTENCY_KEY_REUSED" -> R.string.error_key_reused
        "BODY_TOO_LARGE" -> R.string.error_file_too_large
        "FILE_TOO_LARGE" -> R.string.error_file_too_large
        "IMAGE_DIMENSIONS_TOO_LARGE" -> R.string.error_image_too_large
        "INVALID_IMAGE" -> R.string.error_image_invalid
        "VALIDATION_ERROR" -> R.string.error_validation
        "BAD_REQUEST" -> R.string.error_validation
        "STORAGE_UNAVAILABLE" -> R.string.error_storage_unavailable
        "STORAGE_ERROR" -> R.string.error_storage_unavailable
        "LAST_TEACHER" -> R.string.error_last_teacher
        "SUCCESSOR_REQUIRED" -> R.string.error_successor_required
        "CLASS_OWNER_REQUIRED" -> R.string.error_owner_required
        "TEACHER_ALREADY_IN_CLASS" -> R.string.error_teacher_already
        "TEACHER_NOT_IN_CLASS" -> R.string.error_teacher_not_in_class
        "INTERNAL_ERROR" -> R.string.error_server
        else -> null
}
