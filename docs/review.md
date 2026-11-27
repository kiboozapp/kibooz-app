# Review dan komentar di pull request

Halaman ini menjelaskan siapa yang mereview pull request, apa yang dikerjakan bot di pull request, dan cara menangani komentar sebelum merge. Daftar pengecekan wajib dan aturan branch ada di [ci-cd.md](ci-cd.md). Cara membuat pull request ada di [CONTRIBUTING.md](../CONTRIBUTING.md).

## Siapa yang mereview

- `.github/CODEOWNERS` mendaftar lima code owner, yaitu `ArmJour`, `ValentinusMarvel`, `SyafaHadyan`, `FhmiSddq` dan `RozdanHanyv`. Berkas itu hanya menentukan siapa yang diminta mereview sebuah berkas dan tidak memblokir merge.
- Ruleset `main` tidak mewajibkan persetujuan review, dan tidak mewajibkan review dari code owner.
- Pull request yang dibuka oleh seorang code owner tidak perlu direview oleh code owner lain. Code owner yang membuka pull request boleh menggabungkannya sendiri setelah syarat di bagian "Sebelum merge" terpenuhi.
- Persetujuan review yang sudah ada dibatalkan oleh push baru, seperti dijelaskan di [ci-cd.md](ci-cd.md).

## Bot yang berkomentar

Tiga bot memberi review atau laporan di pull request. Ketiganya tidak termasuk dua belas pengecekan wajib, jadi hasilnya tidak memblokir merge sendiri. Pengaturan CodeRabbit dan DeepSource ada di layanan masing-masing. Pengaturan Codecov ada di `codecov.yml`, yang membuat status cakupannya bersifat informasi saja.

| Bot | Yang ditulis | Cara menanganinya |
| --- | --- | --- |
| CodeRabbit | Satu komentar ringkasan yang diperbarui bot di tempat, dan komentar inline pada baris tertentu | Baca ringkasannya setelah setiap push. Komentar inline menjadi thread review yang harus diselesaikan |
| DeepSource | Pengecekan "DeepSource: Kotlin" dan "DeepSource: Secrets", serta komentar inline | Temuan yang berasal dari perubahanmu diperbaiki, walaupun pengecekannya tidak memblokir merge |
| Codecov | Laporan cakupan baris yang kamu ubah | Dibaca sebagai informasi. Layar baru bisa menunjukkan cakupan rendah karena belum ada tes untuknya |

Bot menulis ulang komentarnya di tempat dan menambah thread baru setelah push. Baca ulang seluruh komentar setiap kali pengecekan selesai, tidak hanya daftar thread yang belum diselesaikan. Komentar bot juga menyebut commit yang dibahasnya. Kalau commit itu bukan commit terbaru di branch, komentarnya bisa sudah usang.

Kalau CodeRabbit menulis bahwa review dijeda, komentar `@coderabbitai resume` di pull request melanjutkannya.

## Menangani komentar

1. Baca isi komentar dan periksa dulu apakah temuannya benar pada kode yang ada di branch.
2. Perbaiki temuan yang benar di commit tersendiri, dengan pesan Conventional Commits. Perbaikan kecil di dalam tujuan pull request tidak perlu menunggu persetujuan siapa pun.
3. Perbarui isi pull request supaya tetap satu bullet untuk setiap commit. Lakukan ini sebelum push, supaya pengecekan "PR format" yang berjalan karena push itu sudah membaca isi yang benar.
4. Push, lalu tekan "Resolve conversation" pada thread yang sudah ditangani. Tombol itu hanya menutup thread dan tidak memperbaiki apa pun, jadi tekan setelah perbaikannya ada di branch.
5. Kalau temuannya keliru, tidak berlaku, atau membahas kode yang tidak kamu ubah, tulis alasannya di thread itu dan sebut maintainer sebelum menutupnya.
6. Temuan yang butuh perubahan perilaku, perubahan API atau perubahan desain tidak diperbaiki diam-diam di pull request yang sama. Bicarakan dulu dengan maintainer, dan buat pull request tersendiri bila disetujui.

## Sebelum merge

Pull request digabung dengan merge commit setelah semua hal berikut terpenuhi.

- Dua belas pengecekan wajib berhasil.
- Branch sudah mutakhir terhadap `main`.
- Semua thread review sudah diselesaikan.
- Semua komentar dan ringkasan bot sudah dibaca setelah push terakhir.

Ruleset memeriksa tiga hal pertama. Ruleset tidak menilai apakah perbaikan sebuah komentar benar, dan tidak melihat apakah komentar bot yang baru muncul sudah dibaca. Dua hal itu menjadi tanggung jawab penulis pull request.
