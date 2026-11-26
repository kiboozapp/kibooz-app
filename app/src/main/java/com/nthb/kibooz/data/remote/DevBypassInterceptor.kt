package com.nthb.kibooz.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/**
 * Interseptor khusus mode pengembang/bypass (token berawalan "mock-").
 * Mencegah pengiriman token mock ke server live produksi yang pasti ditolak 401 (AUTH_TOKEN_INVALID),
 * dan langsung mengembalikan payload tiruan sukses agar eksplorasi antarmuka pengujian berjalan lancar.
 */
class DevBypassInterceptor(
    private val tokenProvider: TokenProvider
) : Interceptor {

    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    private data class MockStudent(
        val id: String,
        val fullName: String,
        var nisn: String,
        var nisnVerified: Boolean,
        var nisnNeedsUpdate: Boolean,
        val currentPoints: Int,
        val classRank: Int,
        val todayMood: String
    )

    private val mockStudents = mutableListOf(
        MockStudent("std-demo-1", "Adit Pratama", "0012345671", nisnVerified = true, nisnNeedsUpdate = false, currentPoints = 120, classRank = 1, todayMood = "SENANG"),
        MockStudent("std-demo-2", "Bella Saphira", "0012345672", nisnVerified = false, nisnNeedsUpdate = false, currentPoints = 110, classRank = 2, todayMood = "SENANG"),
        MockStudent("std-demo-3", "Cahya Putra", "0012345673", nisnVerified = false, nisnNeedsUpdate = false, currentPoints = 95, classRank = 3, todayMood = "BINGUNG"),
        MockStudent("std-demo-4", "Dimas Anggara", "0012345672", nisnVerified = false, nisnNeedsUpdate = false, currentPoints = 80, classRank = 4, todayMood = "SEDIH")
    )

    private var mockGuruName = "Tika Sartika, S.Pd"
    private var mockGuruPhone = "081234567890"
    private var mockGuruAddress = "Jl. Melati No. 14, Lowokwaru, Kota Malang"

    private var mockWaliName = "Budi Santoso"
    private var mockWaliPhone = "081234567890"
    private var mockWaliWhatsapp = "081234567890"
    private var mockWaliAddress = "Jl. Pendidikan No. 12, Malang"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider.accessToken()

        // Hanya aktif jika sesi menggunakan token mock/bypass pengembang
        if (token?.startsWith("mock-") != true) {
            return chain.proceed(request)
        }

        val path = request.url.encodedPath
        val bodyMap = readBodyMap(request)
        val json = mockResponseFor(path, request.method, bodyMap) ?: return chain.proceed(request)

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun readBodyMap(request: Request): Map<String, Any?> {
        val body = request.body ?: return emptyMap()
        return runCatching {
            val buffer = Buffer()
            body.writeTo(buffer)
            mapAdapter.fromJson(buffer.readUtf8()).orEmpty()
        }.getOrDefault(emptyMap())
    }

    private fun mockResponseFor(path: String, method: String, body: Map<String, Any?>): String? {
        return when {
            path.endsWith("/guru/profile") && method == "GET" -> """
                {
                    "success": true,
                    "data": {
                        "id": "guru-bypass-1",
                        "fullName": ${jsonString(mockGuruName)},
                        "nip": "198705122010012003",
                        "schoolName": "SD Negeri Percobaan",
                        "avatarUrl": null
                    }
                }
            """.trimIndent()

            path.endsWith("/guru/profile/detail") || (path.endsWith("/guru/profile") && method == "PUT") -> {
                if (method == "PUT") {
                    (body["fullName"] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { mockGuruName = it }
                    (body["phoneNumber"] as? String)?.let { mockGuruPhone = it.trim() }
                    (body["address"] as? String)?.let { mockGuruAddress = it.trim() }
                }
                """
                {
                    "success": true,
                    "data": {
                        "id": "guru-bypass-1",
                        "fullName": ${jsonString(mockGuruName)},
                        "nip": "198705122010012003",
                        "schoolName": "SD Negeri Percobaan",
                        "avatarUrl": null,
                        "email": "tika.sartika@kibooz.com",
                        "phoneNumber": ${jsonString(mockGuruPhone)},
                        "address": ${jsonString(mockGuruAddress)},
                        "joinedAt": "2024-07-15T00:00:00Z"
                    }
                }
                """.trimIndent()
            }

            path.endsWith("/wali/profile") && method == "PATCH" -> {
                (body["fullName"] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { mockWaliName = it }
                (body["phoneNumber"] as? String)?.let { mockWaliPhone = it.trim() }
                (body["whatsappNumber"] as? String)?.let { mockWaliWhatsapp = it.trim() }
                (body["address"] as? String)?.let { mockWaliAddress = it.trim() }
                """
                {
                    "success": true,
                    "data": {
                        "fullName": ${jsonString(mockWaliName)},
                        "email": "budi.santoso@kibooz.com",
                        "phoneNumber": ${jsonString(mockWaliPhone)},
                        "whatsappNumber": ${jsonString(mockWaliWhatsapp)},
                        "address": ${jsonString(mockWaliAddress)}
                    }
                }
                """.trimIndent()
            }

            path.endsWith("/guru/dashboard") -> """
                {
                    "success": true,
                    "data": {
                        "teacher": {
                            "fullName": "Tika Sartika, S.Pd",
                            "nip": "198705122010012003",
                            "avatarUrl": null
                        },
                        "classOverview": {
                            "classId": "class-demo-1",
                            "className": "Kelas 4A (Seni & Kreativitas)",
                            "joinCode": "SENI-A1",
                            "joinOpen": true,
                            "totalStudents": 24,
                            "presentStudents": 24,
                            "dominantMood": "SENANG"
                        },
                        "dailyMoodDistribution": {
                            "senang": 18,
                            "sedih": 2,
                            "marah": 0,
                            "bingung": 4
                        }
                    }
                }
            """.trimIndent()

            path.endsWith("/guru/mood/analytics") -> """
                {
                    "success": true,
                    "data": {
                        "donutSummary": [
                            {"mood": "SENANG", "percentage": 58, "colorHex": "#F59E0B"},
                            {"mood": "BINGUNG", "percentage": 20, "colorHex": "#F97316"},
                            {"mood": "SEDIH", "percentage": 15, "colorHex": "#3B82F6"},
                            {"mood": "MARAH", "percentage": 7, "colorHex": "#EF4444"}
                        ],
                        "weeklyTrend": [
                            {"day": "Monday", "averageHappyScore": 75},
                            {"day": "Tuesday", "averageHappyScore": 82},
                            {"day": "Wednesday", "averageHappyScore": 88},
                            {"day": "Thursday", "averageHappyScore": 80},
                            {"day": "Friday", "averageHappyScore": 92}
                        ],
                        "monthlyDistribution": [
                            {"mood": "SENANG", "count": 68},
                            {"mood": "BINGUNG", "count": 18},
                            {"mood": "SEDIH", "count": 10},
                            {"mood": "MARAH", "count": 4}
                        ]
                    }
                }
            """.trimIndent()

            path.endsWith("/guru/classes") || (path.endsWith("/classes") && !path.contains("students")) -> """
                {
                    "success": true,
                    "data": {
                        "page": 1,
                        "limit": 50,
                        "total": 1,
                        "classes": [
                            {
                                "id": "class-demo-1",
                                "name": "Kelas 4A (Seni & Kreativitas)",
                                "gradeLevel": "4",
                                "schoolName": "SD Negeri Percobaan",
                                "academicYear": "2026/2027",
                                "joinCode": "SENI-A1",
                                "joinOpen": true,
                                "totalStudents": 24,
                                "createdAt": "2026-08-01T07:00:00Z",
                                "archivedAt": null
                            }
                        ]
                    }
                }
            """.trimIndent()

            path.endsWith("/nisn-verification") -> handleMockNisnVerification(path, method)

            path.contains("/guru/classes/") && path.contains("/students/") && method == "DELETE" ->
                handleMockRemoveStudent(path)

            path.contains("/students") -> {
                val studentsJson = mockStudents.joinToString(",") { s ->
                    """
                    {
                        "id": ${jsonString(s.id)},
                        "fullName": ${jsonString(s.fullName)},
                        "nisn": ${jsonString(s.nisn)},
                        "nisnVerified": ${s.nisnVerified},
                        "nisnNeedsUpdate": ${s.nisnNeedsUpdate},
                        "avatarUrl": null,
                        "currentPoints": ${s.currentPoints},
                        "seasonPoints": ${s.currentPoints},
                        "classRank": ${s.classRank},
                        "todayMood": ${jsonString(s.todayMood)}
                    }
                    """.trimIndent()
                }
                """
                {
                    "success": true,
                    "data": {
                        "page": 1,
                        "limit": 50,
                        "total": ${mockStudents.size},
                        "students": [$studentsJson]
                    }
                }
                """.trimIndent()
            }

            path.endsWith("/wali/dashboard") -> """
                {
                    "success": true,
                    "data": {
                        "student": {
                            "id": "child-demo-1",
                            "fullName": "Adit Pratama",
                            "classId": "class-demo-1",
                            "className": "Kelas 4A • SD Negeri Percobaan",
                            "joinCode": "SENI-A1",
                            "avatarUrl": null,
                            "nisn": "0012345671"
                        },
                        "todayMood": {
                            "moodType": "SENANG",
                            "label": "Senang",
                            "confidence": 0.94,
                            "recordedAt": "2026-11-24T07:15:00Z",
                            "teacherNotes": "Adit sangat aktif dan bersemangat dalam pembelajaran seni hari ini."
                        },
                        "pointsSummary": {
                            "totalPoints": 120,
                            "seasonPoints": 120,
                            "classRank": 1,
                            "organicCount": 8,
                            "anorganicCount": 6
                        },
                        "recommendedGuidance": {
                            "id": "guidance-demo-1",
                            "title": "Dukungan Eksplorasi Seni & Motorik Kreatif",
                            "category": "Kreativitas & Minat",
                            "bannerUrl": ""
                        }
                    }
                }
            """.trimIndent()

            path.endsWith("/wali/children") -> """
                {
                    "success": true,
                    "data": {
                        "children": [
                            {
                                "id": "child-demo-1",
                                "fullName": "Adit Pratama",
                                "nisn": "0012345671",
                                "avatarUrl": null,
                                "classId": "class-demo-1",
                                "className": "Kelas 4A (Seni & Kreativitas)",
                                "joinCode": "SENI-A1",
                                "nisnVerified": true,
                                "nisnNeedsUpdate": false,
                                "classArchivedAt": null
                            }
                        ]
                    }
                }
            """.trimIndent()

            path.contains("/move-requests") -> """
                {
                    "success": true,
                    "message": "Move requested",
                    "data": {
                        "id": "move-req-demo-1",
                        "studentId": "child-demo-1",
                        "classId": "class-demo-2",
                        "className": "Kelas 4B (Matematika & Logika)",
                        "status": "pending",
                        "requestedAt": "2026-11-24T15:41:17.304Z"
                    }
                }
            """.trimIndent()

            path.endsWith("/guru/history") -> """
                {
                    "success": true,
                    "data": {
                        "page": 1,
                        "limit": 30,
                        "total": 2,
                        "events": [
                            {
                                "id": "hist-1",
                                "type": "mood",
                                "classId": "class-demo-1",
                                "className": "Kelas Bintang",
                                "studentId": "child-demo-1",
                                "studentName": "Adit Pratama",
                                "actorName": "Tika Sartika, S.Pd",
                                "moodType": "SENANG",
                                "confidenceScore": 0.94,
                                "title": "Adit Pratama: Happy / Cheerful",
                                "description": "Antusias mengikuti pelajaran",
                                "occurredAt": "2026-11-25T08:15:00Z"
                            },
                            {
                                "id": "hist-2",
                                "type": "video",
                                "classId": "class-demo-1",
                                "className": "Kelas Bintang",
                                "actorName": "Tika Sartika, S.Pd",
                                "title": "Mengenal Emosi Dasar Manusia",
                                "description": "Video materi kelas",
                                "occurredAt": "2026-11-24T08:00:00Z"
                            }
                        ]
                    }
                }
            """.trimIndent()

            path.contains("/history") -> """
                {
                    "success": true,
                    "data": {
                        "page": 1,
                        "limit": 30,
                        "total": 2,
                        "student": {
                            "id": "child-demo-1",
                            "fullName": "Adit Pratama",
                            "className": "Kelas 4A • SD Negeri Percobaan"
                        },
                        "events": [
                            {
                                "id": "w-hist-1",
                                "type": "mood",
                                "moodType": "SENANG",
                                "title": "Mood: Happy / Cheerful",
                                "description": "Ceria di kelas pagi ini",
                                "occurredAt": "2026-11-25T08:15:00Z"
                            },
                            {
                                "id": "w-hist-2",
                                "type": "trash",
                                "trashType": "ORGANIK",
                                "pointsAwarded": 10,
                                "title": "ORGANIK (+10 points)",
                                "occurredAt": "2026-11-24T10:00:00Z"
                            }
                        ]
                    }
                }
            """.trimIndent()

            path.contains("/wali/child") -> """
                {
                    "success": true,
                    "data": {
                        "id": "child-demo-1",
                        "fullName": "Adit Pratama",
                        "nisn": "0012345671",
                        "nisnVerified": true,
                        "nisnNeedsUpdate": false,
                        "avatarUrl": null,
                        "classId": "class-demo-1",
                        "className": "Kelas 4A (Seni & Kreativitas)",
                        "joinCode": "SENI-A1",
                        "gradeLevel": "4",
                        "schoolName": "SD Negeri Percobaan",
                        "academicYear": "2026/2027",
                        "currentPoints": 120,
                        "seasonPoints": 120,
                        "classRank": 1,
                        "guardian": {
                            "fullName": ${jsonString(mockWaliName)},
                            "email": "budi.santoso@kibooz.com",
                            "phoneNumber": ${jsonString(mockWaliPhone)},
                            "whatsappNumber": ${jsonString(mockWaliWhatsapp)},
                            "address": ${jsonString(mockWaliAddress)}
                        }
                    }
                }
            """.trimIndent()

            path.contains("/leaderboard") -> """
                {
                    "success": true,
                    "data": {
                        "podium": [
                            {"rank": 1, "studentName": "Adit Pratama", "points": 120, "avatarUrl": null},
                            {"rank": 2, "studentName": "Bella Saphira", "points": 110, "avatarUrl": null},
                            {"rank": 3, "studentName": "Cahya Putra", "points": 95, "avatarUrl": null}
                        ],
                        "rankings": [
                            {"rank": 4, "studentName": "Dimas Anggara", "points": 80, "avatarUrl": null},
                            {"rank": 5, "studentName": "Eka Rahmawati", "points": 75, "avatarUrl": null}
                        ]
                    }
                }
            """.trimIndent()

            path.endsWith("/trash/stats") -> """
                {
                    "success": true,
                    "data": {
                        "student": {
                            "id": "child-demo-1",
                            "fullName": "Adit Pratama"
                        },
                        "totalPoints": 120,
                        "seasonPoints": 120,
                        "classRank": 1,
                        "totalScans": 14,
                        "breakdown": [
                            {"trashType": "ORGANIK", "scans": 8, "points": 80},
                            {"trashType": "ANORGANIK", "scans": 6, "points": 40},
                            {"trashType": "B3", "scans": 0, "points": 0}
                        ],
                        "todayScans": 3,
                        "dailyLimit": 5,
                        "remainingDailyScans": 2
                    }
                }
            """.trimIndent()

            path.contains("/trash/scan-claim") -> """
                {
                    "success": true,
                    "data": {
                        "pointsAdded": 10,
                        "totalPoints": 130,
                        "seasonPoints": 130,
                        "newRank": 1,
                        "remainingDailyScans": 1
                    }
                }
            """.trimIndent()

            path.contains("/wali/guidance/apply") -> """
                {
                    "success": true,
                    "message": "Panduan berhasil diterapkan"
                }
            """.trimIndent()

            path.contains("/threads") -> """
                {
                    "success": true,
                    "data": {
                        "page": 1,
                        "limit": 20,
                        "total": 2,
                        "threads": [
                            {
                                "id": "th-1",
                                "title": "Diskusi Tugas Seni Budaya Pekan Ini",
                                "body": "Selamat pagi bapak ibu, mohon bimbingan untuk proyek kolase anorganik di rumah ya.",
                                "author": {
                                    "id": "guru-bypass-1",
                                    "fullName": "Tika Sartika, S.Pd",
                                    "role": "GURU",
                                    "avatarUrl": null
                                },
                                "replyCount": 3,
                                "createdAt": "2026-11-24T08:30:00Z",
                                "editedAt": null
                            },
                            {
                                "id": "th-2",
                                "title": "Konfirmasi Jadwal Kelas",
                                "body": "Apakah jadwal pertemuan besok tetap pukul 08.00 WIB?",
                                "author": {
                                    "id": "wali-bypass-1",
                                    "fullName": "Budi Santoso",
                                    "role": "WALI",
                                    "avatarUrl": null
                                },
                                "replyCount": 1,
                                "createdAt": "2026-11-24T09:15:00Z",
                                "editedAt": null
                            }
                        ]
                    }
                }
            """.trimIndent()

            path.contains("/videos") -> when (method) {
                "DELETE" -> """{"success": true, "message": "Learning video deleted", "data": null}"""
                "PATCH" -> """
                    {
                        "success": true,
                        "message": "Learning video updated",
                        "data": {
                            "id": "vid-1",
                            "title": "Video Pembelajaran Diperbarui",
                            "description": "Deskripsi diperbarui",
                            "videoUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                            "thumbnailUrl": null,
                            "durationSeconds": 300,
                            "createdAt": "2026-11-24T07:00:00Z"
                        }
                    }
                """.trimIndent()
                "POST" -> """
                    {
                        "success": true,
                        "message": "Learning video added",
                        "data": {
                            "id": "vid-new",
                            "title": "Video Pembelajaran Baru",
                            "description": "Materi pembelajaran kelas",
                            "videoUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                            "thumbnailUrl": null,
                            "durationSeconds": null,
                            "createdAt": "2026-11-25T12:00:00Z"
                        }
                    }
                """.trimIndent()
                else -> """
                    {
                        "success": true,
                        "data": {
                            "page": 1,
                            "limit": 20,
                            "total": 2,
                            "videos": [
                                {
                                    "id": "vid-1",
                                    "title": "Mengenal Emosi Dasar Manusia",
                                    "description": "Video animasi edukasi mengenal emosi senang, sedih, dan marah untuk anak.",
                                    "videoUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                                    "thumbnailUrl": null,
                                    "durationSeconds": 300,
                                    "createdAt": "2026-11-24T07:00:00Z"
                                },
                                {
                                    "id": "vid-2",
                                    "title": "Panduan Memilah Sampah Sekolah",
                                    "description": "Cara mudah membedakan sampah organik, anorganik, dan B3.",
                                    "videoUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                                    "thumbnailUrl": null,
                                    "durationSeconds": 240,
                                    "createdAt": "2026-11-24T08:00:00Z"
                                }
                            ]
                        }
                    }
                """.trimIndent()
            }

            path.contains("/mood/scan") -> """
                {
                    "success": true,
                    "message": "Hasil scan berhasil disimpan"
                }
            """.trimIndent()

            path.contains("/modules") -> when (method) {
                "DELETE" -> """{"success": true, "message": "Learning module deleted", "data": null}"""
                "PATCH" -> """
                    {
                        "success": true,
                        "message": "Learning module updated",
                        "data": {
                            "id": "mod-1",
                            "meetingNumber": 1,
                            "title": "Pertemuan Diperbarui",
                            "description": "Materi kelas diperbarui",
                            "status": "completed",
                            "scheduledAt": "2026-11-25T08:00:00Z",
                            "createdAt": "2026-11-24T07:00:00Z",
                            "updatedAt": "2026-11-25T12:00:00Z"
                        }
                    }
                """.trimIndent()
                "POST" -> """
                    {
                        "success": true,
                        "message": "Learning module created",
                        "data": {
                            "id": "mod-new",
                            "meetingNumber": 5,
                            "title": "Pertemuan Tambahan",
                            "description": "Materi pembelajaran baru",
                            "status": "upcoming",
                            "scheduledAt": "2026-11-30T08:00:00Z",
                            "createdAt": "2026-11-25T12:00:00Z",
                            "updatedAt": "2026-11-25T12:00:00Z"
                        }
                    }
                """.trimIndent()
                else -> """
                    {
                        "success": true,
                        "data": {
                            "page": 1,
                            "limit": 50,
                            "total": 2,
                            "modules": [
                                {
                                    "id": "mod-1",
                                    "meetingNumber": 1,
                                    "title": "Mengenal Warna Emosiku",
                                    "description": "Pengenalan warna dasar dan ekspresi perasaan.",
                                    "status": "completed",
                                    "scheduledAt": "2026-11-20T08:00:00Z",
                                    "createdAt": "2026-11-20T07:00:00Z",
                                    "updatedAt": "2026-11-20T07:00:00Z"
                                },
                                {
                                    "id": "mod-2",
                                    "meetingNumber": 2,
                                    "title": "Kolase Kertas Daur Ulang",
                                    "description": "Membuat karya kolase bersama teman kelas.",
                                    "status": "ongoing",
                                    "scheduledAt": "2026-11-25T08:00:00Z",
                                    "createdAt": "2026-11-24T07:00:00Z",
                                    "updatedAt": "2026-11-24T07:00:00Z"
                                }
                            ]
                        }
                    }
                """.trimIndent()
            }

            path.contains("/notifications") -> when {
                method == "POST" && path.endsWith("/read-all") ->
                    """{"success": true, "message": "All notifications marked as read", "data": null}"""
                method == "POST" && path.endsWith("/read") ->
                    """{"success": true, "message": "Notification marked as read", "data": null}"""
                else -> """
                    {
                        "success": true,
                        "data": {
                            "page": 1,
                            "limit": 30,
                            "total": 2,
                            "unreadCount": 1,
                            "notifications": [
                                {
                                    "id": "notif-1",
                                    "kind": "mood_logged",
                                    "title": "Catatan Mood Baru",
                                    "body": "Mood Adit Pratama hari ini tercatat Senang.",
                                    "referenceType": "mood_log",
                                    "referenceId": "child-demo-1",
                                    "readAt": null,
                                    "createdAt": "2026-11-25T08:15:00Z"
                                },
                                {
                                    "id": "notif-2",
                                    "kind": "video_added",
                                    "title": "Video Pembelajaran Baru",
                                    "body": "Guru menambahkan video Mengenal Emosi Dasar Manusia.",
                                    "referenceType": "learning_video",
                                    "referenceId": "vid-1",
                                    "readAt": "2026-11-25T07:00:00Z",
                                    "createdAt": "2026-11-24T07:00:00Z"
                                }
                            ]
                        }
                    }
                """.trimIndent()
            }

            else -> null
        }
    }

    private fun handleMockNisnVerification(path: String, method: String): String {
        val segments = path.split('/').filter { it.isNotEmpty() }
        val studentsIdx = segments.indexOf("students")
        val studentId = if (studentsIdx >= 0 && studentsIdx + 1 < segments.size) segments[studentsIdx + 1] else ""
        val target = mockStudents.find { it.id == studentId } ?: mockStudents.first()

        return if (method == "POST") {
            var displaced = 0
            if (target.nisn.isNotEmpty()) {
                mockStudents.forEach { other ->
                    if (other.id != target.id && !other.nisnVerified && other.nisn == target.nisn) {
                        other.nisn = ""
                        other.nisnNeedsUpdate = true
                        displaced++
                    }
                }
                target.nisnVerified = true
                target.nisnNeedsUpdate = false
            }
            """{"success":true,"message":"NISN verified","data":{"nisnVerified":true,"displaced":$displaced}}"""
        } else {
            target.nisnVerified = false
            """{"success":true,"message":"NISN verification removed","data":{"nisnVerified":false,"displaced":0}}"""
        }
    }

    private fun handleMockRemoveStudent(path: String): String {
        val segments = path.split('/').filter { it.isNotEmpty() }
        val studentsIdx = segments.indexOf("students")
        val studentId = if (studentsIdx >= 0 && studentsIdx + 1 < segments.size) segments[studentsIdx + 1] else ""
        mockStudents.removeAll { it.id == studentId }
        return """{"success":true,"message":"Student removed","data":null}"""
    }

    private fun jsonString(value: String): String =
        moshi.adapter(String::class.java).toJson(value)
}
