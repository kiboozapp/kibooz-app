# CI dan Aturan Repositori

Halaman ini mendaftar semua workflow di `.github/workflows` dan menjelaskan apa yang dicek oleh masing-masing. Daftar ini sebaiknya diperbarui di pull request yang sama dengan perubahan workflow.

## Aturan branch main

Branch `main` dilindungi ruleset bernama `main` tanpa pengecualian (bypass).

- Perubahan hanya masuk lewat pull request, dan hanya dengan merge commit.
- Semua thread review harus diselesaikan sebelum merge. Persetujuan review tidak wajib.
- `main` tidak bisa dihapus dan tidak bisa di-force push.
- Dua belas pengecekan harus berhasil, yaitu Unit tests, Debug build, Android lint, Conventional commits, PR format, File names, Secret scan (gitleaks), Trivy filesystem scan, Actionlint, Zizmor, CodeQL analysis dan Dependency review.
- Branch pull request harus mutakhir terhadap `main` sebelum digabung.
- Persetujuan review yang sudah ada dibatalkan oleh push baru.
- Commit tidak wajib ditandatangani.

Mengubah branch tujuan sebuah pull request menjadi `main` hanya menjalankan Conventional commits dan PR format. Pengecekan lain berjalan setelah ada push baru, misalnya saat branch dimutakhirkan.

Branch buatan tombol Revert di GitHub tidak lolos PR format. Untuk membatalkan perubahan, buat branch `revert/deskripsi-singkat` sendiri dengan judul pull request `revert: deskripsi singkat`.

Zizmor memakai data terbaru dari GitHub, jadi temuan baru pada action yang sudah dipakai bisa membuat semua pull request gagal. Jalan keluarnya adalah pull request yang memperbarui action itu, atau yang menambahkan pengecualian beserta alasannya di `.github/zizmor.yml`.

Dependency review menunggu daftar dependensi Gradle dari commit pull request itu, paling lama sekitar dua puluh menit. Kalau Dependency graph atau pengirimannya gagal, atau daftarnya tidak datang, pengecekan ini gagal. Jalankan ulang workflow yang gagal, lalu jalankan ulang Dependency review. Pull request dari fork tidak mengirim daftar, sehingga pengecekan ini hanya memberi peringatan dan dependensi Gradle di sana tidak diperiksa. Kalau Dependency graph atau pengirimannya gagal di `main`, jalankan ulang workflow itu. Kalau pengecekan ini melaporkan dependensi lama sebagai dependensi baru tepat setelah sebuah merge, tunggu pengiriman daftar `main` selesai, lalu jalankan ulang.

CodeQL analysis juga bisa gagal bukan karena isi pull request, misalnya saat unduhan atau code scanning GitHub bermasalah, atau saat CodeQL default setup diaktifkan di pengaturan repositori. Jalankan ulang pengecekannya setelah penyebabnya hilang. Yang dianalisis hanya kode utama varian debug. Pengecekan ini gagal kalau analisisnya tidak selesai, bukan karena ada temuan.

Pengecekan lain hanya melaporkan hasil. Repositori juga mengaktifkan secret scanning dengan push protection, Dependabot alerts tanpa pull request otomatis, pelaporan kerentanan secara pribadi, dan kewajiban mem-pin action ke SHA commit lengkap. Branch pull request dihapus otomatis setelah digabung. Mengubah aturan ini adalah pengaturan repositori yang hanya bisa dilakukan oleh pengguna dengan akses admin.

## Daftar workflow

