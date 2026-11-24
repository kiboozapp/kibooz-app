# Kebijakan Keamanan

## Versi yang ditangani

Hanya kode di branch `main` yang ditangani.

## Melaporkan kerentanan

Jangan membuka issue atau pull request publik untuk masalah keamanan. Tulis laporan secara pribadi kepada pemilik repositori, `ValentinusMarvel`, di GitHub.

Fitur pelaporan kerentanan pribadi dari GitHub belum diaktifkan di repositori ini. Karena itu, mulai dengan pesan singkat yang menyebut bahwa isinya soal keamanan. Rincian lengkap dikirim setelah pemilik repositori membalas.

Laporan yang baik memuat hal berikut.

- Bagian aplikasi yang terdampak, misalnya nama layar atau berkas.
- Langkah untuk mengulang masalahnya.
- Dampak yang Anda perkirakan.
- Versi Android dan perangkat yang dipakai, jika relevan.

Jangan menyertakan data pribadi anak, wali murid atau guru yang sungguhan. Pakai data contoh.

## Cara aplikasi menangani sesi

Bagian ini menjelaskan perilaku aplikasi saat ini, supaya laporan bisa dinilai dengan benar.

- Aplikasi menyimpan access token, refresh token dan `deviceToken` dalam keadaan terenkripsi dengan AES-256-GCM. Kuncinya dibuat dan disimpan di Android Keystore.
- Berkas sesi berada di `files/datastore/` milik aplikasi. Berkas itu, beserta database dan `sharedpref`, dikeluarkan dari backup cloud dan dari transfer perangkat lewat `data_extraction_rules.xml` dan `backup_rules.xml`.
- Setiap permintaan ke API membawa header `Authorization: Bearer` jika sesi ada.
- Alamat API berawalan `https://` hanya boleh memakai TLS 1.2 dan 1.3 dengan cipher modern.

## Lalu lintas jaringan

- Konfigurasi keamanan jaringan di build utama melarang lalu lintas `http` sepenuhnya.
- Build debug hanya membolehkan `http` ke `10.0.2.2`, yaitu alamat mesin pengembang dari emulator.
- Build selain debug wajib memakai alamat API berawalan `https://`. Gradle menolak membangun varian rilis jika `kibooz.apiBaseUrl` tidak berawalan `https://`.

## Kerahasiaan

Jangan menaruh token, kata sandi, keystore, `google-services.json` atau berkas `.env` di repositori. Berkas semacam itu sudah masuk `.gitignore`. Jika Anda menemukan rahasia di riwayat repositori, laporkan lewat cara di atas dan bukan lewat issue publik.
