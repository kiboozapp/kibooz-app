# Model Pengganti Kamera (Placeholder)

Dua model TFLite pra-latih dipasang sebagai pengganti sementara sampai model Kibooz hasil pelatihan sendiri tersedia (ML-02 dan ML-05 di [04_AI_ML_AND_CAMERA_PIPELINE.md](prd_and_roadmap/04_AI_ML_AND_CAMERA_PIPELINE.md)). Alur kamera, pemilihan siswa, review, simpan ke server dan klaim poin sudah memakai model ini. **Hasilnya bukan hasil yang bisa diandalkan.** Layar kamera menampilkan penanda "Model contoh: hasil belum akurat" selama model pengganti terpasang.

## Model yang dipakai

| | Emosi wajah | Jenis sampah |
| --- | --- | --- |
| Berkas | `kibooz_emotion_v1.tflite` (1,5 MB) | `kibooz_trash_classifier_v1.tflite` (5,4 MB) |
| Sumber | [nxp/deepface-emotion-imx](https://huggingface.co/nxp/deepface-emotion-imx), berkas `original_model/emotion_uint8_float32.tflite` | [EfficientNet-Lite0 int8](https://storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/int8/latest/efficientnet_lite0.tflite) dari MediaPipe |
| Dasar | DeepFace (Serengil), dilatih pada FER2013, dikuantisasi NXP | EfficientNet-Lite0, dilatih pada ImageNet (1000 kelas) |
| Lisensi | MIT (Serengil 2019 dan NXP 2026) | Apache License 2.0 (tertulis di metadata berkas modelnya) |
| Masukan | piksel abu-abu 48x48, uint8 | RGB 224x224, uint8 |
| Keluaran | 7 probabilitas | 1000 probabilitas |
| SHA-256 | `a772b3ff2984da5bcc25f9494d475f368df3772ba697a950243025138acdb218` | `bc2ffe19c1118de0c0c2a9088992da5589722656e0fba81421385300a4a34b16` |

Teks lisensi dan sumbernya ikut di aplikasi: `app/src/main/assets/licenses/`. Kedua lisensi mewajibkan pemberitahuan hak cipta ikut disertakan, jadi berkas itu tidak boleh dihapus selama modelnya dipakai.

## Cara hasil model dipetakan ke API

Pemetaan ada di berkas `<nama model>.json` di `assets/`, bukan di kode. Keyakinan sebuah hasil adalah **jumlah probabilitas** label di kelompoknya.

**Emosi** (7 kelas model ke 4 nilai `moodType`):

| Label model | Nilai API |
| --- | --- |
| happy, neutral | `SENANG` |
| sad | `SEDIH` |
| angry, disgust | `MARAH` |
| fear, surprise | `BINGUNG` |

Wajah netral dibaca `SENANG`, sejalan dengan keputusan produk bahwa Kalem dikirim sebagai `SENANG`. Pemetaan ini sudah disetujui tim.

**Sampah** (ImageNet ke `trashType`): 36 label buah, sayur dan jamur menjadi `ORGANIK`, 29 label kemasan seperti botol, kantong, karton dan gelas menjadi `ANORGANIK`, dan 22 label seperti semprotan, korek api, telepon genggam dan perangkat elektronik menjadi `B3`. Daftar lengkapnya ada di `kibooz_trash_classifier_v1.json`. Label di luar daftar tidak dihitung, jadi benda yang tidak dikenal berkeyakinan rendah dan aturan BR-04 (di bawah 75%) meminta pilihan manual.

### Hasil uji

Diuji di luar Android dengan LiteRT dan berkas konfigurasi yang sama persis dengan yang dikirim, memakai foto berlisensi terbuka dari Wikimedia Commons (foto itu tidak disimpan di repositori).

| Masukan | Hasil | Gerbang 75% |
| --- | --- | --- |
| Wajah contoh dari kartu model | SENANG 0,996 | otomatis |
| Pisang | ORGANIK 0,97 | otomatis |
| Kulit pisang | ORGANIK 0,95 | otomatis |
| Apel | ORGANIK 0,66 | manual |
| Botol plastik | ANORGANIK 0,47 | manual |
| Gelas kertas | ANORGANIK 0,54 | manual |
| Baterai AA | B3 0,60 (kebetulan benar) | manual |
| Kaleng minuman | B3 0,46 (salah) | manual |

Hanya buah yang jelas yang diklaim otomatis. Benda lain jatuh ke dialog pilihan manual.

## Keterbatasan yang perlu diketahui

- **Akurasi emosi sekitar 57%** pada FER2013, yaitu data wajah dewasa. Pada anak kecil hampir pasti lebih rendah dan belum diukur.
- **Keyakinan emosi tidak terkalibrasi.** Model memberi "happy 0,996" bahkan pada gambar derau acak. Karena itu ambang 75% (BR-04) hampir tidak pernah menangkap kesalahannya, dan konfirmasi guru di layar review adalah satu-satunya pengaman nyata.
- **Pengenalan sampah kasar.** Model ImageNet tidak dilatih untuk sampah. Kaleng, kertas dan banyak benda lain tidak dikenali, dan B3 praktis hanya muncul karena kebetulan.
- **Hampir semua benda jatuh ke pilihan manual.** Di bawah 75% orang tua memilih sendiri organik, anorganik atau B3. Model pengganti jarang yakin pada benda selain buah, jadi pilihan manual inilah yang menentukan kategori sebagian besar klaim.
- **Ketentuan data ImageNet** untuk penggunaan komersial belum diperiksa. Periksa dengan pihak hukum sebelum model ini dirilis ke publik. Pemeriksaan hukum soal inferensi emosi pada anak juga masih terbuka.
- **Belum diuji di perangkat.** Pemuatan interpreter TFLite di Android hanya diuji lewat hitungan di JVM dan lewat padanannya di LiteRT. Jalankan layar kamera di ponsel sebelum percaya hasilnya.

## Gerbang rilis

Layar kamera mood dan sampah hanya terbuka jika modelnya bukan pengganti, atau build-nya debug, atau dibuat dengan `-Pkibooz.allowPlaceholderModels=true` untuk demo internal:

```
./gradlew assembleRelease -Pkibooz.allowPlaceholderModels=true
```

Tanpa itu build rilis menampilkan "belum tersedia" dan menutup layarnya. Build rilis biasa tidak membuka model pengganti.

APK yang dilampirkan pada rilis GitHub dibangun oleh `release-build.yaml` dengan flag tersebut, sehingga pemindainya terbuka dengan model pengganti. Hapus flag itu dari workflow kalau pemindai harus ditutup, atau setelah model sungguhan terpasang.

## Mengganti dengan model Kibooz sungguhan

1. Taruh berkas `.tflite` baru di `app/src/main/assets/` dengan nama yang sama (`kibooz_emotion_v1.tflite` atau `kibooz_trash_classifier_v1.tflite`).
2. Ubah berkas `.json` di sebelahnya: `colorMode`, `mean`, `std`, `centerCrop`, `labels` (atau `labelsFile`) dan `groups`. Untuk model yang langsung mengeluarkan empat emosi, `groups` cukup `{"SENANG": ["SENANG"], ...}`. Ukuran masukan, tipe data dan kuantisasi dibaca dari modelnya sendiri.
3. Set `"placeholder": false`. Penanda "Model contoh" hilang dan gerbang di atas terbuka sendiri.
4. Jalankan `./gradlew testDebugUnitTest`. `ModelConfigTest` memeriksa bahwa konfigurasi cocok dengan daftar labelnya, bahwa berkasnya TFLite yang sah, dan bahwa ukurannya masuk anggaran (di bawah 8 MB untuk emosi dan 12 MB untuk sampah).
5. Hapus berkas di `assets/licenses/` milik model yang diganti, dan uji di perangkat.

Memeriksa bentuk masukan dan keluaran sebuah model di luar Android:

```
pip install ai-edge-litert
python -c "from ai_edge_litert.interpreter import Interpreter as I; i=I(model_path='model.tflite'); i.allocate_tensors(); print(i.get_input_details()[0]['shape'], i.get_input_details()[0]['quantization'], i.get_output_details()[0]['shape'])"
```
