# Pipeline Kecerdasan Buatan (AI/ML) dan Visi Komputer Kibooz

Panduan ini merinci arsitektur computer vision, model edge TensorFlow Lite (TFLite), dan integrasi CameraX untuk deteksi emosi anak serta klasifikasi sampah.

---

## 1. Prinsip Utama dan Privasi Data Anak

1. **Inferensi On-Device Penuh:** Prosesor perangkat (NPU/GPU/CPU) memproses semua frame video kamera langsung di tempat.
2. **Perlindungan Privasi Wajah (Zero-Cloud Face Streaming):** Rekaman video atau foto wajah anak mentah tidak pernah diunggah atau disimpan di server cloud. Yang dikirim ke backend hanyalah label emosi akhir (`SENANG`, `SEDIH`, `MARAH`, `BINGUNG`) beserta skor keyakinan (*confidence score*).
3. **Efisiensi Daya:** Pipeline kamera menggunakan resolusi optimal 720p (1280x720) dengan strategi `STRATEGY_KEEP_ONLY_LATEST` agar perangkat tidak mengalami *thermal throttling* atau pemborosan baterai.

---

## 2. Model 1: Deteksi Emosi Wajah Siswa (Face & Mood Recognition)

```mermaid
graph LR
    CameraFrame[CameraX ImageProxy 720p] --> FaceDetector[ML Kit Face Detection]
    FaceDetector -->|Bounding Box Wajah| CropScale[Crop & Resize ke 224x224 RGB]
    CropScale --> TFLiteModel[TFLite Model: MobileNetV3 Emotion Classifier]
    TFLiteModel --> Softmax[Probabilitas Emosi 4 Kelas]
    Softmax --> Evaluator{Confidence >= 0.75?}
    Evaluator -->|Ya| AutoLock[Kunci Emosi & Tampilkan Tag Mood]
    Evaluator -->|Tidak| ManualConfirm[Tampilkan Dialog Pilihan Konfirmasi Guru]
```

### 2.1 Spesifikasi Model Emosi
- **Arsitektur Dasar:** MobileNetV3-Small / EfficientNet-Lite0.
- **Ukuran Input:** Tensor 1 x 224 x 224 x 3 (Tipe data `FLOAT32` atau quantized `INT8`).
- **Kelas Keluaran (4 Kategori):**
  1. `SENANG` (Gembira, antusias, senyum lebar).
  2. `SEDIH` (Menangis, bibir melengkung ke bawah, murung).
  3. `MARAH` (Alis berkerut tajam, tegang).
  4. `BINGUNG` (Ragu, cemas, pandangan tidak fokus, perlu pendampingan).
- **Target Latensi:** < 65 milidetik per frame pada chipset Snapdragon seri 6 atau MediaTek Helio G-series.
- **Format Berkas:** `kibooz_emotion_v1.tflite` (Ukuran file < 8 MB) diletakkan di direktori `app/src/main/assets/`.

---

## 3. Model 2: Klasifikasi Jenis Sampah (Eco-Gamification)

```mermaid
graph LR
    TrashFrame[CameraX ImageProxy 720p] --> Preprocess[Normalisasi Tensor 320x320]
    Preprocess --> YOLOModel[YOLOv8-Nano / SSD-MobileNet TFLite]
    YOLOModel --> Detections[Bounding Box + Class ID + Score]
    Detections --> CategoryMapper[Pemetaan: Organik vs Anorganik vs B3]
    CategoryMapper --> BottomSheet[Tampilkan Kartu Identifikasi Sampah & Poin]
```

### 3.1 Spesifikasi Model Sampah
- **Arsitektur Dasar:** YOLOv8n (Nano) Object Detector atau SSD-MobileNetV2.
- **Ukuran Input:** Tensor 1 x 320 x 320 x 3 (`FLOAT32`).
- **Label Objek yang Dikenali:**
  - **Organik (10 Poin):** Sisa buah (pisang, apel), daun kering, sisa sayuran, kulit telur, sisa roti.
  - **Anorganik (15 Poin):** Botol plastik (PET), gelas plastik, kaleng aluminium, kemasan snack, sedotan, kardus/kertas.
  - **B3 (Peringatan Khusus):** Baterai bekas, botol pembersih kimia (tidak diberi poin, diarahkan ke kotak khusus).
