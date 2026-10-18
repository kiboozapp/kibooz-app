# Spesifikasi Teknis Backend: Arsitektur API dan Basis Data Kibooz

Panduan ini memuat diagram relasi entitas, DDL skema database, kontrak RESTful API, dan rincian tugas backend dari fase MVP hingga produksi penuh.

---

## 1. Arsitektur Layanan Backend

- **Runtime:** Go dengan Fiber v3 dan GORM. Satu binary menjalankan API dan menerapkan migrasi SQL yang tertanam saat mulai.
- **Basis Data Utama:** PostgreSQL 15 (relasional, ACID untuk pencatatan poin dan log mood). Token refresh juga disimpan di sini dalam bentuk hash SHA-256.
- **In-Memory Caching:** Redis 7 bersifat opsional. Redis mempercepat penolakan token yang dipakai ulang, batas laju yang dipakai bersama dan cache leaderboard. API tetap berjalan tanpa Redis dan `GET /healthz` melaporkan status `degraded`.
- **Penyimpanan Berkas (Object Storage):** Bucket yang kompatibel dengan S3 (Cloudflare R2 secara bawaan) untuk foto profil, foto bukti scan sampah dan video kelas. Video diunggah langsung ke bucket lewat alamat bertanda tangan, bukan melalui API. Unggahan nonaktif sampai konfigurasi S3 lengkap.
- **Protokol Komunikasi:** RESTful JSON melalui HTTPS dengan header `Authorization: Bearer <JWT>`. Semua respons memakai satu amplop dan semua pesan berbahasa Inggris, sehingga klien bercabang pada `errorCode` dan tidak pada teks pesan.
- **Alamat Layanan:** `https://kibooz-api.syafahadyan.com/api/v1`. Dokumentasi interaktif ada di `https://docs.kibooz.syafahadyan.com` dan dibangun dari `openapi.yaml` di repositori backend (`SyafaHadyan/kibooz-backend`), yang menjadi acuan jika ada perbedaan dengan dokumen ini.
- **Cakupan:** kontrak MVP di dokumen ini ditambah registrasi, token refresh, logout, unggah foto, hapus akun, video pembelajaran, forum diskusi kelas, pengelolaan kelas oleh guru, profil guru, profil anak dan statistik sampah. Video ditambahkan guru sebagai alamat https, baik tautan ke tempat lain maupun berkas yang diunggah langsung ke bucket lewat alamat bertanda tangan. Guru kelas dapat mengubah dan menghapus video, dan pembuat postingan forum dapat mengubah postingannya sendiri. Berkas video yang diunggah menunggu di folder sementara sampai videonya ditambahkan, dan postingan forum yang diubah penulisnya ditandai dengan `editedAt`.

## 2. Diagram Relasi Entitas (ERD)

```mermaid
erDiagram
    USERS ||--o| GURUS : "profil guru"
    USERS ||--o| WALIS : "profil wali"
    WALIS ||--o{ STUDENTS : "memiliki anak"
    CLASSES ||--o{ STUDENTS : "terdaftar dalam kelas"
    GURUS ||--o{ CLASS_TEACHERS : "mengajar kelas"
    CLASSES ||--o{ CLASS_TEACHERS : "diajar oleh"
    STUDENTS ||--o{ MOOD_LOGS : "memiliki riwayat mood"
    GURUS ||--o{ MOOD_LOGS : "mencatat mood"
    STUDENTS ||--o{ TRASH_SCANS : "memilah sampah"
    STUDENTS ||--o{ GUIDANCE_APPLICATIONS : "penerapan panduan"
    WALIS ||--o{ GUIDANCE_APPLICATIONS : "dikirim oleh wali"
    USERS ||--o{ REFRESH_TOKENS : "sesi masuk"
    CLASSES ||--o{ LEARNING_VIDEOS : "memiliki materi video"
    GURUS ||--o{ LEARNING_VIDEOS : "menambahkan video"
    CLASSES ||--o{ FORUM_POSTS : "memiliki forum diskusi"
    USERS ||--o{ FORUM_POSTS : "menulis postingan"
    FORUM_POSTS ||--o{ FORUM_POSTS : "dibalas oleh"

    USERS {
        uuid id PK
        string email UK
        string password_hash
        enum role "GURU, WALI, ADMIN"
        string full_name
        string phone_number
        string avatar_url
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at "disembunyikan saat akun dihapus"
    }

    GURUS {
        uuid id PK
        uuid user_id FK
        string nip UK
        string school_name
        string address "alamat dinas"
        timestamp deleted_at
    }

    WALIS {
        uuid id PK
        uuid user_id FK
        string address
        string whatsapp_number
        timestamp deleted_at
    }

    CLASSES {
        uuid id PK
        string join_code UK "kode untuk wali bergabung"
        string school_name
        string name
        string grade_level
        string academic_year
        timestamp created_at
    }

    STUDENTS {
        uuid id PK
        uuid wali_id FK
        uuid class_id FK
        string nisn UK
        string full_name
        string avatar_url
        integer current_points
        integer season_points
        integer rank_position
        timestamp created_at
        timestamp deleted_at
    }

    MOOD_LOGS {
        uuid id PK
        uuid student_id FK
        uuid recorded_by_guru_id FK
        enum mood_type "SENANG, SEDIH, MARAH, BINGUNG"
        float confidence_score
        enum source "AI_CAMERA, MANUAL_INPUT"
        string notes
        timestamp recorded_at
    }

    TRASH_SCANS {
        uuid id PK
        uuid student_id FK
        enum trash_type "ORGANIK, ANORGANIK, B3"
        float confidence_score
        integer points_awarded
        string photo_url
        timestamp scanned_at
    }

    CLASS_TEACHERS {
        uuid class_id PK
        uuid guru_id PK
        timestamp created_at
    }

    GUIDANCE_APPLICATIONS {
        uuid id PK
        uuid student_id FK
        uuid wali_id FK
        string guidance_id
        string parent_notes
        timestamp applied_at
    }

    LEARNING_VIDEOS {
        uuid id PK
        uuid class_id FK
        uuid added_by_guru_id FK
        string title
        string description
        string video_url "alamat https"
        string thumbnail_url
        integer duration_seconds
        timestamp created_at
    }

    FORUM_POSTS {
        uuid id PK
        uuid class_id FK
        uuid parent_id FK "kosong untuk utas, terisi untuk balasan"
        uuid author_user_id FK
        string title "hanya untuk utas"
        string body
        timestamp created_at
        timestamp edited_at "kosong sampai penulis mengubah isinya"
    }

    REFRESH_TOKENS {
        uuid id PK
        uuid user_id FK
        string token_hash UK "SHA-256, token asli tidak disimpan"
        timestamp expires_at
        timestamp created_at
    }
```

Tabel `class_teachers` sudah ada di ERD awal tetapi belum ada di DDL awal, sedangkan `guidance_applications` dan `refresh_tokens` ditambahkan oleh implementasi. Tabel `learning_videos` dan `forum_posts` ada di ERD awal tanpa DDL, sehingga keduanya dirancang di implementasi. Video adalah alamat https yang ditambahkan guru, baik tautan ke tempat lain maupun berkas yang diunggah ke bucket. Forum terdiri dari utas, yaitu postingan dengan judul, dan balasan, yaitu postingan tanpa judul yang menunjuk ke utas di kelas yang sama. Kolom `gurus.address` ditambahkan untuk alamat dinas guru. Kolom `forum_posts.edited_at` menandai kapan penulis terakhir mengubah judul atau isi postingan dan kosong untuk postingan yang belum pernah diubah. Berkas video yang baru diunggah menunggu di awalan `pending/` pada bucket dan disalin ke alamat permanennya di `videos/` saat video ditambahkan, sedangkan berkas yang tidak pernah ditambahkan dihapus bucket setelah satu hari.

---

## 3. Skema Basis Data Relasional (PostgreSQL DDL)

DDL di bawah ini adalah gabungan migrasi `000001` sampai `000007` dari `internal/infra/db/migrations` di repositori backend. Migrasi tertanam di binary dan dijalankan otomatis saat API mulai. Dibanding rancangan awal, `gen_random_uuid()` menggantikan `uuid_generate_v4()` sehingga tidak perlu ekstensi, dan ada tabel serta kolom tambahan yang dijelaskan di bagian 2.

