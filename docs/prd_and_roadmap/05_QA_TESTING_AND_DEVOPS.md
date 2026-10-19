# Strategi Pengujian (QA), Otomasi CI/CD, dan Panduan DevOps Kibooz

Panduan ini memuat piramida pengujian, otomasi UI Espresso, konfigurasi GitHub Actions CI/CD, alur rilis APK, dan aturan git tim.

---

## 1. Piramida Pengujian (Testing Pyramid)

```mermaid
graph TD
    classDef top fill:#FD9C27,stroke:#B45309,stroke-width:2px,color:#FFFFFF;
    classDef mid fill:#724AE2,stroke:#4C1D95,stroke-width:2px,color:#FFFFFF;
    classDef base fill:#5D40B3,stroke:#2E1065,stroke-width:2px,color:#FFFFFF;

    L3["<b>Level 3: UI & End-to-End Tests (10%)</b><br/>Otomasi alur navigasi dan interaksi pengguna via Espresso"]:::top
    L2["<b>Level 2: Integration Tests (30%)</b><br/>Pengujian relasi database Room dan MockWebServer API"]:::mid
    L1["<b>Level 1: Unit Tests (60%)</b><br/>Pengujian fungsi logika ViewModel, Repository, dan kalkulasi chart"]:::base

    L3 -->|Skala kecil, uji alur nyata| L2
    L2 -->|Skala menengah, uji antarkomponen| L1
```

| Tingkatan | Porsi | Sasaran Pengujian | Alat dan Framework | Karakteristik |
| :--- | :---: | :--- | :--- | :--- |
| **UI & E2E** | 10% | Otomasi alur login, presensi mood, dan klaim sampah | Espresso, UI Automator | Menguji alur pengguna nyata, eksekusi lambat |
| **Integration** | 30% | Konsistensi query Room DAO dan serialisasi JSON API | MockWebServer, Room In-Memory DB | Menguji interaksi antarkomponen |
| **Unit** | 60% | Logika bisnis ViewModel, transformasi data Repository, formula chart | JUnit 5, MockK, Turbine | Cepat, terisolasi, berjalan di JVM lokal |

### 1.1 Spesifikasi Pengujian Unit (Unit Testing)
- **Framework:** JUnit 5 / JUnit 4 + MockK + Turbine (untuk pengujian `StateFlow` dan Coroutines).
- **Target Cakupan Kode (Code Coverage):** Minimal 80% pada package `data/` dan `viewmodel/`.
- **Contoh Kasus Uji Kunci:**
  - `AuthViewModelTest`: Memverifikasi transisi state dari `Loading` menuju `Success` saat kredensial valid, dan emisi `Error` saat password salah.
  - `LeaderboardViewModelTest`: Memverifikasi sorting dan pemisahan data 3 besar podium versus daftar peringkat urutan 4 ke bawah.
  - `TrashPointsCalculatorTest`: Memverifikasi kalkulasi poin harian tidak melebihi kuota 5 kali pemindaian per hari.

### 1.2 Spesifikasi Pengujian Integrasi (Integration Testing)
- **Room DAO In-Memory Test:** Memastikan operasi CRUD pada tabel `students` dan `mood_logs` berjalan konsisten tanpa *data leak*.
- **MockWebServer Test:** Memverifikasi serialisasi JSON Retrofit untuk penanganan respons error HTTP 401, 404, dan 500.

### 1.3 Pengujian Antarmuka Pengguna (UI & Espresso Automation)
- **Target Alur Kritis (Critical User Journeys):**
  1. **Journey 1 (Otentikasi & Navigasi):** Onboarding $\rightarrow$ Role Selection $\rightarrow$ Login Guru $\rightarrow$ Dashboard Guru tampil utuh.
  2. **Journey 2 (Pencatatan Mood):** Dashboard Guru $\rightarrow$ Ruang Kelas $\rightarrow$ Peserta $\rightarrow$ Input Mood Siswa $\rightarrow$ Dialog Sukses.
  3. **Journey 3 (Gamifikasi & Poin):** Dashboard Wali $\rightarrow$ Buka Scanner Sampah $\rightarrow$ Konfirmasi Scan $\rightarrow$ Poin bertambah $\rightarrow$ Leaderboard diperbarui.

---

## 2. Matriks Pengujian Manual (Manual QA Test Cases)

