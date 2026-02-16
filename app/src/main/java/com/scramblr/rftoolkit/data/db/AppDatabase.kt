package com.scramblr.rftoolkit.data.db

import android.content.Context
import androidx.room.*
import com.scramblr.rftoolkit.data.models.*
import kotlinx.coroutines.flow.Flow

/**
 * Type converters for Room
 */
class Converters {
    @TypeConverter
    fun fromNetworkType(value: NetworkType): String = value.name
    
    @TypeConverter
    fun toNetworkType(value: String): NetworkType = NetworkType.valueOf(value)
    
    @TypeConverter
    fun fromSecurityType(value: SecurityType): String = value.name
    
    @TypeConverter
    fun toSecurityType(value: String): SecurityType = SecurityType.valueOf(value)
}

/**
 * Network DAO
 */
@Dao
interface NetworkDao {
    
    @Query("SELECT * FROM network ORDER BY lastSeen DESC LIMIT :limit OFFSET :offset")
    fun getNetworks(limit: Int = 100, offset: Int = 0): Flow<List<Network>>
    
    @Query("SELECT * FROM network ORDER BY bestLevel DESC LIMIT :limit OFFSET :offset")
    fun getNetworksBySignal(limit: Int = 100, offset: Int = 0): Flow<List<Network>>
    
    @Query("SELECT * FROM network WHERE lastSeen BETWEEN :startTime AND :endTime ORDER BY lastSeen DESC LIMIT :limit OFFSET :offset")
    fun getNetworksByDateRange(startTime: Long, endTime: Long, limit: Int = 1000, offset: Int = 0): Flow<List<Network>>
    
    @Query("SELECT * FROM network WHERE type = :type ORDER BY lastSeen DESC")
    fun getNetworksByType(type: NetworkType): Flow<List<Network>>
    
    @Query("SELECT * FROM network WHERE bssid = :bssid LIMIT 1")
    suspend fun getNetworkByBssid(bssid: String): Network?
    
    @Query("SELECT * FROM network WHERE ssid LIKE '%' || :query || '%' OR bssid LIKE '%' || :query || '%' ORDER BY lastSeen DESC")
    fun searchNetworks(query: String): Flow<List<Network>>
    
    @Query("SELECT * FROM network WHERE bestLat != 0.0 AND bestLon != 0.0 ORDER BY lastSeen DESC")
    fun getNetworksWithLocation(): Flow<List<Network>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(network: Network)
    
    @Update
    suspend fun update(network: Network)
    
    @Query("UPDATE network SET lastSeen = :lastSeen, lastLat = :lat, lastLon = :lon, timesObserved = timesObserved + 1, bestLevel = CASE WHEN :rssi > bestLevel THEN :rssi ELSE bestLevel END, bestLat = CASE WHEN :rssi > bestLevel THEN :lat ELSE bestLat END, bestLon = CASE WHEN :rssi > bestLevel THEN :lon ELSE bestLon END WHERE bssid = :bssid")
    suspend fun updateObservation(bssid: String, rssi: Int, lat: Double, lon: Double, lastSeen: Long)
    
    @Query("SELECT COUNT(*) FROM network")
    suspend fun getCount(): Int
    
    @Query("SELECT COUNT(*) FROM network WHERE type = :type")
    suspend fun getCountByType(type: NetworkType): Int
    
    @Query("SELECT MAX(lastSeen) FROM network")
    suspend fun getLastScanTime(): Long?
    
    @Query("DELETE FROM network")
    suspend fun deleteAll()
    
    @Query("DELETE FROM network WHERE lastSeen < :before")
    suspend fun deleteOlderThan(before: Long): Int
}

/**
 * Location DAO
 */
@Dao
interface LocationDao {
    
    @Query("SELECT * FROM location WHERE networkId = :networkId ORDER BY timestamp DESC LIMIT :limit")
    fun getLocationsForNetwork(networkId: String, limit: Int = 100): Flow<List<Location>>
    
    @Query("SELECT * FROM location WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getLocationsForSession(sessionId: String): Flow<List<Location>>
    
    @Query("SELECT * FROM location WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getLocationsByDateRange(startTime: Long, endTime: Long): Flow<List<Location>>
    
    @Insert
    suspend fun insert(location: Location)
    
    @Query("SELECT COUNT(*) FROM location")
    suspend fun getCount(): Int
    
    @Query("SELECT COUNT(*) FROM location WHERE timestamp > :since")
    suspend fun getCountSince(since: Long): Int
    
    @Query("DELETE FROM location")
    suspend fun deleteAll()
}

/**
 * Session DAO
 */
@Dao
interface SessionDao {
    
    @Query("SELECT * FROM session ORDER BY startTime DESC LIMIT :limit")
    fun getSessions(limit: Int = 50): Flow<List<Session>>
    
    @Query("SELECT * FROM session WHERE id = :id LIMIT 1")
    suspend fun getSession(id: String): Session?
    
    @Insert
    suspend fun insert(session: Session)
    
    @Update
    suspend fun update(session: Session)
    
    @Query("UPDATE session SET endTime = :endTime, totalNetworks = :totalNetworks, totalLocations = :totalLocations WHERE id = :id")
    suspend fun endSession(id: String, endTime: Long, totalNetworks: Int, totalLocations: Int)
    
    @Query("SELECT COUNT(*) FROM session")
    suspend fun getCount(): Int
    
    @Query("DELETE FROM session")
    suspend fun deleteAll()
}

/**
 * Route DAO
 */
@Dao
interface RouteDao {
    
    @Query("SELECT * FROM route WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getRouteForSession(sessionId: String): Flow<List<RoutePoint>>
    
    @Query("SELECT * FROM route ORDER BY timestamp ASC")
    fun getAllRoutePoints(): Flow<List<RoutePoint>>
    
    @Query("SELECT * FROM route WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getRouteByDateRange(startTime: Long, endTime: Long): Flow<List<RoutePoint>>
    
    @Query("SELECT * FROM route ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastRoutePoint(): RoutePoint?
    
    @Insert
    suspend fun insert(routePoint: RoutePoint)
    
    @Query("DELETE FROM route WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
    
    @Query("DELETE FROM route WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
    
    @Query("DELETE FROM route")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM route")
    suspend fun getCount(): Int
}

/**
 * IRK DAO
 */
@Dao
interface IrkDao {
    
    @Query("SELECT * FROM irk ORDER BY addedAt DESC")
    fun getAllIrks(): Flow<List<Irk>>
    
    @Query("SELECT * FROM irk")
    suspend fun getAllIrksList(): List<Irk>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(irk: Irk)
    
    @Query("UPDATE irk SET name = :name, deviceType = :deviceType WHERE id = :id")
    suspend fun update(id: String, name: String?, deviceType: String?)
    
    @Delete
    suspend fun delete(irk: Irk)
    
    @Query("DELETE FROM irk WHERE id = :id")
    suspend fun deleteById(id: String)
}

/**
 * Main Database
 */
@Database(
    entities = [Network::class, Location::class, Session::class, RoutePoint::class, Irk::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun networkDao(): NetworkDao
    abstract fun locationDao(): LocationDao
    abstract fun sessionDao(): SessionDao
    abstract fun routeDao(): RouteDao
    abstract fun irkDao(): IrkDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rftoolkit.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