```sql
-- Initial Kibooz schema following 02_BACKEND_API_AND_DATABASE.md with the addition of
-- class_teachers, class join codes, and guidance_applications.

CREATE TYPE user_role AS ENUM ('GURU', 'WALI', 'ADMIN');
CREATE TYPE mood_enum AS ENUM ('SENANG', 'SEDIH', 'MARAH', 'BINGUNG');
CREATE TYPE input_source_enum AS ENUM ('AI_CAMERA', 'MANUAL_INPUT');
CREATE TYPE trash_category_enum AS ENUM ('ORGANIK', 'ANORGANIK', 'B3');

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role user_role NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    phone_number VARCHAR(30),
    avatar_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE gurus (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    nip VARCHAR(50) UNIQUE,
    school_name VARCHAR(150) NOT NULL DEFAULT 'TK Pertiwi Harapan Bangsa'
);

CREATE TABLE walis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    address TEXT,
    whatsapp_number VARCHAR(30)
);

CREATE TABLE classes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    join_code VARCHAR(12) NOT NULL UNIQUE,
    school_name VARCHAR(150) NOT NULL DEFAULT 'TK Pertiwi Harapan Bangsa',
    name VARCHAR(50) NOT NULL,
    grade_level VARCHAR(20) NOT NULL DEFAULT 'Kelas A',
    academic_year VARCHAR(20) NOT NULL DEFAULT '2026/2027',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE class_teachers (
    class_id UUID NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    guru_id UUID NOT NULL REFERENCES gurus(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (class_id, guru_id)
);

CREATE INDEX idx_class_teachers_guru ON class_teachers(guru_id);

CREATE TABLE students (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wali_id UUID NOT NULL REFERENCES walis(id) ON DELETE RESTRICT,
    class_id UUID NOT NULL REFERENCES classes(id) ON DELETE RESTRICT,
    nisn VARCHAR(30) UNIQUE NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    avatar_url TEXT,
    current_points INTEGER NOT NULL DEFAULT 0 CHECK (current_points >= 0),
    rank_position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_students_wali ON students(wali_id);
CREATE INDEX idx_students_class_points ON students(class_id, current_points DESC, full_name, id);

CREATE TABLE mood_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    recorded_by_guru_id UUID NOT NULL REFERENCES gurus(id) ON DELETE RESTRICT,
    mood_type mood_enum NOT NULL,
    confidence_score REAL NOT NULL DEFAULT 1.0 CHECK (confidence_score >= 0 AND confidence_score <= 1),
    source input_source_enum NOT NULL DEFAULT 'MANUAL_INPUT',
    notes TEXT,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mood_logs_student_date ON mood_logs(student_id, recorded_at);
CREATE INDEX idx_mood_logs_date ON mood_logs(recorded_at);

CREATE TABLE trash_scans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    trash_type trash_category_enum NOT NULL,
    confidence_score REAL NOT NULL DEFAULT 1.0 CHECK (confidence_score >= 0 AND confidence_score <= 1),
    points_awarded INTEGER NOT NULL CHECK (points_awarded >= 0),
    photo_url TEXT,
    scanned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trash_scans_student_date ON trash_scans(student_id, scanned_at);

CREATE TABLE guidance_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    wali_id UUID NOT NULL REFERENCES walis(id) ON DELETE CASCADE,
    guidance_id VARCHAR(64) NOT NULL,
    parent_notes TEXT,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_guidance_applications_student ON guidance_applications(student_id, applied_at);

-- 000002 refresh tokens
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user_expiry ON refresh_tokens(user_id, expires_at);

-- 000003 soft delete
-- The unique rules only apply to rows that are still active, so an email, NIP or NISN can be used again after deletion.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE gurus ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE walis ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE students ADD COLUMN deleted_at TIMESTAMPTZ;

ALTER TABLE users DROP CONSTRAINT users_email_key;
ALTER TABLE gurus DROP CONSTRAINT gurus_nip_key;
ALTER TABLE students DROP CONSTRAINT students_nisn_key;

-- the index names stay the same as the old constraints, the API maps them to friendly errors
CREATE UNIQUE INDEX users_email_key ON users (email) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX gurus_nip_key ON gurus (nip) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX students_nisn_key ON students (nisn) WHERE deleted_at IS NULL;

-- 000004 default grade level
ALTER TABLE classes ALTER COLUMN grade_level SET DEFAULT 'Class A';

-- 000005 learning videos and the class forum
-- A teacher adds learning videos to a class as https addresses, and the class forum holds threads and replies.
-- A thread is a forum post with a title and no parent, a reply is a post without a title that points at a thread of the same class.

CREATE TABLE learning_videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    added_by_guru_id UUID NOT NULL REFERENCES gurus(id) ON DELETE RESTRICT,
    title VARCHAR(150) NOT NULL,
    description TEXT,
    video_url TEXT NOT NULL,
    thumbnail_url TEXT,
    duration_seconds INTEGER CHECK (duration_seconds IS NULL OR duration_seconds > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_learning_videos_class ON learning_videos(class_id, created_at DESC, id DESC);

CREATE TABLE forum_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    parent_id UUID,
    author_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(150),
    body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, class_id),
    -- the composite key keeps a reply in the class of its thread
    FOREIGN KEY (parent_id, class_id) REFERENCES forum_posts(id, class_id) ON DELETE CASCADE,
    CHECK ((parent_id IS NULL) = (title IS NOT NULL))
);

CREATE INDEX idx_forum_threads_class ON forum_posts(class_id, created_at DESC, id DESC) WHERE parent_id IS NULL;
CREATE INDEX idx_forum_replies_thread ON forum_posts(parent_id, created_at, id) WHERE parent_id IS NOT NULL;

-- 000006 teacher address
-- A teacher keeps an official address next to the phone number that already lives on the account.
ALTER TABLE gurus ADD COLUMN address TEXT;

-- 000007 forum edited marker
-- The time a post was last changed by its author, empty for a post that was never edited.
ALTER TABLE forum_posts ADD COLUMN edited_at TIMESTAMP WITH TIME ZONE;

-- 000008 to 000010 are not listed here: a video url index, refresh token families and eight character join codes.

-- 000011 file cleanups
-- A file that could not be removed from the bucket when its owner deleted the account waits here until a later try works.
CREATE TABLE file_cleanups (
    object_key TEXT PRIMARY KEY,
    attempts INTEGER NOT NULL DEFAULT 0,
    queued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    next_try_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- 000012 semester leaderboard
-- The leaderboard ranks season_points, and current_points keeps the points of all semesters.
ALTER TABLE students ADD COLUMN season_points INTEGER NOT NULL DEFAULT 0 CHECK (season_points >= 0);
CREATE INDEX idx_students_class_season_points ON students(class_id, season_points DESC, full_name, id);

CREATE TABLE leaderboard_seasons (
    label TEXT PRIMARY KEY,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE leaderboard_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID NOT NULL REFERENCES classes(id),
    student_id UUID NOT NULL,
    student_name VARCHAR(150) NOT NULL,
    season_label TEXT NOT NULL,
    points INTEGER NOT NULL,
    rank_position INTEGER NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- 000013 class owner
-- Satu guru memiliki setiap kelas. Pemilik dapat mengeluarkan guru lain dan menyerahkan kelas, dan setiap guru dapat keluar.
-- Guru aktif yang bergabung paling awal menjadi pemilik kelas yang sudah ada.
ALTER TABLE class_teachers ADD COLUMN is_owner BOOLEAN NOT NULL DEFAULT false;
-- (pengisian awal is_owner untuk kelas yang sudah ada ada di berkas migrasi)

-- paling banyak satu pemilik untuk satu kelas
CREATE UNIQUE INDEX idx_class_teachers_owner ON class_teachers(class_id) WHERE is_owner;
```

---

## 4. Spesifikasi Kontrak RESTful API

### 4.0 Aturan Umum

Semua alamat di bawah ini relatif terhadap `https://kibooz-api.syafahadyan.com/api/v1`, kecuali `GET /healthz` yang berada di akar.

**Amplop respons.** Respons sukses berbentuk `{ "success": true, "message": "...", "data": { ... } }` dengan `message` dan `data` yang opsional. Respons gagal berbentuk `{ "success": false, "message": "...", "errorCode": "...", "details": { ... } }`. Kolom `details` hanya muncul pada kegagalan validasi dan berisi satu pesan untuk setiap kolom yang salah.

**Bahasa.** Pesan dan nama hari dalam respons memakai bahasa Inggris (`Monday` sampai `Friday`), dan nilai bawaan `gradeLevel` kelas baru adalah `Class A`. Aplikasi menerjemahkan teks tampilan di sisi klien.

**Batas laju.** Rute untuk pengguna yang sudah masuk dibatasi per pengguna (120 permintaan per 60 detik). Login dan registrasi dibatasi per email, atau per perangkat jika login membawa `deviceToken` yang valid, sedangkan token refresh dan logout dibatasi per token refresh, masing-masing 10 permintaan per 60 detik untuk tiap rute. Registrasi yang menyebut kode kelas juga berbagi satu batas untuk seluruh API (60 permintaan per 60 detik) agar kode kelas tidak dapat ditebak. Tidak ada batas yang memakai alamat IP. Permintaan yang melewati batas mendapat `429` dengan `errorCode` `RATE_LIMITED` dan header `Retry-After` yang berisi jumlah detik sampai permintaan berikutnya diterima. Permintaan yang ditolak tetap dihitung, sehingga aplikasi yang terus mencoba harus menunggu lebih lama. Respons dari rute yang dibatasi juga memuat `X-RateLimit-Limit`, `X-RateLimit-Remaining` dan `X-RateLimit-Reset`. Proksi di depan API dapat menjawab `429` tanpa isi dan tanpa `Retry-After`, sehingga aplikasi memeriksa kode status lebih dulu, menunggu sekitar 30 detik jika header itu tidak ada, dan menggandakan waktunya pada tiap `429` berikutnya. Batas harian klaim sampah berbeda, yaitu `429` dengan `TRASH_DAILY_LIMIT_REACHED` tanpa `Retry-After`, dan batas itu mulai lagi pada hari lokal berikutnya.

**Daftar berhalaman.** Daftar video, utas, balasan, kelas dan murid memakai query `page` (mulai dari 1) dan `limit` (1 sampai 50, bawaan 20). Respons memuat `page`, `limit` dan `total` di samping isi daftar, dan halaman setelah yang terakhir menghasilkan daftar kosong.

**Poin semester.** Setiap anak punya `totalPoints` atau `currentPoints` untuk poin dari semua semester, dan `seasonPoints` untuk poin semester yang sedang berjalan. Papan peringkat dan `classRank` mengikuti `seasonPoints`. Pada hari pertama bulan awal semester (Januari dan Juli secara bawaan, diatur oleh `SEMESTER_START_MONTHS`) pukul 00.00 zona waktu server, `seasonPoints` semua anak kembali ke 0 dan peringkat dihitung ulang, sedangkan `totalPoints` tetap. Server menyimpan peringkat akhir semester yang berakhir di tabel `leaderboard_history`, dan `GET /leaderboard/history` membacanya kembali. Pemeriksaan kalender berjalan setiap menit, sehingga poin yang masuk dalam semenit setelah pergantian semester mungkin masih dihitung ke semester yang berakhir.

