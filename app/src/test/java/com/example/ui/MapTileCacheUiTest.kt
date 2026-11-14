package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.data.cache.MapTileCache
import com.example.data.cache.MapTileCacheStats
import com.example.data.cache.SlippyMapMath
import com.example.data.model.LatLon
import com.example.localization.AppLang
import com.example.localization.NirapadStrings
import com.example.ui.components.NirapadMapCanvas
import com.example.ui.screens.ProfileAdminScreen
import com.example.ui.theme.NirapadTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class MapTileCacheUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var scope: CoroutineScope? = null

    @After
    fun tearDown() = runBlocking {
        scope?.coroutineContext?.job?.cancelAndJoin()
        scope = null
    }

    @Test
    fun nirapadMapCanvas_rendersOfflineCachedTileBadgeAndOsmAttributionWhenOffline() {
        val dsScope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scope = it }
        val ds = PreferenceDataStoreFactory.create(scope = dsScope) {
            File(tempFolder.root, "ui_map_cache.preferences_pb")
        }
        val cache = MapTileCache(
            dataStore = ds,
            isOnlineProvider = { false },
            tileFetcher = { null }
        )

        val center = LatLon(28.6315, 77.2167)
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFFD0E8F2.toInt())
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        val pngBytes = out.toByteArray()

        // Pre-populate tiles for zoom 12..15 around center into DataStore before rendering
        runBlocking {
            for (z in 12..15) {
                val centerTile = SlippyMapMath.latLonToTile(center.lat, center.lon, z)
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        cache.putTileBytes(
                            centerTile.copy(x = centerTile.x + dx, y = centerTile.y + dy),
                            pngBytes
                        )
                    }
                }
            }
        }

        composeRule.setContent {
            NirapadTheme {
                NirapadMapCanvas(
                    currentLocation = center,
                    routes = emptyList(),
                    selectedRouteId = null,
                    breadcrumbs = emptyList(),
                    facilities = emptyList(),
                    isDeviated = false,
                    modifier = Modifier.height(220.dp),
                    tileCacheOverride = cache
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("© OSM").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("map_cache_status_badge")
            .assertIsDisplayed()
            .assertTextContains("Offline Cache", substring = true)
        composeRule.onNodeWithText("© OSM").assertIsDisplayed()
    }

    @Test
    fun profileAdminScreen_displaysDataStoreTileCount_andTriggersPrefetchAndClearActions() {
        val copy = NirapadStrings.get(AppLang.EN)
        var prefetchClicks = 0
        var clearClicks = 0

        composeRule.setContent {
            NirapadTheme {
                ProfileAdminScreen(
                    copy = copy,
                    profile = null,
                    contacts = emptyList(),
                    language = AppLang.EN,
                    isAdmin = false,
                    adminReports = emptyList(),
                    auditLogs = emptyList(),
                    onSaveContact = { _, _, _, _, _, _, _, _ -> },
                    onDeleteContact = {},
                    onOpenTrustedContacts = {},
                    onUpdateSettings = { _, _, _, _ -> },
                    onPurgeHistory = {},
                    onModerateReport = { _, _, _ -> },
                    onAddVerifiedFacility = { _, _, _, _, _ -> },
                    onSignOut = {},
                    tileCacheStats = MapTileCacheStats(
                        cachedTileCount = 12,
                        totalBytes = 128 * 1024L,
                        lastCachedAtMs = 1_000L
                    ),
                    onPrefetchOfflineMap = { prefetchClicks++ },
                    onClearMapTileCache = { clearClicks++ }
                )
            }
        }

        composeRule.onNodeWithTag("map_cache_count_text")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("12/72 tiles")

        composeRule.onNodeWithTag("cache_map_area_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("clear_map_cache_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(1, prefetchClicks)
        assertEquals(1, clearClicks)
    }

    @Test
    fun profileAdminScreen_disablesClearButtonWhenCacheIsEmpty() {
        val copy = NirapadStrings.get(AppLang.EN)
        composeRule.setContent {
            NirapadTheme {
                ProfileAdminScreen(
                    copy = copy,
                    profile = null,
                    contacts = emptyList(),
                    language = AppLang.EN,
                    isAdmin = false,
                    adminReports = emptyList(),
                    auditLogs = emptyList(),
                    onSaveContact = { _, _, _, _, _, _, _, _ -> },
                    onDeleteContact = {},
                    onOpenTrustedContacts = {},
                    onUpdateSettings = { _, _, _, _ -> },
                    onPurgeHistory = {},
                    onModerateReport = { _, _, _ -> },
                    onAddVerifiedFacility = { _, _, _, _, _ -> },
                    onSignOut = {},
                    tileCacheStats = MapTileCacheStats(cachedTileCount = 0, totalBytes = 0L)
                )
            }
        }

        composeRule.onNodeWithTag("clear_map_cache_button")
            .performScrollTo()
            .assertIsNotEnabled()
    }
}
