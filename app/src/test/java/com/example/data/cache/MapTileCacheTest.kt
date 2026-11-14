package com.example.data.cache

import android.graphics.Bitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.data.model.LatLon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapTileCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = runBlocking {
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        scopes.clear()
    }

    private fun createTestDataStore(name: String = "test_map_tiles.preferences_pb"): DataStore<Preferences> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scopes += scope
        val file = File(tempFolder.root, name)
        return PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    private fun createValidPngBytes(color: Int = 0xFF1E88E5.toInt()): ByteArray {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }

    @Test
    fun getTileBytes_fetchesOnlineAndPersistsAcrossDataStoreInstances() = runBlocking {
        val dsFile = File(tempFolder.root, "persisted_tiles.preferences_pb")
        val pngBytes = createValidPngBytes()
        var networkCalls = 0

        val scope1 = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        val ds1 = PreferenceDataStoreFactory.create(scope = scope1) { dsFile }
        val cache1 = MapTileCache(
            dataStore = ds1,
            isOnlineProvider = { true },
            tileFetcher = {
                networkCalls++
                pngBytes
            }
        )

        val coord = TileCoord(15, 23412, 13640)
        val firstResult = cache1.getTileBytes(coord)
        assertEquals(TileOrigin.NETWORK, firstResult.origin)
        assertArrayEquals(pngBytes, firstResult.bytes)
        assertEquals(1, networkCalls)

        // Second memory-warm read stays in L1 MEMORY cache
        val memResult = cache1.getTileBytes(coord)
        assertEquals(TileOrigin.MEMORY, memResult.origin)
        assertEquals(1, networkCalls)

        // Close first DataStore cleanly before opening a second DataStore instance on the same file
        scope1.coroutineContext.job.cancelAndJoin()

        // Fresh MapTileCache reading the same DataStore file offline must hit DATASTORE_FRESH
        val scope2 = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        val ds2 = PreferenceDataStoreFactory.create(scope = scope2) { dsFile }
        var secondNetworkCalls = 0
        val cache2 = MapTileCache(
            dataStore = ds2,
            isOnlineProvider = { false },
            tileFetcher = {
                secondNetworkCalls++
                null
            }
        )

        val diskResult = cache2.getTileBytes(coord)
        assertEquals(TileOrigin.DATASTORE_FRESH, diskResult.origin)
        assertArrayEquals(pngBytes, diskResult.bytes)
        assertEquals(0, secondNetworkCalls)

        val (decoded, origin) = cache2.getTileBitmap(coord)
        assertNotNull(decoded)
        assertEquals(TileOrigin.MEMORY, origin)
    }

    @Test
    fun getTileBytes_fallsBackToStaleDataStoreTileWhenOfflineOrNetworkFails() = runBlocking {
        val ds = createTestDataStore("stale_fallback.preferences_pb")
        val pngBytes = createValidPngBytes(0xFF00897B.toInt())
        var nowMs = 1_000L
        var online = true
        var failNetwork = false

        val writerCache = MapTileCache(
            dataStore = ds,
            isOnlineProvider = { online },
            tileFetcher = { if (failNetwork) null else pngBytes },
            timeProvider = { nowMs },
            ttlMillis = 500L
        )
        val coord = TileCoord(15, 100, 200)
        writerCache.putTileBytes(coord, pngBytes)

        // Advance time past TTL so cached tile is now stale, and recreate cache with empty memory L1
        nowMs = 5_000L
        online = false
        val offlineReader = MapTileCache(
            dataStore = ds,
            isOnlineProvider = { online },
            tileFetcher = { error("Should not fetch while offline") },
            timeProvider = { nowMs },
            ttlMillis = 500L
        )

        val staleOfflineResult = offlineReader.getTileBytes(coord)
        assertEquals(TileOrigin.DATASTORE_OFFLINE_STALE, staleOfflineResult.origin)
        assertArrayEquals(pngBytes, staleOfflineResult.bytes)

        // Also test poor connectivity where online == true but network fetch throws / returns null
        online = true
        failNetwork = true
        val flakyReader = MapTileCache(
            dataStore = ds,
            isOnlineProvider = { online },
            tileFetcher = { throw RuntimeException("Timeout under weak signal") },
            timeProvider = { nowMs },
            ttlMillis = 500L
        )
        val poorNetworkResult = flakyReader.getTileBytes(coord)
        assertEquals(TileOrigin.DATASTORE_OFFLINE_STALE, poorNetworkResult.origin)
        assertArrayEquals(pngBytes, poorNetworkResult.bytes)
    }

    @Test
    fun putTileBytes_enforcesMaxCapacityWithLruEviction_andClearCacheResetsStats() = runBlocking {
        val ds = createTestDataStore("lru_eviction.preferences_pb")
        val maxTiles = 3
        val png = createValidPngBytes()
        val cache = MapTileCache(
            dataStore = ds,
            isOnlineProvider = { false },
            tileFetcher = { null },
            maxCachedTiles = maxTiles
        )

        val t1 = TileCoord(15, 1, 1)
        val t2 = TileCoord(15, 1, 2)
        val t3 = TileCoord(15, 1, 3)
        val t4 = TileCoord(15, 1, 4)

        cache.putTileBytes(t1, png)
        cache.putTileBytes(t2, png)
        cache.putTileBytes(t3, png)
        cache.putTileBytes(t4, png) // Should evict t1 (oldest)

        val statsAfterEvict = cache.statsFlow.first()
        assertEquals(maxTiles, statsAfterEvict.cachedTileCount)
        assertTrue(statsAfterEvict.totalBytes > 0L)

        // Verify in a fresh cache reading from DataStore that t1 is gone and t2..t4 exist
        val dsReader = MapTileCache(
            dataStore = ds,
            isOnlineProvider = { false },
            tileFetcher = { null },
            maxCachedTiles = maxTiles
        )
        assertNull(dsReader.getTileBytes(t1).bytes)
        assertEquals(TileOrigin.UNAVAILABLE, dsReader.getTileBytes(t1).origin)
        assertNotNull(dsReader.getTileBytes(t2).bytes)
        assertNotNull(dsReader.getTileBytes(t3).bytes)
        assertNotNull(dsReader.getTileBytes(t4).bytes)

        cache.clearCache()
        val clearedStats = cache.statsFlow.first()
        assertEquals(0, clearedStats.cachedTileCount)
        assertEquals(0L, clearedStats.totalBytes)
        assertNull(cache.getTileBytes(t4).bytes)
        val freshAfterClear = MapTileCache(
            dataStore = ds,
            isOnlineProvider = { false },
            tileFetcher = { null },
            maxCachedTiles = maxTiles
        )
        assertNull(freshAfterClear.getTileBytes(t4).bytes)
    }

    @Test
    fun prefetchSurroundingAndRouteCorridor_cachesTilesWhenOnlineAndSkipsWhenOffline() = runBlocking {
        val ds = createTestDataStore("prefetch_test.preferences_pb")
        val png = createValidPngBytes()
        var online = true
        val cache = MapTileCache(
            dataStore = ds,
            isOnlineProvider = { online },
            tileFetcher = { png }
        )

        val start = LatLon(28.6315, 77.2167)
        val end = LatLon(28.5494, 77.2001)

        // 3x3 surrounding grid -> 9 tiles
        val surroundingCount = cache.prefetchSurroundingTiles(center = start, zoom = 15, radiusTiles = 1)
        assertEquals(9, surroundingCount)

        // Route corridor adds remaining distinct tiles along the path
        val corridorCount = cache.prefetchRouteCorridor(
            points = listOf(start, LatLon(28.5900, 77.2080), end),
            zoom = 15
        )
        assertTrue(corridorCount >= 1)

        val stats = cache.statsFlow.first()
        assertEquals(9 + corridorCount, stats.cachedTileCount)

        // Offline prefetch does 0 network downloads
        online = false
        assertEquals(0, cache.prefetchSurroundingTiles(center = end, zoom = 15, radiusTiles = 1))
        assertEquals(0, cache.prefetchRouteCorridor(points = listOf(start, end), zoom = 15))
    }

    @Test
    fun slippyMapMath_projectionCoversCenterTileWithinViewportBounds() {
        val center = LatLon(28.6315, 77.2167)
        val projection = SlippyMapMath.buildProjection(
            points = listOf(center, LatLon(28.6139, 77.2090)),
            fallbackCenter = center,
            widthPx = 800f,
            heightPx = 500f
        )
        val tiles = projection.visibleTiles()
        assertTrue(tiles.isNotEmpty())
        val centerTile = SlippyMapMath.latLonToTile(center.lat, center.lon, projection.zoom)
        assertTrue(tiles.any { it.coord == centerTile })

        val (cx, cy) = projection.project(center)
        assertTrue(cx in 0f..800f)
        assertTrue(cy in 0f..500f)
    }
}