**Kode kesalahan.**

| `errorCode` | Arti |
|:---|:---|
| `VALIDATION_ERROR` | Masukan tidak valid, rincian per kolom ada di `details`. Teks yang memuat karakter NUL (`\u0000`) juga ditolak dengan nama kolomnya |
| `AUTH_INVALID_CREDENTIALS` | Email atau kata sandi tidak cocok untuk peran yang dipilih |
| `AUTH_TOKEN_MISSING`, `AUTH_TOKEN_INVALID` | Token akses tidak dikirim atau tidak valid |
| `AUTH_REFRESH_INVALID` | Sesi sudah berakhir dan pengguna harus masuk lagi |
| `AUTH_FORBIDDEN` | Peran tidak boleh melakukan aksi ini |
| `AUTH_PASSWORD_INCORRECT` | Kata sandi salah saat menghapus akun |
| `EMAIL_ALREADY_REGISTERED`, `NIP_ALREADY_REGISTERED`, `NISN_ALREADY_REGISTERED` | Data sudah terdaftar |
| `CLASS_CODE_NOT_FOUND`, `CLASS_NOT_FOUND`, `CLASS_NO_ACTIVE_TEACHER` | Masalah pada kode atau data kelas |
| `CHILD_LIMIT_REACHED` | Wali sudah memiliki 10 anak, jumlah terbanyak |
| `TEACHER_NOT_FOUND`, `TEACHER_ALREADY_IN_CLASS` | Tidak ada akun guru aktif dengan email itu, baik saat menambah guru maupun saat menyebut pengganti, atau guru itu sudah mengajar kelas ini |
| `TEACHER_NOT_IN_CLASS` | Guru yang dituju tidak mengajar kelas ini |
| `CLASS_OWNER_REQUIRED` | Hanya pemilik kelas yang boleh melakukan aksi ini |
| `LAST_TEACHER` | Guru itu satu-satunya yang tersisa di kelas, sehingga tidak dapat keluar |
| `SUCCESSOR_REQUIRED` | Akun adalah satu-satunya guru sebuah kelas dan tidak menyebut `successorEmail` saat dihapus |
| `STUDENT_NOT_FOUND`, `PROFILE_NOT_FOUND`, `GUIDANCE_NOT_FOUND`, `FORUM_POST_NOT_FOUND`, `VIDEO_NOT_FOUND` | Data yang dicari tidak ada |
| `TRASH_DAILY_LIMIT_REACHED` | Batas klaim harian anak sudah tercapai |
| `INVALID_IMAGE`, `IMAGE_DIMENSIONS_TOO_LARGE`, `FILE_TOO_LARGE` | Berkas bukan gambar, ukuran pikselnya terlalu besar, atau ukuran berkasnya terlalu besar |
| `STORAGE_UNAVAILABLE`, `STORAGE_ERROR` | Penyimpanan berkas belum diatur atau gagal |
| `RATE_LIMITED` | Batas laju terlewati |

---

### 4.1 Autentikasi (`/auth`)

#### `POST /auth/register`
- **Deskripsi:** Membuat akun. Seorang GURU mendaftar dengan kelas baru dan seorang WALI bergabung dengan kode kelas. Mengetahui kode kelas tidak pernah memberi akses sebagai guru. Kode kelas terdiri dari 8 karakter huruf besar dan angka, tanpa huruf `I` dan `O` serta angka `0` dan `1` agar tidak tertukar saat dibaca atau disalin.
- **Request Body (WALI):**
```json
{
  "email": "sarah.kartika@gmail.com",
  "password": "PasswordRahasia123!",
  "fullName": "Sarah Kartika, S.Pd.",
  "role": "WALI",
  "classCode": "KBZ48R2M",
  "student": { "nisn": "3124567890", "fullName": "Amelia Siti Zahra" }
}
```
- **Request Body (GURU):** `email`, `password`, `fullName`, `role: "GURU"`, `nip` dan `schoolName` yang opsional, serta `class` berisi `name` (wajib), `gradeLevel`, `academicYear` dan `schoolName`.
- **Response 201 Created:** sama dengan respons login di bawah, dengan `message` `Registration successful`.
- **Kegagalan:** `400` `VALIDATION_ERROR`, `404` `CLASS_CODE_NOT_FOUND` atau `CLASS_NO_ACTIVE_TEACHER`, `409` untuk email, NIP atau NISN yang sudah terdaftar, dan `429` `RATE_LIMITED` ketika batas pendaftaran dengan kode kelas sedang habis. Batas ini berlaku untuk seluruh API dan bukan per pengguna, sehingga aplikasi menampilkan pesan agar mencoba lagi nanti sesuai header `Retry-After`.

#### `POST /auth/login`
- **Deskripsi:** Masuk ke akun sebagai Guru atau Wali Murid.
- **Request Body:** `deviceToken` bersifat opsional dan diisi dengan token perangkat dari login atau registrasi sebelumnya untuk email yang sama.
```json
{
  "email": "sarah.kartika@gmail.com",
  "password": "PasswordRahasia123!",
  "role": "WALI",
  "deviceToken": "..."
}
```
- **Response 200 OK:**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "d8f8a123-bc42-4b21-8931...",
    "deviceToken": "...",
    "user": {
      "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
      "email": "sarah.kartika@gmail.com",
      "fullName": "Sarah Kartika, S.Pd.",
      "role": "WALI",
      "avatarUrl": "https://kibooz-media.syafahadyan.com/avatars/wali_sarah.png"
    }
  }
}
```
- **Response 401 Unauthorized:**
```json
{
  "success": false,
  "message": "The email or password does not match the selected role",
  "errorCode": "AUTH_INVALID_CREDENTIALS"
}
```
- `deviceToken` bukan kredensial. Token ini hanya memberi perangkat anggaran login sendiri untuk email tersebut, dan aplikasi menyimpannya serta mengirimkannya pada login berikutnya.

#### `POST /auth/refresh-token`
- **Deskripsi:** Menukar token refresh dengan pasangan token baru. Setiap token refresh hanya berlaku sekali, jadi aplikasi harus mengganti yang tersimpan dengan yang baru pada setiap panggilan yang berhasil.
- **Request Body:** `{ "refreshToken": "d8f8a123-bc42-4b21-8931..." }`
- **Response 200 OK:** `message` `Token refreshed` dan `data` yang sama dengan respons login.
- **Response 401 Unauthorized:** `AUTH_REFRESH_INVALID`, artinya sesi berakhir dan pengguna harus masuk lagi.

#### `POST /auth/logout`
- **Deskripsi:** Mencabut sebuah token refresh.
- **Request Body:** `{ "refreshToken": "d8f8a123-bc42-4b21-8931..." }`
- **Response 200 OK:** `{ "success": true, "message": "Logout successful" }`

---

### 4.2 Portal Wali Murid (`/wali`)

#### `GET /wali/dashboard`
- **Header:** `Authorization: Bearer <token>`
- **Query Params:** `studentId=<uuid>` opsional, untuk wali yang memiliki lebih dari satu anak. Tanpa parameter ini, yang dikembalikan adalah anak yang terdaftar paling awal.
- **Response 200 OK:** `classId` dipakai wali untuk rute `/classes/{classId}`. `bannerUrl` bernilai teks kosong jika alamat publik penyimpanan belum diatur di server. `label`, `title` dan `category` berasal dari katalog tetap di backend dan berbahasa Inggris. Ada empat panduan, satu untuk setiap mood (`guidance-senang-4step`, `guidance-sedih-4step`, `guidance-marah-4step` dan `guidance-bingung-4step`), dan `bannerUrl` dibentuk dari alamat publik bucket ditambah `guidance/banner_ceria.png`, `banner_temani.png`, `banner_tenang.png` atau `banner_aman.png`. `className` berbentuk `<jenjang> • <nama sekolah>`.
```json
{
  "success": true,
  "data": {
    "student": {
      "id": "c4b3a981-5502-4f32-8231-10c970a03bd7",
      "fullName": "Amelia Siti Zahra",
      "classId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "className": "Class A • TK Pertiwi Harapan Bangsa",
      "avatarUrl": "https://kibooz-media.syafahadyan.com/avatars/amelia.png",
      "nisn": "3124567890"
    },
    "todayMood": {
      "moodType": "BINGUNG",
      "label": "Unsure / Needs Support",
      "confidence": 0.88,
      "recordedAt": "2026-10-04T08:15:00Z",
      "teacherNotes": "Amelia sempat ragu saat berpisah di gerbang, namun ceria saat sesi mewarnai."
    },
    "pointsSummary": {
      "totalPoints": 340,
      "seasonPoints": 120,
      "classRank": 2,
      "organicCount": 28,
      "anorganicCount": 20
    },
    "recommendedGuidance": {
      "id": "guidance-bingung-4step",
      "title": "Give Your Child a Sense of Safety",
      "category": "Confused / Unsure",
      "bannerUrl": "https://kibooz-media.syafahadyan.com/guidance/banner_aman.png"
    }
  }
}
```

#### `POST /wali/guidance/apply`
- **Deskripsi:** Konfirmasi penerapan panduan penanganan emosi oleh wali. Penerapan disimpan di tabel `guidance_applications` dan dibaca guru kelas lewat `GET /classes/{classId}/guidance-reports`.
- **Request Body:**
```json
{
  "guidanceId": "guidance-bingung-4step",
  "studentId": "c4b3a981-5502-4f32-8231-10c970a03bd7",
  "parentNotes": "Sudah diajak berbicara pelan di rumah, Amelia sudah tenang dan siap sekolah besok."
}
```
- **Response 200 OK:**
```json
{
  "success": true,
  "message": "Handling status recorded"
}
```
- **Kegagalan:** `404` `STUDENT_NOT_FOUND` atau `GUIDANCE_NOT_FOUND`.

#### `GET /wali/child/{studentId}`
- **Deskripsi:** Data resmi seorang anak beserta kontak wali, untuk layar profil siswa. Hanya anak milik wali yang masuk yang dapat dilihat.
- **Header:** `Authorization: Bearer <token>`
- **Response 200 OK:** `phoneNumber`, `whatsappNumber` dan `address` bernilai `null` jika wali tidak mengisinya saat registrasi.
```json
{
  "success": true,
  "data": {
    "id": "c4b3a981-5502-4f32-8231-10c970a03bd7",
    "fullName": "Amelia Siti Zahra",
    "nisn": "3124567890",
    "avatarUrl": null,
    "classId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "className": "Mawar",
    "gradeLevel": "Class A",
    "schoolName": "TK Pertiwi Harapan Bangsa",
    "academicYear": "2026/2027",
    "currentPoints": 340,
    "seasonPoints": 120,
    "classRank": 2,
    "guardian": {
      "fullName": "Sarah Kartika",
      "email": "sarah@example.com",
      "phoneNumber": "081234567890",
      "whatsappNumber": "+6281234567890",
      "address": "Jl. Kenanga 5, Malang"
    }
  }
}
```
- **Kegagalan:** `400` `VALIDATION_ERROR` untuk id yang bukan UUID dan `404` `STUDENT_NOT_FOUND` untuk anak yang bukan milik wali.

#### `GET /wali/children`
- **Deskripsi:** Daftar anak milik wali yang masuk, yang terdaftar paling awal di urutan pertama. `id` dipakai sebagai `studentId` pada rute lain yang menerimanya, seperti `GET /wali/dashboard`.
- **Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "children": [
      {
        "id": "c4b3a981-5502-4f32-8231-10c970a03bd7",
        "fullName": "Amelia Siti Zahra",
        "nisn": "3124567890",
        "avatarUrl": null,
        "classId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
        "className": "Mawar"
      }
    ]
  }
}
```

