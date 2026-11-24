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
        val json = mockResponseFor(path) ?: return chain.proceed(request)

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun mockResponseFor(path: String): String? {
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
                        "parent": {
                            "fullName": "Budi Santoso",
                            "email": "budi.santoso@kibooz.com",
                            "avatarUrl": null
                        },
                        "children": [
                            {
                                "id": "child-demo-1",
                                "fullName": "Adit Pratama",
                                "className": "Kelas 4A (Seni & Kreativitas)",
                                "schoolName": "SD Negeri Percobaan",
                                "todayMood": "SENANG"
                            }
                        ]
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
                                "className": "Kelas 4A (Seni & Kreativitas)",
                                "schoolName": "SD Negeri Percobaan",
                                "todayMood": "SENANG"
                            }
                        ]
                    }
                }
            """.trimIndent()

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
