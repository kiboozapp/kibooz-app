package com.nthb.kibooz.ui.camera

import android.graphics.Color
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.viewModels
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.databinding.ActivityCameraScanSampahBinding
import com.nthb.kibooz.ml.ClassifierProvider
import com.nthb.kibooz.ml.isScanAllowed
import com.nthb.kibooz.ml.TrashAnalyzer
import com.nthb.kibooz.ml.TrashCategory
import com.nthb.kibooz.ml.TrashResult
import com.nthb.kibooz.util.InsetsUtil
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

class CameraScanSampahActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraScanSampahBinding
    private lateinit var cameraPermission: CameraPermissionHelper

    private val viewModel: TrashScanViewModel by viewModels {
        TrashScanViewModel.Factory(appContainer.classRepository, appContainer.trashRepository)
    }

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null
    private var analyzer: TrashAnalyzer? = null
    private var cameraGranted = false
    private var isFlashOn = false

    private var childDialog: AlertDialog? = null
    private var categoryDialog: AlertDialog? = null
    private var errorBar: Snackbar? = null

    private val analysisListener = object : TrashAnalyzer.Listener {
        // Dipanggil dari thread analyzer, jadi pindahkan ke thread utama sebelum menyentuh tampilan
        override fun onResult(result: TrashResult?) {
            runOnUiThread { if (!isDestroyed) viewModel.onDetected(result) }
        }

        override fun onLowLight(isLow: Boolean) {
            runOnUiThread { if (!isDestroyed) binding.tvLowLight.isVisible = isLow }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Model sampah yang terpasang masih pengganti sementara (lihat ModelConfig.placeholder), jadi poin tidak boleh
        // diklaim dari hasilnya oleh pengguna sungguhan. Gerbang ini otomatis terbuka saat berkas model sungguhan
        // terpasang dengan placeholder=false. Modelnya dimuat sebentar hanya untuk memeriksa itu, lalu dimuat lagi di startCamera
        val placeholder = ClassifierProvider.trash(this)?.use { it.isPlaceholder }
        if (placeholder == null || !isScanAllowed(placeholder)) {
            Toast.makeText(this, R.string.sampah_klaim_belum_tersedia, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding = ActivityCameraScanSampahBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopOverlay,
            bottomBar = binding.clBottomControls
        )

        binding.tvModelContoh.isVisible = placeholder
        viewModel.start(intent.getStringExtra(EXTRA_STUDENT_ID))
        setupListeners()
        observeState()

        cameraPermission = CameraPermissionHelper(
            this,
            binding.layoutIzinKamera,
            R.string.izin_kamera_sampah
        ) { granted ->
            cameraGranted = granted
            render(viewModel.state.value)
            if (granted) startCamera()
        }
        cameraPermission.start()
    }

    override fun onResume() {
        super.onResume()
        if (::cameraPermission.isInitialized) cameraPermission.onResume()
    }

    override fun onDestroy() {
        childDialog?.dismiss()
        categoryDialog?.dismiss()

        // Tutup analyzer di thread-nya sendiri setelah frame yang sedang diproses selesai
        analyzer?.let { analysisExecutor.execute { it.close() } }
        analysisExecutor.shutdown()

        super.onDestroy()
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

        binding.btnClaimPoin.setOnClickListener {
            if (viewModel.state.value.claimed != null) viewModel.scanAgain() else viewModel.claim()
        }
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

            val resolution = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                )
                .build()

            val preview = Preview.Builder().setResolutionSelector(resolution).build()
                .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }

            val classifier = ClassifierProvider.trash(this)
            if (classifier == null) {
                showCameraError()
                return@addListener
            }

            val trashAnalyzer = TrashAnalyzer(classifier, analysisListener)
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { it.setAnalyzer(analysisExecutor, trashAnalyzer) }

            // Hanya Preview dan ImageAnalysis yang dipasang, tidak pernah ImageCapture, jadi tidak ada jalur menyimpan foto
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                analyzer = trashAnalyzer
            } catch (e: IllegalStateException) {
                analysisExecutor.execute { trashAnalyzer.close() }
                showCameraError()
            } catch (e: IllegalArgumentException) {
                analysisExecutor.execute { trashAnalyzer.close() }
                showCameraError()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showCameraError() {
        Toast.makeText(this, R.string.mood_kamera_gagal, Toast.LENGTH_LONG).show()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    render(state)
                    showChildChoiceIfNeeded(state)
                    showCategoryChoiceIfNeeded(state)
                    state.error?.let { showError(it, state.canRetry, state.childId == null) }
                }
            }
        }
    }

    private fun render(state: TrashScanState) {
        val detected = state.detected.takeIf { state.claimed == null }

        binding.llDeteksi.isVisible = detected != null
        if (detected != null) {
            binding.llDeteksi.setBackgroundColor(categoryColor(detected.category))
            binding.tvKategori.setText(categoryName(detected.category))
            binding.tvDetail.text = getString(R.string.sampah_akurasi, (detected.confidence * 100).toInt())
        }

        binding.tvPetunjuk.text = when {
            detected == null -> getString(R.string.sampah_mencari)
            detected.category == TrashCategory.B3 -> getString(R.string.sampah_petunjuk_b3)
            else -> getString(R.string.sampah_petunjuk, getString(shortName(detected.category)))
        }

        val claimed = state.claimed
        binding.llPoinHeader.isVisible = claimed != null
        if (claimed != null) {
            val points = if (claimed.pointsAdded > 0) {
                getString(R.string.sampah_poin_diterima, claimed.pointsAdded)
            } else {
                getString(R.string.sampah_tanpa_poin)
            }
            binding.tvPoin.text = getString(
                R.string.sampah_poin_ringkasan,
                points,
                getString(R.string.sampah_sisa_klaim, claimed.remainingDailyScans)
            )
        }

        val label = when {
            claimed != null -> R.string.sampah_klaim_lagi
            state.retryInSeconds != null -> R.string.batas_laju_menunggu
            state.claiming -> R.string.sampah_memproses
            else -> R.string.sampah_klaim_tombol
        }
        binding.btnClaimPoin.text = state.retryInSeconds?.let { getString(label, it) } ?: getString(label)
        val enabled = cameraGranted && !state.claiming && !state.loadingChildren
        binding.btnClaimPoin.isEnabled = enabled
        binding.btnClaimPoin.alpha = if (enabled) 1f else 0.4f

        if (state.claiming || claimed != null) {
            errorBar?.dismiss()
            errorBar = null
        }
    }

    private fun categoryName(category: TrashCategory) = when (category) {
        TrashCategory.ORGANIK -> R.string.sampah_kategori_organik
        TrashCategory.ANORGANIK -> R.string.sampah_kategori_anorganik
        TrashCategory.B3 -> R.string.sampah_kategori_b3
    }

    private fun shortName(category: TrashCategory) = when (category) {
        TrashCategory.ORGANIK -> R.string.sampah_organik
        TrashCategory.ANORGANIK -> R.string.sampah_anorganik
        TrashCategory.B3 -> R.string.sampah_kategori_b3
    }

    /** Warna hanya pelengkap, kategori selalu tertulis di lencana. */
    private fun categoryColor(category: TrashCategory) = when (category) {
        TrashCategory.ORGANIK -> Color.parseColor("#D92E7D32")
        TrashCategory.ANORGANIK -> Color.parseColor("#D91565C0")
        TrashCategory.B3 -> Color.parseColor("#D9C62828")
    }

    /** Wali yang punya beberapa anak memilih satu. */
    private fun showChildChoiceIfNeeded(state: TrashScanState) {
        val choices = state.childChoices
        if (choices.isEmpty() || childDialog?.isShowing == true) return

        childDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sampah_pilih_anak_judul)
            .setCancelable(false)
            .setItems(choices.map { it.fullName }.toTypedArray()) { _, which ->
                viewModel.selectChild(choices[which].id)
            }
            .setNegativeButton(R.string.konsen_batal) { _, _ -> finish() }
            .show()
    }

    /** BR-04: di bawah 75% pengguna menentukan sendiri organik atau anorganik. */
    private fun showCategoryChoiceIfNeeded(state: TrashScanState) {
        if (!state.askCategory || categoryDialog?.isShowing == true) return

        val options = arrayOf(getString(R.string.sampah_organik), getString(R.string.sampah_anorganik))
        categoryDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sampah_manual_judul)
            .setMessage(R.string.sampah_manual_pesan)
            .setItems(options) { _, which ->
                viewModel.claimManual(if (which == 0) TrashCategory.ORGANIK else TrashCategory.ANORGANIK)
            }
            .setNegativeButton(R.string.konsen_batal) { _, _ -> viewModel.dismissCategoryChoice() }
            .setOnCancelListener { viewModel.dismissCategoryChoice() }
            .show()
    }

    private fun showError(messageRes: Int, canRetry: Boolean, childMissing: Boolean) {
        errorBar?.dismiss()
        val bar = Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_INDEFINITE)
            .setAnchorView(binding.clBottomControls)

        when {
            childMissing -> bar.setAction(R.string.review_coba_lagi) { viewModel.retryChildren() }
            canRetry -> bar.setAction(R.string.review_coba_lagi) { viewModel.claim() }
            else -> bar.duration = Snackbar.LENGTH_LONG
        }

        errorBar = bar.also { it.show() }
        viewModel.errorShown()
    }

    companion object {
        /** Anak yang membuang sampah. Opsional: tanpa ini layar menanyakan daftar anak Wali ke server. */
        const val EXTRA_STUDENT_ID = "EXTRA_STUDENT_ID"
    }
}