#### `POST /wali/children`
- **Deskripsi:** Mendaftarkan satu anak lagi untuk wali yang masuk di kelas pemilik `classCode`, yang boleh berbeda dari kelas anak pertama. Satu wali dapat memiliki sampai 10 anak. Rute ini memakai anggaran tebakan kode kelas yang sama dengan registrasi (`CLASS_CODE_LIMITER_MAX` untuk seluruh API), sehingga dapat menjawab `429` ketika banyak kode sedang dicoba.
- **Request Body:**
```json
{
  "classCode": "K7M2QX4R",
  "student": { "nisn": "3124567891", "fullName": "Budi Santoso" }
}
```
- **Response 201 Created:** `message` bernilai `Child added` dan `data` berisi satu anak dengan bentuk yang sama seperti isi `children` di atas.
- **Kegagalan:** `400` `VALIDATION_ERROR`, `404` `CLASS_CODE_NOT_FOUND`, `409` `NISN_ALREADY_REGISTERED`, `CLASS_NO_ACTIVE_TEACHER` atau `CHILD_LIMIT_REACHED`, dan `429` `RATE_LIMITED`.

---

### 4.3 Portal Guru (`/guru`)

#### `GET /guru/dashboard`
- **Header:** `Authorization: Bearer <token>`
- **Query Params:** `classId=<uuid>` opsional, untuk guru yang mengajar lebih dari satu kelas.
- **Response 200 OK:** `joinCode` adalah kode yang dibagikan guru kepada wali, dan `dominantMood` bernilai `null` selama belum ada catatan emosi hari itu.
```json
{
  "success": true,
  "data": {
    "teacher": {
      "fullName": "Siti Rahayu, S.Pd.",
      "nip": "198507152010032005",
      "avatarUrl": "https://kibooz-media.syafahadyan.com/avatars/guru_siti.png"
    },
    "classOverview": {
      "classId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "className": "Class A (Mawar)",
      "joinCode": "KBZ48R2M",
      "totalStudents": 24,
      "presentStudents": 22,
      "dominantMood": "SENANG"
    },
    "dailyMoodDistribution": {
      "senang": 14,
      "sedih": 3,
      "marah": 1,
      "bingung": 4
    }
  }
}
```

#### `POST /guru/mood/log`
- **Deskripsi:** Guru menginputkan atau mengonfirmasi hasil deteksi mood siswa. Server menyimpan nilai `confidenceScore` apa adanya, sedangkan dialog konfirmasi untuk hasil AI di bawah 75% (BR-04) dikerjakan oleh aplikasi.
- **Request Body:**
```json
{
  "studentId": "c4b3a981-5502-4f32-8231-10c970a03bd7",
  "moodType": "SENANG",
  "source": "AI_CAMERA",
  "confidenceScore": 0.94,
  "notes": "Anak tersenyum lepas saat menyanyikan lagu pembuka."
}
```
- **Response 201 Created:**
```json
{
  "success": true,
  "message": "Student mood log saved",
  "data": {
    "logId": "e1f2a3b4-5678-90ab-cdef-1234567890ab",
    "recordedAt": "2026-10-04T08:30:00Z"
  }
}
```

#### `GET /guru/mood/analytics`
- **Query Params:** `classId=<uuid>` dan `range=weekly|monthly` (bawaan `weekly`). Keduanya opsional.
- **Response 200 OK:** `donutSummary` mengikuti rentang yang diminta, yaitu minggu berjalan atau bulan berjalan. `averageHappyScore` adalah persentase siswa pada hari itu yang catatan terakhirnya `SENANG`. `weeklyTrend` hanya berisi hari Senin sampai Jumat dengan nama hari berbahasa Inggris. `monthlyDistribution` hanya muncul pada `range=monthly` dan contohnya ada di bawah ini.
```json
{
  "success": true,
  "data": {
    "donutSummary": [
      { "mood": "SENANG", "percentage": 58, "colorHex": "#34C759" },
      { "mood": "BINGUNG", "percentage": 20, "colorHex": "#FF9500" },
      { "mood": "SEDIH", "percentage": 14, "colorHex": "#5856D6" },
      { "mood": "MARAH", "percentage": 8, "colorHex": "#FF3B30" }
    ],
    "weeklyTrend": [
      { "day": "Monday", "averageHappyScore": 72 },
      { "day": "Tuesday", "averageHappyScore": 78 },
      { "day": "Wednesday", "averageHappyScore": 65 },
      { "day": "Thursday", "averageHappyScore": 82 },
      { "day": "Friday", "averageHappyScore": 90 }
    ],
    "monthlyDistribution": [
      { "mood": "SENANG", "count": 120 },
      { "mood": "BINGUNG", "count": 41 },
      { "mood": "SEDIH", "count": 27 },
      { "mood": "MARAH", "count": 12 }
    ]
  }
}
```

#### `GET /guru/classes`
- **Deskripsi:** Kelas yang diajar guru, dari yang paling lama, dengan kode bergabung dan jumlah anak.
- **Header:** `Authorization: Bearer <token>`
- **Query Params:** `page` dan `limit` opsional.
- **Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "classes": [
      {
        "id": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
        "name": "Mawar",
        "gradeLevel": "Class A",
        "schoolName": "TK Pertiwi Harapan Bangsa",
        "academicYear": "2026/2027",
        "joinCode": "K7ZH2MQ4",
        "totalStudents": 24,
        "createdAt": "2026-10-01T07:00:00Z"
      }
    ],
    "page": 1,
    "limit": 20,
    "total": 1
  }
}
```

#### `POST /guru/classes`
- **Deskripsi:** Membuat kelas baru. Guru yang membuatnya menjadi pengajar kelas itu dan kelas mendapat kode bergabung sendiri untuk wali.
- **Request Body:** `name` wajib (maksimal 50 karakter). `gradeLevel` (bawaan `Class A`), `academicYear` (bawaan `2026/2027`) dan `schoolName` (bawaan sekolah guru) opsional.
```json
{
  "name": "Melati",
  "gradeLevel": "Class B",
  "academicYear": "2027/2028"
}
```
- **Response 201 Created:** `message` bernilai `Class created` dan `data` berisi satu kelas dengan bentuk yang sama seperti isi `classes` di atas, dengan `totalStudents` 0.
- **Kegagalan:** `400` `VALIDATION_ERROR`.

#### `POST /guru/classes/{classId}/join-code`
- **Deskripsi:** Mengganti kode bergabung sebuah kelas dengan kode acak yang baru. Kode lama berhenti berlaku saat itu juga, sehingga wali yang belum bergabung memerlukan kode baru, sedangkan wali yang sudah bergabung tidak terpengaruh. Dipakai ketika kode terlanjur dibagikan terlalu luas atau berhasil ditebak. Hanya guru yang mengajar kelas itu yang dapat memakainya.
- **Response 200 OK:** `{ "success": true, "message": "Join code replaced", "data": { "joinCode": "K7M2QX4R" } }`. Aplikasi menampilkan kode baru dan meminta guru membagikannya lagi.
- **Catatan rilis 0.7.0:** kode bergabung kini terdiri dari 8 karakter, dan migrasi pada rilis itu mengganti setiap kode 6 karakter yang sudah ada dengan kode baru. Kode lama berhenti berlaku, sehingga guru perlu membagikan kode baru. Aplikasi selalu membaca `joinCode` dari dashboard dan tidak menyimpannya sendiri.
- **Kegagalan:** `400` `VALIDATION_ERROR` untuk id yang bukan UUID, `403` `AUTH_FORBIDDEN` untuk peran selain guru, `404` `CLASS_NOT_FOUND` untuk kelas yang tidak diajar guru itu, dan `429` `RATE_LIMITED`.

#### `GET /guru/classes/{classId}/teachers`
- **Deskripsi:** Daftar guru yang mengajar sebuah kelas milik guru yang masuk, guru yang bergabung paling awal di urutan pertama.
- **Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "teachers": [
      {
        "id": "7a2f5c1e-3b44-4c8d-9d0e-5f6a7b8c9d0e",
        "fullName": "Siti Rahayu, S.Pd.",
        "nip": "198001012005012001",
        "avatarUrl": null,
        "isOwner": true,
        "joinedAt": "2026-10-01T07:00:00Z"
      }
    ]
  }
}
```
- `isOwner` bernilai `true` untuk tepat satu guru di setiap kelas, yaitu pemilik kelas. Guru yang membuat kelas menjadi pemiliknya, dan guru yang ditambahkan tidak pernah menjadi pemilik.
- **Kegagalan:** `400` `VALIDATION_ERROR` untuk id yang bukan UUID, `403` `AUTH_FORBIDDEN` untuk peran selain guru, dan `404` `CLASS_NOT_FOUND` untuk kelas yang tidak diajar guru itu.