| Workflow | Job | Dijalankan pada | Yang dicek |
| :--- | :--- | :--- | :--- |
| `android-ci.yaml` | Unit tests | pull request ke `main`, push ke `main`, manual | `./gradlew testDebugUnitTest koverXmlReportDebug` |
| `android-ci.yaml` | Coverage upload | sama | Mengunggah laporan coverage Kover ke Codecov. Tidak wajib. Kegagalan unggah hanya menggagalkan push ke `main` |
| `android-ci.yaml` | Debug build | sama | `./gradlew assembleDebug` |
| `android-ci.yaml` | Android lint | sama | `./gradlew lintDebug` |
| `commits.yaml` | Conventional commits | pull request ke `main` (dibuka, push baru, dibuka ulang, judul, isi atau branch tujuan diubah) | Pesan setiap commit dan judul pull request mengikuti Conventional Commits |
| `prformat.yaml` | PR format | pull request ke `main` (termasuk saat isi diubah) | Nama branch, label, assignee, dan isi pull request berupa daftar bullet |
| `release-please.yaml` | Release PR and tag | push ke `main`, manual | Membuat pull request rilis berisi changelog dan versi baru. Setelah digabung, membuat tag `v*` dan draft release |
| `release-please.yaml` | Sign and publish the release | setelah rilis dibuat | Menandatangani APK rilis, melampirkannya ke draft release, lalu menerbitkan rilisnya |
| `release-build.yaml` | Release build | pull request ke `main`, dipanggil oleh `release-please.yaml` | Membangun APK rilis yang belum ditandatangani. Tidak wajib |
| `file-names.yaml` | File names | pull request ke `main`, push ke `main`, manual | Penamaan berkas menurut `.ls-lint.yml` |
| `gitleaks.yaml` | Secret scan (gitleaks) | pull request ke `main`, push ke `main`, manual | Seluruh riwayat git dicek untuk rahasia yang tidak sengaja ikut |
| `trivy.yaml` | Trivy filesystem scan | pull request ke `main`, push ke `main`, setiap Senin, manual | Kerentanan, rahasia, dan salah konfigurasi di berkas repositori. Hanya temuan CRITICAL dan HIGH yang sudah punya perbaikan yang dilaporkan |
| `workflow-lint.yaml` | Actionlint | pull request ke `main`, push ke `main`, manual | Sintaks berkas workflow |
| `workflow-lint.yaml` | Zizmor | sama | Keamanan berkas workflow |
| `codeql.yaml` | CodeQL analysis | pull request ke `main`, push ke `main`, setiap Selasa dini hari WIB | Analisis keamanan statis kode Kotlin dan Java. Temuannya masuk ke code scanning GitHub |
| `scorecard.yaml` | OSSF Scorecard | push ke `main`, setiap Selasa dini hari WIB | Praktik keamanan repositori menurut OpenSSF Scorecard. Tidak pernah wajib |
| `dependency-graph.yaml` | Dependency graph | pull request ke `main`, push ke `main` | Menyusun daftar dependensi Gradle dan menyimpannya sebagai artifact |
| `dependency-submit.yaml` | Submit the dependency graph | setelah Dependency graph berhasil | Mengirim daftar itu ke dependency graph GitHub dengan token tulis, tanpa menjalankan kode pull request |
| `dependency-review.yaml` | Dependency review | pull request ke `main` | Dependensi baru atau berubah tidak punya kerentanan tingkat high ke atas dan tidak berlisensi SSPL |

Beberapa catatan tentang isi pengecekan.

- Job Android memakai JDK 17 dan Gradle wrapper repositori. Laporan pengujian dan lint diunggah sebagai artifact hanya saat job gagal. Job Debug build menyimpan APK debug sebagai artifact `kibooz-debug-apk` selama 7 hari. APK ini ditandatangani dengan kunci sementara runner, sehingga versi lama harus dihapus dari ponsel sebelum memasang yang baru. Pasang hanya APK dari run di `main` atau dari pull request anggota tim.
- Job Trivy tidak menemukan apa pun di dependensi Gradle, karena repositori tidak memiliki lock file. Hasilnya ditulis ke log dan ke ringkasan job, tidak ke code scanning GitHub.
- Gitleaks memakai `.gitleaksignore` untuk empat contoh token atau UUID yang terpotong di riwayat awal PRD. Isinya bukan rahasia sungguhan.
- PR format melewati pull request milik bot di cabang `renovate/*`, `dependabot/*` atau `release-please--*`. Conventional commits tetap memeriksa semua pull request.

