package com.scramblr.rftoolkit.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Network types supported by the scanner
 */
enum class NetworkType {
    WIFI,
    BLUETOOTH,
    BLE,
    CELLULAR
}

/**
 * WiFi security types
 */
enum class SecurityType {
    OPEN,
    WEP,
    WPA,
    WPA2,
    WPA3,
    WPA_EAP,
    WPA2_EAP,
    WPA3_EAP,
    UNKNOWN
}

/**
 * Main network entity - stores unique networks discovered
 * Schema compatible with WiGLE WiFi Wardriving
 */
@Entity(
    tableName = "network",
    indices = [
        Index(value = ["bssid"], unique = true),
        Index(value = ["type"]),
        Index(value = ["lastSeen"])
    ]
)
data class Network(
    @PrimaryKey
    val id: String,
    
    val bssid: String,              // MAC address
    val ssid: String,               // Network name or device name
    val type: NetworkType,
    
    // WiFi specific
    val frequency: Int? = null,     // MHz
    val channel: Int? = null,
    val capabilities: String? = null,
    val security: SecurityType = SecurityType.UNKNOWN,
    
    // Bluetooth specific
    val bluetoothType: String? = null,  // CLASSIC, LE, DUAL
    val deviceClass: Int? = null,
    val serviceUuids: String? = null,   // JSON array
    val manufacturerData: String? = null,
    val txPower: Int? = null,
    val isConnectable: Boolean = true,
    val isRpa: Boolean = false,         // Resolvable Private Address
    val resolvedIrk: String? = null,    // IRK that resolved this RPA
    
    // Cellular specific
    val cellType: String? = null,       // GSM, CDMA, LTE, 5G
    val mcc: Int? = null,
    val mnc: Int? = null,
    val lac: Int? = null,
    val cid: Int? = null,
    val operatorName: String? = null,
    
    // Location & Signal (best observation)
    val bestLevel: Int = -100,          // Best RSSI observed
    val bestLat: Double = 0.0,
    val bestLon: Double = 0.0,
    
    // Last known location
    val lastLat: Double = 0.0,
    val lastLon: Double = 0.0,
    
    // Timestamps
    val firstSeen: Long,
    val lastSeen: Long,
    
    // Observation tracking
    val timesObserved: Int = 1,
    
    // Derived/computed data
    val manufacturer: String? = null
) {
    companion object {
        fun generateId(): String = java.util.UUID.randomUUID().toString()
    }
}

/**
 * Location observation - stores each individual observation with GPS
 */
@Entity(
    tableName = "location",
    indices = [
        Index(value = ["networkId"]),
        Index(value = ["sessionId"]),
        Index(value = ["timestamp"])
    ]
)
data class Location(
    @PrimaryKey
    val id: String,
    
    val networkId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val rssi: Int,
    val timestamp: Long,
    val sessionId: String? = null
) {
    companion object {
        fun generateId(): String = java.util.UUID.randomUUID().toString()
    }
}

/**
 * Scanning session - tracks a scanning trip
 */
@Entity(
    tableName = "session",
    indices = [Index(value = ["startTime"])]
)
data class Session(
    @PrimaryKey
    val id: String,
    
    val startTime: Long,
    val endTime: Long? = null,
    val totalNetworks: Int = 0,
    val newNetworks: Int = 0,
    val totalLocations: Int = 0,
    val distance: Float = 0f,
    val notes: String? = null
) {
    companion object {
        fun generateId(): String = java.util.UUID.randomUUID().toString()
    }
}

/**
 * GPS route point for track logging
 */
@Entity(
    tableName = "route",
    indices = [Index(value = ["sessionId"])]
)
data class RoutePoint(
    @PrimaryKey
    val id: String,
    
    val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float? = null,
    val bearing: Float? = null,
    val timestamp: Long
) {
    companion object {
        fun generateId(): String = java.util.UUID.randomUUID().toString()
    }
}

/**
 * Identity Resolving Key for BLE RPA resolution
 */
@Entity(
    tableName = "irk",
    indices = [Index(value = ["irk"], unique = true)]
)
data class Irk(
    @PrimaryKey
    val id: String,
    
    val irk: String,            // 32 hex characters
    val name: String? = null,
    val deviceType: String? = null,
    val addedAt: Long,
    val timesResolved: Int = 0
) {
    companion object {
        fun generateId(): String = java.util.UUID.randomUUID().toString()
    }
}