#### `POST /guru/classes/{classId}/teachers`
- **Deskripsi:** Seorang guru kelas menambahkan guru lain yang sudah terdaftar, dengan email akunnya. Guru yang ditambahkan langsung melihat seluruh kelas, yaitu anak, mood dan forum, sehingga hanya tambahkan orang yang memang berhak. Seorang guru tetap tidak dapat bergabung ke kelas sendiri dengan kode bergabung. Hanya pemilik kelas yang dapat mengeluarkan guru lagi, lewat `DELETE /guru/classes/{classId}/teachers/{teacherId}`. Jawaban untuk email yang bukan akun guru sama bagi semua penanya yang sudah lolos akses kelas, dan permintaan dihitung pada batas pengguna.
- **Request Body:** `{ "email": "budi@example.com" }`
- **Response 201 Created:** `message` bernilai `Teacher added` dan `data` berisi satu guru dengan bentuk yang sama seperti isi `teachers` di atas.
- **Kegagalan:** `400` `VALIDATION_ERROR`, `403` `AUTH_FORBIDDEN`, `404` `CLASS_NOT_FOUND` atau `TEACHER_NOT_FOUND`, dan `409` `TEACHER_ALREADY_IN_CLASS`.

#### `DELETE /guru/classes/{classId}/teachers/{teacherId}`
- **Deskripsi:** Pemilik kelas dapat mengeluarkan guru lain, dan setiap guru dapat keluar dari kelas dengan mengirim id dirinya sendiri. Guru yang keluar tidak lagi melihat kelas, anak, mood dan forum, sedangkan tulisannya tetap ada. Ketika pemilik keluar, guru yang bergabung berikutnya menjadi pemilik. Satu-satunya guru aktif sebuah kelas tidak dapat keluar, sehingga kelas selalu punya guru dan pemilik. Untuk menyerahkan kelas dengan sengaja, pakai `PUT /guru/classes/{classId}/owner` lebih dulu. `teacherId` adalah `id` salah satu isi `teachers` pada `GET /guru/classes/{classId}/teachers`.
- **Response 200 OK:** `{ "success": true, "message": "Teacher removed" }`
- **Kegagalan:** `400` `VALIDATION_ERROR` untuk id yang bukan UUID, `403` `AUTH_FORBIDDEN` untuk peran selain guru atau `CLASS_OWNER_REQUIRED` ketika guru yang bukan pemilik mengeluarkan orang lain, `404` `CLASS_NOT_FOUND` untuk kelas yang tidak diajar guru itu atau `TEACHER_NOT_IN_CLASS`, `409` `LAST_TEACHER`, dan `429` `RATE_LIMITED`.

#### `PUT /guru/classes/{classId}/owner`
- **Deskripsi:** Pemilik menyerahkan kepemilikan kelas kepada guru lain di kelas yang sama. Pemilik baru dapat mengeluarkan guru dan menyerahkan kelas lagi, sedangkan pemilik lama tetap menjadi guru kelas. Menyebut pemilik yang sekarang tidak mengubah apa pun dan tetap dijawab dengan pemilik itu.
- **Request Body:** `{ "teacherId": "7a2f5c1e-3b44-4c8d-9d0e-5f6a7b8c9d0e" }`
- **Response 200 OK:** `message` bernilai `Ownership handed over` dan `data` berisi satu guru dengan bentuk yang sama seperti isi `teachers` di atas, yaitu pemilik yang baru.
- **Kegagalan:** `400` `VALIDATION_ERROR`, `403` `AUTH_FORBIDDEN` atau `CLASS_OWNER_REQUIRED`, `404` `CLASS_NOT_FOUND` atau `TEACHER_NOT_IN_CLASS`, dan `429` `RATE_LIMITED`.

#### `GET /guru/classes/{classId}`
- **Deskripsi:** Satu kelas milik guru dengan jumlah anak, video pembelajaran dan utas forum. Layar detail kelas memakai jumlah ini sebagai ringkasan aktivitas.
- **Response 200 OK:** semua kolom satu kelas dari daftar di atas ditambah dua kolom.
```json
{
  "success": true,
  "data": {
    "id": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "name": "Mawar",
    "gradeLevel": "Class A",
    "schoolName": "TK Pertiwi Harapan Bangsa",
    "academicYear": "2026/2027",
    "joinCode": "K7ZH2MQ4",
    "totalStudents": 24,
    "createdAt": "2026-10-01T07:00:00Z",
    "totalVideos": 3,
    "totalThreads": 5
  }
}
```
- **Kegagalan:** `400` `VALIDATION_ERROR` dan `404` `CLASS_NOT_FOUND` untuk kelas yang tidak diajar guru itu.

#### `GET /guru/profile`
- **Deskripsi:** Identitas guru untuk layar profil. `id` adalah id akun.
- **Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "id": "7d1e6c52-0a3b-4c8e-9f21-5b6a7c8d9e0f",
    "fullName": "Siti Rahayu, S.Pd.",
    "nip": "198507152010032005",
    "schoolName": "TK Pertiwi Harapan Bangsa",
    "avatarUrl": "https://kibooz-media.syafahadyan.com/avatars/guru_siti.png"
  }
}
```

#### `GET /guru/profile/detail`
- **Deskripsi:** Data lengkap guru. `phoneNumber` dan `address` bernilai `null` selama belum diisi.
- **Response 200 OK:** semua kolom `GET /guru/profile` ditambah empat kolom.
```json
{
  "success": true,
  "data": {
    "id": "7d1e6c52-0a3b-4c8e-9f21-5b6a7c8d9e0f",
    "fullName": "Siti Rahayu, S.Pd.",
    "nip": "198507152010032005",
    "schoolName": "TK Pertiwi Harapan Bangsa",
    "avatarUrl": null,
    "email": "siti@example.com",
    "phoneNumber": "+62 812-3456-7890",
    "address": "Jl. Merdeka 1, Malang",
    "joinedAt": "2026-10-01T07:00:00Z"
  }
}
```

#### `PUT /guru/profile`
- **Deskripsi:** Mengubah nomor telepon dan alamat. Kolom yang tidak dikirim dibiarkan apa adanya dan teks kosong menghapus nilainya. Foto diganti lewat `POST /users/avatar`.
- **Request Body:** salah satu atau kedua kolom. Nomor telepon berisi 6 sampai 30 karakter berupa angka dengan tanda plus di depan, serta spasi, tanda hubung dan kurung.
```json
{
  "phoneNumber": "+62 812-3456-7890",
  "address": "Jl. Merdeka 1, Malang"
}
```
- **Response 200 OK:** `message` bernilai `Profile updated` dan `data` berisi data lengkap seperti `GET /guru/profile/detail`.
- **Kegagalan:** `400` `VALIDATION_ERROR` jika tidak ada kolom yang dikirim atau nomor telepon tidak valid.

---

### 4.4 Gamifikasi dan Poin Sampah (`/trash` dan `/leaderboard`)

#### `POST /trash/scan-claim`
- **Deskripsi:** Klaim poin setelah melakukan pemindaian objek sampah. Poin per klaim adalah 10 untuk ORGANIK, 15 untuk ANORGANIK dan 0 untuk B3 (nilai bawaan yang dapat diatur), dengan batas 5 klaim per anak per hari. `photoBase64` bersifat opsional (base64 atau data URI, paling besar 4 MiB setelah didekode, dan aplikasi memperkecilnya dulu seperti foto profil).
- **Request Body:**
```json
{
  "studentId": "c4b3a981-5502-4f32-8231-10c970a03bd7",
  "trashType": "ANORGANIK",
  "confidenceScore": 0.91,
  "photoBase64": "data:image/jpeg;base64,..."
}
```
- **Response 200 OK:**
```json
{
  "success": true,
  "message": "Trash sorting points claimed!",
  "data": {
    "pointsAdded": 15,
    "totalPoints": 355,
    "seasonPoints": 135,
    "newRank": 2,
    "remainingDailyScans": 4
  }
}
```
- **Kegagalan:** `400` `INVALID_IMAGE` atau `IMAGE_DIMENSIONS_TOO_LARGE` untuk foto yang tidak valid, dan `429` `TRASH_DAILY_LIMIT_REACHED` ketika batas harian anak sudah tercapai.

#### `GET /leaderboard`
- **Query Params:** `classId=<uuid>` opsional.
- **Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "podium": [
      { "rank": 1, "studentName": "Farhan Al-Fatih", "points": 420, "avatarUrl": "..." },
      { "rank": 2, "studentName": "Amelia Siti Zahra", "points": 355, "avatarUrl": "..." },
      { "rank": 3, "studentName": "Kenzo Putra", "points": 310, "avatarUrl": "..." }
    ],
    "rankings": [
      { "rank": 4, "studentName": "Zhafira Bilqis", "points": 290, "avatarUrl": "..." },
      { "rank": 5, "studentName": "Rizky Ramadhan", "points": 275, "avatarUrl": "..." }
    ]
  }
}
```
- `points` adalah poin semester yang sedang berjalan (`seasonPoints`), dan peringkat mengikuti poin itu. Hasil disimpan di cache selama `LEADERBOARD_CACHE_SECONDS` (300 detik) dan cache dihapus setiap kali peringkat berubah, termasuk saat semester baru dimulai.

