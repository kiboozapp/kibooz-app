package com.nthb.kibooz.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Interseptor khusus mode pengembang/bypass (token berawalan "mock-").
 * Mencegah pengiriman token mock ke server live produksi yang pasti ditolak 401 (AUTH_TOKEN_INVALID),
 * dan langsung mengembalikan payload tiruan sukses agar eksplorasi antarmuka pengujian berjalan lancar.
 */
class DevBypassInterceptor(
    private val tokenProvider: TokenProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider.accessToken()

        // Hanya aktif jika sesi menggunakan token mock/bypass pengembang
        if (token?.startsWith("mock-") != true) {
            return chain.proceed(request)
        }

        val path = request.url.encodedPath
        val json = mockResponseFor(path, request.method) ?: return chain.proceed(request)

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun mockResponseFor(path: String, method: String): String? {
        return when {
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

            path.contains("/students") -> """
                {
                    "success": true,
                    "data": {
                        "page": 1,
                        "limit": 50,
                        "total": 3,
                        "students": [
                            {
                                "id": "std-demo-1",
                                "fullName": "Adit Pratama",
                                "nisn": "0012345671",
                                "avatarUrl": null,
                                "currentPoints": 120,
                                "seasonPoints": 120,
                                "classRank": 1,
                                "todayMood": "SENANG"
                            },
                            {
                                "id": "std-demo-2",
                                "fullName": "Bella Saphira",
                                "nisn": "0012345672",
                                "avatarUrl": null,
                                "currentPoints": 110,
                                "seasonPoints": 110,
                                "classRank": 2,
                                "todayMood": "SENANG"
                            },
                            {
                                "id": "std-demo-3",
                                "fullName": "Cahya Putra",
                                "nisn": "0012345673",
                                "avatarUrl": null,
                                "currentPoints": 95,
                                "seasonPoints": 95,
                                "classRank": 3,
                                "todayMood": "BINGUNG"
                            }
                        ]
                    }
                }
            """.trimIndent()

            path.endsWith("/wali/dashboard") -> """
                {
                    "success": true,
                    "data": {
                        "student": {
                            "id": "child-demo-1",
                            "fullName": "Adit Pratama",
                            "classId": "class-demo-1",
                            "className": "Kelas 4A • SD Negeri Percobaan",
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
                        "gradeLevel": "4",
                        "schoolName": "SD Negeri Percobaan",
                        "academicYear": "2026/2027",
                        "currentPoints": 120,
                        "seasonPoints": 120,
                        "classRank": 1,
                        "guardian": {
                            "fullName": "Budi Santoso",
                            "email": "budi.santoso@kibooz.com",
                            "phoneNumber": "081234567890",
                            "whatsappNumber": "081234567890",
                            "address": "Jl. Pendidikan No. 12, Malang"
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

            else -> null
        }
    }
}
