# Aksesibilitas

Kibooz App dipakai oleh guru dan wali murid, dan kami ingin aplikasinya bisa dipakai oleh sebanyak mungkin orang. Berkas ini menyatakan komitmen itu dan cara melaporkan hambatan.

## Keadaan saat ini

Aplikasi masih dalam pengembangan. Beberapa hal yang sudah ada di layout.

- Banyak gambar dan ikon memiliki `contentDescription`.
- Ukuran teks memakai satuan `sp`, jadi mengikuti ukuran font yang dipilih pengguna di pengaturan Android.

Hal yang belum diketahui atau belum diperiksa.

- Belum ada audit aksesibilitas menyeluruh yang tercatat di repositori.
- Tidak semua kontrol sentuh sudah dipastikan berukuran minimal 48dp.
- Aplikasi belum diuji dengan TalkBack di setiap layar.

Karena itu, kami tidak menyatakan kepatuhan terhadap standar aksesibilitas tertentu.

## Melaporkan hambatan

Jika Anda menemui layar atau kontrol yang sulit dipakai, misalnya tombol yang tidak terbaca pembaca layar, teks yang terlalu kecil atau kontras warna yang rendah, beri tahu kami.

1. Buka issue dengan template Laporan bug.
2. Tulis kata "aksesibilitas" di judul, lalu sebut layar, perangkat, versi Android dan alat bantu yang Anda pakai.
3. Reviewer akan memasang label `accessibility`.

Perbaikan aksesibilitas disambut lewat pull request. Ikuti [CONTRIBUTING.md](CONTRIBUTING.md).
