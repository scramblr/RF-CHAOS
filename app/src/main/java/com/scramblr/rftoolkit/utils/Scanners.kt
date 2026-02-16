package com.scramblr.rftoolkit.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult as WifiScanResult
import android.net.wifi.WifiManager
import android.os.Build
import com.scramblr.rftoolkit.data.models.NetworkType
import com.scramblr.rftoolkit.data.models.SecurityType
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * WiFi Scanner utility
 */
class WifiScanner(private val context: Context) {
    
    private val wifiManager: WifiManager = 
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    private var scanCallback: ((List<WifiNetwork>) -> Unit)? = null
    
    private val wifiReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                val results = wifiManager.scanResults
                val networks = results.map { parseWifiResult(it) }
                scanCallback?.invoke(networks)
            }
        }
    }
    
    fun startScan(callback: (List<WifiNetwork>) -> Unit) {
        scanCallback = callback
        context.registerReceiver(
            wifiReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
        
        @Suppress("DEPRECATION")
        wifiManager.startScan()
    }
    
    fun stopScan() {
        try {
            context.unregisterReceiver(wifiReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
        scanCallback = null
    }
    
    @SuppressLint("MissingPermission")
    fun getLastResults(): List<WifiNetwork> {
        return wifiManager.scanResults.map { parseWifiResult(it) }
    }
    
    private fun parseWifiResult(result: WifiScanResult): WifiNetwork {
        return WifiNetwork(
            bssid = result.BSSID,
            ssid = result.SSID ?: "",
            rssi = result.level,
            frequency = result.frequency,
            channel = frequencyToChannel(result.frequency),
            capabilities = result.capabilities,
            security = parseSecurityType(result.capabilities),
            isHidden = result.SSID.isNullOrEmpty()
        )
    }
    
    private fun frequencyToChannel(frequency: Int): Int {
        return when {
            frequency in 2412..2484 -> {
                if (frequency == 2484) 14 else (frequency - 2412) / 5 + 1
            }
            frequency in 5170..5825 -> (frequency - 5170) / 5 + 34
            frequency in 5935..7115 -> (frequency - 5935) / 5 + 1
            else -> 0
        }
    }
    
    private fun parseSecurityType(capabilities: String): SecurityType {
        val caps = capabilities.uppercase()
        return when {
            caps.contains("WPA3") -> SecurityType.WPA3
            caps.contains("WPA2-EAP") || caps.contains("RSN-EAP") -> SecurityType.WPA2_EAP
            caps.contains("WPA2") || caps.contains("RSN") -> SecurityType.WPA2
            caps.contains("WPA-EAP") -> SecurityType.WPA_EAP
            caps.contains("WPA") -> SecurityType.WPA
            caps.contains("WEP") -> SecurityType.WEP
            caps.contains("ESS") && !caps.contains("WPA") && !caps.contains("WEP") -> SecurityType.OPEN
            else -> SecurityType.UNKNOWN
        }
    }
}

data class WifiNetwork(
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val frequency: Int,
    val channel: Int,
    val capabilities: String,
    val security: SecurityType,
    val isHidden: Boolean
)

/**
 * Bluetooth/BLE Scanner utility
 */
@SuppressLint("MissingPermission")
class BluetoothScanner(private val context: Context) {
    
    private val bluetoothManager: BluetoothManager? = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private var bleCallback: ScanCallback? = null
    private var classicCallback: BroadcastReceiver? = null
    
    private val discoveredDevices = mutableMapOf<String, BleDevice>()
    
    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true
    
    /**
     * Start BLE scanning
     */
    fun startBleScan(
        irks: List<String> = emptyList(),
        callback: (List<BleDevice>) -> Unit
    ) {
        if (bleScanner == null) return
        
        discoveredDevices.clear()
        
        bleCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = parseBleResult(result, irks)
                discoveredDevices[device.address] = device
                callback(discoveredDevices.values.toList())
            }
            
            override fun onBatchScanResults(results: List<ScanResult>) {
                for (result in results) {
                    val device = parseBleResult(result, irks)
                    discoveredDevices[device.address] = device
                }
                callback(discoveredDevices.values.toList())
            }
        }
        
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        bleScanner.startScan(null, settings, bleCallback)
    }
    
    fun stopBleScan() {
        bleCallback?.let {
            bleScanner?.stopScan(it)
        }
        bleCallback = null
    }
    
    /**
     * Start classic Bluetooth discovery
     */
    fun startClassicScan(callback: (BluetoothDevice, Int) -> Unit) {
        if (bluetoothAdapter == null) return
        
        classicCallback = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        device?.let { callback(it, rssi) }
                    }
                }
            }
        }
        
        context.registerReceiver(
            classicCallback,
            IntentFilter(BluetoothDevice.ACTION_FOUND)
        )
        
        bluetoothAdapter.startDiscovery()
    }
    
    fun stopClassicScan() {
        bluetoothAdapter?.cancelDiscovery()
        classicCallback?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Not registered
            }
        }
        classicCallback = null
    }
    
    private fun parseBleResult(result: ScanResult, irks: List<String>): BleDevice {
        val address = result.device.address
        val isRpa = RpaResolver.isRpa(address)
        var resolvedIrk: String? = null
        
        // Try to resolve RPA against known IRKs
        if (isRpa) {
            for (irk in irks) {
                if (RpaResolver.resolveRpa(address, irk)) {
                    resolvedIrk = irk
                    break
                }
            }
        }
        
        val manufacturerData = result.scanRecord?.manufacturerSpecificData?.let { data ->
            if (data.size() > 0) {
                val key = data.keyAt(0)
                val value = data.valueAt(0)
                String.format("%04X", key) + value.toHexString()
            } else null
        }
        
        val serviceUuids = result.scanRecord?.serviceUuids?.map { it.toString() }
        
        return BleDevice(
            address = address,
            name = result.device.name ?: result.scanRecord?.deviceName ?: "",
            rssi = result.rssi,
            txPower = result.scanRecord?.txPowerLevel ?: -127,
            isConnectable = result.isConnectable,
            isRpa = isRpa,
            resolvedIrk = resolvedIrk,
            manufacturerData = manufacturerData,
            serviceUuids = serviceUuids
        )
    }
    
    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }
}

