# Panduan Memindahkan Layar ke Jetpack Compose

Panduan ini untuk siapa pun yang memindahkan layar XML ke Compose. Aturan singkatnya ada di bagian Layar Jetpack Compose pada [CONTRIBUTING.md](../CONTRIBUTING.md). Halaman ini menjelaskan langkahnya satu per satu.

## Prinsip

- Layar hasil pindahan harus tampil dan berperilaku sama seperti layar XML lama. Perubahan tampilan dikerjakan di pull request tersendiri.
- Satu pull request memindahkan satu layar, atau satu layar bersama pasangannya yang memakai komponen yang sama.
- `ViewModel` tidak diubah. Layar membaca state dari `ViewModel` yang sama.
- `applicationId` dan nama paket aplikasi tidak berubah.
- Teks dipertahankan apa adanya. Layar yang memakai teks langsung di XML boleh memindahkannya ke `strings.xml`, tetapi isinya tidak diubah.

## Langkah memindahkan satu layar

1. Baca activity dan layout XML lama. Catat teks, warna, ukuran, state tampilan (memuat, galat, kosong), dialog, tujuan navigasi dan extra intent yang dikirim.
2. Pindahkan activity ke paket fiturnya di bawah `ui` dengan `git mv`. Perbarui nama activity di `AndroidManifest.xml` dan import di layar lain yang membukanya.
3. Buat berkas `<Nama>Screen.kt` yang berisi composable layar. Composable ini menerima state dan callback, tidak menerima `ViewModel`, dan dibuat `internal`.
4. Di activity, isi layar dengan `setContent { KiboozTheme { ... } }`. Baca state dengan `collectAsStateWithLifecycle()`.
5. Pesan sekali tampil seperti toast atau perpindahan layar tetap ditangani di activity lewat `lifecycleScope` dan `repeatOnLifecycle(STARTED)`. Contohnya ada di `UbahKataSandiActivity`.
6. Pakai komponen bersama dari tabel di bawah sebelum membuat komponen baru.
7. Perbarui `SessionAccess.kt` dan `SessionAccessTest.kt` jika letak atau nama kelasnya berubah. Paket menentukan aturan akses. Paket `splash`, `onboarding` dan `auth` terbuka tanpa login, jadi layar yang wajib login tidak diletakkan di sana. Layar di paket `profile` tidak menunjukkan perannya lewat nama paket, jadi tiap layar ditulis satu per satu di `SessionAccess.kt`.
8. Hapus layout XML lama dan resource yang hanya dipakai layout itu. Cari nama activity, kelas binding dan nama layout di seluruh repositori, termasuk folder `docs`, karena pengecekan "Markdown links" gagal pada tautan ke berkas yang sudah pindah.
9. Tambahkan fungsi `@Preview` di berkas layar dengan teks contoh.
10. Buang import yang tidak terpakai. Pengecekan "Kotlin lint" gagal karenanya. Import `getValue` dan `setValue` tetap diperlukan oleh `by mutableStateOf`.
11. Ambil tangkapan layar versi XML dari ponsel lebih dulu. Lalu uji di ponsel asli dengan APK debug dari pull request dan bandingkan hasilnya. Setiap build CI memakai kunci penandatanganan baru, jadi versi yang sudah terpasang harus dihapus dulu sebelum APK baru dipasang. Menghapus aplikasi juga menghapus data lokalnya, seperti sesi masuk. Langkah uji dan daftar yang diperiksa ada di [docs/phone-testing.md](phone-testing.md), dan detail APK debug ada di [docs/ci-cd.md](ci-cd.md).

## Komponen bersama

| Komponen | Letak | Fungsi |
| :--- | :--- | :--- |
| `KiboozTheme` | `ui/theme` | Tema layar Compose. Warnanya dibaca dari `colors.xml` dan hanya ada tema terang |
| `KiboozPrimaryButton`, `KiboozDangerButton`, `KiboozOutlinedButton` | `ui/components` | Tombol utama, tombol bahaya dan tombol garis tepi |
| `KiboozTextField`, `KiboozOutlinedField`, `KiboozOutlinedPasswordField` | `ui/components` | Kolom isian |
| `KiboozTopBar` | `ui/components` | Bilah atas dengan judul dan tombol kembali |
| `ConnectionStatusCard` | `ui/components` | Kartu gangguan server, pasangan `ConnectionStatusView` di layar XML |
| `AvatarImage` | `ui/components` | Foto bulat lewat `AvatarLoader`, jadi cache dan `evict` tetap berlaku. Naikkan parameter `version` saat berkas di URL yang sama berganti |
| `AuthSheet` | `ui/auth` | Latar ungu dengan logo dan lembar putih yang bisa digulir, dipakai layar masuk dan daftar |
| `KiboozGuruScaffold` dan `GuruTab` | `ui/components` | Kerangka portal Guru dengan bilah bawah dan tombol scan mood |
| `KiboozWaliScaffold` dan `WaliTab` | `ui/components` | Kerangka portal Wali dengan bilah bawah dan tombol scan sampah |
| `ProfileHeader`, `ProfileDetailButton`, `ProfileMenuCard` | `ui/profile` | Kepala, tombol detail dan kartu menu yang dipakai profil Guru dan Wali |

Komponen yang dipakai lebih dari satu fitur diletakkan di `ui/components`. Komponen yang hanya dipakai satu fitur tetap di paket fitur itu.

## Hal yang sering terlewat

- Bandingkan jarak dan ukuran dengan tangkapan layar XML pada ponsel yang sama. Selisih beberapa piksel, seperti padding vertikal kartu menu, mudah terlewat.
- Foto yang dibungkus `AndroidView` perlu deskripsi konten pada node yang bisa diketuk, bukan pada `ImageView` di dalamnya, karena deskripsi di dalam tidak terbaca pada node yang diketuk.
- Area sentuh minimal 48dp. Tab yang terpilih ditandai dengan menulis `this.selected = aktif` di dalam `semantics { }` (lihat `BarTab`), bukan hanya dengan warna.
- Daftar yang dibangun dari `getString` dan lambda boleh dibuat sekali dengan `remember`, selama isinya tidak bergantung pada state yang bisa berubah. Jika daftar memuat callback untuk pilihan yang bisa berganti, beri kunci pada `remember` dengan state itu, atau baca nilai terbaru di dalam callback, agar ketukan tidak bekerja pada pilihan yang lama.
- `KiboozTheme` hanya mengatur warna dan ikon gelap pada bilah navigasi sistem. Ia tidak menambah padding. Kerangka portal Guru dan Wali memberi padding bilah navigasi sendiri. Layar biasa menambahkan `windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))` sendiri, agar tombol tidak tertutup bilah gestur dan kolom isian tidak tertutup keyboard. Bagian keyboard hanya bekerja jika activity di `AndroidManifest.xml` memakai `android:windowSoftInputMode="adjustResize"`, seperti layar masuk dan ubah kata sandi. Activity dengan `adjustNothing`, seperti `PickLocationActivity`, menyerahkan penanganan keyboard kepada activity itu sendiri. Jangan mengubahnya ke `adjustResize` hanya demi resep ini.
- `KiboozTopBar` sudah memberi padding bilah status. Pakai `statusBarsPadding()` hanya di layar yang tidak memakai `KiboozTopBar`. Jangan memanggil `InsetsUtil.applySystemBarInsets` di layar Compose.
- State seperti nama atau anak yang dipilih dibaca dari state yang sedang tampil, bukan dari nilai terbaru `StateFlow`, agar tombol memakai data yang sama dengan yang terlihat di layar.
