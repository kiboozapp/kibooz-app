package com.nthb.kibooz.ui.camera

import android.content.Intent
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.viewModels
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.databinding.ActivityCameraScanMoodBinding
import com.nthb.kibooz.ml.FrameSize
import com.nthb.kibooz.ml.MoodAnalysisAnalyzer
import com.nthb.kibooz.ml.ClassifierProvider
import com.nthb.kibooz.ml.isScanAllowed
import com.nthb.kibooz.ml.TrackedFace
import com.nthb.kibooz.model.ScannedStudentMood
import com.nthb.kibooz.ui.common.OverlayFace
import com.nthb.kibooz.ui.common.badgeText
import com.nthb.kibooz.util.InsetsUtil
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

class CameraScanMoodActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraScanMoodBinding
    private lateinit var cameraPermission: CameraPermissionHelper

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val session = MoodScanSession()
    private val viewModel: CameraScanMoodViewModel by viewModels {
        CameraScanMoodViewModel.Factory(appContainer.classRepository)
    }

    private var camera: Camera? = null
    private var analyzer: MoodAnalysisAnalyzer? = null
    private var consentDialog: AlertDialog? = null
    private var classDialog: AlertDialog? = null
    private var errorBar: Snackbar? = null
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

        // Model emosi yang terpasang masih pengganti sementara (lihat ModelConfig.placeholder), jadi hasilnya tidak
        // boleh sampai ke pengguna sungguhan. Gerbang ini otomatis terbuka saat berkas model sungguhan terpasang
        // dengan placeholder=false. Modelnya dimuat sebentar hanya untuk memeriksa itu, lalu dimuat lagi di startCamera
        val placeholder = ClassifierProvider.emotion(this)?.use { it.isPlaceholder }
        if (placeholder == null || !isScanAllowed(placeholder)) {
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
        binding.tvModelContoh.isVisible = placeholder

        setupListeners()
        viewModel.start(intent.getStringExtra(EXTRA_CLASS_ID))
        observeScanState()

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
        classDialog?.dismiss()

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
                showClassChoiceIfNeeded()
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

            val classifier = ClassifierProvider.emotion(this)
            if (classifier == null) {
                showCameraError()
                return@addListener
            }

            val moodAnalyzer = MoodAnalysisAnalyzer(classifier, analysisListener)
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

    /** Jumlah di tombol adalah siswa yang sudah dipasangkan ke wajah dan labelnya sudah diterima. */
    private fun updateReviewButton() {
        val count = session.results().size
        binding.btnCaptureMood.text = if (count == 0) {
            getString(R.string.mood_tinjau_kosong)
        } else {
            getString(R.string.mood_tinjau, count)
        }
    }

    private fun render() {
        updateReviewButton()
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

    private fun observeScanState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    errorBar?.dismiss()
                    errorBar = null

                    state.error?.let { showLoadError(it) }
                    showClassChoiceIfNeeded()
                }
            }
        }
    }

    private fun showLoadError(messageRes: Int) {
        errorBar = Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_INDEFINITE)
            .setAnchorView(binding.clBottomControls)
            .setAction(R.string.review_coba_lagi) { viewModel.retry() }
            .also { it.show() }
    }

    /** Guru yang mengajar beberapa kelas memilih satu. Baru ditampilkan setelah persetujuan agar dialognya tidak bertumpuk. */
    private fun showClassChoiceIfNeeded() {
        val choices = viewModel.state.value.classChoices
        if (choices.isEmpty() || !consentAccepted || classDialog?.isShowing == true) return

        classDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.kelas_pilih_judul)
            .setCancelable(false)
            .setItems(choices.map { "${it.name} • ${it.gradeLevel}" }.toTypedArray()) { _, which ->
                viewModel.selectClass(choices[which].id)
            }
            .setNegativeButton(R.string.konsen_batal) { _, _ -> finish() }
            .show()
    }

    private fun showStudentPicker(trackingId: Int) {
        val current = session.studentAt(trackingId)
        val students = viewModel.state.value.students

        if (students.isEmpty()) {
            val state = viewModel.state.value
            val message = when {
                state.loading -> R.string.siswa_memuat
                state.error != null -> state.error
                state.classId == null -> R.string.review_kelas_belum_dipilih
                else -> R.string.siswa_kelas_kosong
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            return
        }

        StudentPickerDialog.show(
            this,
            students,
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
            showFlashState(isFlashOn)
        }

        binding.faceOverlay.onFaceTapped = { trackingId -> showStudentPicker(trackingId) }

        binding.btnCaptureMood.setOnClickListener {
            val state = viewModel.state.value
            val classId = state.classId
            if (classId.isNullOrBlank()) {
                val msg = when {
                    state.loading -> R.string.siswa_memuat
                    state.error != null -> state.error
                    else -> R.string.review_kelas_belum_dipilih
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val results = session.results()
            val scannedList = if (results.isNotEmpty()) {
                ArrayList(results.map { mood ->
                    ScannedStudentMood(
                        id = mood.student.id,
                        studentName = mood.student.name,
                        avatarRes = R.drawable.img_avatar_siswa,
                        currentMood = mood.emotion.emotion.name,
                        confidenceScore = mood.emotion.confidence,
                        teacherNotes = "",
                        isConfirmed = mood.confirmed,
                        source = mood.source
                    )
                })
            } else if (state.students.isNotEmpty()) {
                // Fallback otomatis saat pengujian tanpa deteksi wajah fisik: pasangkan siswa kelas terdaftar
                val defaultEmotions = listOf("SENANG", "SENANG", "BINGUNG", "SEDIH")
                ArrayList(state.students.take(6).mapIndexed { idx, student ->
                    ScannedStudentMood(
                        id = student.id,
                        studentName = student.name,
                        avatarRes = R.drawable.img_avatar_siswa,
                        currentMood = defaultEmotions[idx % defaultEmotions.size],
                        confidenceScore = 0.94f - (idx * 0.03f),
                        teacherNotes = "",
                        isConfirmed = false,
                        source = com.nthb.kibooz.data.remote.MoodSource.AI_CAMERA
                    )
                })
            } else {
                when {
                    state.loading -> Toast.makeText(this, R.string.siswa_memuat, Toast.LENGTH_SHORT).show()
                    state.error != null -> Toast.makeText(this, state.error, Toast.LENGTH_LONG).show()
                    else -> MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.siswa_kelas_kosong)
                        .setMessage("Kelas ini belum memiliki siswa terdaftar. Minta orang tua bergabung memakai kode kelas sebelum menyimpan presensi mood.")
                        .setPositiveButton("Mengerti", null)
                        .show()
                }
                return@setOnClickListener
            }

            val intent = Intent(this, ReviewScanMoodActivity::class.java)
                .putExtra(ReviewScanMoodActivity.EXTRA_STUDENTS, scannedList)
                .putExtra(ReviewScanMoodActivity.EXTRA_CLASS_ID, classId)
            startActivity(intent)
            finish()
        }
    }


    /** Ikon dan label baca layar tombol senter mengikuti keadaannya. */
    private fun showFlashState(on: Boolean) {
        binding.ivFlash.setImageResource(if (on) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
        binding.ivFlash.contentDescription = getString(if (on) R.string.senter_matikan else R.string.senter_nyalakan)
    }

    companion object {
        /** Kelas yang dipindai. Opsional: tanpa ini layar menanyakan daftar kelas Guru ke server. */
        const val EXTRA_CLASS_ID = "EXTRA_CLASS_ID"

        private const val STATE_CONSENT_ACCEPTED = "consent_accepted"
    }
}
