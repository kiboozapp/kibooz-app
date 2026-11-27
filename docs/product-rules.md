# Aturan Produk

Halaman ini mengumpulkan aturan produk yang berlaku untuk setiap perubahan di aplikasi. Kebutuhan lengkap ada di [dokumen PRD](prd_and_roadmap/00_INDEX.md). Halaman ini hanya memuat aturan yang tidak boleh dilanggar oleh kontributor.

## AI berjalan di perangkat

Semua AI di aplikasi berjalan di perangkat pengguna. Tidak ada AI yang berjalan di server.

- Model emosi wajah dan model jenis sampah berbentuk berkas TFLite yang dibawa di dalam aplikasi. Deteksi wajah memakai ML Kit di perangkat. Bingkai kamera diproses di perangkat dan tidak dikirim ke server.
- Server hanya menerima hasilnya. Catatan mood berisi id siswa, nilai emosi, asal catatan (`AI_CAMERA` atau `MANUAL_INPUT`), skor keyakinan dan catatan guru. Klaim sampah berisi id siswa, jenis sampah dan skor keyakinan. Tidak ada foto di dalamnya.
- Jangan menambah fitur yang mengirim gambar atau data lain ke server agar dianalisis oleh model, dan jangan memanggil model yang dihosting pihak lain. Kalau sebuah fitur butuh model baru, model itu dibawa di dalam aplikasi.
- Foto yang dikirim untuk disimpan sebagai data, seperti foto profil dan media di chat, bukan inferensi. Foto itu dikirim sebagai berkas lewat endpoint unggah masing-masing, dan server tidak menganalisisnya dengan AI.
- Saat mengubah model, layar pemindai atau layar kamera, sebutkan aturan ini di deskripsi pull request dan jelaskan data apa yang dikirim ke server.

## Model pengganti

Model yang terpasang sekarang masih model pengganti, sehingga hasilnya bisa salah. Rinciannya ada di [MODEL_PLACEHOLDERS.md](MODEL_PLACEHOLDERS.md).

- Layar pemindai hanya boleh dibuka dengan model sungguhan. Model pengganti hanya boleh dipakai di build debug atau di build yang sengaja dibuat dengan `-Pkibooz.allowPlaceholderModels=true`. Aturannya ada di `ml/ModelGate.kt`.

## Nilai mood

Catatan mood memakai empat nilai, yaitu `SENANG`, `SEDIH`, `MARAH` dan `BINGUNG`. Wajah netral dikirim sebagai `SENANG`, sesuai keputusan produk yang dicatat di [MODEL_PLACEHOLDERS.md](MODEL_PLACEHOLDERS.md). Pemetaan dari label model ke empat nilai itu ada di berkas konfigurasi di `assets/`, bukan di kode.

## Peran dan akses

- Layar pilih peran menawarkan dua peran, yaitu Guru dan Wali murid. Siswa bukan peran yang masuk sendiri. Wali murid menambahkan dan mengelola profil anaknya.
- Layar untuk satu peran tidak boleh bisa dibuka oleh peran lain. Aturannya ada di `ui/auth/SessionAccess.kt` dan diuji di `SessionAccessTest`. Layar baru atau layar yang dipindahkan harus ditambahkan ke sana.
- Pemindai mood adalah fitur Guru. Pemindai sampah dan klaim poin adalah fitur Wali murid.

## Poin sampah

Klaim poin sampah dibatasi per hari. Setelah sisa klaim berpoin penuh hari itu habis, klaim tetap diterima tetapi poinnya dikurangi. Aplikasi menampilkan hal itu kepada pengguna dan tidak menyembunyikannya.

## Privasi pesan

Notifikasi dorong tidak membawa teks pesan. Isinya hanya id percakapan dan id pesan. Aplikasi mengambil pesannya dari API dan menampilkannya sebagai notifikasi di perangkat. Jangan menambahkan teks pesan ke muatan notifikasi dorong.

## Tampilan

- Layar yang dipindahkan ke Compose tampil dan berperilaku sama seperti layar XML lama. Perubahan tampilan dikerjakan di pull request tersendiri, seperti di [compose-guide.md](compose-guide.md).
- Aplikasi hanya punya tema terang.