#### `GET /leaderboard/history`
- **Deskripsi:** Podium dan peringkat akhir setiap semester yang sudah berakhir untuk sebuah kelas, semester terbaru di urutan pertama. Semester diberi nama bulan awalnya, misalnya `2026-07`, dan baru muncul setelah berakhir, sehingga semester yang sedang berjalan hanya ada di `GET /leaderboard`. Anak memakai nama dan poin yang ia punya saat semester berakhir. Anak yang akunnya sudah dihapus tidak ditampilkan, sehingga sebuah peringkat bisa hilang dari daftar dan sebuah semester bisa tidak punya peringkat tersisa. Wali melihat kelas anaknya dan guru melihat kelas yang ia ajar.
- **Query Params:** `classId=<uuid>` opsional, serta `page` (mulai dari 1) dan `limit` (1 sampai 50, bawaan 20) yang menghitung semester dan bukan anak.
- **Response 200 OK:** daftar kosong untuk kelas yang belum punya semester berakhir.
```json
{
  "success": true,
  "data": {
    "page": 1,
    "limit": 20,
    "total": 2,
    "seasons": [
      {
        "label": "2026-07",
        "podium": [
          { "rank": 1, "studentName": "Farhan Al-Fatih", "points": 120 },
          { "rank": 2, "studentName": "Amelia Siti Zahra", "points": 95 }
        ],
        "rankings": [
          { "rank": 4, "studentName": "Zhafira Bilqis", "points": 60 }
        ]
      }
    ]
  }
}
```
- Isi `podium` adalah peringkat 1 sampai 3 yang masih ada, dan `rankings` adalah peringkat sesudahnya. Berbeda dari `GET /leaderboard`, setiap isi tidak memuat `avatarUrl`.
- **Kegagalan:** `400` `VALIDATION_ERROR`, `401` tanpa token, `403` `AUTH_FORBIDDEN`, `404` `CLASS_NOT_FOUND` dan `429` `RATE_LIMITED`.

#### `GET /trash/stats`
- **Deskripsi:** Statistik pemilahan sampah seorang anak. Hanya untuk wali.
- **Query Params:** `studentId=<uuid>` opsional. Tanpa parameter ini, yang dikembalikan adalah anak yang terdaftar paling awal.
- **Response 200 OK:** `breakdown` selalu berisi tiga kategori dengan urutan `ORGANIK`, `ANORGANIK` dan `B3`.
```json
{
  "success": true,
  "data": {
    "student": { "id": "c4b3a981-5502-4f32-8231-10c970a03bd7", "fullName": "Amelia Siti Zahra" },
    "totalPoints": 340,
    "seasonPoints": 120,
    "classRank": 2,
    "totalScans": 48,
    "breakdown": [
      { "trashType": "ORGANIK", "scans": 28, "points": 280 },
      { "trashType": "ANORGANIK", "scans": 20, "points": 60 },
      { "trashType": "B3", "scans": 0, "points": 0 }
    ],
    "todayScans": 2,
    "dailyLimit": 5,
    "remainingDailyScans": 3
  }
}
```
- **Kegagalan:** `400` `VALIDATION_ERROR` dan `404` `STUDENT_NOT_FOUND`.

---

### 4.5 Akun dan Foto (`/users`)

#### `POST /users/avatar`
- **Deskripsi:** Mengunggah foto profil pengguna, atau foto seorang anak jika `studentId` diisi oleh wali pemiliknya.
- **Request:** `multipart/form-data` dengan kolom `file` (gambar) dan `studentId` (opsional). Berkas paling besar 2 MiB. Server tidak memperkecil atau mengompres gambar, sehingga aplikasi memperkecil foto sebelum mengunggah, sekitar 1600 piksel pada sisi terpanjang dengan kualitas JPEG 80. Server menolak gambar yang lebih dari 40 megapiksel atau yang sisinya lebih dari 8192 piksel.
- **Response 200 OK:** `{ "success": true, "message": "Profile photo updated", "data": { "avatarUrl": "https://kibooz-media.syafahadyan.com/avatars/..." } }`
- **Kegagalan:** `400` `INVALID_IMAGE` atau `IMAGE_DIMENSIONS_TOO_LARGE`, `413` `FILE_TOO_LARGE`, `503` `STORAGE_UNAVAILABLE`.
- **Menampilkan foto yang lebih kecil:** untuk daftar yang berisi banyak foto, aplikasi meminta salinan kecil dari host media dan tidak mengunduh aslinya. Sisipkan `/cdn-cgi/image/width=128,quality=80,format=webp,onerror=redirect` setelah nama host pada `avatarUrl`, misalnya `https://kibooz-media.syafahadyan.com/cdn-cgi/image/width=128,quality=80,format=webp,onerror=redirect/avatars/...`. Aplikasi memakai daftar lebar yang pendek dan tetap, yaitu 128 dan 256 untuk foto profil, serta opsi yang sama setiap kali, karena setiap kombinasi foto dan opsi dihitung satu kali per bulan dan jatah gratisnya 5.000 per bulan. `onerror=redirect` selalu disertakan agar aplikasi mendapat foto aslinya ketika salinan tidak dapat dibuat. Cara ini hanya berlaku untuk alamat di host media, dan foto yang diganti mendapat alamat baru.

#### `DELETE /users/me`
- **Deskripsi:** Menghapus akun sendiri. Akun dan data anak disembunyikan dengan `deleted_at`, sehingga email, NIP dan NISN dapat dipakai lagi setelahnya. Guru yang menjadi satu-satunya guru sebuah kelas wajib menyebut `successorEmail`, yaitu email guru lain yang sudah terdaftar. Pengganti itu ditambahkan ke setiap kelas yang tidak diajar guru lain dan menjadi pemiliknya, sehingga kelas tidak pernah kehilangan guru terakhirnya. Kelas yang juga diajar guru lain tetap bersama mereka, dan jika akun yang dihapus adalah pemiliknya, guru yang bergabung berikutnya menjadi pemilik. Penghapusan yang ditolak tidak mengubah apa pun.
- **Request Body:** `{ "password": "PasswordRahasia123!", "successorEmail": "budi@example.com" }`. `successorEmail` opsional dan diabaikan untuk wali. Isinya tidak boleh email akun itu sendiri.
- **Response 200 OK:** `{ "success": true, "message": "Account deleted" }`
- **Kegagalan:** `400` `VALIDATION_ERROR` (termasuk email pengganti yang sama dengan akun itu), `403` `AUTH_PASSWORD_INCORRECT` atau `AUTH_FORBIDDEN`, `404` `PROFILE_NOT_FOUND` atau `TEACHER_NOT_FOUND` untuk email pengganti yang bukan akun guru aktif, `409` `SUCCESSOR_REQUIRED`, dan `429` `RATE_LIMITED`.

---

### 4.6 Kesehatan Layanan

#### `GET /healthz` (di akar, tanpa `/api/v1` dan tanpa token)
- **Response 200 OK:** (nilai `version` hanya contoh dan mengikuti versi rilis) `{ "success": true, "status": "ok", "version": "0.10.0", "checks": { "database": "ok", "redis": "ok", "storage": "ok" } }`
- `status` bernilai `degraded` jika Redis mati atau bucket menolak penulisan (tetap `200`), dan `down` dengan `503` jika basis data mati. Nilai `storage` adalah `ok`, `disabled` jika konfigurasi S3 belum lengkap (tidak memengaruhi `status`), atau `down` jika server gagal menulis lalu menghapus objek kecil di bawah `healthcheck/`. Hasil pemeriksaan penyimpanan dipakai ulang selama satu menit, sehingga kredensial yang tidak bisa menulis terlihat di sini dan tidak baru pada unggahan pertama.

---

### 4.7 Kelas, Video Pembelajaran dan Forum (`/classes`)

Semua rute di bagian ini memakai `classId` dari kelas yang diikuti pengguna. Guru yang mengajar kelas itu dan wali yang memiliki anak di kelas itu boleh membaca dan menulis, kecuali ada catatan lain. Pengguna lain menerima `403` `AUTH_FORBIDDEN`. Wali menemukan `classId` di `student.classId` pada `GET /wali/dashboard` dan guru menemukannya di `classOverview.classId` pada `GET /guru/dashboard`. Semua daftar di bagian ini berhalaman dan memuat `page`, `limit` dan `total`.

