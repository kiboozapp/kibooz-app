# CI dan Aturan Repositori

Halaman ini mendaftar semua workflow di `.github/workflows` dan menjelaskan apa yang dicek oleh masing-masing. Daftar ini sebaiknya diperbarui di pull request yang sama dengan perubahan workflow.

## Aturan branch main

Branch `main` dilindungi ruleset bernama `main` tanpa pengecualian (bypass).

- Perubahan hanya masuk lewat pull request, dan hanya dengan merge commit.
- Semua thread review harus diselesaikan sebelum merge. Persetujuan review tidak wajib.
- `main` tidak bisa dihapus dan tidak bisa di-force push.
- Sepuluh pengecekan harus berhasil, yaitu Unit tests, Debug build, Android lint, Conventional commits, PR format, File names, Secret scan (gitleaks), Trivy filesystem scan, Actionlint dan Zizmor.
- Branch pull request harus mutakhir terhadap `main` sebelum digabung.
- Persetujuan review yang sudah ada dibatalkan oleh push baru.
- Commit tidak wajib ditandatangani.

Mengubah branch tujuan sebuah pull request menjadi `main` hanya menjalankan Conventional commits dan PR format. Pengecekan lain berjalan setelah ada push baru, misalnya saat branch dimutakhirkan.

Branch buatan tombol Revert di GitHub tidak lolos PR format. Untuk membatalkan perubahan, buat branch `revert/deskripsi-singkat` sendiri dengan judul pull request `revert: deskripsi singkat`.

Pengecekan lain hanya melaporkan hasil. Repositori juga mengaktifkan secret scanning dengan push protection, Dependabot alerts tanpa pull request otomatis, pelaporan kerentanan secara pribadi, dan kewajiban mem-pin action ke SHA commit lengkap. Branch pull request dihapus otomatis setelah digabung. Mengubah aturan ini adalah pengaturan repositori yang hanya bisa dilakukan oleh pengguna dengan akses admin.

## Daftar workflow

| Workflow | Job | Dijalankan pada | Yang dicek |
| :--- | :--- | :--- | :--- |
| `android-ci.yaml` | Unit tests | pull request ke `main`, push ke `main`, manual | `./gradlew testDebugUnitTest` |
| `android-ci.yaml` | Debug build | sama | `./gradlew assembleDebug` |
| `android-ci.yaml` | Android lint | sama | `./gradlew lintDebug` |
| `commits.yaml` | Conventional commits | pull request ke `main` (dibuka, push baru, dibuka ulang, judul, isi atau branch tujuan diubah) | Pesan setiap commit dan judul pull request mengikuti Conventional Commits |
| `prformat.yaml` | PR format | pull request ke `main` (termasuk saat isi diubah) | Nama branch, label, assignee, dan isi pull request berupa daftar bullet |
| `file-names.yaml` | File names | pull request ke `main`, push ke `main`, manual | Penamaan berkas menurut `.ls-lint.yml` |
| `gitleaks.yaml` | Secret scan (gitleaks) | pull request ke `main`, push ke `main`, manual | Seluruh riwayat git dicek untuk rahasia yang tidak sengaja ikut |
| `trivy.yaml` | Trivy filesystem scan | pull request ke `main`, push ke `main`, setiap Senin, manual | Kerentanan, rahasia, dan salah konfigurasi di berkas repositori. Hanya temuan CRITICAL dan HIGH yang sudah punya perbaikan yang dilaporkan |
| `workflow-lint.yaml` | Actionlint | pull request ke `main`, push ke `main`, manual | Sintaks berkas workflow |
| `workflow-lint.yaml` | Zizmor | sama | Keamanan berkas workflow |

Beberapa catatan tentang isi pengecekan.

- Job Android memakai JDK 17 dan Gradle wrapper repositori. Laporan pengujian dan lint diunggah sebagai artifact hanya saat job gagal.
- Job Trivy tidak menemukan apa pun di dependensi Gradle, karena repositori tidak memiliki lock file. Hasilnya ditulis ke log dan ke ringkasan job, tidak ke code scanning GitHub.
- Gitleaks memakai `.gitleaksignore` untuk empat contoh token atau UUID yang terpotong di riwayat awal PRD. Isinya bukan rahasia sungguhan.
- PR format melewati pull request milik bot di cabang `renovate/*` atau `dependabot/*`. Conventional commits tetap memeriksa semua pull request.

## Aturan penulisan pull request

Aturan lengkapnya ada di [CONTRIBUTING.md](../CONTRIBUTING.md). Ringkasnya.

- Pesan commit dan judul pull request mengikuti Conventional Commits.
- Nama branch berbentuk `type/deskripsi-singkat` dengan huruf kecil.
- Isi pull request hanya berisi bullet, satu untuk setiap commit.

## Berkas pendukung

| Berkas | Fungsi |
| :--- | :--- |
| `commitlint.config.mjs` | Aturan pesan commit untuk job Conventional commits |
| `renovate.json` | Pengaturan Renovate. Pembaruan rutin dibuat pada Senin pagi setelah rilisnya berumur 7 hari. Pembaruan keamanan bisa dibuat kapan saja. Pembaruan besar alat build dan versi minor Kotlin menunggu centang di dependency dashboard |
| `.ls-lint.yml` | Aturan penamaan berkas untuk job File names |
| `.github/zizmor.yml` | Pengaturan audit Zizmor, semua action wajib dipin ke SHA commit |
| `.gitleaksignore` | Sidik jari temuan gitleaks yang sudah diperiksa dan bukan rahasia |
| `.github/CODEOWNERS` | Siapa yang diminta mereview berkas, tidak memblokir merge |
| `.github/PULL_REQUEST_TEMPLATE.md` dan `.github/ISSUE_TEMPLATE` | Templat pull request dan issue |

## Aturan semua workflow

- Setiap action dipin ke SHA commit lengkap dengan versi di komentar.
- Setiap workflow memakai `permissions: {}` di atas dan izin minimum untuk setiap job.
- Checkout memakai `persist-credentials: false`, dan setiap job memiliki `timeout-minutes`.
- Tidak ada workflow yang memerlukan secret, jadi semuanya juga berjalan di pull request dari fork.
