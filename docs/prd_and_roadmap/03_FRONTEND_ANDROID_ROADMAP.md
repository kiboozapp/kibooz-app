# Roadmap Frontend Android: Arsitektur MVVM, State Management, dan Integrasi Layar

Panduan ini memetakan langkah migrasi UI Kibooz dari prototipe statis menjadi aplikasi Android reaktif. Kode memakai arsitektur Clean MVVM (Model-View-ViewModel).

---

## 1. Arsitektur Target (Clean MVVM)

```mermaid
graph TD
    subgraph UI_Layer [UI Layer: Android]
        Activity[Activity / ViewBinding]
        CustomChart[Custom View Charts]
        CameraXView[CameraX PreviewView]
    end

    subgraph Presentation_Layer [Presentation Layer]
        ViewModel[Jetpack ViewModel]
        UiState[StateFlow UiState]
    end

    subgraph Domain_Data_Layer [Data & Repository Layer]
        Repository[Kibooz Repository]
        RoomDB[(Local Cache: Room DB)]
        RetrofitClient[Remote API: Retrofit 2]
        DataStore[(Session: Jetpack DataStore)]
    end

    Activity -->|Observes| UiState
    Activity -->|User Intent| ViewModel
    ViewModel -->|Exposes| UiState
    ViewModel -->|Calls| Repository
    Repository -->|Caches to| RoomDB
    Repository -->|Fetches from| RetrofitClient
    Repository -->|Reads Token| DataStore
```

---

## 2. Kebutuhan Pustaka Tambahan (`build.gradle.kts`)

Tambahkan dependensi berikut ke dalam file [build.gradle.kts](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/build.gradle.kts):

```kotlin
dependencies {
    // 1. Networking & Serialization
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    // 2. Coroutines & Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 3. Local Database & Cache (Room)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // 4. Session & DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // 5. Image Loading
    implementation("io.coil-kt:coil:2.7.0")

    // 6. CameraX & ML Kit
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
}
```

---

## 3. Pola Pemodelan State UI (`UiState`)

Setiap ViewModel wajib mengekspos state yang konsisten untuk menghindari kondisi balapan (*race conditions*):

```kotlin
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val throwable: Throwable? = null) : UiState<Nothing>
}
```

Implementasi pada Activity menggunakan ekstensi siklus hidup `repeatOnLifecycle`:

```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.dashboardState.collect { state ->
            when (state) {
                is UiState.Loading -> showShimmerLoading()
                is UiState.Success -> bindDashboardData(state.data)
                is UiState.Error -> showErrorSnackbar(state.message) { viewModel.retry() }
                is UiState.Idle -> Unit
            }
        }
    }
}
```

---

## 4. Matriks Integrasi Layar-ke-API (20 Activity)

