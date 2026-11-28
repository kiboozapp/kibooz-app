package com.nthb.kibooz.ui.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.nthb.kibooz.BuildConfig
import com.nthb.kibooz.R
import com.nthb.kibooz.databinding.ActivityPickLocationBinding
import com.nthb.kibooz.util.InsetsUtil
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay

/**
 * Pemilih lokasi interaktif berbasis OpenStreetMap (tanpa API key berbayar).
 * Pengguna dapat menggeser peta, mengetuk titik tertentu, mencari nama sekolah/jalan/kota,
 * atau memusatkan pin ke posisi GPS saat ini, lalu mengembalikan alamat hasil format lewat [EXTRA_ADDRESS].
 */
class PickLocationActivity : AppCompatActivity() {

    private val binding by lazy { ActivityPickLocationBinding.inflate(layoutInflater) }
    private lateinit var geocoder: LocationGeocoder

    private var currentLat = DEFAULT_LAT
    private var currentLon = DEFAULT_LON
    private var currentAddress: String = ""
    private var reverseJob: Job? = null
    private var searchJob: Job? = null
    private var gpsCancellation: CancellationSignal? = null
    private var warnedFallbackOnce = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingReverseRunnable = Runnable {
        val center = binding.mapView.mapCenter
        fetchAddressForPoint(center.latitude, center.longitude)
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                moveToCurrentLocation()
            } else {
                Toast.makeText(this, R.string.lokasi_izin_ditolak, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Konfigurasi cache internal sebelum inflating MapView agar berjalan tanpa izin penyimpanan eksternal (Android 10+)
        val osmConfig = Configuration.getInstance()
        osmConfig.load(applicationContext, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE))
        val osmBase = File(cacheDir, "osmdroid")
        osmConfig.osmdroidBasePath = osmBase
        osmConfig.osmdroidTileCache = File(osmBase, "tiles")
        osmConfig.userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME} (https://kibooz.syafahadyan.com)"

        setContentView(binding.root)

        applyLocationInsets()

        geocoder = LocationGeocoder(this)
        setupMap()
        setupListeners()
        resolveInitialPosition()
    }

