package com.scramblr.rftoolkit.ui.signalfinder

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.scramblr.rftoolkit.R
import com.scramblr.rftoolkit.RFToolkitApp
import com.scramblr.rftoolkit.data.models.Irk
import com.scramblr.rftoolkit.data.repository.NetworkRepository
import com.scramblr.rftoolkit.databinding.FragmentSignalFinderBinding
import com.scramblr.rftoolkit.utils.RpaResolver
import com.scramblr.rftoolkit.utils.SignalUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class SignalFinderFragment : Fragment() {

    private var _binding: FragmentSignalFinderBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var repository: NetworkRepository
    
    private var isSearching = false
    private var searchJob: Job? = null
    private var targetMac: String = ""
    private var targetIrk: String? = null
    private var searchMode: SearchMode = SearchMode.BLUETOOTH
    
    private var wifiManager: WifiManager? = null
    private var bluetoothManager: BluetoothManager? = null
    private var vibrator: Vibrator? = null
    
    private var lastRssi: Int = -100
    private var lastSeenTime: Long = 0
    private var detectionCount: Int = 0
    
    private var storedIrks: List<Irk> = emptyList()
    
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            checkDevice(result)
        }
        
        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { checkDevice(it) }
        }
    }
    
    private fun checkDevice(result: ScanResult) {
        val deviceMac = result.device.address.uppercase(Locale.US)
        
        when (searchMode) {
            SearchMode.BLUETOOTH -> {
                if (deviceMac == targetMac) {
                    onTargetFound(result.rssi, deviceMac)
                }
            }
            SearchMode.IRK -> {
                // Check if this RPA resolves to our target IRK
                targetIrk?.let { irk ->
                    if (RpaResolver.isRpa(deviceMac) && RpaResolver.resolveRpa(deviceMac, irk)) {
                        onTargetFound(result.rssi, deviceMac)
                    }
                }
            }
            SearchMode.WIFI -> {
                // Handled separately
            }
        }
    }
    
    enum class SearchMode {
        WIFI, BLUETOOTH, IRK
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignalFinderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        repository = NetworkRepository(RFToolkitApp.instance.database)
        
        wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        
        setupUI()
        loadIrks()
        updateUIState()
    }

    override fun onDestroyView() {
        stopSearch()
        super.onDestroyView()
        _binding = null
    }
    
    private fun loadIrks() {
        viewLifecycleOwner.lifecycleScope.launch {
            storedIrks = repository.getAllIrks().first()
            updateIrkSpinner()
        }
    }
    
    private fun updateIrkSpinner() {
        val irkNames = storedIrks.map { it.name ?: it.irk.take(16) + "..." }
        if (irkNames.isNotEmpty()) {
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, irkNames)
            binding.spinnerIrk.adapter = adapter
            binding.layoutIrk.visibility = if (searchMode == SearchMode.IRK) View.VISIBLE else View.GONE
        } else {
            binding.layoutIrk.visibility = View.GONE
        }
    }

    private fun setupUI() {
        binding.toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                searchMode = when (checkedId) {
                    R.id.btnBluetooth -> SearchMode.BLUETOOTH
                    R.id.btnWifi -> SearchMode.WIFI
                    R.id.btnIrk -> SearchMode.IRK
                    else -> SearchMode.BLUETOOTH
                }
                updateModeUI()
            }
        }
        
        binding.btnStartStop.setOnClickListener {
            if (isSearching) {
                stopSearch()
            } else {
                startSearch()
            }
        }
        
        binding.btnClear.setOnClickListener {
            binding.etTargetMac.text?.clear()
            resetDisplay()
        }
        
        updateModeUI()
    }
    
    private fun updateModeUI() {
        when (searchMode) {
            SearchMode.BLUETOOTH -> {
                binding.tilTargetMac.hint = "Target BLE/Bluetooth MAC (e.g. AA:BB:CC:DD:EE:FF)"
                binding.layoutMac.visibility = View.VISIBLE
                binding.layoutIrk.visibility = View.GONE
            }
            SearchMode.WIFI -> {
                binding.tilTargetMac.hint = "Target WiFi BSSID (e.g. AA:BB:CC:DD:EE:FF)"
                binding.layoutMac.visibility = View.VISIBLE
                binding.layoutIrk.visibility = View.GONE
            }
            SearchMode.IRK -> {
                binding.layoutMac.visibility = View.GONE
                binding.layoutIrk.visibility = if (storedIrks.isNotEmpty()) View.VISIBLE else View.GONE
                if (storedIrks.isEmpty()) {
                    Toast.makeText(requireContext(), "No IRKs stored. Add IRKs in Settings.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun startSearch() {
        when (searchMode) {
            SearchMode.BLUETOOTH, SearchMode.WIFI -> {
                val input = binding.etTargetMac.text?.toString()?.trim()?.uppercase(Locale.US) ?: ""
                val macPattern = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
                if (!macPattern.matches(input)) {
                    Toast.makeText(requireContext(), "Invalid MAC address format. Use AA:BB:CC:DD:EE:FF", Toast.LENGTH_SHORT).show()
                    return
                }
                targetMac = input
                targetIrk = null
            }
            SearchMode.IRK -> {
                if (storedIrks.isEmpty()) {
                    Toast.makeText(requireContext(), "No IRKs available", Toast.LENGTH_SHORT).show()
                    return
                }
                val selectedIndex = binding.spinnerIrk.selectedItemPosition
                if (selectedIndex < 0 || selectedIndex >= storedIrks.size) {
                    Toast.makeText(requireContext(), "Select an IRK", Toast.LENGTH_SHORT).show()
                    return
                }
                targetIrk = storedIrks[selectedIndex].irk
                targetMac = ""
            }
        }
        
        isSearching = true
        detectionCount = 0
        lastRssi = -100
        lastSeenTime = 0
        
        updateUIState()
        
        when (searchMode) {
            SearchMode.BLUETOOTH, SearchMode.IRK -> startBluetoothScan()
            SearchMode.WIFI -> startWifiScan()
        }
        
        val targetDesc = when (searchMode) {
            SearchMode.IRK -> "IRK device"
            else -> targetMac
        }
        Toast.makeText(requireContext(), "Searching for $targetDesc...", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopSearch() {
        isSearching = false
        searchJob?.cancel()
        searchJob = null
        
        stopBluetoothScan()
        
        updateUIState()
    }
    
    private fun startBluetoothScan() {
        if (!hasBluetoothPermission()) {
            Toast.makeText(requireContext(), "Bluetooth permission required", Toast.LENGTH_SHORT).show()
            stopSearch()
            return
        }
        
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(requireContext(), "Bluetooth not available", Toast.LENGTH_SHORT).show()
            stopSearch()
            return
        }
        
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()
            
            scanner.startScan(null, settings, bleScanCallback)
            
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                while (isActive && isSearching) {
                    updateSignalDisplay()
                    delay(200)
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
            stopSearch()
        }
    }
    
    private fun stopBluetoothScan() {
        if (!hasBluetoothPermission()) return
        
        try {
            bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
        } catch (e: SecurityException) {
            // Ignore
        }
    }
    
    private fun startWifiScan() {
        if (!hasLocationPermission()) {
            Toast.makeText(requireContext(), "Location permission required for WiFi scanning", Toast.LENGTH_SHORT).show()
            stopSearch()
            return
        }
        
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive && isSearching) {
                scanWifi()
                delay(1000)
            }
        }
    }
    
    private fun scanWifi() {
        if (!hasLocationPermission()) return
        
        try {
            @Suppress("DEPRECATION")
            wifiManager?.startScan()
            
            @Suppress("DEPRECATION")
            val results = wifiManager?.scanResults ?: return
            
            for (result in results) {
                val bssid = result.BSSID?.uppercase(Locale.US) ?: continue
                if (bssid == targetMac) {
                    onTargetFound(result.level, bssid)
                    return
                }
            }
            
            updateSignalDisplay()
        } catch (e: SecurityException) {
            // Ignore
        }
    }
    
    private fun onTargetFound(rssi: Int, mac: String) {
        lastRssi = rssi
        lastSeenTime = System.currentTimeMillis()
        detectionCount++
        
        vibrateForSignal(rssi)
        
        activity?.runOnUiThread {
            updateSignalDisplay()
        }
    }
    
    private fun vibrateForSignal(rssi: Int) {
        val quality = SignalUtils.rssiToQuality(rssi)
        
        val duration = when {
            quality >= 80 -> 100L
            quality >= 60 -> 75L
            quality >= 40 -> 50L
            quality >= 20 -> 25L
            else -> 15L
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        } catch (e: Exception) {
            // Vibration not available
        }
    }
    
    private fun updateSignalDisplay() {
        _binding?.let { b ->
            val timeSinceLastSeen = System.currentTimeMillis() - lastSeenTime
            val isRecent = timeSinceLastSeen < 3000 && lastSeenTime > 0
            
            if (isRecent) {
                val quality = SignalUtils.rssiToQuality(lastRssi)
                val distance = SignalUtils.estimateDistance(lastRssi)
                
                b.tvRssiValue.text = "$lastRssi dBm"
                b.tvQualityValue.text = "$quality%"
                b.tvDistanceValue.text = String.format(Locale.US, "~%.1f m", distance)
                b.tvDetectionCount.text = "Detections: $detectionCount"
                b.tvLastSeen.text = "Last seen: ${timeSinceLastSeen / 1000}s ago"
                b.tvStatus.text = "TARGET FOUND"
                b.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
                
                val color = when {
                    quality >= 80 -> R.color.signal_excellent
                    quality >= 60 -> R.color.signal_good
                    quality >= 40 -> R.color.signal_fair
                    quality >= 20 -> R.color.signal_weak
                    else -> R.color.signal_poor
                }
                b.signalCircle.setColorFilter(ContextCompat.getColor(requireContext(), color))
                
                val scale = 0.5f + (quality / 200f)
                b.signalCircle.scaleX = scale
                b.signalCircle.scaleY = scale
            } else if (isSearching) {
                b.tvStatus.text = "SEARCHING..."
                b.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning))
                b.tvLastSeen.text = if (lastSeenTime > 0) "Last seen: ${timeSinceLastSeen / 1000}s ago" else "Not detected yet"
                
                b.signalCircle.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                b.signalCircle.scaleX = 0.5f
                b.signalCircle.scaleY = 0.5f
            }
        }
    }
    
    private fun resetDisplay() {
        _binding?.let { b ->
            b.tvRssiValue.text = "-- dBm"
            b.tvQualityValue.text = "--%"
            b.tvDistanceValue.text = "-- m"
            b.tvDetectionCount.text = "Detections: 0"
            b.tvLastSeen.text = ""
            b.tvStatus.text = "IDLE"
            b.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            b.signalCircle.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            b.signalCircle.scaleX = 0.5f
            b.signalCircle.scaleY = 0.5f
        }
        
        detectionCount = 0
        lastRssi = -100
        lastSeenTime = 0
    }
    
    private fun updateUIState() {
        _binding?.let { b ->
            b.btnStartStop.text = if (isSearching) "STOP SEARCH" else "START SEARCH"
            b.btnStartStop.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isSearching) R.color.error else R.color.primary
                )
            )
            
            b.etTargetMac.isEnabled = !isSearching
            b.toggleMode.isEnabled = !isSearching
            b.spinnerIrk.isEnabled = !isSearching
            
            if (!isSearching) {
                b.tvStatus.text = "IDLE"
                b.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            }
        }
    }
    
    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
