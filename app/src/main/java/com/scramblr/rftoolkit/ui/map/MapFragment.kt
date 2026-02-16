package com.scramblr.rftoolkit.ui.map

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.scramblr.rftoolkit.R
import com.scramblr.rftoolkit.RFToolkitApp
import com.scramblr.rftoolkit.data.models.Network
import com.scramblr.rftoolkit.data.models.NetworkType
import com.scramblr.rftoolkit.data.repository.NetworkRepository
import com.scramblr.rftoolkit.databinding.FragmentMapBinding
import com.scramblr.rftoolkit.databinding.BottomSheetNetworkDetailBinding
import com.scramblr.rftoolkit.services.ScanningService
import com.scramblr.rftoolkit.utils.SignalUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.*

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: NetworkRepository
    
    // Location overlay
    private var locationOverlay: MyLocationNewOverlay? = null
    
    // Route tracking
    private var routePolyline: Polyline? = null
    private val routePoints = mutableListOf<GeoPoint>()
    private var lastRoutePoint: GeoPoint? = null
    private val MIN_ROUTE_DISTANCE = 5.0f // meters
    
    // Network markers
    private val networkMarkers = mutableMapOf<String, Marker>()
    private val markerNetworkMap = mutableMapOf<Marker, Network>()
    
    // Filter state
    private var showWifi = true
    private var showBluetooth = true
    private var showBle = true
    private var showCellular = true
    private var showRouteTrace = true
    
    // Scanning service
    private var scanningService: ScanningService? = null
    private var serviceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as ScanningService.LocalBinder
            scanningService = localBinder.getService()
            serviceBound = true
            observeLocation()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            scanningService = null
            serviceBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        repository = NetworkRepository(RFToolkitApp.instance.database)
        
        // Configure osmdroid
        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )
        Configuration.getInstance().userAgentValue = requireContext().packageName
        
        setupMap()
        setupFilterButtons()
    }
    
    private fun setupMap() {
        binding.mapView.apply {
            // Use OpenStreetMap tiles
            setTileSource(TileSourceFactory.MAPNIK)
            
            // Enable multi-touch zoom
            setMultiTouchControls(true)
            
            // Show zoom buttons
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            
            // Set default zoom and position (will be overridden when we get location)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(37.7749, -122.4194)) // Default to SF
            
            // Enable rotation gestures
            isVerticalMapRepetitionEnabled = false
            isHorizontalMapRepetitionEnabled = true
            
            // Dark theme tile invert filter
            overlayManager.tilesOverlay.setColorFilter(android.graphics.ColorMatrixColorFilter(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            ))
        }
        
        // Add my location overlay
        locationOverlay = MyLocationNewOverlay(
            GpsMyLocationProvider(requireContext()),
            binding.mapView
        ).apply {
            enableMyLocation()
            enableFollowLocation()
        }
        binding.mapView.overlays.add(locationOverlay)
        
        // Initialize route polyline
        routePolyline = Polyline().apply {
            outlinePaint.color = Color.parseColor("#00E676")
            outlinePaint.strokeWidth = 8f
            outlinePaint.isAntiAlias = true
        }
        binding.mapView.overlays.add(routePolyline)
        
        loadNetworks()
        loadSavedRoute()
    }
    
    override fun onStart() {
        super.onStart()
        Intent(requireContext(), ScanningService::class.java).also { intent ->
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        locationOverlay?.enableMyLocation()
    }
    
    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        locationOverlay?.disableMyLocation()
    }
    
    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            requireContext().unbindService(serviceConnection)
            serviceBound = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private fun setupFilterButtons() {
        binding.chipWifi.setOnCheckedChangeListener { _, isChecked ->
            showWifi = isChecked
            refreshMarkers()
        }
        
        binding.chipBluetooth.setOnCheckedChangeListener { _, isChecked ->
            showBluetooth = isChecked
            refreshMarkers()
        }
        
        binding.chipBle.setOnCheckedChangeListener { _, isChecked ->
            showBle = isChecked
            refreshMarkers()
        }
        
        binding.chipCellular.setOnCheckedChangeListener { _, isChecked ->
            showCellular = isChecked
            refreshMarkers()
        }
        
        binding.chipRoute.setOnCheckedChangeListener { _, isChecked ->
            showRouteTrace = isChecked
            routePolyline?.isVisible = isChecked
            binding.mapView.invalidate()
        }
        
        binding.btnClearRoute.setOnClickListener {
            clearRoute()
        }
        
        binding.btnCenterLocation.setOnClickListener {
            centerOnCurrentLocation()
        }
    }
    
    private fun observeLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                scanningService?.currentPosition?.collectLatest { location ->
                    location?.let { loc ->
                        val geoPoint = GeoPoint(loc.latitude, loc.longitude)
                        addRoutePoint(geoPoint)
                        
                        // Update stats
                        _binding?.let { b ->
                            b.tvRoutePoints.text = "${routePoints.size} points"
                            b.tvRouteDistance.text = String.format("%.2f km", calculateRouteDistance() / 1000)
                        }
                    }
                }
            }
        }
    }
    
    private fun addRoutePoint(point: GeoPoint) {
        // Only add if moved enough distance
        val shouldAdd = lastRoutePoint?.let { last ->
            last.distanceToAsDouble(point) >= MIN_ROUTE_DISTANCE
        } ?: true
        
        if (shouldAdd) {
            routePoints.add(point)
            lastRoutePoint = point
            
            // Update polyline
            routePolyline?.setPoints(routePoints)
            binding.mapView.invalidate()
            
            // Save periodically
            if (routePoints.size % 10 == 0) {
                saveRoute()
            }
        }
    }
    
    private fun calculateRouteDistance(): Float {
        if (routePoints.size < 2) return 0f
        
        var totalDistance = 0.0
        for (i in 1 until routePoints.size) {
            totalDistance += routePoints[i-1].distanceToAsDouble(routePoints[i])
        }
        return totalDistance.toFloat()
    }
    
    private fun clearRoute() {
        routePoints.clear()
        lastRoutePoint = null
        routePolyline?.setPoints(emptyList())
        binding.mapView.invalidate()
        
        _binding?.let { b ->
            b.tvRoutePoints.text = "0 points"
            b.tvRouteDistance.text = "0.00 km"
        }
        
        // Clear saved route
        viewLifecycleOwner.lifecycleScope.launch {
            repository.clearRoutePoints()
        }
        
        Toast.makeText(requireContext(), "Route cleared", Toast.LENGTH_SHORT).show()
    }
    
    private fun saveRoute() {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.saveRoutePoints(routePoints.toList())
        }
    }
    
    private fun loadSavedRoute() {
        viewLifecycleOwner.lifecycleScope.launch {
            val savedPoints = repository.getRoutePoints()
            if (savedPoints.isNotEmpty()) {
                routePoints.clear()
                routePoints.addAll(savedPoints)
                lastRoutePoint = routePoints.lastOrNull()
                routePolyline?.setPoints(routePoints)
                binding.mapView.invalidate()
                
                _binding?.let { b ->
                    b.tvRoutePoints.text = "${routePoints.size} points"
                    b.tvRouteDistance.text = String.format("%.2f km", calculateRouteDistance() / 1000)
                }
            }
        }
    }
    
    private fun loadNetworks() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getNetworksWithLocation().collectLatest { networks ->
                    updateMarkers(networks)
                    
                    // Update count display
                    _binding?.tvNetworkCount?.text = "${networks.size} networks"
                    
                    // Center on first load if we have networks
                    if (networks.isNotEmpty() && networkMarkers.isEmpty()) {
                        val firstWithLoc = networks.firstOrNull { it.bestLat != 0.0 && it.bestLon != 0.0 }
                        firstWithLoc?.let {
                            binding.mapView.controller.setCenter(GeoPoint(it.bestLat, it.bestLon))
                            binding.mapView.controller.setZoom(15.0)
                        }
                    }
                }
            }
        }
    }
    
    private fun updateMarkers(networks: List<Network>) {
        // Remove markers for networks no longer in list
        val currentBssids = networks.map { it.bssid }.toSet()
        networkMarkers.keys.filter { it !in currentBssids }.forEach { bssid ->
            networkMarkers.remove(bssid)?.let { marker ->
                markerNetworkMap.remove(marker)
                binding.mapView.overlays.remove(marker)
            }
        }
        
        // Add/update markers
        for (network in networks) {
            if (network.bestLat == 0.0 || network.bestLon == 0.0) continue
            if (!shouldShowNetwork(network)) continue
            
            val position = GeoPoint(network.bestLat, network.bestLon)
            
            val existingMarker = networkMarkers[network.bssid]
            if (existingMarker != null) {
                // Update position if changed
                if (existingMarker.position != position) {
                    existingMarker.position = position
                }
            } else {
                // Create new marker
                val marker = Marker(binding.mapView).apply {
                    this.position = position
                    title = network.ssid.ifEmpty { "(Hidden)" }
                    snippet = "${network.type.name} | ${network.bestLevel} dBm"
                    icon = getMarkerIcon(network)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    
                    setOnMarkerClickListener { m, _ ->
                        markerNetworkMap[m]?.let { net ->
                            showNetworkDetail(net)
                        }
                        true
                    }
                }
                
                binding.mapView.overlays.add(marker)
                networkMarkers[network.bssid] = marker
                markerNetworkMap[marker] = network
            }
        }
        
        binding.mapView.invalidate()
    }
    
    private fun refreshMarkers() {
        // Show/hide markers based on filter
        for ((_, marker) in networkMarkers) {
            val network = markerNetworkMap[marker] ?: continue
            if (shouldShowNetwork(network)) {
                if (marker !in binding.mapView.overlays) {
                    binding.mapView.overlays.add(marker)
                }
            } else {
                binding.mapView.overlays.remove(marker)
            }
        }
        binding.mapView.invalidate()
    }
    
    private fun shouldShowNetwork(network: Network): Boolean {
        return when (network.type) {
            NetworkType.WIFI -> showWifi
            NetworkType.BLUETOOTH -> showBluetooth
            NetworkType.BLE -> showBle
            NetworkType.CELLULAR -> showCellular
        }
    }
    
    private fun getMarkerIcon(network: Network): Drawable {
        val color = when (network.type) {
            NetworkType.WIFI -> {
                // Color by signal strength
                val quality = SignalUtils.rssiToQuality(network.bestLevel)
                when {
                    quality >= 80 -> Color.parseColor("#4CAF50") // Green
                    quality >= 60 -> Color.parseColor("#00BCD4") // Cyan
                    quality >= 40 -> Color.parseColor("#FFEB3B") // Yellow
                    quality >= 20 -> Color.parseColor("#FF9800") // Orange
                    else -> Color.parseColor("#F44336") // Red
                }
            }
            NetworkType.BLUETOOTH -> Color.parseColor("#2196F3") // Blue
            NetworkType.BLE -> Color.parseColor("#9C27B0") // Purple
            NetworkType.CELLULAR -> Color.parseColor("#E91E63") // Pink
        }
        
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_gps)!!.mutate()
        DrawableCompat.setTint(drawable, color)
        return drawable
    }
    
    private fun centerOnCurrentLocation() {
        locationOverlay?.myLocation?.let { loc ->
            binding.mapView.controller.animateTo(loc)
            binding.mapView.controller.setZoom(17.0)
        } ?: run {
            // Fallback to service location
            scanningService?.currentPosition?.value?.let { loc ->
                binding.mapView.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
                binding.mapView.controller.setZoom(17.0)
            }
        }
    }
    
    private fun showNetworkDetail(network: Network) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetNetworkDetailBinding.inflate(layoutInflater)
        
        sheetBinding.apply {
            tvSsid.text = network.ssid.ifEmpty { "(Hidden Network)" }
            tvBssid.text = network.bssid
            tvType.text = network.type.name
            tvSecurity.text = network.security.name
            tvRssi.text = "${network.bestLevel} dBm"
            tvChannel.text = network.channel?.toString() ?: "N/A"
            tvFrequency.text = if (network.frequency != null) "${network.frequency} MHz" else "N/A"
            
            val distance = SignalUtils.estimateDistance(network.bestLevel)
            tvDistance.text = String.format("~%.1f m", distance)
            
            val quality = SignalUtils.rssiToQuality(network.bestLevel)
            tvQuality.text = "$quality%"
            progressQuality.progress = quality
            
            tvFirstSeen.text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(Date(network.firstSeen))
            tvLastSeen.text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(Date(network.lastSeen))
            tvTimesObserved.text = network.timesObserved.toString()
            
            if (network.bestLat != 0.0 && network.bestLon != 0.0) {
                tvLocation.text = String.format("%.6f, %.6f", network.bestLat, network.bestLon)
            } else {
                tvLocation.text = "No location data"
            }
            
            tvManufacturer.text = network.manufacturer ?: "Unknown"
            
            // RPA indicator
            if (network.isRpa) {
                tvRpa.visibility = View.VISIBLE
                tvRpa.text = "RPA (Randomized Private Address)"
            } else {
                tvRpa.visibility = View.GONE
            }
            
            // Close button
            btnClose.setOnClickListener {
                dialog.dismiss()
            }
            
            // Navigate to location
            btnNavigate.setOnClickListener {
                if (network.bestLat != 0.0 && network.bestLon != 0.0) {
                    binding.mapView.controller.animateTo(GeoPoint(network.bestLat, network.bestLon))
                    binding.mapView.controller.setZoom(18.0)
                }
                dialog.dismiss()
            }
        }
        
        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }
}
