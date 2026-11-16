package com.example.data.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Base64
import android.util.Log
import android.util.LruCache
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan

private val Context.mapTilePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nirapad_map_tile_cache"
)

data class TileCoord(
    val z: Int,
    val x: Int,
    val y: Int
) {
    val key: String get() = "${z}_${x}_${y}"
}

enum class TileOrigin {
    MEMORY,
    DATASTORE_FRESH,
    DATASTORE_OFFLINE_STALE,
    NETWORK,
    UNAVAILABLE
}

data class CachedTileResult(
    val coord: TileCoord,
    val bytes: ByteArray?,
    val origin: TileOrigin
)

data class MapTileCacheStats(
    val cachedTileCount: Int = 0,
    val totalBytes: Long = 0L,
    val lastCachedAtMs: Long = 0L
)

data class TilePlacement(
    val coord: TileCoord,
    val leftPx: Float,
    val topPx: Float,
    val sizePx: Float
)

data class MapViewportProjection(
    val zoom: Int,
    val centerWorldX: Double,
    val centerWorldY: Double,
    val scale: Float,
    val widthPx: Float,
    val heightPx: Float
) {
    fun project(pt: LatLon): Pair<Float, Float> {
        val wx = SlippyMapMath.lonToWorldPx(pt.lon, zoom)
        val wy = SlippyMapMath.latToWorldPx(pt.lat, zoom)
        val sx = ((wx - centerWorldX) * scale + widthPx / 2f).toFloat()
        val sy = ((wy - centerWorldY) * scale + heightPx / 2f).toFloat()
        return sx to sy
    }

    fun unproject(screenX: Float, screenY: Float): LatLon {
        val safeScale = max(scale.toDouble(), 0.01)
        val wx = centerWorldX + (screenX - widthPx / 2.0) / safeScale
        val wy = centerWorldY + (screenY - heightPx / 2.0) / safeScale
        return LatLon(
            lat = SlippyMapMath.worldPxToLat(wy, zoom),
            lon = SlippyMapMath.worldPxToLon(wx, zoom)
        )
    }

    fun panByScreenDelta(dxPx: Float, dyPx: Float): LatLon {
        val safeScale = max(scale.toDouble(), 0.01)
        val nextWorldX = centerWorldX + dxPx / safeScale
        val nextWorldY = centerWorldY + dyPx / safeScale
        return LatLon(
            lat = SlippyMapMath.worldPxToLat(nextWorldY, zoom),
            lon = SlippyMapMath.worldPxToLon(nextWorldX, zoom)
        )
    }

    fun visibleTiles(): List<TilePlacement> {
        if (widthPx <= 0f || heightPx <= 0f || scale <= 0f) return emptyList()
        val halfWorldW = (widthPx / 2f) / scale
        val halfWorldH = (heightPx / 2f) / scale
        val minWorldX = centerWorldX - halfWorldW
        val maxWorldX = centerWorldX + halfWorldW
        val minWorldY = centerWorldY - halfWorldH
        val maxWorldY = centerWorldY + halfWorldH

        val maxTileIndex = (1 shl zoom) - 1
        val minTileX = floor(minWorldX / SlippyMapMath.TILE_SIZE_PX).toInt().coerceIn(0, maxTileIndex)
        val maxTileX = floor(maxWorldX / SlippyMapMath.TILE_SIZE_PX).toInt().coerceIn(0, maxTileIndex)
        val minTileY = floor(minWorldY / SlippyMapMath.TILE_SIZE_PX).toInt().coerceIn(0, maxTileIndex)
        val maxTileY = floor(maxWorldY / SlippyMapMath.TILE_SIZE_PX).toInt().coerceIn(0, maxTileIndex)

        val tileScreenSize = (SlippyMapMath.TILE_SIZE_PX * scale).toFloat()
        val out = mutableListOf<TilePlacement>()
        for (tx in minTileX..maxTileX) {
            for (ty in minTileY..maxTileY) {
                val tileWorldLeft = tx * SlippyMapMath.TILE_SIZE_PX
                val tileWorldTop = ty * SlippyMapMath.TILE_SIZE_PX
                val screenLeft = ((tileWorldLeft - centerWorldX) * scale + widthPx / 2f).toFloat()
                val screenTop = ((tileWorldTop - centerWorldY) * scale + heightPx / 2f).toFloat()
                out += TilePlacement(
                    coord = TileCoord(zoom, tx, ty),
                    leftPx = screenLeft,
                    topPx = screenTop,
                    sizePx = tileScreenSize
                )
            }
        }
        return out
    }
}

