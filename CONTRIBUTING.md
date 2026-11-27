# Panduan Berkontribusi

Terima kasih sudah ikut mengerjakan Kibooz App. Panduan ini menjaga agar setiap perubahan mudah ditinjau.

## Persiapan

Proyek ini memakai Gradle 8.14 dan JDK 17. Cara membuka dan menjalankan proyek ada di bagian [Mulai Cepat pada README.md](README.md#mulai-cepat). Rencana kerja ada di folder `docs/prd_and_roadmap`.

## Membuat perubahan

1. Buat branch dari `main`. Beri nama `type/deskripsi-singkat`, misalnya `fix/scan-camera-frame`, dengan `type` yang sama seperti commit di dalamnya.
2. Satu pull request hanya berisi satu hal. Perubahan yang tidak berkaitan dibuat di branch dan pull request terpisah.
3. Tulis commit dengan gaya [Conventional Commits](https://www.conventionalcommits.org/) dalam bentuk `type: ringkasan` atau `type(scope): ringkasan`. Pilihan `type` adalah `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore` dan `revert`. Judul commit paling panjang 120 karakter. Ringkasan ditulis dengan huruf kecil di awal dan tanpa titik di akhir.
4. Commit yang tidak mengikuti format ini membuat pengecekan "Conventional commits" gagal. Perbaiki pesan commit terakhir dengan `git commit --amend`, atau dengan interactive rebase untuk commit yang lebih lama, lalu push ulang branch milikmu sendiri dengan `git push --force-with-lease`.

Merge commit dengan pesan `Merge branch 'main' into <branch>` dan merge commit buatan GitHub (`Merge pull request #N from ...`) tidak diperiksa.

## Layar Jetpack Compose

- Bungkus isi layar dengan `KiboozTheme` dari `ui/theme`.
- Pakai komponen bersama di `ui/components` sebelum membuat yang baru. Komponen yang dipakai lebih dari satu fitur diletakkan di sana.
- Layar yang dipindahkan ke Compose diletakkan di paket fiturnya di bawah `ui`, yaitu `onboarding`, `splash`, `auth`, `account`, `profile`, `dashboard`, `stats`, `classroom`, `chat`, `scan` atau `location`.
- Aturan akses layar ditentukan oleh paket dan nama kelas di `SessionAccess.kt`. Perbarui aturan dan testnya pada pull request yang memindahkan layar.
- Warna tetap berasal dari `colors.xml` dan teks dari `strings.xml`.

## Menjalankan unit test

Jalankan unit test di komputer sendiri sebelum push.

```sh
./gradlew testDebugUnitTest
```

Perubahan perilaku sebaiknya disertai test. Perbaikan bug sebaiknya disertai test yang gagal tanpa perbaikan itu.

## Pull request

- Judul pull request memakai format Conventional Commits, misalnya `fix: perbaiki bingkai pemindai sampah`.
- Isi pull request hanya daftar commit di dalamnya, satu bullet untuk setiap commit. Baris komentar HTML seperti petunjuk di template boleh tetap ada.
- Nama branch mengikuti `type/deskripsi-singkat` dengan huruf kecil dan tanda hubung, misalnya `fix/scan-camera-frame`.
- Pasang satu label dari yang sudah ada di repositori, misalnya `bug` untuk perbaikan, `enhancement` untuk fitur atau peningkatan, `documentation` untuk perubahan dokumentasi saja dan `accessibility` untuk aksesibilitas.
- Jadikan diri sendiri sebagai assignee.
- Pull request boleh meminta `ArmJour`, `ValentinusMarvel`, `SyafaHadyan`, `FhmiSddq` atau `RozdanHanyv` sebagai reviewer.

## Pengecekan otomatis

Pull request memicu dua pengecekan untuk format. Pengecekan "Conventional commits" memeriksa pesan commit dan judul pull request. Pengecekan "PR format" memeriksa nama branch, label, assignee dan isi pull request. Pull request yang dibuka bot dari branch `renovate/`, `dependabot/` atau `release-please--` tidak diperiksa oleh "PR format".

Kedua pengecekan ini termasuk pengecekan wajib di ruleset `main`, sehingga pull request tidak bisa digabung selama salah satunya gagal. Daftar lengkap pengecekan wajib dan aturan branch ada di [docs/ci-cd.md](docs/ci-cd.md).

Workflow di pull request dari fork menunggu persetujuan maintainer sebelum berjalan. Kontributor dari fork biasanya tidak bisa memasang label atau assignee, jadi maintainer yang memasangnya.

Menambahkan label atau assignee tidak memulai pengecekan lagi. Setelah menambahkannya, jalankan ulang pengecekan "PR format" dari tab Checks. Menjalankan ulang memerlukan izin menulis di repositori. Tanpa izin itu, minta maintainer menjalankannya, atau ubah isi pull request agar pengecekan berjalan lagi.

## Sebelum pull request digabung

Pull request digabung setelah dua hal terpenuhi, yaitu semua pengecekan lulus dan semua komentar review sudah ditangani.

- Semua pengecekan harus lulus. Ruleset `main` memblokir merge selama pengecekan wajib gagal atau belum selesai. Pengecekan yang tidak wajib, seperti DeepSource, tidak memblokir merge, tetapi temuan yang berasal dari perubahanmu tetap diperbaiki. Temuan di kode yang tidak kamu ubah cukup disampaikan ke maintainer.
- Pengecekan yang gagal diperbaiki dengan commit baru, bukan dilewati. Baca log pengecekan itu dulu supaya penyebabnya jelas. Jalankan ulang hanya jika kegagalannya berasal dari infrastruktur GitHub, misalnya runner yang mati, dan bukan dari kode.
- Semua komentar review harus ditangani, baik dari reviewer maupun dari bot seperti CodeRabbit dan DeepSource. Menangani berarti masalahnya diperbaiki di kode atau dokumen, lalu perbaikannya di-push. Tombol "Resolve conversation" hanya menutup thread dan tidak memperbaiki apa pun, jadi tekan setelah perbaikannya ada di branch.
- Jika sebuah komentar menurutmu keliru atau tidak berlaku, tulis alasannya di thread itu sebelum menutupnya, supaya reviewer bisa menilai ulang. Jangan menutup thread tanpa perbaikan atau alasan.
- Ruleset `main` hanya mengizinkan merge setelah semua thread review diselesaikan, tetapi tidak menilai apakah perbaikannya benar. Bagian itu menjadi tanggung jawab penulis dan reviewer.
- Setelah setiap push, cek lagi daftar pengecekan dan komentar, karena bot bisa menambahkan komentar baru pada commit terbaru.

## Melaporkan masalah

Gunakan template issue untuk bug dan permintaan fitur. Masalah keamanan jangan dilaporkan sebagai issue publik. Kirim secara pribadi lewat [Report a vulnerability](https://github.com/kiboozapp/kibooz-app/security/advisories/new), seperti dijelaskan di [SECURITY.md](SECURITY.md).