#### `GET /classes/{classId}/videos`
- **Deskripsi:** Video pembelajaran kelas, dari yang terbaru.
- **Response 200 OK:** `description`, `thumbnailUrl` dan `durationSeconds` bernilai `null` jika tidak diisi.
```json
{
  "success": true,
  "data": {
    "videos": [
      {
        "id": "3f2a9b1c-6d4e-4f70-8a12-9c0d1e2f3a4b",
        "title": "Mengenal Sampah Organik",
        "description": "Video pendek untuk anak TK.",
        "videoUrl": "https://videos.example.com/sampah-organik.mp4",
        "thumbnailUrl": "https://videos.example.com/sampah-organik.jpg",
        "durationSeconds": 125,
        "createdAt": "2026-10-08T09:00:00Z"
      }
    ],
    "page": 1,
    "limit": 20,
    "total": 1
  }
}
```

#### `POST /classes/{classId}/videos`
- **Deskripsi:** Guru kelas menambahkan video. Videonya berupa alamat https yang diputar aplikasi. Untuk berkas hasil rekaman guru, minta alamat unggah dulu lewat `POST /classes/{classId}/videos/upload-url`, kirim berkasnya, lalu tambahkan video dengan `videoUrl` dari jawaban itu. Berkasnya harus sudah sampai, milik kelas ini dan berupa video yang diterima. Berkas itu menunggu di folder sementara, dan saat video ditambahkan berkasnya disalin ke alamat permanen yang menjadi `videoUrl` pada video yang dikembalikan. Karena itu putar video dari alamat yang dikembalikan dan bukan dari alamat unggahan. Berkas sementara hanya dapat ditambahkan sekali. Alamat permanen boleh ditambahkan lagi untuk berbagi satu berkas antar video di kelas yang sama.
- **Request Body:** `title` dan `videoUrl` wajib. `videoUrl` dan `thumbnailUrl` harus alamat https tanpa spasi dan tanpa nama pengguna. `durationSeconds` bernilai 1 sampai 86400.
```json
{
  "title": "Mengenal Sampah Organik",
  "description": "Video pendek untuk anak TK.",
  "videoUrl": "https://videos.example.com/sampah-organik.mp4",
  "thumbnailUrl": "https://videos.example.com/sampah-organik.jpg",
  "durationSeconds": 125
}
```
- **Response 201 Created:** `message` bernilai `Learning video added` dan `data` berisi satu video seperti pada daftar di atas.
- **Kegagalan:** `400` `VALIDATION_ERROR` (termasuk berkas unggahan yang belum sampai, sudah pernah ditambahkan, milik kelas lain atau bukan video), `403` `AUTH_FORBIDDEN` untuk wali dan untuk guru yang tidak mengajar kelas itu, dan `502` `STORAGE_ERROR` jika bucket gagal dihubungi.

#### `POST /classes/{classId}/videos/upload-url`
- **Deskripsi:** Guru kelas meminta alamat bertanda tangan untuk mengunggah satu berkas video langsung ke bucket. Berkas besar tidak melewati API, sehingga batas ukuran permintaan tidak berlaku. Bucket menolak berkas yang ukuran atau jenisnya berbeda dari yang diumumkan. Berkas disimpan sementara di bawah `pending/` dan dihapus bucket setelah satu hari jika videonya tidak pernah ditambahkan. Guru yang membatalkan dapat menghapus berkas itu seketika dengan `DELETE` pada alamat yang sama.
- **Request Body:** `contentType` bernilai `video/mp4` atau `video/webm`, dan `sizeBytes` adalah ukuran persis berkas (paling besar `VIDEO_MAX_MB`, bawaan 100 MB).
```json
{
  "contentType": "video/mp4",
  "sizeBytes": 48211340
}
```
- **Response 200 OK:** `message` bernilai `Upload URL created`. Aplikasi mengirim `PUT` berisi berkas ke `uploadUrl` sebelum `expiresAt` (bawaan 15 menit), hanya dengan header dari `headers`, lalu menambahkan video dengan `videoUrl`, yaitu alamat sementara berkas itu. Video yang ditambahkan mengembalikan alamat permanennya.
```json
{
  "success": true,
  "message": "Upload URL created",
  "data": {
    "uploadUrl": "https://<bucket-endpoint>/pending/videos/a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d/0e9f8d7c.mp4?X-Amz-Signature=...",
    "method": "PUT",
    "headers": { "Content-Type": "video/mp4" },
    "videoUrl": "https://kibooz-media.syafahadyan.com/pending/videos/a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d/0e9f8d7c.mp4",
    "expiresAt": "2026-10-09T09:15:00Z"
  }
}
```
- **Kegagalan:** `400` `VALIDATION_ERROR` untuk jenis atau ukuran yang tidak valid, `403` `AUTH_FORBIDDEN`, `502` `STORAGE_ERROR` dan `503` `STORAGE_UNAVAILABLE` jika penyimpanan belum diatur.

#### `DELETE /classes/{classId}/videos/upload-url`
- **Deskripsi:** Guru kelas menghapus berkas yang sudah diunggah tetapi tidak jadi ditambahkan sebagai video, misalnya saat guru membatalkan formulir. Berkas langsung dihapus dari folder `pending/`, tanpa menunggu aturan bucket yang menghapusnya setelah satu hari. Berkas yang sudah hilang tetap dijawab 200, sehingga mengulang permintaan aman.
- **Query Params:** `videoUrl` (wajib), yaitu `videoUrl` dari jawaban `POST /classes/{classId}/videos/upload-url`. Nilainya perlu di-encode sebagai URL.
- **Response 200 OK:** `{ "success": true, "message": "Upload deleted" }`
- **Kegagalan:** `400` `VALIDATION_ERROR` jika `videoUrl` kosong atau bukan berkas sementara milik kelas itu, yaitu alamat kelas lain, video yang sudah ditambahkan, jenis berkas lain atau tautan ke situs lain, dan tidak ada yang dihapus. `401`, `403` `AUTH_FORBIDDEN` untuk wali dan untuk guru yang tidak mengajar kelas itu, dan `502` `STORAGE_ERROR` jika bucket gagal dihubungi.

#### `PATCH /classes/{classId}/videos/{videoId}`
- **Deskripsi:** Guru kelas mengubah rincian sebuah video. Guru mana pun di kelas itu boleh melakukannya. Kolom yang tidak dikirim dibiarkan apa adanya, `description` atau `thumbnailUrl` berisi teks kosong menghapus nilainya, dan `durationSeconds` bernilai 0 menghapus durasi. Alamat video tidak dapat diubah, sehingga berkas lain berarti video baru.
- **Request Body:** salah satu atau beberapa kolom. `title` tidak boleh kosong, dan `thumbnailUrl` harus alamat https.
```json
{
  "title": "Mengenal Sampah Organik (versi baru)",
  "description": "",
  "durationSeconds": 130
}
```
- **Response 200 OK:** `message` bernilai `Learning video updated` dan `data` berisi satu video seperti pada daftar video.
- **Kegagalan:** `400` `VALIDATION_ERROR` termasuk jika tidak ada kolom yang dikirim, `403` `AUTH_FORBIDDEN` untuk wali dan untuk guru yang tidak mengajar kelas itu, dan `404` `VIDEO_NOT_FOUND` jika video itu bukan milik kelas itu.

#### `DELETE /classes/{classId}/videos/{videoId}`
- **Deskripsi:** Guru kelas menghapus sebuah video. Jika videonya berkas yang diunggah dan tidak ada video lain yang memakai berkas yang sama, berkasnya ikut dihapus dari penyimpanan. Tautan ke tempat lain hanya dilupakan.
- **Response 200 OK:** `{ "success": true, "message": "Learning video deleted" }`
- **Kegagalan:** `400` `VALIDATION_ERROR`, `403` `AUTH_FORBIDDEN` dan `404` `VIDEO_NOT_FOUND`.