data class BleDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val txPower: Int,
    val isConnectable: Boolean,
    val isRpa: Boolean,
    val resolvedIrk: String?,
    val manufacturerData: String?,
    val serviceUuids: List<String>?
)

/**
 * RPA (Resolvable Private Address) Resolver
 * 
 * Implements the Bluetooth ah() function for resolving RPAs using IRKs
 * Based on Bluetooth Core Specification Vol 3, Part H, Section 2.2.2
 */
object RpaResolver {
    
    /**
     * Check if a Bluetooth address is a Resolvable Private Address
     * RPA: top 2 bits of first byte are 01 (0x40-0x7F)
     */
    fun isRpa(address: String): Boolean {
        val firstByte = address.replace(":", "").substring(0, 2).toInt(16)
        return (firstByte and 0xC0) == 0x40
    }
    
    /**
     * Parse IRK from hex string (supports various formats)
     */
    fun parseIrk(irk: String): ByteArray {
        val cleaned = irk.lowercase()
            .removePrefix("0x")
            .replace("[:-]".toRegex(), "")
        
        require(cleaned.length == 32) { "IRK must be 32 hex characters" }
        
        return cleaned.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
    
    /**
     * Resolve an RPA against an IRK
     * Returns true if the address resolves to this IRK
     */
    fun resolveRpa(address: String, irk: String): Boolean {
        if (!isRpa(address)) return false
        
        return try {
            val irkBytes = parseIrk(irk)
            val addressBytes = address.replace(":", "")
                .chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
            
            // Extract prand (first 3 bytes) and hash (last 3 bytes)
            val prand = addressBytes.copyOfRange(0, 3)
            val hash = addressBytes.copyOfRange(3, 6)
            
            // Compute expected hash using ah() function
            val computedHash = ah(irkBytes, prand)
            
            // Compare
            hash.contentEquals(computedHash)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Bluetooth ah() function
     * ah(k, r) = e(k, r') mod 2^24
     * Where r' = padding || r (128 bits total)
     */
    private fun ah(irk: ByteArray, prand: ByteArray): ByteArray {
        // Create 16-byte input: 13 bytes padding + 3 bytes prand
        val input = ByteArray(16)
        input[13] = prand[0]
        input[14] = prand[1]
        input[15] = prand[2]
        
        // AES-128-ECB encrypt
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        val keySpec = SecretKeySpec(irk, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encrypted = cipher.doFinal(input)
        
        // Return last 3 bytes
        return encrypted.copyOfRange(13, 16)
    }
}

/**
 * Signal strength utilities
 */
object SignalUtils {
    
    /**
     * Convert RSSI to quality percentage (0-100)
     */
    fun rssiToQuality(rssi: Int): Int {
        val min = -100
        val max = -30
        val clamped = rssi.coerceIn(min, max)
        return ((clamped - min) * 100 / (max - min))
    }
    
    /**
     * Get signal strength label
     */
    fun rssiToLabel(rssi: Int): String {
        return when {
            rssi >= -50 -> "Excellent"
            rssi >= -60 -> "Good"
            rssi >= -70 -> "Fair"
            rssi >= -80 -> "Weak"
            else -> "Poor"
        }
    }
    
    /**
     * Estimate distance from RSSI using log-distance path loss model
     */
    fun estimateDistance(rssi: Int, txPower: Int = -59, n: Double = 2.5): Double {
        if (rssi == 0) return -1.0
        return Math.pow(10.0, (txPower - rssi) / (10.0 * n))
    }
}
