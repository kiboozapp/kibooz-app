package com.nthb.kibooz

import android.content.Context
import com.nthb.kibooz.data.remote.AccountApi
import com.nthb.kibooz.data.remote.ApiClient
import com.nthb.kibooz.data.remote.AuthApi
import com.nthb.kibooz.data.remote.ClassApi
import com.nthb.kibooz.data.remote.ForumApi
import com.nthb.kibooz.data.remote.GuruApi
import com.nthb.kibooz.data.remote.GuruClassApi
import com.nthb.kibooz.data.remote.GuruProfileApi
import com.nthb.kibooz.data.remote.HistoryApi
import com.nthb.kibooz.data.remote.LeaderboardApi
import com.nthb.kibooz.data.remote.ModuleApi
import com.nthb.kibooz.data.remote.MoodApi
import com.nthb.kibooz.data.remote.MoveRequestApi
import com.nthb.kibooz.data.remote.NotificationApi
import com.nthb.kibooz.data.remote.TokenAuthenticator
import com.nthb.kibooz.data.remote.TrashApi
import com.nthb.kibooz.data.remote.VideoApi
import com.nthb.kibooz.data.remote.WaliApi
import com.nthb.kibooz.data.remote.WaliProfileApi
import com.nthb.kibooz.data.repository.AccountRepository
import com.nthb.kibooz.data.repository.AuthRepository
import com.nthb.kibooz.data.repository.ClassManagementRepository
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.repository.ForumRepository
import com.nthb.kibooz.data.repository.GuruProfileRepository
import com.nthb.kibooz.data.repository.GuruRepository
import com.nthb.kibooz.data.repository.HistoryRepository
import com.nthb.kibooz.data.repository.LeaderboardRepository
import com.nthb.kibooz.data.repository.ModuleRepository
import com.nthb.kibooz.data.repository.MoodRepository
import com.nthb.kibooz.data.repository.MoveRequestRepository
import com.nthb.kibooz.data.repository.NotificationRepository
import com.nthb.kibooz.data.repository.TrashRepository
import com.nthb.kibooz.data.repository.VideoRepository
import com.nthb.kibooz.data.repository.WaliProfileRepository
import com.nthb.kibooz.data.repository.WaliRepository
import com.nthb.kibooz.data.session.DataStoreSessionStore
import com.nthb.kibooz.data.session.SessionStore
import com.nthb.kibooz.util.AvatarLoader

/** Perakitan manual objek yang hidup selama proses. Dibuat sekali oleh [KiboozApp]. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val sessionStore: SessionStore by lazy { DataStoreSessionStore(appContext) }

    // ApiClient butuh AuthRepository untuk token dan refresh, sedangkan AuthRepository butuh ApiClient
    // untuk AuthApi. Keduanya lazy dan lambda di bawah baru dievaluasi saat ada request, jadi tidak melingkar.
    val apiClient: ApiClient by lazy {
        ApiClient(
            tokenProvider = { authRepository.accessToken() },
            authenticator = TokenAuthenticator { failedToken -> authRepository.refresh(failedToken) }
        )
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(apiClient.create(AuthApi::class.java), sessionStore, apiClient.moshi)
    }

    val accountRepository: AccountRepository by lazy {
        AccountRepository(apiClient.create(AccountApi::class.java), apiClient.moshi)
    }

    val moodRepository: MoodRepository by lazy {
        MoodRepository(apiClient.create(MoodApi::class.java), apiClient.moshi)
    }

    val classRepository: ClassRepository by lazy {
        ClassRepository(apiClient.create(ClassApi::class.java), apiClient.moshi)
    }

    val classManagementRepository: ClassManagementRepository by lazy {
        ClassManagementRepository(classRepository, apiClient.create(GuruClassApi::class.java), apiClient.moshi)
    }

    val trashRepository: TrashRepository by lazy {
        TrashRepository(apiClient.create(TrashApi::class.java), apiClient.moshi)
    }

    val waliRepository: WaliRepository by lazy {
        WaliRepository(apiClient.create(WaliApi::class.java), apiClient.moshi)
    }

    val waliProfileRepository: WaliProfileRepository by lazy {
        WaliProfileRepository(apiClient.create(WaliProfileApi::class.java), apiClient.moshi)
    }

    val leaderboardRepository: LeaderboardRepository by lazy {
        LeaderboardRepository(apiClient.create(LeaderboardApi::class.java), apiClient.moshi)
    }

    val guruRepository: GuruRepository by lazy {
        GuruRepository(apiClient.create(GuruApi::class.java), apiClient.moshi)
    }

    val guruProfileRepository: GuruProfileRepository by lazy {
        GuruProfileRepository(apiClient.create(GuruProfileApi::class.java), apiClient.moshi)
    }

    val moveRequestRepository: MoveRequestRepository by lazy {
        MoveRequestRepository(apiClient.create(MoveRequestApi::class.java), apiClient.moshi)
    }

    val forumRepository: ForumRepository by lazy {
        ForumRepository(apiClient.create(ForumApi::class.java), apiClient.moshi)
    }

    val videoRepository: VideoRepository by lazy {
        VideoRepository(apiClient.create(VideoApi::class.java), apiClient.moshi)
    }

    val moduleRepository: ModuleRepository by lazy {
        ModuleRepository(apiClient.create(ModuleApi::class.java), apiClient.moshi)
    }

    val notificationRepository: NotificationRepository by lazy {
        NotificationRepository(apiClient.create(NotificationApi::class.java), apiClient.moshi)
    }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(apiClient.create(HistoryApi::class.java), apiClient.moshi)
    }

    val avatarLoader: AvatarLoader by lazy {
        AvatarLoader(apiClient.okHttpClient)
    }

    val guruClassSelection: com.nthb.kibooz.ui.guru.GuruClassSelection by lazy {
        com.nthb.kibooz.ui.guru.GuruClassSelection()
    }
}