| No | Activity File | Sumber Data / Endpoint | Aksi Pengguna Utama |
| :---: | :--- | :--- | :--- |
| **01** | [SplashActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/splash/SplashActivity.kt) | Cek token di `SessionDataStore` | Jika ada token valid, langsung buka Dashboard sesuai peran; jika tidak ada, buka Onboarding. |
| **02** | [OnboardingActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/onboarding/OnboardingActivity.kt) | Lokal asset (slide 1-3) | Navigasi antar-slide, tombol "Mulai" membuka Role Selection. |
| **03** | [RoleSelectionActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/auth/RoleSelectionActivity.kt) | Pemilihan peran | Menyimpan `SELECTED_ROLE` ke intent dan membuka LoginActivity. |
| **04** | [LoginActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/auth/LoginActivity.kt) | `POST /api/v1/auth/login` | Validasi kredensial, simpan token JWT, arahkan ke Dashboard Guru / Dashboard Wali. |
| **05** | [DashboardGuruActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/guru/DashboardGuruActivity.kt) | `GET /api/v1/guru/dashboard` | Menampilkan metrik kelas, status kehadiran, dan sebaran mood hari ini. |
| **06** | [StatistikMoodActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/guru/StatistikMoodActivity.kt) | `GET /api/v1/guru/mood/analytics` | Mengumpan data dinamis ke `DonutMoodChart`, `WeeklyLineChart`, dan `MonthlyBarChart`. |
| **07** | [RuangKelasGuruActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/guru/RuangKelasGuruActivity.kt) | `GET /api/v1/guru/classes` | Menampilkan daftar kelas, dialog tambah kelas baru (`POST /api/v1/guru/classes`). |
| **08** | [DetailKelasActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/guru/DetailKelasActivity.kt) | `GET /api/v1/guru/classes/{id}` | Menampilkan 6 mata pelajaran 3D dan ringkasan aktivitas belajar. |
| **09** | [VideoPembelajaranActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/guru/VideoPembelajaranActivity.kt) | `GET /api/v1/classes/{id}/videos` | Memutar video edukasi streaming dengan kontrol play/pause ExoPlayer. |
| **10** | [PesertaKelasActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/guru/PesertaKelasActivity.kt) | `GET /api/v1/classes/{id}/students` | Menampilkan daftar murid, perolehan poin sampah, dan tombol input mood personal. |
| **11** | [ForumDiskusiActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/guru/ForumDiskusiActivity.kt) | `GET & POST /api/v1/classes/{id}/forum` | Menampilkan utas diskusi kelas dan mengirim komentar/catatan baru. |
| **12** | [ProfilGuruActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/guru/ProfilGuruActivity.kt) | `GET /api/v1/guru/profile` | Menampilkan identitas pendidik, NIP, menu pengaturan, dan tombol logout. |
| **13** | [DetailProfilGuruActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/guru/DetailProfilGuruActivity.kt) | `GET /api/v1/guru/profile/detail` | Menampilkan data lengkap guru, alamat dinas, dan nomor kontak aktif. |
| **14** | [EditProfilGuruActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/guru/EditProfilGuruActivity.kt) | `PUT /api/v1/guru/profile` | Mengubah nomor telepon, alamat, dan mengunggah foto profil baru. |
| **15** | [DashboardWaliActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/wali/DashboardWaliActivity.kt) | `GET /api/v1/wali/dashboard` | Menampilkan kartu mood harian anak, akumulasi poin sampah, dan pintasan scanner. |
| **16** | [DetailPenangananMoodActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/wali/DetailPenangananMoodActivity.kt) | `POST /api/v1/wali/guidance/apply` | Membaca 4 langkah penanganan emosi dan mengirim konfirmasi tindakan ke guru. |
| **17** | [StatistikSampahActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/wali/StatistikSampahActivity.kt) | `GET /api/v1/trash/stats` | Menampilkan rincian statistik pemilahan Organik vs Anorganik dan total poin. |
| **18** | [LeaderboardActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/wali/LeaderboardActivity.kt) | `GET /api/v1/leaderboard` | Mengisi data podium 3D juara 1-3 dan daftar peringkat siswa sekelas. |
| **19** | [ProfilSiswaActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/wali/ProfilSiswaActivity.kt) | `GET /api/v1/wali/child/{id}` | Menampilkan identitas resmi anak, NISN, nama wali, email, dan alamat rumah. |
| **20** | [ReviewScanMoodActivity.kt](file:///c:/Users/anang/Downloads/Semester%205/PAPB/Projek%20Kelas/Hasil/KiboozApp/app/src/main/java/com/nthb/kibooz/ui/camera/ReviewScanMoodActivity.kt) | `POST /api/v1/guru/mood/batch` | Meninjau hasil scan multi-wajah, mengoreksi emosi, menambah catatan, dan menyimpan batch. |

---

## 5. Rencana Kerja Tim Frontend (Work Breakdown)

- [ ] **FE-01:** Konfigurasi Retrofit Network Client, OkHttp logging interceptor, dan AuthTokenInterceptor.
- [ ] **FE-02:** Pembuatan Room Database (`KiboozDatabase`), Entity (`StudentEntity`, `MoodLogEntity`), dan DAO.
- [ ] **FE-03:** Pembuatan `AuthRepository` dan penyimpanan sesi dengan Jetpack Preferences DataStore.
- [ ] **FE-04:** Refaktor `LoginActivity` ke `AuthViewModel`, penanganan pesan error jaringan, dan validasi form.
- [ ] **FE-05:** Refaktor `DashboardWaliActivity` ke `WaliViewModel`, menghubungkan kartu mood dan poin nyata.
- [ ] **FE-06:** Refaktor `DashboardGuruActivity` ke `GuruViewModel`, menghubungkan metrik kelas dinamis.
- [ ] **FE-07:** Integrasi data dinamis pada grafik kustom `DonutMoodChart`, `WeeklyLineChart`, dan `MonthlyBarChart`.
- [ ] **FE-08:** Refaktor `LeaderboardActivity` ke `LeaderboardViewModel`, mengisi podium dan recycler view.
- [ ] **FE-09:** Refaktor `DetailPenangananMoodActivity` untuk mengirimkan payload `guidance/apply` ke server.
- [ ] **FE-10:** Pembuatan `Formulir Edit Profil` (Siswa & Guru) dengan validasi masukan dan callback simpan data.
- [ ] **FE-11:** Penambahan efek *Shimmer Loading Placeholder* pada dashboard saat data sedang diunduh dari jaringan.
- [ ] **FE-12:** Penanganan kondisi jaringan putus (*offline banner*) dan tombol aksi coba lagi (*retry button*).