- **Format Berkas:** `kibooz_trash_classifier_v1.tflite` (Ukuran file < 12 MB) di `app/src/main/assets/`.

---

## 4. Implementasi Blueprint CameraX Analyzer

Berikut contoh kode analyzer Kotlin penghubung CameraX dengan TensorFlow Lite:

```kotlin
class MoodAnalysisAnalyzer(
    private val context: Context,
    private val onMoodDetected: (moodType: String, confidence: Float, faceRect: Rect) -> Unit
) : ImageAnalysis.Analyzer {

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.2f)
            .build()
    )

    private val interpreter: Interpreter by lazy {
        val assetFileDescriptor = context.assets.openFd("kibooz_emotion_v1.tflite")
        val fileChannel = FileInputStream(assetFileDescriptor.fileDescriptor).channel
        val mappedBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
        Interpreter(mappedBuffer, Interpreter.Options().apply {
            setNumThreads(4)
            useNNAPI = true
        })
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (primaryFace != null) {
                    val (emotionLabel, confidence) = classifyFaceEmotion(mediaImage, primaryFace.boundingBox)
                    onMoodDetected(emotionLabel, confidence, primaryFace.boundingBox)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun classifyFaceEmotion(image: Image, bounds: Rect): Pair<String, Float> {
        // Logika crop, resize ke 224x224, konversi ke ByteBuffer, dan inferensi interpreter TFLite
        return Pair("SENANG", 0.92f)
    }
}
```

---

## 5. Penanganan Kondisi Khusus dan Kasus Ekstrem (*Edge Cases*)

| Skenario | Permasalahan | Solusi Sistem Kibooz |
| :--- | :--- | :--- |
| **Cahaya Rendah (Remang)** | Akurasi deteksi wajah menurun drastis. | Sistem mendeteksi rata-rata luminans di bawah ambang batas (Luma < 40) dan memunculkan tombol toggle lampu senter (*torch*) serta peringatan "Arahkan ke tempat terang". |
| **Banyak Wajah di Kelas** | Kamera mendeteksi 5-10 wajah siswa sekaligus. | Algoritma memilih wajah dengan luas *bounding box* terbesar yang berada paling dekat dengan titik tengah viewfinder (*primary subject prioritization*). |
| **Objek Bergerak Cepat** | Gambar blur menghasilkan prediksi acak. | Sistem menerapkan penyaringan kestabilan 3-frame berturut-turut (*temporal smoothing*). Hasil baru dikunci setelah prediksi konsisten selama minimal 300ms. |
| **Klaim Sampah Palsu** | Siswa memindai foto sampah dari layar HP lain. | Menerapkan pengecekan kilau layar reflektif (*moire effect*) dan mewajibkan pergerakan objek selama 1 detik sebelum tombol klaim terbuka. |

---

## 6. Rencana Kerja Tim AI/ML (Work Breakdown)

- [ ] **ML-01:** Kurasi dataset emosi anak (ekspresi senang, sedih, bingung, marah) dengan variasi pencahayaan Asia Tenggara.
- [ ] **ML-02:** Pelatihan model MobileNetV3 Emotion Classifier dengan transfer learning dan validasi akurasi minimal 88%.
- [ ] **ML-03:** Kuantisasi model ke TFLite INT8 dan pengujian latensi di Android Studio Profiler (<70ms).
- [ ] **ML-04:** Integrasi Google ML Kit Face Detection pada CameraX di [CameraScanMoodActivity.kt](../../app/src/main/java/com/nthb/kibooz/ui/camera/CameraScanMoodActivity.kt).
- [ ] **ML-05:** Kurasi dataset sampah sekolah (botol plastik, kotak susu, daun, sisa buah) dan pelatihan YOLOv8-nano.
- [ ] **ML-06:** Ekspor dan kuantisasi model deteksi sampah ke format TFLite dengan metadata input/output terdefinisi.
- [ ] **ML-07:** Integrasi deteksi sampah langsung dengan bounding box overlay di [CameraScanSampahActivity.kt](../../app/src/main/java/com/nthb/kibooz/ui/camera/CameraScanSampahActivity.kt).
- [ ] **ML-08:** Pembuatan dialog konfirmasi manual saat akurasi prediksi berada di bawah 75%.