object SlippyMapMath {
    const val TILE_SIZE_PX = 256.0
    private const val MAX_LAT = 85.05112878

    fun latLonToTile(lat: Double, lon: Double, zoom: Int): TileCoord {
        val z = zoom.coerceIn(1, 18)
        val n = 1 shl z
        val clampedLat = lat.coerceIn(-MAX_LAT, MAX_LAT)
        val latRad = Math.toRadians(clampedLat)
        val x = floor(((lon + 180.0) / 360.0) * n).toInt().coerceIn(0, n - 1)
        val y = floor(
            (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        ).toInt().coerceIn(0, n - 1)
        return TileCoord(z, x, y)
    }

    fun lonToWorldPx(lon: Double, zoom: Int): Double {
        val worldSize = TILE_SIZE_PX * (1 shl zoom)
        return ((lon.coerceIn(-180.0, 180.0) + 180.0) / 360.0) * worldSize
    }

    fun latToWorldPx(lat: Double, zoom: Int): Double {
        val worldSize = TILE_SIZE_PX * (1 shl zoom)
        val sinLat = sin(Math.toRadians(lat.coerceIn(-MAX_LAT, MAX_LAT)))
        val yNorm = 0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)
        return yNorm * worldSize
    }

    fun worldPxToLon(worldX: Double, zoom: Int): Double {
        val worldSize = TILE_SIZE_PX * (1 shl zoom)
        val wrapped = ((worldX % worldSize) + worldSize) % worldSize
        return (wrapped / worldSize) * 360.0 - 180.0
    }

    fun worldPxToLat(worldY: Double, zoom: Int): Double {
        val worldSize = TILE_SIZE_PX * (1 shl zoom)
        val clampedY = worldY.coerceIn(0.0, worldSize)
        val n = PI - (2.0 * PI * clampedY) / worldSize
        val latRad = atan(sinh(n))
        return Math.toDegrees(latRad).coerceIn(-MAX_LAT, MAX_LAT)
    }

    fun buildCameraProjection(
        center: LatLon,
        continuousZoom: Float,
        widthPx: Float,
        heightPx: Float
    ): MapViewportProjection {
        val safeW = max(widthPx, 1f)
        val safeH = max(heightPx, 1f)
        val clampedZoom = continuousZoom.coerceIn(10.0f, 17.5f)
        val tileZoom = clampedZoom.roundToInt().coerceIn(10, 17)
        val scale = 2.0.pow((clampedZoom - tileZoom).toDouble()).toFloat().coerceIn(0.6f, 1.5f)
        return MapViewportProjection(
            zoom = tileZoom,
            centerWorldX = lonToWorldPx(center.lon, tileZoom),
            centerWorldY = latToWorldPx(center.lat, tileZoom),
            scale = scale,
            widthPx = safeW,
            heightPx = safeH
        )
    }

    fun fitCameraState(
        points: List<LatLon>,
        fallbackCenter: LatLon,
        widthPx: Float,
        heightPx: Float,
        minZoom: Int = 11,
        maxZoom: Int = 15
    ): Pair<LatLon, Float> {
        val proj = buildProjection(
            points = points,
            fallbackCenter = fallbackCenter,
            widthPx = widthPx,
            heightPx = heightPx,
            minZoom = minZoom,
            maxZoom = maxZoom
        )
        val centerLatLon = LatLon(
            lat = worldPxToLat(proj.centerWorldY, proj.zoom),
            lon = worldPxToLon(proj.centerWorldX, proj.zoom)
        )
        val continuousZoom = (proj.zoom + ln(proj.scale.toDouble()) / ln(2.0)).toFloat().coerceIn(10.5f, 16.5f)
        return centerLatLon to continuousZoom
    }