#### `GET /classes/{classId}/forum`
- **Deskripsi:** Utas forum kelas, dari yang terbaru. `author.id` adalah id akun penulis, sehingga aplikasi dapat menandai postingan sendiri dengan membandingkannya dengan id pengguna yang masuk. Akun yang sudah dihapus tampil dengan nama `Deleted account` tanpa foto. `editedAt` berisi waktu terakhir penulis mengubah judul atau isinya dan bernilai `null` untuk utas yang belum pernah diubah, sehingga aplikasi dapat menampilkan label "diedit". Balasan memiliki `editedAt` yang sama.
- **Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "threads": [
      {
        "id": "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
        "title": "Kegiatan kunjungan",
        "body": "Mohon anak membawa topi.",
        "author": {
          "id": "7d1e6c52-0a3b-4c8e-9f21-5b6a7c8d9e0f",
          "fullName": "Siti Rahayu, S.Pd.",
          "role": "GURU",
          "avatarUrl": null
        },
        "replyCount": 2,
        "createdAt": "2026-10-08T09:30:00Z",
        "editedAt": null
      }
    ],
    "page": 1,
    "limit": 20,
    "total": 1
  }
}
```

#### `POST /classes/{classId}/forum`
- **Deskripsi:** Memulai utas baru. Guru kelas dan wali dari anak di kelas itu sama-sama boleh menulis.
- **Request Body:** `title` (maksimal 150 karakter) dan `body` (maksimal 5000 karakter), keduanya wajib dan tidak boleh hanya berisi spasi.
```json
{
  "title": "Kegiatan kunjungan",
  "body": "Mohon anak membawa topi."
}
```
- **Response 201 Created:** `message` bernilai `Forum thread created` dan `data` berisi satu utas dengan `replyCount` 0.
- **Kegagalan:** `400` `VALIDATION_ERROR` dan `403` `AUTH_FORBIDDEN`.

#### `PATCH /classes/{classId}/forum/{postId}`
- **Deskripsi:** Pembuat sebuah utas mengubah judul atau isinya. Hanya pembuatnya yang boleh, guru kelas pun tidak. Kolom yang tidak dikirim dibiarkan apa adanya. `editedAt` terisi waktu perubahan hanya jika judul atau isinya benar-benar berbeda, sehingga simpan tanpa perubahan tidak menandai utas sebagai diedit.
- **Request Body:** `title` (maksimal 150 karakter) dan atau `body` (maksimal 5000 karakter), tidak boleh hanya berisi spasi, dan paling sedikit satu kolom.
```json
{
  "body": "Mohon anak membawa topi dan botol minum."
}
```
- **Response 200 OK:** `message` bernilai `Forum thread updated` dan `data` berisi satu utas seperti pada daftar utas.
- **Kegagalan:** `400` `VALIDATION_ERROR`, `403` `AUTH_FORBIDDEN` untuk selain pembuatnya dan untuk yang bukan anggota kelas, dan `404` `FORUM_POST_NOT_FOUND`.

#### `DELETE /classes/{classId}/forum/{postId}`
- **Deskripsi:** Pembuat utas atau guru kelas menghapus sebuah utas. Semua balasannya ikut terhapus.
- **Response 200 OK:** `{ "success": true, "message": "Forum thread deleted" }`
- **Kegagalan:** `400` `VALIDATION_ERROR`, `403` `AUTH_FORBIDDEN` untuk wali selain pembuatnya dan untuk yang bukan anggota kelas, dan `404` `FORUM_POST_NOT_FOUND`.

#### `GET /classes/{classId}/forum/{postId}/replies`
- **Deskripsi:** Balasan sebuah utas, dari yang paling lama. `postId` harus id sebuah utas di kelas itu.
- **Response 200 OK:**
```json
{
  "success": true,
  "data": {
    "replies": [
      {
        "id": "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f",
        "body": "Baik, akan saya siapkan.",
        "author": {
          "id": "5b6a7c8d-9e0f-4a1b-8c2d-3e4f5a6b7c8d",
          "fullName": "Sarah Kartika",
          "role": "WALI",
          "avatarUrl": null
        },
        "createdAt": "2026-10-08T09:45:00Z",
        "editedAt": null
      }
    ],
    "page": 1,
    "limit": 20,
    "total": 1
  }
}
```
- **Kegagalan:** `403` `AUTH_FORBIDDEN` dan `404` `FORUM_POST_NOT_FOUND` jika `postId` bukan utas di kelas itu, termasuk jika id itu milik sebuah balasan.

#### `POST /classes/{classId}/forum/{postId}/replies`
- **Deskripsi:** Membalas sebuah utas. Balasan tidak dapat dibalas lagi.
- **Request Body:** `{ "body": "Baik, akan saya siapkan." }` dengan `body` wajib (maksimal 5000 karakter).
- **Response 201 Created:** `message` bernilai `Forum reply added` dan `data` berisi satu balasan seperti pada daftar di atas.
- **Kegagalan:** `400` `VALIDATION_ERROR`, `403` `AUTH_FORBIDDEN` dan `404` `FORUM_POST_NOT_FOUND`.

#### `PATCH /classes/{classId}/forum/{postId}/replies/{replyId}`
- **Deskripsi:** Pembuat sebuah balasan mengubah isinya. `postId` adalah utas tempat balasan itu berada, dan hanya pembuatnya yang boleh mengubah. `editedAt` terisi waktu perubahan hanya jika isinya benar-benar berbeda.
- **Request Body:** `{ "body": "Baik, akan saya siapkan dua." }` dengan `body` wajib (maksimal 5000 karakter) dan tidak boleh hanya berisi spasi.
- **Response 200 OK:** `message` bernilai `Forum reply updated` dan `data` berisi satu balasan seperti pada daftar balasan.
- **Kegagalan:** `400` `VALIDATION_ERROR`, `403` `AUTH_FORBIDDEN` dan `404` `FORUM_POST_NOT_FOUND` jika balasan itu bukan milik utas dan kelas pada alamat tersebut.

#### `DELETE /classes/{classId}/forum/{postId}/replies/{replyId}`
- **Deskripsi:** Pembuat balasan atau guru kelas menghapus sebuah balasan.
- **Response 200 OK:** `{ "success": true, "message": "Forum reply deleted" }`
- **Kegagalan:** `400` `VALIDATION_ERROR`, `403` `AUTH_FORBIDDEN` dan `404` `FORUM_POST_NOT_FOUND`.

#### `GET /classes/{classId}/students`
- **Deskripsi:** Daftar anak di kelas untuk layar peserta kelas, diurutkan menurut nama. Hanya untuk guru kelas itu karena memuat NISN, sehingga wali menerima `403` `AUTH_FORBIDDEN`.
- **Response 200 OK:** `todayMood` adalah mood terbaru yang dicatat hari ini dan bernilai `null` jika belum ada.
```json
{
  "success": true,
  "data": {
    "students": [
      {
        "id": "c4b3a981-5502-4f32-8231-10c970a03bd7",
        "fullName": "Amelia Siti Zahra",
        "nisn": "3124567890",
        "avatarUrl": null,
        "currentPoints": 340,
        "seasonPoints": 120,
        "classRank": 2,
        "todayMood": "SENANG"
      }
    ],
    "page": 1,
    "limit": 20,
    "total": 1
  }
}
```

#### `GET /classes/{classId}/guidance-reports`
- **Deskripsi:** Panduan penanganan emosi yang diterapkan wali untuk anak di kelas ini, laporan terbaru di urutan pertama. Laporan dibuat ketika wali memanggil `POST /wali/guidance/apply`, sehingga guru tahu bahwa penanganan sedang berjalan. Hanya untuk guru kelas itu.
- **Response 200 OK:** `parentNotes` bernilai `null` jika wali tidak menulis catatan.
```json
{
  "success": true,
  "data": {
    "reports": [
      {
        "id": "5d8c3a2e-1f4b-4a6c-8e7d-9b0a1c2d3e4f",
        "studentId": "c4b3a981-5502-4f32-8231-10c970a03bd7",
        "studentName": "Amelia Siti Zahra",
        "guidanceId": "guidance-bingung-4step",
        "guidanceTitle": "Give Your Child a Sense of Safety",
        "category": "Confused / Unsure",
        "parentNotes": "Sudah diajak berbicara pelan di rumah, Amelia sudah tenang dan siap sekolah besok.",
        "appliedAt": "2026-10-04T18:30:00Z"
      }
    ],
    "page": 1,
    "limit": 20,
    "total": 1
  }
}
```
- **Kegagalan:** `400` `VALIDATION_ERROR`, `403` `AUTH_FORBIDDEN` untuk wali dan guru kelas lain, dan `401` tanpa token.

---

## 5. Daftar Tugas Tim Backend (Work Breakdown)

- [x] **BE-01:** Setup environment backend dan konfigurasi Docker Compose untuk PostgreSQL 15 & Redis 7.
- [x] **BE-02:** Migrasi skema database relasional (migrasi SQL tertanam di binary Go).
- [x] **BE-03:** Pembuatan modul autentikasi JWT (login multi-peran, registrasi, token refresh sekali pakai, logout, bcrypt password hash).
- [x] **BE-04:** Pembuatan endpoint Dashboard Wali Murid dan pemantauan mood harian anak.
- [x] **BE-05:** Pembuatan endpoint Dashboard Guru dan agregasi grafik analitik mood (Donut, Weekly Trend, Monthly Distribution).
- [x] **BE-06:** Pembuatan endpoint pencatatan mood anak (verifikasi peran guru dan validasi enum).
- [x] **BE-07:** Pembuatan endpoint transaksi pilah sampah, kalkulasi poin reward, dan limit 5x per hari.
- [x] **BE-08:** Optimasi query leaderboard terindeks dengan cache Redis opsional.
- [x] **BE-09:** Integrasi penyimpanan cloud (S3 / R2) untuk unggah avatar dan foto sampah.
- [x] **BE-10:** Pengujian otomatis dengan rangkaian uji Go (unit dan end to end) yang memeriksa setiap respons terhadap `openapi.yaml`.
- [x] **BE-11:** Pembuatan endpoint video pembelajaran kelas (daftar, tambah lewat alamat https, ubah dan hapus).
- [x] **BE-12:** Pembuatan forum diskusi kelas dengan utas dan balasan, termasuk mengubah, menghapus dan penanda `editedAt` pada postingan yang diubah.
- [x] **BE-13:** Pembuatan endpoint kelas guru (daftar, buat dan detail), daftar peserta kelas, profil guru, profil anak dan statistik sampah.
- [x] **BE-14:** Unggah berkas video ke penyimpanan cloud lewat alamat bertanda tangan, selain tautan https. Berkas menunggu di `pending/` dan disalin ke `videos/` saat video ditambahkan. Berkas yang tidak jadi ditambahkan dapat dihapus seketika dengan `DELETE /classes/{classId}/videos/upload-url`.
- [x] **BE-15:** Reset papan peringkat di awal semester (BR-03), otomatis pada bulan awal semester dengan poin semester terpisah dari poin seluruh semester.
- [x] **BE-16:** Daftar dan tambah anak untuk wali, daftar dan tambah guru untuk kelas, dan laporan panduan untuk guru.
- [x] **BE-17:** Pemeriksaan tulis penyimpanan pada `/healthz` dan antrean penghapusan berkas akun yang dihapus.
- [x] **BE-18:** Pemilik kelas, mengeluarkan guru dan keluar dari kelas, pengganti wajib saat satu-satunya guru menghapus akun, dan rute riwayat papan peringkat per semester.
