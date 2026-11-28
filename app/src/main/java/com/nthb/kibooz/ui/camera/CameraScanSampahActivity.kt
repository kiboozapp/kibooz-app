package com.nthb.kibooz.ui.camera

import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
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
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.ml.ClassifierProvider
import com.nthb.kibooz.ml.TrashAnalyzer
import com.nthb.kibooz.ml.TrashCategory
import com.nthb.kibooz.ml.TrashResult
import com.nthb.kibooz.ml.isScanAllowed
import com.nthb.kibooz.ui.theme.KiboozTheme
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CameraScanSampahActivity : AppCompatActivity() {

    // Null sampai onCreate lolos gerbang model. Gerbang itu bisa menutup layar sebelum helper dibuat
    private var cameraPermission: CameraPermissionHelper? = null
    private val previewView by lazy { PreviewView(this).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    private val viewModel: TrashScanViewModel by viewModels {
        TrashScanViewModel.Factory(appContainer.classRepository, appContainer.trashRepository)
    }

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null
    private var analyzer: TrashAnalyzer? = null

    // Keadaan tampilan yang bukan milik ViewModel: izin, cahaya dan senter
    // Null selama belum diketahui, supaya panel izin tidak muncul di belakang dialog izin sistem, sama seperti layar XML
    private var permission by mutableStateOf<CameraPermissionState?>(null)
    private var lowLight by mutableStateOf(false)
    private var flashOn by mutableStateOf(false)

    private val snackbarHostState = SnackbarHostState()
    private var errorJob: Job? = null

    private var childDialog: AlertDialog? = null
    private var categoryDialog: AlertDialog? = null

    private val analysisListener = object : TrashAnalyzer.Listener {
        // Dipanggil dari thread analyzer, jadi pindahkan ke thread utama sebelum menyentuh tampilan
        override fun onResult(result: TrashResult?) {
            runOnUiThread { if (!isDestroyed) viewModel.onDetected(result) }
        }

        override fun onLowLight(isLow: Boolean) {
            runOnUiThread { if (!isDestroyed) lowLight = isLow }
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

        val permissionHelper = CameraPermissionHelper(this) { newState ->
            permission = newState
            if (newState == CameraPermissionState.GRANTED) startCamera()
        }
        cameraPermission = permissionHelper

        setContent {
            KiboozTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                CameraScanSampahScreen(
                    state = state,
                    permission = permission,
                    lowLight = lowLight,
                    placeholderModel = placeholder,
                    flashOn = flashOn,
                    snackbarHostState = snackbarHostState,
                    onBack = ::finish,
                    onFlash = ::toggleFlash,
                    onClaim = ::onClaimClicked,
                    onPermissionAction = permissionHelper::requestOrOpenSettings,
                    cameraPreview = { AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize()) }
                )
            }
        }

        viewModel.start(intent.getStringExtra(EXTRA_STUDENT_ID))
        observeEvents()
        permissionHelper.start()
    }

    override fun onResume() {
        super.onResume()
        cameraPermission?.onResume()
    }

    override fun onDestroy() {
        childDialog?.dismiss()
        categoryDialog?.dismiss()

        // Tutup analyzer di thread-nya sendiri setelah frame yang sedang diproses selesai
        analyzer?.let { analysisExecutor.execute { it.close() } }
        analysisExecutor.shutdown()

        super.onDestroy()
    }

    private fun toggleFlash() {
        val activeCamera = camera
        if (activeCamera == null || !activeCamera.cameraInfo.hasFlashUnit()) {
            Toast.makeText(this, R.string.mood_senter_tidak_ada, Toast.LENGTH_SHORT).show()
            return
        }

        flashOn = !flashOn
        activeCamera.cameraControl.enableTorch(flashOn)
    }

    private fun onClaimClicked() {
        if (viewModel.state.value.claimed != null) viewModel.scanAgain() else viewModel.claim()
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
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

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

    /** Dialog dan snackbar sekali tampil. Isi layarnya digambar oleh [CameraScanSampahScreen]. */
    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (state.claiming || state.claimed != null) dismissError()
                    showChildChoiceIfNeeded(state)
                    showCategoryChoiceIfNeeded(state)
                    state.error?.let { showError(it, state.canRetry, state.childId == null) }
                }
            }
        }
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

    /**
     * BR-04: di bawah 75% pengguna menentukan sendiri jenisnya, termasuk B3. Penjelasan ada di judul karena
     * AlertDialog tidak menampilkan daftar pilihan jika pesan juga diisi.
     */
    private fun showCategoryChoiceIfNeeded(state: TrashScanState) {
        if (!state.askCategory || categoryDialog?.isShowing == true) return

        val choices = MANUAL_TRASH_CHOICES
        val options = choices.map { getString(manualLabel(it)) }.toTypedArray()
        categoryDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sampah_manual_judul)
            .setItems(options) { _, which -> viewModel.claimManual(choices[which]) }
            .setNegativeButton(R.string.konsen_batal) { _, _ -> viewModel.dismissCategoryChoice() }
            .setOnCancelListener { viewModel.dismissCategoryChoice() }
            .show()
    }

    /**
     * Galat yang bisa diulang tetap tampil sampai pengguna menekan Coba lagi. Snackbar baru menggantikan yang lama,
     * karena SnackbarHostState mengantrekan snackbar dan snackbar tanpa batas waktu akan menahan antrean.
     */
    private fun showError(@StringRes messageRes: Int, canRetry: Boolean, childMissing: Boolean) {
        dismissError()
        val action = if (childMissing || canRetry) getString(R.string.review_coba_lagi) else null
        val message = getString(messageRes)

        errorJob = lifecycleScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = action,
                duration = if (action != null) SnackbarDuration.Indefinite else SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (childMissing) viewModel.retryChildren() else viewModel.claim()
            }
        }
        viewModel.errorShown()
    }

    private fun dismissError() {
        errorJob?.cancel()
        errorJob = null
    }

    companion object {
        /** Anak yang membuang sampah. Opsional: tanpa ini layar menanyakan daftar anak Wali ke server. */
        const val EXTRA_STUDENT_ID = "EXTRA_STUDENT_ID"
    }

    private fun manualLabel(category: TrashCategory): Int = when (category) {
        TrashCategory.ORGANIK -> R.string.sampah_organik
        TrashCategory.ANORGANIK -> R.string.sampah_anorganik
        TrashCategory.B3 -> R.string.sampah_pilihan_b3
    }
}