    fun buildProjection(
        points: List<LatLon>,
        fallbackCenter: LatLon,
        widthPx: Float,
        heightPx: Float,
        minZoom: Int = 12,
        maxZoom: Int = 15
    ): MapViewportProjection {
        val safeW = max(widthPx, 1f)
        val safeH = max(heightPx, 1f)
        val valid = points.ifEmpty { listOf(fallbackCenter) }

        val minLat = valid.minOf { it.lat }
        val maxLat = valid.maxOf { it.lat }
        val minLon = valid.minOf { it.lon }
        val maxLon = valid.maxOf { it.lon }

        val padLat = max((maxLat - minLat) * 0.25, 0.0045)
        val padLon = max((maxLon - minLon) * 0.25, 0.0045)

        var chosenZoom = maxZoom
        var chosenScale = 1f
        for (z in maxZoom downTo minZoom) {
            val left = lonToWorldPx(minLon - padLon, z)
            val right = lonToWorldPx(maxLon + padLon, z)
            val top = latToWorldPx(maxLat + padLat, z)
            val bottom = latToWorldPx(minLat - padLat, z)
            val spanW = max(right - left, 1.0)
            val spanH = max(bottom - top, 1.0)
            val fitScale = min(safeW / spanW, safeH / spanH)
            if (fitScale >= 0.75 || z == minZoom) {
                chosenZoom = z
                chosenScale = fitScale.toFloat().coerceIn(0.65f, 2.0f)
                break
            }
        }

        val left = lonToWorldPx(minLon - padLon, chosenZoom)
        val right = lonToWorldPx(maxLon + padLon, chosenZoom)
        val top = latToWorldPx(maxLat + padLat, chosenZoom)
        val bottom = latToWorldPx(minLat - padLat, chosenZoom)
        return MapViewportProjection(
            zoom = chosenZoom,
            centerWorldX = (left + right) / 2.0,
            centerWorldY = (top + bottom) / 2.0,
            scale = chosenScale,
            widthPx = safeW,
            heightPx = safeH
        )
    }
}

/**
 * Persistent DataStore-backed cache for OpenStreetMap raster tiles with LRU eviction,
 * memory L1 cache, route/area prefetching, and offline stale-tile fallback so basic
 * navigation and location viewing remain functional with poor or no connectivity.
 */