| ID Uji | Modul | Skenario Pengujian | Hasil yang Diharapkan |
| :--- | :--- | :--- | :--- |
| **TC-01** | **Jaringan** | Pengguna membuka aplikasi saat perangkat dalam *Mode Pesawat* (*Airplane Mode*). | Aplikasi tidak *crash*; menampilkan banner offline dan memuat data cache terakhir dari Room DB. |
| **TC-02** | **Izin Akses** | Pengguna menolak izin kamera (*Camera Permission Denied*) saat membuka scanner. | Viewfinder kamera menampilkan pesan edukatif dan tombol langsung menuju "Pengaturan Izin Aplikasi". |
| **TC-03** | **Sesi Kedaluwarsa** | Token JWT kedaluwarsa saat pengguna sedang berada di halaman profil. | Interceptor menangkap kode HTTP 401, menghapus token di DataStore, dan menampilkan dialog "Sesi telah berakhir, silakan masuk kembali". |
| **TC-04** | **Rotasi Layar** | Layar dirotasi atau ukuran jendela diubah pada tablet / mode split-screen. | Data form tidak hilang (*retained via ViewModel*), status bar dan bilah navigasi tetap proporsional berkat `InsetsUtil`. |
| **TC-05** | **Input Ekstrem** | Pengguna memasukkan nama anak sepanjang 100 karakter atau simbol khusus. | Tampilan UI memotong teks (*ellipsize end*) tanpa merusak tata letak kartu antarmuka. |

---

## 3. Konfigurasi Otomasi CI/CD (GitHub Actions)

Berkas konfigurasi pipeline otomatis ditempatkan pada [.github/workflows/android-ci.yml](../../.github/workflows/android-ci.yml):

```yaml
name: Kibooz Android CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  lint-and-unit-tests:
    name: Lint & Unit Testing
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Source Code
        uses: actions/checkout@v4

      - name: Setup Java JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Setup Gradle Cache
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: |
            ${{ runner.os }}-gradle-

      - name: Run Kotlin Lint Verification
        run: ./gradlew lintDebug

      - name: Execute Unit Test Suite
        run: ./gradlew testDebugUnitTest

      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unit-test-results
          path: app/build/reports/tests/testDebugUnitTest/

  build-debug-apk:
    name: Assemble Debug APK
    needs: lint-and-unit-tests
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Source Code
        uses: actions/checkout@v4

      - name: Setup Java JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Grant Execute Permission to Gradlew
        run: chmod +x gradlew

      - name: Build Debug APK
        run: ./gradlew assembleDebug --stacktrace

      - name: Upload Debug APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: kibooz-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. Standar Alur Kerja Git dan Tinjauan Kode (Code Review Rules)

### 4.1 Format Penamaan Branch
- `feat/<nama-fitur>`: Penambahan fungsionalitas baru (contoh: `feat/wali-dashboard-api`).
- `fix/<nama-bug>`: Perbaikan bug atau kesalahan layout (contoh: `fix/chart-overlap-padding`).
- `chore/<nama-tugas>`: Pembaruan dependensi, konfigurasi Gradle, atau dokumentasi.

### 4.2 Standar Pesan Komit (Conventional Commits)
Gunakan format terstruktur: `<tipe>(<lingkup>): <deskripsi singkat>`
- Contoh: `feat(api): integrasi login endpoint dengan datastore session`
- Contoh: `fix(layout): seimbangkan margin judul profil wali di atas avatar`

### 4.3 Kriteria Wajib Merge (Definition of Done / DoD)
Sebuah pull request baru dapat digabungkan (*merged*) ke branch `main` jika memenuhi:
1. Telah disetujui (*Approved*) minimal oleh satu rekan tim pemrogram.
2. Seluruh tes otomatis pada pipeline GitHub Actions berstatus hijau (*Pass*).
3. Tidak ada penambahan nilai string atau warna *hardcoded* (wajib menggunakan `strings.xml` dan `colors.xml`).
4. Menggunakan `InsetsUtil` untuk penanganan bilah sistem *edge-to-edge*.

---

## 5. Rencana Kerja Tim QA & DevOps (Work Breakdown)

- [ ] **QA-01:** Pembuatan skenario pengujian unit untuk validasi form login dan register.
- [ ] **QA-02:** Pembuatan unit test untuk kalkulasi poin pilah sampah dan penanganan batas harian 5x.
- [ ] **QA-03:** Penulisan script otomasi UI Espresso untuk alur Login $\rightarrow$ Dashboard $\rightarrow$ Logout.
- [ ] **QA-04:** Penulisan script otomasi UI Espresso untuk alur Scanner Sampah $\rightarrow$ Klaim Poin.
- [ ] **QA-05:** Pengujian kompatibilitas pada berbagai resolusi layar (FHD+, HD+, rasio 18:9 s.d. 20:9).
- [ ] **DO-01:** Pembuatan direktori `.github/workflows/` dan implementasi workflow `android-ci.yml`.
- [ ] **DO-02:** Konfigurasi pengelolaan kredensial rahasia (*Repository Secrets*) untuk Signing Keystore.
- [ ] **DO-03:** Konfigurasi otomatisasi rilis APK berkala (*Nightly Build Artifacts*).
