# 🐻 Kibooz App — Smart Early Childhood Education & Eco-Gamification Platform

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24%20(Nougat)-orange?style=for-the-badge)](https://developer.android.com/about/dashboards)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-35%20(VanillaIceCream)-blue?style=for-the-badge)](https://developer.android.com/about/versions/15)
[![Build Tool](https://img.shields.io/badge/Gradle-8.14-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org)
[![JDK](https://img.shields.io/badge/Java-JDK%2017-ED8B00?style=for-the-badge&logo=java&logoColor=white)](https://www.oracle.com/java/)

**Kibooz** adalah aplikasi mobile Android inovatif untuk ekosistem pendidikan anak usia dini (PAUD/TK) yang menghubungkan **Guru** dan **Wali Murid**. Aplikasi ini mengintegrasikan pemantauan kesejahteraan emosional (*mood tracker*) berbasis ekspresi wajah anak dengan pembiasaan peduli lingkungan (*eco-gamification*) melalui fitur pilah sampah berhadiah poin reward.

---

## 📑 Daftar Isi

- [Fitur Utama](#-fitur-utama)
  - [1. Autentikasi & Onboarding](#1-autentikasi--onboarding)
  - [2. Portal Guru (Teacher Space)](#2-portal-guru-teacher-space)
  - [3. Portal Wali Murid (Parent Space)](#3-portal-wali-murid-parent-space)
  - [4. Smart AI Scanner (Kamera Pintar)](#4-smart-ai-scanner-kamera-pintar)
- [Arsitektur & Teknologi](#-arsitektur--teknologi)
- [Desain Sistem & Visual](#-desain-sistem--visual)
- [Struktur Direktori Proyek](#-struktur-direktori-proyek)
- [Panduan Instalasi & Menjalankan](#-panduan-instalasi--menjalankan)
- [Panduan Kolaborasi Git & Tim](#-panduan-kolaborasi-git--tim)
  - [Pengaturan .gitignore & .git/info/exclude](#pengaturan-gitignore--gitinfoexclude)
  - [Alur Kerja Branching & Commit](#alur-kerja-branching--commit)
- [Lisensi & Hak Cipta](#-lisensi--hak-cipta)

---

## 🌟 Fitur Utama

### 1. Autentikasi & Onboarding
- **Animated Splash Screen:** Transisi pembuka dengan branding maskot Kibooz dan tipografi modern.
- **Carousel Onboarding (3-Langkah):** Edukasi pengenalan fitur utama dengan ilustrasi beresolusi tinggi, *dot indicator* dinamis, serta tombol aksi cepat *Lewati* dan *Lanjut*.
- **Role Selection:** Pemilihan peran instan antara **Guru** dan **Wali Murid** dengan kartu interaktif berbasis *gradient-card* dan efek sentuh responsif.
- **Role-Based Authentication:** Halaman masuk terspesialisasi yang mengarahkan pengguna secara otomatis ke portal kerja masing-masing.

---

### 2. Portal Guru (Teacher Space)
- **Dashboard Guru:**
  - Header profil interaktif dengan drawer menu navigasi samping.
  - Kartu ringkasan mood kelas harian (*Senang, Sedih, Marah, Takut*).
  - Akses cepat daftar kelas, mata pelajaran, dan jadwal kegiatan.
  - Navigasi bawah (*Bottom Navigation Bar*) terintegrasi.
- **Ruang Kelas Guru:**
  - Katalog kelas yang diajar lengkap dengan jumlah peserta didik.
  - Dialog pop-up tambah kelas baru.
  - Pencarian dan filter kategori kelas.
- **Detail & Peserta Kelas:**
  - Informasi rincian mata pelajaran (Seni & Kreativitas, Bahasa & Literasi, Sosial & Emosional, Sains & Eksplorasi).
  - Peringkat peserta kelas dengan poin akumulasi dan status mood terkini.
- **Statistik & Analisis Mood Siswa:**
  - Grafik kustom interaktif yang dibangun menggunakan Android Canvas murni:
    - **Donut Chart Mood:** Proporsi emosi siswa secara keseluruhan.
    - **Weekly Line Chart:** Tren fluktuasi kebahagiaan siswa dalam sepekan.
    - **Monthly Bar Chart:** Distribusi frekuensi emosi per bulan.
- **Fitur Pendukung:**
  - **Video Pembelajaran:** Pustaka konten edukatif interaktif untuk anak.
  - **Forum Diskusi:** Wadah komunikasi dan tanya jawab antara pendidik.
  - **Manajemen Profil Guru:** Pengaturan biodata, instansi mengajar, dan preferensi akun.

---

### 3. Portal Wali Murid (Parent Space)
- **Dashboard Wali Murid:**
  - Widget status emosional anak hari ini disertai tips penanganan cepat.
  - Banner gamifikasi pemilahan sampah (*Koin/Poin Eco-Reward*).
  - Shortcut langsung ke pemindai sampah dan pemantau mood.
- **Papan Peringkat (Leaderboard):**
  - Podium bertingkat 3D (Juara 1, 2, 3) dengan avatar profil lingkaran sempurna.
  - Badge poin *opaque flame* berkontras tinggi untuk keterbacaan optimal.
  - Kartu urutan peringkat siswa berikutnya dengan visualisasi *bottom sheet* melengkung.
- **Statistik Pilah Sampah:**
  - Rekapitulasi pemilahan sampah anak (Organik, Anorganik, B3, Daur Ulang).
  - Riwayat perolehan poin ramah lingkungan (*Eco-Points*).
- **Detail Penanganan Mood:**
  - Rekomendasi psikologis praktis bagi orang tua dalam merespons kondisi emosional anak.
- **Manajemen Profil:**
  - Profil Anak & Edit Data Siswa.
  - Profil Wali Murid.

---

### 4. Smart AI Scanner (Kamera Pintar)
- **Camera Scan Mood:** Antarmuka pemindai wajah anak dengan overlay pemandu deteksi ekspresi wajah secara *real-time*.
- **Camera Scan Sampah:** Antarmuka pemindai objek sampah dengan kotak penanda (*bounding box*) otomatis dan panel identifikasi jenis sampah serta reward poin.

---

## 🛠 Arsitektur & Teknologi

| Komponen | Spesifikasi / Pustaka | Keterangan |
| :--- | :--- | :--- |
| **Bahasa Pemrograman** | Kotlin 1.9+ | Modern, concise, dan null-safe |
| **Arsitektur UI** | MVC / ViewBinding | Bersih, cepat, tanpa overhead `findViewById` |
| **Komponen UI** | Material Design 3 (M3) | MaterialCardView, ShapeableImageView, FloatingAction |
| **Custom Canvas** | Custom View 2D Canvas | DonutMoodChart, WeeklyLineChart, MonthlyBarChart |
| **Sistem Navigasi** | Activity Stack + Drawer | Smooth transition dan deep-linking intent |
| **Edge-to-Edge** | WindowInsetsCompat (`InsetsUtil`) | Tampilan penuh tanpa terpotong notch / system bar |
| **Build System** | Gradle Kotlin DSL (`build.gradle.kts`) | Konfigurasi deklaratif dan modular |

---

## 🎨 Desain Sistem & Visual

Aplikasi mengadopsi palet warna ceria, ramah anak, dan elegan:

| Token Warna | Nilai HEX | Penggunaan Utama |
| :--- | :---: | :--- |
| **Primary Purple** | `#5D40B3` | Latar belakang hero, header, tombol utama |
| **Accent Orange** | `#FD9C27` | Aksen reward, badge api poin, highlight penting |
| **Secondary Violet**| `#724AE2` | Gradasi kartu, balok podium juara 1 |
| **Soft Lilac** | `#EFEBFF` | Latar belakang modul, surface card lembut |
| **Neutral White** | `#FFFFFF` | Bottom sheet, tipografi utama pada hero section |
| **Text Dark** | `#201A3F` | Judul teks pada latar putih |
| **Text Subtitle** | `#756E8A` | Keterangan sekunder dan placeholder |

---

## 📂 Struktur Direktori Proyek

```text
KiboozApp/
├── .git/                      # Repositori Git lokal
├── .github/                   # Konfigurasi GitHub (opsional)
├── gradle/                    # Gradle wrapper binaries
├── app/
│   ├── build.gradle.kts       # Konfigurasi modul aplikasi
│   ├── proguard-rules.pro     # Aturan obfuscation ProGuard/R8
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/valentinusmarvel/kibooz/
│       │   │   ├── ui/
│       │   │   │   ├── auth/          # Login & RoleSelection
│       │   │   │   ├── camera/        # CameraScanMood & CameraScanSampah
│       │   │   │   ├── common/        # Custom Charts (Donut, Bar, Line)
│       │   │   │   ├── guru/          # Ruang Kelas, Detail, Mood, Profil Guru
│       │   │   │   ├── onboarding/    # Carousel Onboarding & Adapter
│       │   │   │   ├── splash/        # Splash Screen
│       │   │   │   └── wali/          # Dashboard Wali, Leaderboard, Statistik
│       │   │   └── util/
│       │   │       └── InsetsUtil.kt  # Helper Insets & Edge-to-Edge System Bar
│       │   └── res/
│       │       ├── drawable/          # Vektor ikon, background gradient, pill
│       │       ├── layout/            # Berkas XML tampilan seluruh Activity
│       │       ├── values/            # colors.xml, strings.xml, themes.xml
│       │       └── mipmap/            # Ikon launcher aplikasi Kibooz
├── .gitignore                 # Konfigurasi pengabaian publik
├── build.gradle.kts           # Root project Gradle configuration
├── gradle.properties          # JVM & Gradle flags
├── gradlew / gradlew.bat      # Gradle wrapper executable
└── settings.gradle.kts        # Repository & plugin settings
```

---

## 🚀 Panduan Instalasi & Menjalankan

### Prasyarat:
1. **Android Studio:** Versi *Hedgehog (2023.1.1)* ke atas (direkomendasikan *Ladybug / Koala*).
2. **JDK:** Versi 17 (disediakan bawaan oleh Android Studio).
3. **Android Device / Emulator:** Menggunakan Android 7.0 (API 24) ke atas.

### Langkah-Langkah:
1. **Clone Repositori:**
   ```bash
   git clone https://github.com/USERNAME/kibooz-app.git
   cd kibooz-app
   ```
2. **Buka Proyek:**
   - Jalankan Android Studio.
   - Pilih menu **Open**, lalu arahkan ke folder proyek `KiboozApp`.
3. **Sinkronisasi Gradle:**
   - Tunggu hingga Android Studio menyelesaikan proses *Gradle Sync*.
   - File `local.properties` akan otomatis dibuat oleh Android Studio sesuai letak Android SDK komputer Anda.
4. **Jalankan Aplikasi:**
   - Hubungkan HP Android dengan USB Debugging aktif atau nyalakan Emulator.
   - Klik tombol **Run** (`Shift + F10`) di Android Studio.

---

## 🤝 Panduan Kolaborasi Git & Tim

### Pengaturan `.gitignore` & `.git/info/exclude`

Untuk menjaga kelancaran kolaborasi tanpa merusak konfigurasi lokal masing-masing anggota tim:

1. **File [`.gitignore`](.gitignore) (Publik):**
   - Mengabaikan folder `.gradle/`, `build/`, `.idea/`, dan `local.properties`.
   - **Penting:** Setiap anggota tim memiliki letak SDK yang berbeda di komputernya masing-masing, sehingga `local.properties` **tidak boleh** di-commit ke Git.

2. **File [`.git/info/exclude`](.git/info/exclude) (Pengecualian Lokal Rahasia):**
   - Jika Anda memiliki catatan pribadi (`notes.txt`, `catatan_pribadi.txt`), screenshot uji coba ADB, atau file rahasia lokal:
   - Tambahkan nama file/ekstensi tersebut ke `.git/info/exclude`.
   - **Kelebihan:** File ini **100% lokal** di dalam folder `.git/` Anda, **tidak akan pernah muncul di `.gitignore`**, dan **tidak akan terlihat oleh rekan tim** di GitHub.

### Alur Kerja Branching & Commit

Untuk menjaga branch utama `main` tetap stabil:

1. **Buat Branch Baru untuk Setiap Fitur/Perbaikan:**
   ```bash
   git checkout -b feat/nama-fitur-baru
   # atau untuk perbaikan bug
   git checkout -b fix/perbaikan-leaderboard
   ```

2. **Gunakan Format Conventional Commits:**
   - `feat: Menambahkan filter pencarian di ruang kelas guru`
   - `fix: Memperbaiki avatar melingkar dan badge poin di leaderboard`
   - `style: Menyesuaikan gradasi warna background dashboard`
   - `docs: Memperbarui panduan README`

3. **Kirim Perubahan (Push):**
   ```bash
   git push origin feat/nama-fitur-baru
   ```

4. **Buat Pull Request (PR):**
   - Buka GitHub dan ajukan PR ke branch `main`.
   - Minta rekan tim mereview sebelum digabungkan (*merge*).

---

## 📄 Lisensi & Hak Cipta

Proyek ini dikembangkan untuk keperluan akademik mata kuliah **Pengembangan Aplikasi Perangkat Bergerak (PAPB)**.  
Hak cipta maskot dan desain antarmuka dimiliki oleh tim pengembang **Kibooz**.