class MapTileCache internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val isOnlineProvider: () -> Boolean,
    private val tileFetcher: suspend (TileCoord) -> ByteArray?,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val maxCachedTiles: Int = DEFAULT_MAX_CACHED_TILES,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS
) {
    private val memoryCache = LruCache<String, ByteArray>(DEFAULT_MAX_CACHED_TILES)
    private val writeMutex = Mutex()

    val statsFlow: Flow<MapTileCacheStats> = dataStore.data.map { prefs ->
        val index = parseIndex(prefs[KEY_TILE_INDEX])
        MapTileCacheStats(
            cachedTileCount = index.size,
            totalBytes = prefs[KEY_TOTAL_BYTES] ?: 0L,
            lastCachedAtMs = prefs[KEY_LAST_SYNC_MS] ?: 0L
        )
    }

    /**
     * Resolves a single map tile using Cache-First + Offline-Resilient fallback:
     * 1) In-memory L1 LRU cache
     * 2) Fresh DataStore persistent cache
     * 3) Live network fetch (if online), persisted into DataStore
     * 4) Stale DataStore fallback if offline or network fetch fails
     */
    suspend fun getTileBytes(coord: TileCoord): CachedTileResult = withContext(Dispatchers.IO) {
        val key = coord.key
        memoryCache.get(key)?.let { memBytes ->
            return@withContext CachedTileResult(coord, memBytes, TileOrigin.MEMORY)
        }

        val snapshot = dataStore.data.first()
        val encoded = snapshot[tileDataKey(key)]
        val cachedTs = snapshot[tileTimestampKey(key)] ?: 0L
        val cachedBytes = encoded?.let { decodeBase64(it) }
        val now = timeProvider()
        if (cachedBytes != null && (now - cachedTs) <= ttlMillis) {
            memoryCache.put(key, cachedBytes)
            return@withContext CachedTileResult(coord, cachedBytes, TileOrigin.DATASTORE_FRESH)
        }

        if (isOnlineProvider()) {
            val fetched = try {
                tileFetcher(coord)
            } catch (e: Exception) {
                Log.w(TAG, "Tile fetch failed for $key: ${e.message}")
                null
            }
            if (fetched != null && fetched.isNotEmpty()) {
                putTileBytes(coord, fetched)
                return@withContext CachedTileResult(coord, fetched, TileOrigin.NETWORK)
            }
        }

        if (cachedBytes != null) {
            memoryCache.put(key, cachedBytes)
            return@withContext CachedTileResult(coord, cachedBytes, TileOrigin.DATASTORE_OFFLINE_STALE)
        }

        CachedTileResult(coord, null, TileOrigin.UNAVAILABLE)
    }

    suspend fun getTileBitmap(coord: TileCoord): Pair<Bitmap?, TileOrigin> {
        val result = getTileBytes(coord)
        val bytes = result.bytes ?: return null to result.origin
        val bitmap = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
        return bitmap to result.origin
    }

    suspend fun putTileBytes(coord: TileCoord, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val key = coord.key
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val now = timeProvider()
        memoryCache.put(key, bytes)

        writeMutex.withLock {
            dataStore.edit { prefs ->
                val currentList = parseIndex(prefs[KEY_TILE_INDEX]).toMutableList()
                currentList.remove(key)
                currentList.add(key)

                prefs[tileDataKey(key)] = encoded
                prefs[tileTimestampKey(key)] = now
                prefs[KEY_LAST_SYNC_MS] = now

                while (currentList.size > maxCachedTiles) {
                    val evictedKey = currentList.removeAt(0)
                    prefs.remove(tileDataKey(evictedKey))
                    prefs.remove(tileTimestampKey(evictedKey))
                    memoryCache.remove(evictedKey)
                }

                var totalEncodedChars = 0L
                currentList.forEach { k ->
                    totalEncodedChars += (prefs[tileDataKey(k)]?.length ?: 0)
                }
                prefs[KEY_TILE_INDEX] = currentList.joinToString(",")
                prefs[KEY_TOTAL_BYTES] = (totalEncodedChars * 3L) / 4L
            }
        }
    }

    /**
     * Pre-caches a grid of tiles around [center] into DataStore for offline viewing.
     */
    suspend fun prefetchSurroundingTiles(
        center: LatLon,
        zoom: Int = 15,
        radiusTiles: Int = 1
    ): Int = withContext(Dispatchers.IO) {
        if (!isOnlineProvider()) return@withContext 0
        val centerTile = SlippyMapMath.latLonToTile(center.lat, center.lon, zoom)
        val maxIdx = (1 shl zoom) - 1
        var downloaded = 0
        for (dx in -radiusTiles..radiusTiles) {
            for (dy in -radiusTiles..radiusTiles) {
                val tx = (centerTile.x + dx).coerceIn(0, maxIdx)
                val ty = (centerTile.y + dy).coerceIn(0, maxIdx)
                val coord = TileCoord(zoom, tx, ty)
                val res = getTileBytes(coord)
                if (res.origin == TileOrigin.NETWORK) {
                    downloaded += 1
                }
            }
        }
        downloaded
    }

    /**
     * Pre-caches sampled tiles along a planned route corridor into DataStore.
     */
    suspend fun prefetchRouteCorridor(
        points: List<LatLon>,
        zoom: Int = 15,
        maxTilesToFetch: Int = 14
    ): Int = withContext(Dispatchers.IO) {
        if (!isOnlineProvider() || points.isEmpty()) return@withContext 0
        val uniqueTiles = LinkedHashSet<TileCoord>()
        val step = max(1, points.size / 8)
        for (i in points.indices step step) {
            uniqueTiles += SlippyMapMath.latLonToTile(points[i].lat, points[i].lon, zoom)
        }
        uniqueTiles += SlippyMapMath.latLonToTile(points.last().lat, points.last().lon, zoom)

        var downloaded = 0
        for (coord in uniqueTiles.take(maxTilesToFetch)) {
            val res = getTileBytes(coord)
            if (res.origin == TileOrigin.NETWORK) {
                downloaded += 1
            }
        }
        downloaded
    }

    suspend fun clearCache() {
        memoryCache.evictAll()
        writeMutex.withLock {
            dataStore.edit { prefs ->
                val keys = parseIndex(prefs[KEY_TILE_INDEX])
                keys.forEach { k ->
                    prefs.remove(tileDataKey(k))
                    prefs.remove(tileTimestampKey(k))
                }
                prefs.remove(KEY_TILE_INDEX)
                prefs[KEY_TOTAL_BYTES] = 0L
            }
        }
    }

    fun isCurrentlyOnline(): Boolean = isOnlineProvider()

    private fun parseIndex(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    private fun decodeBase64(encoded: String): ByteArray? = try {
        Base64.decode(encoded, Base64.DEFAULT).takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val TAG = "MapTileCache"
        const val DEFAULT_MAX_CACHED_TILES = 72
        const val DEFAULT_TTL_MILLIS = 14L * 24L * 60L * 60L * 1000L

        private val KEY_TILE_INDEX = stringPreferencesKey("tile_lru_index")
        private val KEY_TOTAL_BYTES = longPreferencesKey("tile_total_bytes")
        private val KEY_LAST_SYNC_MS = longPreferencesKey("tile_last_sync_ms")

        private fun tileDataKey(tileKey: String) = stringPreferencesKey("tile_${tileKey}_b64")
        private fun tileTimestampKey(tileKey: String) = longPreferencesKey("tile_${tileKey}_ts")

        @Volatile
        private var instance: MapTileCache? = null

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        }

        fun getInstance(context: Context): MapTileCache {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val appCtx = context.applicationContext ?: context
                    MapTileCache(
                        dataStore = appCtx.mapTilePreferencesDataStore,
                        isOnlineProvider = { checkNetworkAvailable(appCtx) },
                        tileFetcher = { coord -> fetchOsmTileBytes(coord) }
                    ).also { instance = it }
                }
            }
        }

        private val isRobolectricEnvironment: Boolean by lazy {
            Build.FINGERPRINT.contains("robolectric", ignoreCase = true) ||
                runCatching { Class.forName("org.robolectric.Robolectric") }.isSuccess
        }

        private fun checkNetworkAvailable(context: Context): Boolean {
            if (isRobolectricEnvironment) return false
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        private fun fetchOsmTileBytes(coord: TileCoord): ByteArray? {
            if (isRobolectricEnvironment) return null
            val url = "https://tile.openstreetmap.org/${coord.z}/${coord.x}/${coord.y}.png"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "NIRAPAD-PersonalSafetyApp/1.0 (Android)")
                .build()
            return try {
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val bytes = resp.body?.bytes() ?: return null
                    // Basic PNG magic check so error HTML pages are never cached as tiles
                    val isPng = bytes.size > 24 &&
                        bytes[0] == 0x89.toByte() &&
                        bytes[1] == 0x50.toByte() &&
                        bytes[2] == 0x4E.toByte() &&
                        bytes[3] == 0x47.toByte()
                    if (isPng) bytes else null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
