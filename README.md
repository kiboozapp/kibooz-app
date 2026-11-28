# Kibooz App

[![Android CI](https://github.com/kiboozapp/kibooz-app/actions/workflows/android-ci.yaml/badge.svg?branch=main)](https://github.com/kiboozapp/kibooz-app/actions/workflows/android-ci.yaml)
[![CodeQL](https://github.com/kiboozapp/kibooz-app/actions/workflows/codeql.yaml/badge.svg?branch=main)](https://github.com/kiboozapp/kibooz-app/actions/workflows/codeql.yaml)
[![Kotlin lint](https://github.com/kiboozapp/kibooz-app/actions/workflows/kotlin-lint.yaml/badge.svg?branch=main)](https://github.com/kiboozapp/kibooz-app/actions/workflows/kotlin-lint.yaml)
[![Docs](https://github.com/kiboozapp/kibooz-app/actions/workflows/docs.yaml/badge.svg?branch=main)](https://github.com/kiboozapp/kibooz-app/actions/workflows/docs.yaml)
[![File names](https://github.com/kiboozapp/kibooz-app/actions/workflows/file-names.yaml/badge.svg?branch=main)](https://github.com/kiboozapp/kibooz-app/actions/workflows/file-names.yaml)
[![Secret scan](https://github.com/kiboozapp/kibooz-app/actions/workflows/gitleaks.yaml/badge.svg?branch=main)](https://github.com/kiboozapp/kibooz-app/actions/workflows/gitleaks.yaml)
[![Trivy](https://github.com/kiboozapp/kibooz-app/actions/workflows/trivy.yaml/badge.svg?branch=main)](https://github.com/kiboozapp/kibooz-app/actions/workflows/trivy.yaml)
[![Workflow lint](https://github.com/kiboozapp/kibooz-app/actions/workflows/workflow-lint.yaml/badge.svg?branch=main)](https://github.com/kiboozapp/kibooz-app/actions/workflows/workflow-lint.yaml)
[![Dependency graph](https://github.com/kiboozapp/kibooz-app/actions/workflows/dependency-graph.yaml/badge.svg?branch=main)](https://github.com/kiboozapp/kibooz-app/actions/workflows/dependency-graph.yaml)
[![Dependency submission](https://github.com/kiboozapp/kibooz-app/actions/workflows/dependency-submit.yaml/badge.svg?branch=main)](https://github.com/kiboozapp/kibooz-app/actions/workflows/dependency-submit.yaml)
[![Release Please](https://github.com/kiboozapp/kibooz-app/actions/workflows/release-please.yaml/badge.svg?branch=main)](https://github.com/kiboozapp/kibooz-app/actions/workflows/release-please.yaml)

[![Release](https://img.shields.io/github/v/release/kiboozapp/kibooz-app)](https://github.com/kiboozapp/kibooz-app/releases/latest)
[![License](https://img.shields.io/github/license/kiboozapp/kibooz-app)](LICENSE)
[![Last commit](https://img.shields.io/github/last-commit/kiboozapp/kibooz-app)](https://github.com/kiboozapp/kibooz-app/commits/main)
[![Conventional Commits](https://img.shields.io/badge/Conventional%20Commits-1.0.0-fe5196)](https://www.conventionalcommits.org)
[![Renovate](https://img.shields.io/badge/Renovate-enabled-1a1f6c?logo=renovatebot&logoColor=white)](https://github.com/kiboozapp/kibooz-app/issues/112)

[![Codecov](https://codecov.io/gh/kiboozapp/kibooz-app/graph/badge.svg)](https://codecov.io/gh/kiboozapp/kibooz-app)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/kiboozapp/kibooz-app/badge)](https://scorecard.dev/viewer/?uri=github.com/kiboozapp/kibooz-app)
[![DeepSource](https://app.deepsource.com/gh/kiboozapp/kibooz-app.svg/?label=active+issues&show_trend=true)](https://app.deepsource.com/gh/kiboozapp/kibooz-app/)

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-orange)](https://developer.android.com/about/dashboards)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-35-blue)](https://developer.android.com/about/versions/15)
[![Gradle](https://img.shields.io/badge/Gradle-8.14-02303A?logo=gradle&logoColor=white)](https://gradle.org)
[![JDK](https://img.shields.io/badge/JDK-17-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)

Kibooz adalah aplikasi Android untuk jenjang PAUD dan TK. Aplikasi ini menghubungkan guru dengan wali murid. Fitur intinya mencakup pemantauan kondisi emosional anak dan pembiasaan pilah sampah berhadiah poin.

APK rilis terbaru ada di halaman [Releases](https://github.com/kiboozapp/kibooz-app/releases/latest). Servernya ada di repositori [kibooz-backend](https://github.com/kiboozapp/kibooz-backend), dengan kontrak di `openapi.yaml` dan `docs/client-guide.md`.

## Daftar Isi

- [Fitur](#fitur)
- [Status](#status)
- [Stack](#stack)
- [Mulai Cepat](#mulai-cepat)
- [Konfigurasi](#konfigurasi)
- [Dokumentasi](#dokumentasi)
- [Struktur Proyek](#struktur-proyek)
- [Kontribusi dan Keamanan](#kontribusi-dan-keamanan)
- [Lisensi](#lisensi)

## Fitur

| Peran | Layar utama |
| :--- | :--- |
| Semua | *Onboarding*, pilih peran, masuk, daftar, verifikasi email, ubah kata sandi, potong foto profil, pilih lokasi lewat peta, hapus akun |
| Guru dan wali murid | Detail kelas, peserta kelas, video pembelajaran (kelola oleh guru), forum diskusi, papan peringkat, chat teks, foto dan video |
| Guru | Dasbor, ruang kelas, permintaan pindah kelas, statistik mood, pemindai mood dengan layar tinjau, riwayat, profil |
| Wali murid | Dasbor, tambah anak, ruang kelas, pemindai sampah dengan klaim poin, statistik pilah sampah, riwayat aktivitas, panduan penanganan mood, profil wali dan siswa |

## Status

- Aplikasi memakai server `https://kibooz-api.syafahadyan.com/api/v1/`. Beberapa bagian tampilan masih memakai isi contoh.
- Model pemindai mood dan sampah masih model pengganti, sehingga hasilnya bisa salah. Lihat [docs/MODEL_PLACEHOLDERS.md](docs/MODEL_PLACEHOLDERS.md). Pemindai terbuka pada build debug dan pada APK rilis di GitHub. Build rilis tanpa properti `kibooz.allowPlaceholderModels=true` menutupnya.
- AI pemindai berjalan di perangkat, bukan di server. Hasil pemindaian dikirim ke server tanpa foto.
- APK rilis belum mendukung Google Sign-In. Gunakan masuk dengan email.

## Stack

| Bagian | Pustaka atau alat |
| :--- | :--- |
| Bahasa dan build | Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Gradle 8.14 (Kotlin DSL), JDK 17 |
| Android | `minSdk` 24, `compileSdk` dan `targetSdk` 35 |
| Tampilan | XML dengan ViewBinding, Material Components, ViewModel, *custom view* untuk grafik. Layar dipindahkan satu per satu ke Jetpack Compose |
| Jaringan | Retrofit, OkHttp, Moshi, WebSocket untuk chat |
| Sesi | DataStore, token dienkripsi dengan kunci Android Keystore |
| Kamera dan AI | CameraX, TensorFlow Lite, ML Kit Face Detection |
| Lainnya | osmdroid (peta), WorkManager (pengingat), Credential Manager (Google Sign-In) |
| Pengujian | JUnit 4, MockWebServer, Kover |

Versi pustaka ada di [app/build.gradle.kts](app/build.gradle.kts), versi plugin di [build.gradle.kts](build.gradle.kts), dan versi Gradle di [gradle-wrapper.properties](gradle/wrapper/gradle-wrapper.properties). Token warna ada di [colors.xml](app/src/main/res/values/colors.xml).

## Mulai Cepat

### Memasang APK

1. Unduh `kibooz-app-<tag>.apk` dan berkas `.sha256` dari [Releases](https://github.com/kiboozapp/kibooz-app/releases/latest).
2. Cocokkan berkasnya dengan `sha256sum -c kibooz-app-<tag>.apk.sha256`.
3. Pasang di perangkat dengan Android 7.0 atau lebih baru. Hapus dulu build debug yang pernah dipasang, karena kunci tanda tangannya berbeda.

APK rilis ditandatangani dengan sertifikat yang sidik jari SHA-256-nya `9ad8adec25830fb86cbbd692b12472c819822a09f5cc24edcd35646137c613eb`. Sidik jari itu bisa diperiksa dengan `apksigner` dari Android SDK, yang ada di folder `build-tools/<versi>`.

```bash
"$ANDROID_HOME/build-tools/<versi>/apksigner" verify --print-certs kibooz-app-<tag>.apk
```

### Menjalankan dari kode sumber

Prasyaratnya Android Studio Ladybug (2024.2.1) atau lebih baru, JDK 17, dan perangkat atau emulator dengan API 24 ke atas.

```bash
git clone https://github.com/kiboozapp/kibooz-app.git
cd kibooz-app
```

Di Android Studio, pilih **Open**, tentukan folder hasil clone, tunggu sinkronisasi Gradle, lalu tekan **Run**. Android Studio membuat `local.properties` yang berisi lokasi Android SDK.

Untuk memakai terminal, lokasi Android SDK harus diketahui lebih dulu, yaitu lewat variabel `ANDROID_HOME`, baris `sdk.dir` di `local.properties`, atau berkas buatan Android Studio tadi.

```bash
sh ./gradlew assembleDebug
sh ./gradlew testDebugUnitTest
```

Di Windows, pakai `gradlew.bat` sebagai ganti `sh ./gradlew`.

## Konfigurasi

Pengaturan diberikan sebagai properti Gradle, misalnya `-Pkibooz.apiBaseUrl=http://10.0.2.2:8080/api/v1/`.

| Properti | Bawaan | Fungsi |
| :--- | :--- | :--- |
| `kibooz.apiBaseUrl` | `https://kibooz-api.syafahadyan.com/api/v1/` | Alamat API, harus diakhiri `/`. Build selain debug wajib memakai `https://`. Build debug hanya boleh memakai `http://` ke `10.0.2.2`, yaitu mesin pengembang dilihat dari emulator |
| `kibooz.allowPlaceholderModels` | `false` | Membuka pemindai pada build rilis walaupun modelnya masih pengganti |
| `kibooz.googleWebClientId` | kosong | Web Client ID OAuth untuk Google Sign-In. Tanpa nilai ini Google Sign-In tidak tersedia |

## Dokumentasi

| Dokumen | Isi |
| :--- | :--- |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Cara mengirim perubahan, aturan branch, commit dan pull request |
| [SECURITY.md](SECURITY.md) | Cara melaporkan kerentanan |
| [SUPPORT.md](SUPPORT.md) | Tempat bertanya |
| [GOVERNANCE.md](GOVERNANCE.md) | Cara keputusan diambil |
| [MAINTAINERS.md](MAINTAINERS.md) | Pengelola repositori |
| [ROADMAP.md](ROADMAP.md) | Rencana pengembangan |
| [ACCESSIBILITY.md](ACCESSIBILITY.md) | Keadaan aksesibilitas aplikasi |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | Aturan perilaku |
| [CHANGELOG.md](CHANGELOG.md) | Perubahan tiap rilis |
| [docs/ci-cd.md](docs/ci-cd.md) | Workflow, pengecekan wajib dan alur rilis |
| [docs/compose-guide.md](docs/compose-guide.md) | Langkah memindahkan layar XML ke Compose dan komponen bersamanya |
| [docs/review.md](docs/review.md) | Siapa yang mereview, komentar bot dan cara menangani thread review |
| [docs/phone-testing.md](docs/phone-testing.md) | Cara menguji perubahan layar di ponsel asli |
| [docs/product-rules.md](docs/product-rules.md) | Aturan produk, termasuk AI yang hanya berjalan di perangkat |
| [docs/MODEL_PLACEHOLDERS.md](docs/MODEL_PLACEHOLDERS.md) | Model pengganti, keterbatasannya dan cara menggantinya |
| [docs/prd_and_roadmap](docs/prd_and_roadmap/00_INDEX.md) | Kebutuhan produk dan rencana kerja |

## Struktur Proyek

```text
app/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── assets/                 # model TFLite, label dan lisensinya
    │   ├── java/com/nthb/kibooz/
    │   │   ├── data/               # remote, repository, session
    │   │   ├── ml/                 # pengklasifikasi mood dan sampah
    │   │   ├── model/
    │   │   ├── notification/
    │   │   ├── ui/                 # account, auth, camera, chat, common, components,
    │   │   │                       # guru, location, onboarding, splash, theme, wali
    │   │   └── util/
    │   └── res/
    └── test/                       # unit test
docs/                               # dokumentasi
.github/workflows/                  # CI dan rilis
```

## Kontribusi dan Keamanan

Baca [CONTRIBUTING.md](CONTRIBUTING.md) sebelum membuka pull request. Perubahan perilaku sebaiknya disertai test. Laporkan kerentanan lewat [pelaporan privat](https://github.com/kiboozapp/kibooz-app/security/advisories/new), bukan lewat issue publik.

## Lisensi

Proyek ini dikembangkan untuk keperluan akademik mata kuliah Pengembangan Aplikasi Perangkat Bergerak (PAPB). Hak cipta desain antarmuka, aset visual, dan maskot sepenuhnya dimiliki oleh Tim Nexus (Nexus Project).

Seluruh isi repositori ini, termasuk kode, dokumentasi, desain antarmuka, aset visual, dan maskot, dilisensikan di bawah [GNU General Public License versi 3](LICENSE). Berkas pihak ketiga tetap mengikuti lisensi pemiliknya, yaitu

- model yang lisensinya ada di `app/src/main/assets/licenses`
- font Baloo 2 di `app/src/main/res/font`, berlisensi [SIL Open Font License 1.1](app/src/main/assets/licenses/baloo2-OFL-1.1.txt)
- sebagian ikon di `app/src/main/res/drawable` yang berasal dari Material Icons milik Google, berlisensi [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0)
- logo Google di `app/src/main/res/drawable/ic_google.xml`, yang merupakan merek dagang Google
- teks Code of Conduct yang diadaptasi dari Contributor Covenant, berlisensi [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)
