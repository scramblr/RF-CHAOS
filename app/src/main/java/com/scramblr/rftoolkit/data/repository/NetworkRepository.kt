package com.scramblr.rftoolkit.data.repository

import android.content.Context
import android.os.Environment
import com.opencsv.CSVWriter
import com.scramblr.rftoolkit.data.db.AppDatabase
import com.scramblr.rftoolkit.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository for all data operations
 */
class NetworkRepository(private val database: AppDatabase) {
    
    private val networkDao = database.networkDao()
    private val locationDao = database.locationDao()
    private val sessionDao = database.sessionDao()
    private val routeDao = database.routeDao()
    private val irkDao = database.irkDao()
    
    // ========================================================================
    // Networks
    // ========================================================================
    
    fun getNetworks(limit: Int = 100, offset: Int = 0): Flow<List<Network>> =
        networkDao.getNetworks(limit, offset)
    
    fun getNetworksBySignal(limit: Int = 100, offset: Int = 0): Flow<List<Network>> =
        networkDao.getNetworksBySignal(limit, offset)
    
    fun getNetworksByDateRange(startTime: Long, endTime: Long, limit: Int = 1000): Flow<List<Network>> =
        networkDao.getNetworksByDateRange(startTime, endTime, limit, 0)
    
    fun getNetworksByType(type: NetworkType): Flow<List<Network>> =
        networkDao.getNetworksByType(type)
    
    fun searchNetworks(query: String): Flow<List<Network>> =
        networkDao.searchNetworks(query)
    
    fun getNetworksWithLocation(): Flow<List<Network>> =
        networkDao.getNetworksWithLocation()
    
    suspend fun getNetworkByBssid(bssid: String): Network? =
        networkDao.getNetworkByBssid(bssid)
    
    /**
     * Insert or update a network observation
     */
    suspend fun upsertNetwork(
        bssid: String,
        ssid: String,
        type: NetworkType,
        rssi: Int,
        latitude: Double,
        longitude: Double,
        altitude: Double = 0.0,
        accuracy: Float = 0f,
        sessionId: String? = null,
        frequency: Int? = null,
        channel: Int? = null,
        capabilities: String? = null,
        security: SecurityType = SecurityType.UNKNOWN,
        bluetoothType: String? = null,
        deviceClass: Int? = null,
        serviceUuids: String? = null,
        manufacturerData: String? = null,
        txPower: Int? = null,
        isConnectable: Boolean = true,
        isRpa: Boolean = false,
        resolvedIrk: String? = null
    ) {
        val now = System.currentTimeMillis()
        val existing = networkDao.getNetworkByBssid(bssid)
        
        if (existing != null) {
            // Update existing network
            networkDao.updateObservation(bssid, rssi, latitude, longitude, now)
        } else {
            // Insert new network
            val network = Network(
                id = Network.generateId(),
                bssid = bssid,
                ssid = ssid,
                type = type,
                frequency = frequency,
                channel = channel,
                capabilities = capabilities,
                security = security,
                bluetoothType = bluetoothType,
                deviceClass = deviceClass,
                serviceUuids = serviceUuids,
                manufacturerData = manufacturerData,
                txPower = txPower,
                isConnectable = isConnectable,
                isRpa = isRpa,
                resolvedIrk = resolvedIrk,
                bestLevel = rssi,
                bestLat = latitude,
                bestLon = longitude,
                lastLat = latitude,
                lastLon = longitude,
                firstSeen = now,
                lastSeen = now
            )
            networkDao.insert(network)
        }
        
        // Always insert location observation
        val location = Location(
            id = Location.generateId(),
            networkId = existing?.id ?: bssid, // Use BSSID as fallback
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracy = accuracy,
            rssi = rssi,
            timestamp = now,
            sessionId = sessionId
        )
        locationDao.insert(location)
    }
    
    // ========================================================================
    // Sessions
    // ========================================================================
    
    fun getSessions(limit: Int = 50): Flow<List<Session>> =
        sessionDao.getSessions(limit)
    
    suspend fun startSession(notes: String? = null): String {
        val session = Session(
            id = Session.generateId(),
            startTime = System.currentTimeMillis(),
            notes = notes
        )
        sessionDao.insert(session)
        return session.id
    }
    
    suspend fun endSession(sessionId: String) {
        val totalNetworks = networkDao.getCount()
        val totalLocations = locationDao.getCount()
        sessionDao.endSession(
            id = sessionId,
            endTime = System.currentTimeMillis(),
            totalNetworks = totalNetworks,
            totalLocations = totalLocations
        )
    }
    
    // ========================================================================
    // Route
    // ========================================================================
    
    fun getRouteForSession(sessionId: String): Flow<List<RoutePoint>> =
        routeDao.getRouteForSession(sessionId)
    
    fun getAllRoutePoints(): Flow<List<RoutePoint>> =
        routeDao.getAllRoutePoints()
    
