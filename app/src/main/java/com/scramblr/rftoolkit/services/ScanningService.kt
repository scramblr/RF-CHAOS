package com.scramblr.rftoolkit.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.scramblr.rftoolkit.MainActivity
import com.scramblr.rftoolkit.R
import com.scramblr.rftoolkit.RFToolkitApp
import com.scramblr.rftoolkit.data.models.NetworkType
import com.scramblr.rftoolkit.data.repository.NetworkRepository
import com.scramblr.rftoolkit.utils.BluetoothScanner
import com.scramblr.rftoolkit.utils.WifiScanner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Foreground service for continuous scanning
 */
class ScanningService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private lateinit var repository: NetworkRepository
    private lateinit var wifiScanner: WifiScanner
    private lateinit var bluetoothScanner: BluetoothScanner
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private var currentLocation: Location? = null
    private var currentSessionId: String? = null
    private var scanJob: Job? = null
    
    // State flows
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    
    private val _networkCount = MutableStateFlow(0)
    val networkCount: StateFlow<Int> = _networkCount
    
    private val _newNetworkCount = MutableStateFlow(0)
    val newNetworkCount: StateFlow<Int> = _newNetworkCount
    
    private val _currentPosition = MutableStateFlow<Location?>(null)
    val currentPosition: StateFlow<Location?> = _currentPosition
    
    // Settings
    var wifiEnabled = true
    var bleEnabled = true
    var classicBluetoothEnabled = false
    var logRoute = true
    var minRssi = -100
    
    private var knownIrks: List<String> = emptyList()
    
    inner class LocalBinder : Binder() {
        fun getService(): ScanningService = this@ScanningService
    }
    
    override fun onCreate() {
        super.onCreate()
        
        repository = NetworkRepository(RFToolkitApp.instance.database)
        wifiScanner = WifiScanner(this)
        bluetoothScanner = BluetoothScanner(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        createNotificationChannel()
        
        // Load IRKs
        serviceScope.launch {
            knownIrks = repository.getAllIrksList().map { it.irk }
        }
    }
    
    override fun onBind(intent: Intent): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startScanning()
            ACTION_STOP -> stopScanning()
        }
        return START_STICKY
    }
    
    override fun onDestroy() {
        stopScanning()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (_isScanning.value) return
        
        // Start foreground
        startForeground(NOTIFICATION_ID, createNotification())
        
        _isScanning.value = true
        _networkCount.value = 0
        _newNetworkCount.value = 0
        
        // Start session
        serviceScope.launch {
            currentSessionId = repository.startSession()
        }
        
        // Start location updates
        startLocationUpdates()
        
        // Start scan loop
        scanJob = serviceScope.launch {
            while (isActive && _isScanning.value) {
                performScanCycle()
                delay(2000) // Scan every 2 seconds
            }
        }
    }
    
    fun stopScanning() {
        _isScanning.value = false
        scanJob?.cancel()
        
        wifiScanner.stopScan()
        bluetoothScanner.stopBleScan()
        bluetoothScanner.stopClassicScan()
        stopLocationUpdates()
        
        // End session
        currentSessionId?.let { sessionId ->
            serviceScope.launch {
                repository.endSession(sessionId)
            }
        }
        currentSessionId = null
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    @SuppressLint("MissingPermission")
    private fun performScanCycle() {
        val location = currentLocation ?: return
        
        // WiFi Scan
        if (wifiEnabled && hasWifiPermission()) {
            val wifiNetworks = wifiScanner.getLastResults()
            
            serviceScope.launch {
                for (network in wifiNetworks) {
                    if (network.rssi >= minRssi) {
                        val existing = repository.getNetworkByBssid(network.bssid)
                        if (existing == null) {
                            _newNetworkCount.value++
                        }
                        
                        repository.upsertNetwork(
                            bssid = network.bssid,
                            ssid = network.ssid,
                            type = NetworkType.WIFI,
                            rssi = network.rssi,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            altitude = location.altitude,
                            accuracy = location.accuracy,
                            sessionId = currentSessionId,
                            frequency = network.frequency,
                            channel = network.channel,
                            capabilities = network.capabilities,
                            security = network.security
                        )
                        _networkCount.value++
                    }
                }
            }
            
            // Trigger new scan
            wifiScanner.startScan { }
        }
        
        // BLE Scan is continuous, handled by callback
        if (bleEnabled && hasBluetoothPermission()) {
            bluetoothScanner.startBleScan(knownIrks) { devices ->
                serviceScope.launch {
                    for (device in devices) {
                        if (device.rssi >= minRssi) {
                            val existing = repository.getNetworkByBssid(device.address)
                            if (existing == null) {
                                _newNetworkCount.value++
                            }
                            
                            repository.upsertNetwork(
                                bssid = device.address,
                                ssid = device.name,
                                type = NetworkType.BLE,
                                rssi = device.rssi,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                altitude = location.altitude,
                                accuracy = location.accuracy,
                                sessionId = currentSessionId,
                                txPower = device.txPower,
                                isConnectable = device.isConnectable,
                                isRpa = device.isRpa,
                                resolvedIrk = device.resolvedIrk,
                                manufacturerData = device.manufacturerData,
                                serviceUuids = device.serviceUuids?.joinToString(",")
                            )
                            _networkCount.value++
                        }
                    }
                }
            }
        }
        
        // Update notification
        updateNotification()
    }
    
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateDistanceMeters(5f)
            .build()
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }
    
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                currentLocation = location
                _currentPosition.value = location
                
                // Log route point
                if (logRoute && currentSessionId != null) {
                    serviceScope.launch {
                        repository.addRoutePoint(
                            sessionId = currentSessionId!!,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            altitude = location.altitude,
                            accuracy = location.accuracy,
                            speed = location.speed,
                            bearing = location.bearing
                        )
                    }
                }
            }
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }
    
    private fun hasWifiPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else hasLocationPermission()
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "RF Scanning",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows scanning status"
        }
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val stopIntent = Intent(this, ScanningService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RF Toolkit Scanning")
            .setContentText("Networks: ${_networkCount.value} | New: ${_newNetworkCount.value}")
            .setSmallIcon(R.drawable.ic_wifi)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification() {
        val notification = createNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
    
    companion object {
        const val CHANNEL_ID = "scanning_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.scramblr.rftoolkit.START_SCANNING"
        const val ACTION_STOP = "com.scramblr.rftoolkit.STOP_SCANNING"
    }
}