## Aturan penulisan pull request

Aturan lengkapnya ada di [CONTRIBUTING.md](../CONTRIBUTING.md). Ringkasnya.

- Pesan commit dan judul pull request mengikuti Conventional Commits.
- Nama branch berbentuk `type/deskripsi-singkat` dengan huruf kecil.
- Isi pull request hanya berisi bullet, satu untuk setiap commit.

## Berkas pendukung

| Berkas | Fungsi |
| :--- | :--- |
| `commitlint.config.mjs` | Aturan pesan commit untuk job Conventional commits |
| `codecov.yml` | Pengaturan status Codecov. Statusnya hanya informasi |
| `renovate.json` | Pengaturan Renovate. Pembaruan rutin dibuat pada Senin pagi setelah rilisnya berumur 7 hari. Pembaruan keamanan bisa dibuat kapan saja. Pembaruan besar alat build dan versi minor Kotlin menunggu centang di dependency dashboard |
| `.ls-lint.yml` | Aturan penamaan berkas untuk job File names |
| `.github/zizmor.yml` | Pengaturan audit Zizmor, semua action wajib dipin ke SHA commit |
| `release-please-config.json` | Pengaturan Release Please, termasuk versi awal 0.1.0 dan baris `versionName` yang diperbarui otomatis |
| `.release-please-manifest.json` | Versi rilis terakhir, ditulis oleh Release Please |
| `.gitleaksignore` | Sidik jari temuan gitleaks yang sudah diperiksa dan bukan rahasia |
| `.github/CODEOWNERS` | Siapa yang diminta mereview berkas, tidak memblokir merge |
| `.github/PULL_REQUEST_TEMPLATE.md` dan `.github/ISSUE_TEMPLATE` | Templat pull request dan issue |

## Rilis

- Release Please membuka dan memperbarui satu pull request rilis. Rilis dibuat saat pull request itu digabung.
- Tag `v*` hanya bisa dibuat oleh GitHub App rilis, dan tidak bisa diubah atau dihapus oleh siapa pun.
- Release immutability aktif, sehingga tag dan berkas rilis yang sudah terbit tidak bisa diganti.
- Rilis berisi `kibooz-app-<tag>.apk` dan berkas `.sha256`. APK ditandatangani dengan kunci rilis, dan job tersebut memeriksa sidik jari sertifikatnya. Kalau job pembangunan atau penandatanganan gagal, rilis tetap berupa draft. Jalankan ulang job yang gagal. Kalau itu tidak bisa, misalnya karena APK sementara sudah lewat 7 hari atau workflow-nya yang salah, jalankan `release-please.yaml` secara manual dari `main` dengan masukan `tag` berisi tag draft tersebut.
- APK rilis belum memiliki Google Sign-In. Layar pemindai terbuka dengan model pengganti, karena APK rilis dibangun dengan `-Pkibooz.allowPlaceholderModels=true`.
- `versionCode` di `app/build.gradle.kts` tidak diperbarui otomatis. Naikkan angkanya secara manual sebelum mengunggah build ke Play Store.

## Aturan semua workflow

- Setiap action dipin ke SHA commit lengkap dengan versi di komentar.
- Setiap workflow memakai `permissions: {}` di atas dan izin minimum untuk setiap job.
- Checkout memakai `persist-credentials: false`, dan setiap job memiliki `timeout-minutes`.
- `release-please.yaml` memakai kunci GitHub App, berkas kunci penandatanganan APK dan kata sandinya di environment `release` yang hanya bisa dipakai dari `main`. Job Coverage upload memakai secret repositori `CODECOV_TOKEN`, di job terpisah yang tidak menjalankan Gradle. Pull request dari fork tidak mendapat secret, dan semua workflow tetap berjalan di sana.