    fun getRouteByDateRange(startTime: Long, endTime: Long): Flow<List<RoutePoint>> =
        routeDao.getRouteByDateRange(startTime, endTime)
    
    suspend fun getRoutePointCount(): Int = routeDao.getCount()
    
    suspend fun addRoutePoint(
        sessionId: String,
        latitude: Double,
        longitude: Double,
        altitude: Double = 0.0,
        accuracy: Float = 0f,
        speed: Float? = null,
        bearing: Float? = null
    ) {
        val point = RoutePoint(
            id = RoutePoint.generateId(),
            sessionId = sessionId,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracy = accuracy,
            speed = speed,
            bearing = bearing,
            timestamp = System.currentTimeMillis()
        )
        routeDao.insert(point)
    }
    
    suspend fun saveRoutePoints(points: List<GeoPoint>) {
        val currentSessionId = "current_route"
        routeDao.deleteBySession(currentSessionId)
        val now = System.currentTimeMillis()
        points.forEachIndexed { index, geoPoint ->
            val point = RoutePoint(
                id = "${currentSessionId}_$index",
                sessionId = currentSessionId,
                latitude = geoPoint.latitude,
                longitude = geoPoint.longitude,
                altitude = 0.0,
                accuracy = 0f,
                speed = null,
                bearing = null,
                timestamp = now + index
            )
            routeDao.insert(point)
        }
    }
    
    suspend fun getRoutePoints(): List<GeoPoint> {
        return try {
            routeDao.getRouteForSession("current_route").first().map { 
                GeoPoint(it.latitude, it.longitude) 
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun clearRoutePoints() {
        routeDao.deleteBySession("current_route")
    }
    
    // ========================================================================
    // IRK
    // ========================================================================
    
    fun getAllIrks(): Flow<List<Irk>> = irkDao.getAllIrks()
    
    suspend fun getAllIrksList(): List<Irk> = irkDao.getAllIrksList()
    
    suspend fun addIrk(irk: String, name: String? = null, deviceType: String? = null) {
        val entry = Irk(
            id = Irk.generateId(),
            irk = irk.lowercase().replace("[:-]".toRegex(), ""),
            name = name,
            deviceType = deviceType,
            addedAt = System.currentTimeMillis()
        )
        irkDao.insert(entry)
    }
    
    suspend fun updateIrk(id: String, name: String?, deviceType: String?) {
        irkDao.update(id, name, deviceType)
    }
    
    suspend fun deleteIrk(id: String) = irkDao.deleteById(id)
    
    // ========================================================================
    // Statistics
    // ========================================================================
    
    suspend fun getStatistics(): Statistics {
        return Statistics(
            totalNetworks = networkDao.getCount(),
            wifiCount = networkDao.getCountByType(NetworkType.WIFI),
            bluetoothCount = networkDao.getCountByType(NetworkType.BLUETOOTH),
            bleCount = networkDao.getCountByType(NetworkType.BLE),
            cellularCount = networkDao.getCountByType(NetworkType.CELLULAR),
            totalLocations = locationDao.getCount(),
            sessionsCount = sessionDao.getCount(),
            lastScanTime = networkDao.getLastScanTime()
        )
    }
    
    suspend fun getObservationsCount(): Int = locationDao.getCount()
    
    // ========================================================================
    // Export
    // ========================================================================
    
    /**
     * Export to WiGLE-compatible CSV format
     */
    suspend fun exportToCsv(context: Context): File = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        val fileName = "rftoolkit_export_${dateFormat.format(Date())}.csv"
        
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        
        val networks = networkDao.getNetworks(Int.MAX_VALUE, 0).first()
        
        CSVWriter(FileWriter(file)).use { writer ->
            // WiGLE CSV header
            writer.writeNext(arrayOf(
                "MAC", "SSID", "AuthMode", "FirstSeen", "Channel", "RSSI",
                "CurrentLatitude", "CurrentLongitude", "AltitudeMeters", "AccuracyMeters", "Type"
            ))
            
            for (network in networks) {
                writer.writeNext(arrayOf(
                    network.bssid,
                    network.ssid,
                    network.security.name,
                    isoFormat.format(Date(network.firstSeen)),
                    (network.channel ?: 0).toString(),
                    network.bestLevel.toString(),
                    network.bestLat.toString(),
                    network.bestLon.toString(),
                    "0",
                    "0",
                    network.type.name
                ))
            }
        }
        
        file
    }
    
    // ========================================================================
    // Clear Data
    // ========================================================================
    
    suspend fun clearAllData() {
        locationDao.deleteAll()
        networkDao.deleteAll()
        sessionDao.deleteAll()
        routeDao.deleteAll()
    }
}

/**
 * Statistics data class
 */
data class Statistics(
    val totalNetworks: Int,
    val wifiCount: Int,
    val bluetoothCount: Int,
    val bleCount: Int,
    val cellularCount: Int,
    val totalLocations: Int,
    val sessionsCount: Int,
    val lastScanTime: Long?
)