    private fun applyLocationInsets() {
        InsetsUtil.ensureNavigationBarColor(binding.clRootPickLocation)
        val baseTopHeight = binding.clTopBar.layoutParams.height
        val baseTopPadding = binding.clTopBar.paddingTop
        val baseBottomPadding = binding.llAddressPreviewContent.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.clRootPickLocation) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            if (baseTopHeight > 0) {
                binding.clTopBar.updateLayoutParams {
                    height = baseTopHeight + statusBars.top
                }
            }
            binding.clTopBar.updatePadding(top = baseTopPadding + statusBars.top)
            binding.llAddressPreviewContent.updatePadding(bottom = baseBottomPadding + navBars.bottom)
            insets
        }
        binding.clRootPickLocation.requestApplyInsets()
    }

    private fun setupMap() {
        val map = binding.mapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setUseDataConnection(true)
        map.setMultiTouchControls(true)
        map.minZoomLevel = 4.0
        map.maxZoomLevel = 19.5
        map.controller.setZoom(16.0)
        map.controller.setCenter(GeoPoint(DEFAULT_LAT, DEFAULT_LON))

        val tapOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                hideSearchResults()
                hideKeyboard()
                map.controller.animateTo(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        map.overlays.add(tapOverlay)

        map.addMapListener(
            DelayedMapListener(
                object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        scheduleReverseAtCenter()
                        return true
                    }

                    override fun onZoom(event: ZoomEvent?): Boolean {
                        scheduleReverseAtCenter()
                        return true
                    }
                },
                350L
            )
        )
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        binding.btnSearchLocation.setOnClickListener {
            performSearch(binding.etSearchLocation.text.toString())
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchLocation.setText("")
            hideSearchResults()
        }

        binding.etSearchLocation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                binding.btnClearSearch.isVisible = !s.isNullOrEmpty()
            }
        })

        binding.etSearchLocation.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                performSearch(binding.etSearchLocation.text.toString())
                true
            } else {
                false
            }
        }

        binding.fabMyLocation.setOnClickListener {
            hideSearchResults()
            if (hasLocationPermission()) {
                moveToCurrentLocation()
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        binding.btnGunakanLokasi.setOnClickListener {
            val resolved = currentAddress.trim().ifEmpty {
                LocationAddressFormatter.formatCoordinates(currentLat, currentLon)
            }
            val result = Intent()
                .putExtra(EXTRA_ADDRESS, LocationAddressFormatter.clampAddress(resolved))
                .putExtra(EXTRA_LATITUDE, currentLat)
                .putExtra(EXTRA_LONGITUDE, currentLon)
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }

    private fun resolveInitialPosition() {
        val initialAddress = intent.getStringExtra(EXTRA_INITIAL_ADDRESS)?.trim().orEmpty()
        if (initialAddress.isNotEmpty()) {
            binding.etSearchLocation.setText(initialAddress)
            val parsedCoords = LocationAddressFormatter.parseCoordinates(initialAddress)
            if (parsedCoords != null) {
                focusMapPoint(parsedCoords.first, parsedCoords.second, zoom = 16.5)
                return
            }

            lifecycleScope.launch {
                binding.pbReverse.isVisible = true
                val matches = geocoder.search(initialAddress)
                val first = matches.firstOrNull()
                if (first != null) {
                    focusMapPoint(first.latitude, first.longitude, zoom = 16.5, prefilledAddress = first.address)
                } else if (hasLocationPermission()) {
                    moveToCurrentLocation()
                } else {
                    focusMapPoint(DEFAULT_LAT, DEFAULT_LON, zoom = 15.0)
                }
            }
            return
        }

        if (hasLocationPermission()) {
            moveToCurrentLocation()
        } else {
            focusMapPoint(DEFAULT_LAT, DEFAULT_LON, zoom = 15.0)
        }
    }

    private fun performSearch(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        hideKeyboard()

        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            binding.pbSearch.isVisible = true
            binding.btnSearchLocation.isEnabled = false
            try {
                val results = geocoder.search(clean)
                if (results.isEmpty()) {
                    hideSearchResults()
                    Toast.makeText(this@PickLocationActivity, R.string.lokasi_tidak_ditemukan, Toast.LENGTH_SHORT).show()
                } else if (results.size == 1) {
                    hideSearchResults()
                    val match = results.first()
                    focusMapPoint(match.latitude, match.longitude, zoom = 17.0, prefilledAddress = match.address)
                } else {
                    showSearchResults(results)
                }
            } finally {
                binding.pbSearch.isVisible = false
                binding.btnSearchLocation.isEnabled = true
            }
        }
    }

    private fun showSearchResults(results: List<LocationSearchCandidate>) {
        val container = binding.llSearchResults
        container.removeAllViews()
        val balooFont = ResourcesCompat.getFont(this, R.font.baloo2)

        results.forEachIndexed { index, candidate ->
            val itemView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_2, container, false)
            itemView.findViewById<TextView>(android.R.id.text1).apply {
                text = candidate.title
                typeface = balooFont
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@PickLocationActivity, R.color.kibooz_text_dark))
            }
            itemView.findViewById<TextView>(android.R.id.text2).apply {
                text = candidate.address
                typeface = balooFont
                textSize = 12f
                maxLines = 2
                setTextColor(ContextCompat.getColor(this@PickLocationActivity, R.color.kibooz_text_secondary))
            }
            itemView.setOnClickListener {
                hideSearchResults()
                binding.etSearchLocation.setText(candidate.title)
                focusMapPoint(candidate.latitude, candidate.longitude, zoom = 17.0, prefilledAddress = candidate.address)
            }
            container.addView(itemView)

            if (index < results.lastIndex) {
                val divider = View(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        (resources.displayMetrics.density).toInt().coerceAtLeast(1)
                    )
                    setBackgroundColor(ContextCompat.getColor(this@PickLocationActivity, R.color.kibooz_border))
                }
                container.addView(divider)
            }
        }
        binding.cardSearchResults.isVisible = true
    }

    private fun hideSearchResults() {
        binding.cardSearchResults.isVisible = false
    }

    private fun focusMapPoint(latitude: Double, longitude: Double, zoom: Double = 16.5, prefilledAddress: String? = null) {
        currentLat = latitude
        currentLon = longitude
        binding.tvCoordinates.text = getString(R.string.lokasi_koordinat_format, latitude, longitude)
        binding.mapView.controller.setZoom(zoom)
        binding.mapView.controller.animateTo(GeoPoint(latitude, longitude))

        if (!prefilledAddress.isNullOrBlank()) {
            reverseJob?.cancel()
            currentAddress = prefilledAddress
            binding.tvSelectedAddress.text = prefilledAddress
            binding.pbReverse.isVisible = false
        } else {
            fetchAddressForPoint(latitude, longitude)
        }
    }

    private fun scheduleReverseAtCenter() {
        mainHandler.removeCallbacks(pendingReverseRunnable)
        mainHandler.postDelayed(pendingReverseRunnable, 420L)
    }

    private fun fetchAddressForPoint(latitude: Double, longitude: Double) {
        currentLat = latitude
        currentLon = longitude
        binding.tvCoordinates.text = getString(R.string.lokasi_koordinat_format, latitude, longitude)

        reverseJob?.cancel()
        reverseJob = lifecycleScope.launch {
            binding.pbReverse.isVisible = true
            binding.tvSelectedAddress.setText(R.string.lokasi_memuat_alamat)
            val result = geocoder.reverse(latitude, longitude)
            currentAddress = result.address
            binding.tvSelectedAddress.text = result.address
            binding.pbReverse.isVisible = false

            if (result.isCoordinateFallback && !warnedFallbackOnce) {
                warnedFallbackOnce = true
                Toast.makeText(this@PickLocationActivity, R.string.lokasi_fallback_offline, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    private fun moveToCurrentLocation() {
        if (!hasLocationPermission()) return
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) -> LocationManager.PASSIVE_PROVIDER
            else -> null
        }

        // Tampilkan lokasi terakhir terlebih dahulu supaya peta terasa instan jika tersedia
        val cached = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

        if (cached != null) {
            focusMapPoint(cached.latitude, cached.longitude, zoom = 17.0)
        }

        if (provider == null) {
            if (cached == null) {
                Toast.makeText(this, R.string.lokasi_gps_tidak_aktif, Toast.LENGTH_LONG).show()
                fetchAddressForPoint(currentLat, currentLon)
            }
            return
        }

        gpsCancellation?.cancel()
        val signal = CancellationSignal()
        gpsCancellation = signal

        binding.pbReverse.isVisible = true
        LocationManagerCompat.getCurrentLocation(
            locationManager,
            provider,
            signal,
            ContextCompat.getMainExecutor(this)
        ) { fresh: Location? ->
            binding.pbReverse.isVisible = false
            if (fresh != null) {
                focusMapPoint(fresh.latitude, fresh.longitude, zoom = 17.0)
            } else if (cached == null) {
                Toast.makeText(this, R.string.lokasi_gps_tidak_aktif, Toast.LENGTH_LONG).show()
                fetchAddressForPoint(currentLat, currentLon)
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(binding.etSearchLocation.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(pendingReverseRunnable)
        gpsCancellation?.cancel()
        reverseJob?.cancel()
        searchJob?.cancel()
        binding.mapView.onDetach()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_INITIAL_ADDRESS = "extra_initial_address"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"

        // Titik awal default (Kota Malang) bila belum ada alamat atau izin lokasi
        private const val DEFAULT_LAT = -7.96662
        private const val DEFAULT_LON = 112.63263

        fun intent(context: Context, initialAddress: String? = null): Intent =
            Intent(context, PickLocationActivity::class.java).apply {
                if (!initialAddress.isNullOrBlank()) {
                    putExtra(EXTRA_INITIAL_ADDRESS, initialAddress.trim())
                }
            }
    }
}
