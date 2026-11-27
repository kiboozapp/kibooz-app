# Menguji di Ponsel Asli

Halaman ini menjelaskan cara menguji perubahan layar di ponsel asli. Tes unit di CI memeriksa logika, tetapi tidak membuka layar mana pun di perangkat. Repositori ini belum punya tes instrumentasi (`androidTest`), jadi tampilan dan alur layar hanya terbukti lewat uji manual di perangkat.

## Kapan perlu

- Pull request yang mengubah tampilan atau perilaku sebuah layar diuji di ponsel asli sebelum digabung. Ini berlaku juga untuk layar yang dipindahkan ke Compose, seperti di [docs/compose-guide.md](compose-guide.md).
- Pull request yang hanya mengubah dokumen, workflow atau tes unit tidak perlu uji ponsel.
- Tulis hasil ujinya di komentar pull request, misalnya layar apa yang dibuka dan apa yang dibandingkan. Jangan melampirkan tangkapan layar yang memperlihatkan data pribadi.

## Menyiapkan ponsel

1. Pakai ponsel dengan Android 7.0 (API 24) atau lebih baru. Aplikasi ini menargetkan API 35, jadi satu ponsel dengan Android 14 atau lebih baru juga berguna untuk memeriksa tepi layar.
2. Aktifkan opsi pengembang dan *USB debugging*. Sebagian ponsel, seperti Xiaomi, punya sakelar tambahan untuk memasang aplikasi lewat USB. Jika `adb install` ditolak dengan `INSTALL_FAILED_USER_RESTRICTED`, nyalakan sakelar itu lalu coba lagi.
3. Hubungkan ponsel dan periksa dengan `adb devices`. Perangkat harus tampil sebagai `device`, bukan `unauthorized`.

## Mendapatkan APK debug

Setiap pull request menghasilkan APK debug dari job Debug build di workflow Android CI. Berkasnya tersimpan sebagai artifact `kibooz-debug-apk` selama 7 hari. Pasang hanya APK dari run di `main` atau dari pull request anggota tim, seperti dijelaskan di [docs/ci-cd.md](ci-cd.md).

```bash
gh run download <id run> --repo kiboozapp/kibooz-app -n kibooz-debug-apk -D apk
adb install apk/app-debug.apk
```

Setiap build CI memakai kunci penandatanganan baru. Karena itu versi yang sudah terpasang harus dihapus lebih dulu, dan `adb install` menolak pemasangan di atasnya. Menghapus aplikasi juga menghapus data lokalnya, termasuk sesi masuk, jadi masuk lagi setelah memasang.

```bash
adb uninstall com.nthb.kibooz
```

## Server yang dipakai

Build debug memakai server produksi secara bawaan. Alamatnya bisa diganti saat build dengan `-Pkibooz.apiBaseUrl=...`. Build debug hanya mengizinkan alamat `http://` ke `10.0.2.2`, yaitu alamat komputer pengembang dari dalam emulator. Ponsel asli tidak bisa memakai alamat itu.

Karena yang diuji adalah server sungguhan, perhatikan hal berikut.

- Masuk dengan akun uji yang diberikan maintainer. Jangan mendaftarkan akun baru di server produksi hanya untuk menguji.
- Jangan mengirim kode kelas, pesan atau formulir yang bisa mengubah data pengguna lain.
- Kata sandi dan token tidak ditulis di komentar pull request, tangkapan layar atau pesan commit.
- Setelah selesai, kembalikan data akun uji ke keadaan semula, misalnya nama profil yang tadi diubah.

## Yang diperiksa

Untuk layar yang dipindahkan ke Compose, ambil tangkapan layar versi XML lebih dulu dari ponsel yang sama, lalu bandingkan dengan versi baru. Periksa hal berikut.

1. Tata letak, jarak, warna dan teks sama dengan versi lama.
2. Semua keadaan layar tampil benar, yaitu memuat, galat, kosong dan berhasil. Putuskan jaringan sebentar untuk melihat keadaan galat.
3. Isian yang salah menampilkan pesan galat, dan pesan itu hilang setelah isian diperbaiki.
4. Putar layar pada layar yang boleh berputar. Isian yang sudah diketik tidak boleh hilang. Beberapa layar memang dikunci ke potret di `AndroidManifest.xml`.
5. Keyboard tidak menutupi kolom isian atau tombol, dan bilah gestur tidak menutupi tombol bawah.
6. Tombol kembali dan tujuan navigasi sama dengan versi lama.
7. Ukuran huruf besar dari pengaturan sistem tidak merusak tata letak.
8. Pembaca layar membaca tombol dan foto yang bisa diketuk dengan deskripsi yang benar.

Layar yang hanya bisa dibuka oleh peran tertentu diuji dengan akun peran itu. Akun Guru dan akun Wali melihat portal yang berbeda.

## Membaca log

Jika aplikasi berhenti sendiri atau layar tidak tampil, baca *logcat* untuk aplikasi ini.

```bash
adb logcat --pid=$(adb shell pidof -s com.nthb.kibooz)
```

Sertakan potongan log yang relevan di pull request. Hapus token, alamat surel dan data pribadi dari potongan itu sebelum menempelkannya.

## Mengembalikan ponsel

Jika pengaturan ponsel diubah untuk pengujian, seperti kunci rotasi atau kecerahan, kembalikan ke nilai semula setelah selesai.
