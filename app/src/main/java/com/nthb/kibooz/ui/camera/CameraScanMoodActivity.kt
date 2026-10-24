package com.nthb.kibooz.ui.camera

import android.content.Intent
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nthb.kibooz.BuildConfig
import com.nthb.kibooz.R
import com.nthb.kibooz.databinding.ActivityCameraScanMoodBinding
import com.nthb.kibooz.ml.FrameSize
import com.nthb.kibooz.ml.MoodAnalysisAnalyzer
import com.nthb.kibooz.ml.StubEmotionClassifier
import com.nthb.kibooz.ml.TrackedFace
import com.nthb.kibooz.model.ScannedStudentMood
import com.nthb.kibooz.ui.common.OverlayFace
import com.nthb.kibooz.ui.common.badgeText
import com.nthb.kibooz.util.InsetsUtil
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

class CameraScanMoodActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraScanMoodBinding
    private lateinit var cameraPermission: CameraPermissionHelper

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val session = MoodScanSession()
    private val studentSource: StudentSource = FakeStudentSource()

    private var camera: Camera? = null
    private var analyzer: MoodAnalysisAnalyzer? = null
    private var consentDialog: AlertDialog? = null
    private var consentAccepted = false
    private var isFlashOn = false

    private var lastFrame: FrameSize? = null
    private var lastFaces: List<TrackedFace> = emptyList()

    private val analysisListener = object : MoodAnalysisAnalyzer.Listener {
        // Dipanggil dari thread analyzer, jadi pindahkan ke thread utama sebelum menyentuh tampilan
        override fun onFaces(frame: FrameSize, faces: List<TrackedFace>) {
            runOnUiThread { if (!isDestroyed) showFaces(frame, faces) }
        }

        override fun onLowLight(isLow: Boolean) {
            runOnUiThread { if (!isDestroyed) binding.tvLowLight.isVisible = isLow }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pengklasifikasi dan daftar siswa di sini masih contoh, jadi hasilnya tidak boleh sampai ke pengguna sungguhan
        if (!BuildConfig.DEBUG) {
            Toast.makeText(this, R.string.mood_belum_tersedia, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding = ActivityCameraScanMoodBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopOverlay,
            bottomBar = binding.clBottomControls
        )

        consentAccepted = savedInstanceState?.getBoolean(STATE_CONSENT_ACCEPTED) ?: false
        binding.tvDetectionStatus.setText(R.string.mood_status_mencari)

        setupListeners()

        cameraPermission = CameraPermissionHelper(
            this,
            binding.layoutIzinKamera,
            R.string.izin_kamera_mood
        ) { granted ->
            binding.btnCaptureMood.isEnabled = granted
            binding.btnCaptureMood.alpha = if (granted) 1f else 0.4f
            if (granted && consentAccepted) startCamera()
        }

        if (consentAccepted) cameraPermission.start() else showConsent()
    }

    override fun onResume() {
        super.onResume()
        if (::cameraPermission.isInitialized) cameraPermission.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_CONSENT_ACCEPTED, consentAccepted)
    }

    override fun onDestroy() {
        consentDialog?.dismiss()

        // Tutup analyzer di thread-nya sendiri setelah frame yang sedang diproses selesai
        analyzer?.let { analysisExecutor.execute { it.close() } }
        analysisExecutor.shutdown()

        super.onDestroy()
    }

    private fun showConsent() {
        consentDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.konsen_mood_judul)
            .setMessage(R.string.konsen_mood_pesan)
            .setCancelable(false)
            .setPositiveButton(R.string.konsen_mood_mulai) { _, _ ->
                consentAccepted = true
                cameraPermission.start()
            }
            .setNegativeButton(R.string.konsen_batal) { _, _ -> finish() }
            .show()
    }

    private fun startCamera() {
        if (camera != null) return

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = try {
                providerFuture.get()
            } catch (e: ExecutionException) {
                showCameraError()
                return@addListener
            }

            // Preview dan analisis sama-sama 16:9 sekitar 720p, supaya kotak wajah sejajar dengan gambar
            val resolution = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                )
                .build()

            val preview = Preview.Builder().setResolutionSelector(resolution).build()
                .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }

            val moodAnalyzer = MoodAnalysisAnalyzer(StubEmotionClassifier(), analysisListener)
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { it.setAnalyzer(analysisExecutor, moodAnalyzer) }

            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                analyzer = moodAnalyzer
            } catch (e: IllegalStateException) {
                analysisExecutor.execute { moodAnalyzer.close() }
                showCameraError()
            } catch (e: IllegalArgumentException) {
                analysisExecutor.execute { moodAnalyzer.close() }
                showCameraError()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showCameraError() {
        Toast.makeText(this, R.string.mood_kamera_gagal, Toast.LENGTH_LONG).show()
    }

    private fun showFaces(frame: FrameSize, faces: List<TrackedFace>) {
        session.onFaces(faces.map { FaceReading(it.trackingId, it.emotion, FaceBox(it.box.left, it.box.top, it.box.right, it.box.bottom)) })
        lastFrame = frame
        lastFaces = faces
        render()
    }

    private fun render() {
        val frame = lastFrame ?: return

        binding.faceOverlay.setFaces(
            frame.width,
            frame.height,
            lastFaces.map {
                OverlayFace(it.trackingId, it.box, it.emotion?.badgeText(), session.studentAt(it.trackingId)?.name)
            }
        )

        binding.tvDetectionStatus.text = if (lastFaces.isEmpty()) {
            getString(R.string.mood_status_mencari)
        } else {
            getString(
                R.string.mood_status_wajah,
                lastFaces.size,
                lastFaces.count { session.studentAt(it.trackingId) != null }
            )
        }
    }

    private fun showStudentPicker(trackingId: Int) {
        val current = session.studentAt(trackingId)

        StudentPickerDialog.show(
            this,
            studentSource.students(),
            isTaken = { student -> session.isAssigned(student.id) && student.id != current?.id },
            onPick = { student ->
                if (!session.assign(trackingId, student)) {
                    Toast.makeText(this, R.string.mood_siswa_sudah_dipilih, Toast.LENGTH_SHORT).show()
                }
                render()
            }
        )
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.ivFlash.setOnClickListener {
            val activeCamera = camera
            if (activeCamera == null || !activeCamera.cameraInfo.hasFlashUnit()) {
                Toast.makeText(this, R.string.mood_senter_tidak_ada, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            isFlashOn = !isFlashOn
            activeCamera.cameraControl.enableTorch(isFlashOn)
        }

        binding.faceOverlay.onFaceTapped = { trackingId -> showStudentPicker(trackingId) }

        binding.btnCaptureMood.setOnClickListener {
            val intent = Intent(this, ReviewScanMoodActivity::class.java)
            val results = session.results()
            if (results.isNotEmpty()) {
                val scannedList = ArrayList(results.map { mood ->
                    ScannedStudentMood(
                        id = mood.student.id,
                        studentName = mood.student.name,
                        avatarRes = R.drawable.img_avatar_siswa,
                        currentMood = mood.emotion.emotion.name,
                        confidenceScore = mood.emotion.confidence,
                        teacherNotes = "",
                        isConfirmed = mood.confirmed
                    )
                })
                intent.putExtra(ReviewScanMoodActivity.EXTRA_STUDENTS, scannedList)
            }
            startActivity(intent)
            finish()
        }
    }

    private companion object {
        const val STATE_CONSENT_ACCEPTED = "consent_accepted"
    }
}
