package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas as AndroidCanvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.cache.MapTileCache
import com.example.data.cache.MapTileCacheStats
import com.example.data.cache.SlippyMapMath
import com.example.data.cache.TileOrigin
import com.example.data.model.ColoredRouteSegment
import com.example.data.model.EmergencyFacility
import com.example.data.model.LatLon
import com.example.data.model.NearbyPoliceStation
import com.example.data.model.RouteOption
import com.example.data.model.SafetyTier
import com.example.data.model.SegmentSafetyLevel
import com.example.data.service.RoutingAndSafetyEngine
import com.example.ui.theme.NirapadBorderSubtle
import com.example.ui.theme.NirapadCautionAmber
import com.example.ui.theme.NirapadCautionContainer
import com.example.ui.theme.NirapadEmergencyRed
import com.example.ui.theme.NirapadNavyDark
import com.example.ui.theme.NirapadPositiveGreen
import com.example.ui.theme.NirapadPrimaryTeal
import com.example.ui.theme.NirapadSurface
import com.example.ui.theme.NirapadTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

private val LiveLocationBlue = Color(0xFF1A73E8)
private const val LIVE_LOCATION_BLUE_INT = 0xFF1A73E8.toInt()
private const val EMERGENCY_RED_INT = 0xFFD92D20.toInt()
private const val POSITIVE_GREEN_INT = 0xFF15803D.toInt()
private const val NAVY_DARK_INT = 0xFF0A2540.toInt()
private const val PRIMARY_TEAL_INT = 0xFF00798C.toInt()
private const val CAUTION_AMBER_INT = 0xFFD97706.toInt()
private const val ALT_SLATE_INT = 0xFF627D98.toInt()
private const val MIN_INTERACTIVE_ZOOM = 5.0
private const val MAX_INTERACTIVE_ZOOM = 19.0
private const val DEFAULT_GPS_ZOOM = 16.0

enum class MapLayerStyle(val label: String) {
    STREET("Street"),
    SATELLITE("Satellite")
}

internal fun esriWorldImageryTileUrl(zoom: Int, x: Int, y: Int): String =
    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x"

internal object EsriWorldImageryTileSource : OnlineTileSourceBase(
    "EsriWorldImagery",
    1,
    19,
    256,
    "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        return "$baseUrl$z/$y/$x"
    }
}

private var osmConfigured = false

internal fun ensureOsmDroidConfigured(context: Context) {
    if (osmConfigured) return
    val appCtx = context.applicationContext ?: context
    val osmBase = File(appCtx.cacheDir, "osmdroid").apply { mkdirs() }
    val osmTiles = File(osmBase, "tiles").apply { mkdirs() }
    val prefs = appCtx.getSharedPreferences("nirapad_osmdroid_prefs", Context.MODE_PRIVATE)
    Configuration.getInstance().apply {
        load(appCtx, prefs)
        userAgentValue = "${appCtx.packageName}/1.0 (NIRAPAD-SafetyMap)"
        osmdroidBasePath = osmBase
        osmdroidTileCache = osmTiles
    }
    osmConfigured = true
}

@Composable
fun NirapadMapCanvas(
    currentLocation: LatLon,
    routes: List<RouteOption>,
    selectedRouteId: String?,
    breadcrumbs: List<LatLon>,
    facilities: List<EmergencyFacility>,
    isDeviated: Boolean,
    modifier: Modifier = Modifier,
    tileCacheOverride: MapTileCache? = null,
    bearingDegrees: Float? = null,
    hasLiveGpsFix: Boolean = false,
    accuracyMeters: Double = 15.0,
    locationPermissionGranted: Boolean = hasLiveGpsFix,
    autoFollowByDefault: Boolean = routes.isEmpty(),
    expandable: Boolean = true,
    fullscreenTitle: String? = null,
    fullscreenSubtitle: String? = null,
    destinationQuery: String? = null,
    onDestinationQueryChange: ((String) -> Unit)? = null,
    onSearchDestination: ((String?) -> Unit)? = null,
    onRequestLocationPermission: (() -> Unit)? = null,
    remainingPolyline: List<LatLon>? = null,
    nearbyPoliceStations: List<NearbyPoliceStation> = emptyList()
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current
    val isRobolectric = remember {
        Build.FINGERPRINT.contains("robolectric", ignoreCase = true) ||
            runCatching { Class.forName("org.robolectric.Robolectric") }.isSuccess
    }
    val useNativeOsmDroid = !isInspection && !isRobolectric && tileCacheOverride == null

    val scope = rememberCoroutineScope()
    val tileCache = tileCacheOverride ?: remember(context, isInspection) {
        if (isInspection) null else runCatching { MapTileCache.getInstance(context) }.getOrNull()
    }
    val cacheStats by (tileCache?.statsFlow?.collectAsState(initial = MapTileCacheStats())
        ?: remember { mutableStateOf(MapTileCacheStats()) })

    var isExpanded by remember { mutableStateOf(false) }
    var mapLayerStyle by remember { mutableStateOf(MapLayerStyle.STREET) }
    var isFollowingUser by remember(autoFollowByDefault) { mutableStateOf(autoFollowByDefault) }
    var hasLockedInitialLiveFix by remember { mutableStateOf(false) }
    val initialDefaultZoom = if (useNativeOsmDroid) DEFAULT_GPS_ZOOM else 15.0
    var cameraCenter by remember { mutableStateOf(currentLocation) }
    var cameraZoom by remember { mutableDoubleStateOf(initialDefaultZoom) }
    var zoomCommandNonce by remember { mutableIntStateOf(0) }
    var zoomCommandDelta by remember { mutableDoubleStateOf(0.0) }
    var recenterNonce by remember { mutableIntStateOf(0) }

    val routeSignature = remember(routes) {
        routes.joinToString("|") { r ->
            val end = r.geometry.lastOrNull()
            "${r.id}:${end?.lat ?: 0.0}:${end?.lon ?: 0.0}"
        }
    }
    var hasFittedRoutes by remember(routeSignature) { mutableStateOf(false) }

    // Smoothly interpolate live GPS coordinates in Double precision
    var markerStart by remember { mutableStateOf(currentLocation) }
    var markerTarget by remember { mutableStateOf(currentLocation) }
    val markerProgress = remember { Animatable(1f) }
    val animatedMarkerLocation = remember(markerStart, markerTarget, markerProgress.value) {
        val t = markerProgress.value.toDouble().coerceIn(0.0, 1.0)
        LatLon(
            lat = markerStart.lat + (markerTarget.lat - markerStart.lat) * t,
            lon = markerStart.lon + (markerTarget.lon - markerStart.lon) * t
        )
    }

    // Track GPS or compass heading smoothly
    var lastBearingSource by remember { mutableStateOf(currentLocation) }
    var headingDegrees by remember { mutableStateOf(bearingDegrees) }
    val animatedHeading = remember { Animatable(bearingDegrees ?: 0f) }

    // Immediately snap camera onto the user's real live GPS location when first fix arrives
    LaunchedEffect(hasLiveGpsFix, currentLocation) {
        if (hasLiveGpsFix && !hasLockedInitialLiveFix) {
            hasLockedInitialLiveFix = true
            markerStart = currentLocation
            markerTarget = currentLocation
            markerProgress.snapTo(1f)
            if (routes.isEmpty() || autoFollowByDefault) {
                cameraCenter = currentLocation
                cameraZoom = max(cameraZoom, initialDefaultZoom)
                isFollowingUser = true
                recenterNonce++
            }
        }
    }

    LaunchedEffect(currentLocation) {
        if (currentLocation != markerTarget) {
            markerStart = animatedMarkerLocation
            markerTarget = currentLocation
            markerProgress.snapTo(0f)
            markerProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(currentLocation, bearingDegrees) {
        val nextHeading = when {
            bearingDegrees != null -> bearingDegrees
            else -> {
                val movedMeters = RoutingAndSafetyEngine.haversineMeters(
                    lastBearingSource.lat,
                    lastBearingSource.lon,
                    currentLocation.lat,
                    currentLocation.lon
                )
                if (movedMeters >= 1.5) {
                    val computed = computeBearingDegrees(lastBearingSource, currentLocation)
                    lastBearingSource = currentLocation
                    computed
                } else {
                    headingDegrees
                }
            }
        }
        if (nextHeading != null) {
            headingDegrees = nextHeading
            val delta = shortestAngleDelta(animatedHeading.value, nextHeading)
            animatedHeading.animateTo(
                targetValue = animatedHeading.value + delta,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        }
    }

    // Fallback Canvas auto-follow animation (when not using OSMdroid controller animation)
    var camAnimStart by remember { mutableStateOf(currentLocation) }
    var camAnimTarget by remember { mutableStateOf(currentLocation) }
    val camAnimProgress = remember { Animatable(1f) }

    LaunchedEffect(isFollowingUser, currentLocation, useNativeOsmDroid) {
        if (!useNativeOsmDroid && isFollowingUser && currentLocation != camAnimTarget) {
            camAnimStart = cameraCenter
            camAnimTarget = currentLocation
            camAnimProgress.snapTo(0f)
            camAnimProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(camAnimProgress.value, isFollowingUser, useNativeOsmDroid) {
        if (!useNativeOsmDroid && isFollowingUser && camAnimProgress.value < 1f) {
            val t = camAnimProgress.value.toDouble().coerceIn(0.0, 1.0)
            cameraCenter = LatLon(
                lat = camAnimStart.lat + (camAnimTarget.lat - camAnimStart.lat) * t,
                lon = camAnimStart.lon + (camAnimTarget.lon - camAnimStart.lon) * t
            )
        }
    }

    val allOverviewPoints = remember(currentLocation, hasLiveGpsFix, routes, breadcrumbs, facilities) {
        buildList {
            if (hasLiveGpsFix || routes.isEmpty()) add(currentLocation)
            routes.forEach { addAll(it.geometry) }
            addAll(breadcrumbs)
            facilities.take(5).forEach { add(LatLon(it.lat, it.lon)) }
        }
    }

    val decodedTiles = remember(tileCache) {
        mutableStateMapOf<String, Pair<ImageBitmap, TileOrigin>>()
    }

    fun recenterOnLiveLocation() {
        if (!hasLiveGpsFix && onRequestLocationPermission != null) {
            onRequestLocationPermission()
        }
        isFollowingUser = true
        val targetZoom = max(cameraZoom, initialDefaultZoom)
        cameraZoom = targetZoom
        cameraCenter = currentLocation
        recenterNonce++
        if (!useNativeOsmDroid) {
            camAnimStart = cameraCenter
            camAnimTarget = currentLocation
            scope.launch {
                camAnimProgress.snapTo(0f)
                camAnimProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
                )
                cameraCenter = currentLocation
            }
        }
    }

    val showWaitingGpsOverlay = locationPermissionGranted && !hasLiveGpsFix && routes.isEmpty()

    // Inline Map Card
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFEAF2F8))
            .border(1.dp, NirapadBorderSubtle, RoundedCornerShape(16.dp))
            .semantics { contentDescription = "Interactive OSMdroid live street map" }
    ) {
        if (useNativeOsmDroid) {
            OsmDroidMapLayer(
                currentLocation = currentLocation,
                animatedMarkerLocation = animatedMarkerLocation,
                headingDegrees = if (headingDegrees != null) animatedHeading.value else null,
                hasLiveGpsFix = hasLiveGpsFix,
                accuracyMeters = accuracyMeters,
                locationPermissionGranted = locationPermissionGranted,
                routes = routes,
                selectedRouteId = selectedRouteId,
                breadcrumbs = breadcrumbs,
                facilities = facilities,
                isDeviated = isDeviated,
                mapLayerStyle = mapLayerStyle,
                cameraCenter = cameraCenter,
                cameraZoom = cameraZoom,
                isFollowingUser = isFollowingUser,
                hasFittedRoutes = hasFittedRoutes,
                autoFollowByDefault = autoFollowByDefault,
                allOverviewPoints = allOverviewPoints,
                recenterNonce = recenterNonce,
                zoomCommandNonce = zoomCommandNonce,
                zoomCommandDelta = zoomCommandDelta,
                onCameraChanged = { nextCenter, nextZoom ->
                    cameraCenter = nextCenter
                    cameraZoom = nextZoom.coerceIn(MIN_INTERACTIVE_ZOOM, MAX_INTERACTIVE_ZOOM)
                },
                onUserDraggedMap = { isFollowingUser = false },
                onFittedInitialBounds = { fittedCenter, fittedZoom ->
                    cameraCenter = fittedCenter
                    cameraZoom = fittedZoom
                    hasFittedRoutes = true
                },
                onTapMap = if (expandable) {
                    { isExpanded = true }
                } else null,
                remainingPolyline = remainingPolyline,
                nearbyPoliceStations = nearbyPoliceStations,
                modifier = Modifier.fillMaxSize()
            )
            InlineMapOverlays(
                expandable = expandable,
                isOnline = true,
                mapLayerStyle = mapLayerStyle,
                renderedTileCount = 1,
                cachedTileCount = cacheStats.cachedTileCount,
                isFollowingUser = isFollowingUser,
                showWaitingGpsOverlay = showWaitingGpsOverlay,
                onExpand = { isExpanded = true },
                onToggleLayer = {
                    mapLayerStyle = if (mapLayerStyle == MapLayerStyle.STREET) MapLayerStyle.SATELLITE else MapLayerStyle.STREET
                },
                onRecenter = ::recenterOnLiveLocation,
                showOsmAttributionPill = true
            )
        } else {
            InteractiveFallbackCanvasLayer(
                currentLocation = currentLocation,
                animatedMarkerLocation = animatedMarkerLocation,
                headingDegrees = if (headingDegrees != null) animatedHeading.value else null,
                hasLiveGpsFix = hasLiveGpsFix,
                routes = routes,
                selectedRouteId = selectedRouteId,
                breadcrumbs = breadcrumbs,
                facilities = facilities,
                isDeviated = isDeviated,
                mapLayerStyle = mapLayerStyle,
                tileCache = tileCache,
                cacheStats = cacheStats,
                decodedTiles = decodedTiles,
                cameraCenter = cameraCenter,
                cameraZoom = cameraZoom.toFloat(),
                isFollowingUser = isFollowingUser,
                hasFittedRoutes = hasFittedRoutes,
                autoFollowByDefault = autoFollowByDefault,
                allOverviewPoints = allOverviewPoints,
                onFittedInitialBounds = { fittedCenter, fittedZoom ->
                    cameraCenter = fittedCenter
                    cameraZoom = fittedZoom.toDouble()
                    hasFittedRoutes = true
                },
                onUserGestureCamera = { nextCenter, nextZoom, brokeFollow ->
                    if (brokeFollow) {
                        isFollowingUser = false
                    }
                    cameraCenter = nextCenter
                    cameraZoom = nextZoom.toDouble().coerceIn(MIN_INTERACTIVE_ZOOM, MAX_INTERACTIVE_ZOOM)
                },
                onTapMap = if (expandable) {
                    { isExpanded = true }
                } else null,
                remainingPolyline = remainingPolyline,
                nearbyPoliceStations = nearbyPoliceStations,
                modifier = Modifier.fillMaxSize()
            ) { renderedTileCount, isOnline ->
                InlineMapOverlays(
                    expandable = expandable,
                    isOnline = isOnline,
                    mapLayerStyle = mapLayerStyle,
                    renderedTileCount = renderedTileCount,
                    cachedTileCount = cacheStats.cachedTileCount,
                    isFollowingUser = isFollowingUser,
                    showWaitingGpsOverlay = showWaitingGpsOverlay,
                    onExpand = { isExpanded = true },
                    onToggleLayer = {
                        mapLayerStyle = if (mapLayerStyle == MapLayerStyle.STREET) MapLayerStyle.SATELLITE else MapLayerStyle.STREET
                    },
                    onRecenter = ::recenterOnLiveLocation,
                    showOsmAttributionPill = renderedTileCount > 0 || mapLayerStyle == MapLayerStyle.SATELLITE
                )
            }
        }
    }

    // Expandable Full-Screen Interactive Map Exploration Mode
    if (isExpanded && expandable) {
        Dialog(
            onDismissRequest = { isExpanded = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFEAF2F8))
                    .testTag("fullscreen_map_container")
            ) {
                if (useNativeOsmDroid) {
                    OsmDroidMapLayer(
                        currentLocation = currentLocation,
                        animatedMarkerLocation = animatedMarkerLocation,
                        headingDegrees = if (headingDegrees != null) animatedHeading.value else null,
                        hasLiveGpsFix = hasLiveGpsFix,
                        accuracyMeters = accuracyMeters,
                        locationPermissionGranted = locationPermissionGranted,
                        routes = routes,
                        selectedRouteId = selectedRouteId,
                        breadcrumbs = breadcrumbs,
                        facilities = facilities,
                        isDeviated = isDeviated,
                        mapLayerStyle = mapLayerStyle,
                        cameraCenter = cameraCenter,
                        cameraZoom = cameraZoom,
                        isFollowingUser = isFollowingUser,
                        hasFittedRoutes = hasFittedRoutes,
                        autoFollowByDefault = autoFollowByDefault,
                        allOverviewPoints = allOverviewPoints,
                        recenterNonce = recenterNonce,
                        zoomCommandNonce = zoomCommandNonce,
                        zoomCommandDelta = zoomCommandDelta,
                        onCameraChanged = { nextCenter, nextZoom ->
                            cameraCenter = nextCenter
                            cameraZoom = nextZoom.coerceIn(MIN_INTERACTIVE_ZOOM, MAX_INTERACTIVE_ZOOM)
                        },
                        onUserDraggedMap = { isFollowingUser = false },
                        onFittedInitialBounds = { fittedCenter, fittedZoom ->
                            cameraCenter = fittedCenter
                            cameraZoom = fittedZoom
                            hasFittedRoutes = true
                        },
                        onTapMap = null,
                        remainingPolyline = remainingPolyline,
                        nearbyPoliceStations = nearbyPoliceStations,
                        modifier = Modifier.fillMaxSize()
                    )
                    FullscreenMapOverlays(
                        fullscreenTitle = fullscreenTitle,
                        fullscreenSubtitle = fullscreenSubtitle,
                        selectedRoute = routes.firstOrNull { it.id == selectedRouteId },
                        isFollowingUser = isFollowingUser,
                        hasLiveGpsFix = hasLiveGpsFix,
                        currentLocation = currentLocation,
                        cameraZoom = cameraZoom,
                        isOnline = true,
                        mapLayerStyle = mapLayerStyle,
                        renderedTileCount = 1,
                        cachedTileCount = cacheStats.cachedTileCount,
                        destinationQuery = destinationQuery,
                        onDestinationQueryChange = onDestinationQueryChange,
                        onSearchDestination = onSearchDestination,
                        onToggleLayer = {
                            mapLayerStyle = if (mapLayerStyle == MapLayerStyle.STREET) MapLayerStyle.SATELLITE else MapLayerStyle.STREET
                        },
                        onMinimize = { isExpanded = false },
                        onZoomIn = {
                            cameraZoom = (cameraZoom + 1.0).coerceIn(MIN_INTERACTIVE_ZOOM, MAX_INTERACTIVE_ZOOM)
                            zoomCommandDelta = 1.0
                            zoomCommandNonce++
                        },
                        onZoomOut = {
                            cameraZoom = (cameraZoom - 1.0).coerceIn(MIN_INTERACTIVE_ZOOM, MAX_INTERACTIVE_ZOOM)
                            zoomCommandDelta = -1.0
                            zoomCommandNonce++
                        },
                        onRecenter = ::recenterOnLiveLocation
                    )
                } else {
                    InteractiveFallbackCanvasLayer(
                        currentLocation = currentLocation,
                        animatedMarkerLocation = animatedMarkerLocation,
                        headingDegrees = if (headingDegrees != null) animatedHeading.value else null,
                        hasLiveGpsFix = hasLiveGpsFix,
                        routes = routes,
                        selectedRouteId = selectedRouteId,
                        breadcrumbs = breadcrumbs,
                        facilities = facilities,
                        isDeviated = isDeviated,
                        mapLayerStyle = mapLayerStyle,
                        tileCache = tileCache,
                        cacheStats = cacheStats,
                        decodedTiles = decodedTiles,
                        cameraCenter = cameraCenter,
                        cameraZoom = cameraZoom.toFloat(),
                        isFollowingUser = isFollowingUser,
                        hasFittedRoutes = hasFittedRoutes,
                        autoFollowByDefault = autoFollowByDefault,
                        allOverviewPoints = allOverviewPoints,
                        onFittedInitialBounds = { fittedCenter, fittedZoom ->
                            cameraCenter = fittedCenter
                            cameraZoom = fittedZoom.toDouble()
                            hasFittedRoutes = true
                        },
                        onUserGestureCamera = { nextCenter, nextZoom, brokeFollow ->
                            if (brokeFollow) {
                                isFollowingUser = false
                            }
                            cameraCenter = nextCenter
                            cameraZoom = nextZoom.toDouble().coerceIn(MIN_INTERACTIVE_ZOOM, MAX_INTERACTIVE_ZOOM)
                        },
                        onTapMap = null,
                        remainingPolyline = remainingPolyline,
                        nearbyPoliceStations = nearbyPoliceStations,
                        modifier = Modifier.fillMaxSize()
                    ) { renderedTileCount, isOnline ->
                        FullscreenMapOverlays(
                            fullscreenTitle = fullscreenTitle,
                            fullscreenSubtitle = fullscreenSubtitle,
                            selectedRoute = routes.firstOrNull { it.id == selectedRouteId },
                            isFollowingUser = isFollowingUser,
                            hasLiveGpsFix = hasLiveGpsFix,
                            currentLocation = currentLocation,
                            cameraZoom = cameraZoom,
                            isOnline = isOnline,
                            mapLayerStyle = mapLayerStyle,
                            renderedTileCount = renderedTileCount,
                            cachedTileCount = cacheStats.cachedTileCount,
                            destinationQuery = destinationQuery,
                            onDestinationQueryChange = onDestinationQueryChange,
                            onSearchDestination = onSearchDestination,
                            onToggleLayer = {
                                mapLayerStyle = if (mapLayerStyle == MapLayerStyle.STREET) MapLayerStyle.SATELLITE else MapLayerStyle.STREET
                            },
                            onMinimize = { isExpanded = false },
                            onZoomIn = {
                                cameraZoom = (cameraZoom + 0.7).coerceIn(MIN_INTERACTIVE_ZOOM, MAX_INTERACTIVE_ZOOM)
                            },
                            onZoomOut = {
                                cameraZoom = (cameraZoom - 0.7).coerceIn(MIN_INTERACTIVE_ZOOM, MAX_INTERACTIVE_ZOOM)
                            },
                            onRecenter = ::recenterOnLiveLocation
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.InlineMapOverlays(
    expandable: Boolean,
    isOnline: Boolean,
    mapLayerStyle: MapLayerStyle,
    renderedTileCount: Int,
    cachedTileCount: Int,
    isFollowingUser: Boolean,
    showWaitingGpsOverlay: Boolean,
    onExpand: () -> Unit,
    onToggleLayer: () -> Unit,
    onRecenter: () -> Unit,
    showOsmAttributionPill: Boolean
) {
    // Top-left: Explicit Expand Full Map button
    if (expandable) {
        Surface(
            onClick = onExpand,
            color = NirapadSurface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, NirapadBorderSubtle),
            shadowElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .minimumInteractiveComponentSize()
                .testTag("map_expand_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Expand Full Map",
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Expand",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }
        }
    }

    // Top-right: Map cache/connectivity badge + Google-Maps-style Street/Satellite toggle
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(10.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MapCacheStatusBadge(
            isOnline = isOnline,
            mapLayerStyle = mapLayerStyle,
            renderedTileCount = renderedTileCount,
            cachedTileCount = cachedTileCount,
            modifier = Modifier
                .semantics(mergeDescendants = true) {}
                .testTag("map_cache_status_badge")
        )
        MapLayerToggleButton(
            mapLayerStyle = mapLayerStyle,
            compact = true,
            onClick = onToggleLayer,
            modifier = Modifier.testTag("map_layer_toggle_button")
        )
    }

    if (showWaitingGpsOverlay) {
        Surface(
            color = NirapadSurface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, NirapadPrimaryTeal.copy(alpha = 0.4f)),
            shadowElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = null,
                    tint = NirapadPrimaryTeal,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Locking onto live GPS signal...",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A)
                )
            }
        }
    }

    // Bottom-left: Legend overlay
    Surface(
        color = NirapadSurface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NirapadBorderSubtle),
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LegendDot(color = NirapadPrimaryTeal, label = "Route")
            LegendDot(color = Color(0xFF627D98), label = "Alt")
            LegendDot(color = NirapadPositiveGreen, label = "Help")
        }
    }

    // Bottom-right: Re-center / My Location floating button + attribution
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(10.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ReCenterFloatingButton(
            isFollowingUser = isFollowingUser,
            compact = true,
            onClick = onRecenter,
            modifier = Modifier.testTag("map_recenter_button")
        )
        if (showOsmAttributionPill) {
            Surface(
                color = NirapadSurface.copy(alpha = 0.88f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = if (mapLayerStyle == MapLayerStyle.SATELLITE) "© Esri" else "© OSM",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF475569),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.FullscreenMapOverlays(
    fullscreenTitle: String?,
    fullscreenSubtitle: String?,
    selectedRoute: RouteOption?,
    isFollowingUser: Boolean,
    hasLiveGpsFix: Boolean,
    currentLocation: LatLon,
    cameraZoom: Double,
    isOnline: Boolean,
    mapLayerStyle: MapLayerStyle,
    renderedTileCount: Int,
    cachedTileCount: Int,
    destinationQuery: String?,
    onDestinationQueryChange: ((String) -> Unit)?,
    onSearchDestination: ((String?) -> Unit)?,
    onToggleLayer: () -> Unit,
    onMinimize: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit
) {
    // Top Floating Back + Header + Search Bar Overlay
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = NirapadSurface.copy(alpha = 0.97f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, NirapadBorderSubtle),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = onMinimize,
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("map_minimize_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Minimize full-screen map",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(18.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.CloseFullscreen,
                                contentDescription = null,
                                tint = NirapadPrimaryTeal,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fullscreenTitle ?: selectedRoute?.label ?: "Live Street Map",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = fullscreenSubtitle ?: buildString {
                                append(if (isFollowingUser) "Auto-follow ON" else "Exploring manually")
                                append(" • ")
                                append(if (hasLiveGpsFix) "Live GPS" else "Waiting GPS")
                                if (hasLiveGpsFix) {
                                    append(String.format(Locale.US, " (%.4f, %.4f)", currentLocation.lat, currentLocation.lon))
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF475569),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    MapCacheStatusBadge(
                        isOnline = isOnline,
                        mapLayerStyle = mapLayerStyle,
                        renderedTileCount = renderedTileCount,
                        cachedTileCount = cachedTileCount
                    )
                }

                // Optional Search Bar Overlay inside Full-Screen Map Mode
                if (onDestinationQueryChange != null && destinationQuery != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NirapadOutlinedTextField(
                            value = destinationQuery,
                            onValueChange = onDestinationQueryChange,
                            placeholder = { Text("Search safe destination...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (destinationQuery.isNotEmpty()) {
                                    IconButton(onClick = { onDestinationQueryChange("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear destination")
                                    }
                                }
                            },
                            singleLine = true,
                            forceLightSurface = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { onSearchDestination?.invoke(destinationQuery) }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("fullscreen_map_search_input")
                        )
                        if (onSearchDestination != null) {
                            Button(
                                onClick = {
                                    onSearchDestination(destinationQuery)
                                    onMinimize()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NirapadPrimaryTeal,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .height(52.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("fullscreen_map_search_go")
                            ) {
                                Text("Routes", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Bottom Floating Legend + Right Zoom/Re-center Stack
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.End),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MapLayerToggleButton(
                mapLayerStyle = mapLayerStyle,
                compact = false,
                onClick = onToggleLayer,
                modifier = Modifier.testTag("fullscreen_map_layer_toggle_button")
            )

            Surface(
                color = NirapadSurface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, NirapadBorderSubtle),
                shadowElevation = 4.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onZoomIn,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("map_zoom_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom in map",
                            tint = Color(0xFF0F172A)
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.width(32.dp),
                        color = NirapadBorderSubtle
                    )
                    IconButton(
                        onClick = onZoomOut,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("map_zoom_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom out map",
                            tint = Color(0xFF0F172A)
                        )
                    }
                }
            }

            ReCenterFloatingButton(
                isFollowingUser = isFollowingUser,
                compact = false,
                onClick = onRecenter,
                modifier = Modifier.testTag("fullscreen_map_recenter_button")
            )
        }

        Surface(
            color = NirapadSurface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NirapadBorderSubtle),
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LegendDot(color = LiveLocationBlue, label = "You")
                    LegendDot(color = NirapadPrimaryTeal, label = "Selected")
                    LegendDot(color = Color(0xFF627D98), label = "Alt")
                    LegendDot(color = NirapadPositiveGreen, label = "Help")
                }
                val attrib = if (mapLayerStyle == MapLayerStyle.SATELLITE) "© Esri World Imagery" else "© OpenStreetMap"
                Text(
                    text = if (selectedRoute != null) {
                        "${(selectedRoute.durationSeconds / 60).coerceAtLeast(1)} min • $attrib"
                    } else {
                        "Z${String.format(Locale.US, "%.1f", cameraZoom)} • $attrib"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569)
                )
            }
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun OsmDroidMapLayer(
    currentLocation: LatLon,
    animatedMarkerLocation: LatLon,
    headingDegrees: Float?,
    hasLiveGpsFix: Boolean,
    accuracyMeters: Double,
    locationPermissionGranted: Boolean,
    routes: List<RouteOption>,
    selectedRouteId: String?,
    breadcrumbs: List<LatLon>,
    facilities: List<EmergencyFacility>,
    isDeviated: Boolean,
    mapLayerStyle: MapLayerStyle,
    cameraCenter: LatLon,
    cameraZoom: Double,
    isFollowingUser: Boolean,
    hasFittedRoutes: Boolean,
    autoFollowByDefault: Boolean,
    allOverviewPoints: List<LatLon>,
    recenterNonce: Int,
    zoomCommandNonce: Int,
    zoomCommandDelta: Double,
    onCameraChanged: (LatLon, Double) -> Unit,
    onUserDraggedMap: () -> Unit,
    onFittedInitialBounds: (LatLon, Double) -> Unit,
    onTapMap: (() -> Unit)?,
    remainingPolyline: List<LatLon>? = null,
    nearbyPoliceStations: List<NearbyPoliceStation> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    remember(context) { ensureOsmDroidConfigured(context) }

    val latestOnCameraChanged by rememberUpdatedState(onCameraChanged)
    val latestOnUserDraggedMap by rememberUpdatedState(onUserDraggedMap)
    val latestOnTapMap by rememberUpdatedState(onTapMap)

    var pulsePhase by remember { mutableFloatStateOf(0f) }
    val beaconOverlay = remember { LiveLocationBeaconOverlay() }
    val routeOverlays = remember { mutableListOf<Overlay>() }

    val mapView = remember(context) {
        MapView(context).apply {
            setTileSource(if (mapLayerStyle == MapLayerStyle.SATELLITE) EsriWorldImageryTileSource else TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            setUseDataConnection(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = MIN_INTERACTIVE_ZOOM
            maxZoomLevel = MAX_INTERACTIVE_ZOOM
            controller.setZoom(cameraZoom)
            controller.setCenter(GeoPoint(cameraCenter.lat, cameraCenter.lon))
            overlays.add(beaconOverlay)
        }
    }

    var appliedLayerStyle by remember(mapView) { mutableStateOf(mapLayerStyle) }
    LaunchedEffect(mapView, mapLayerStyle) {
        if (appliedLayerStyle != mapLayerStyle) {
            val targetSource = when (mapLayerStyle) {
                MapLayerStyle.STREET -> TileSourceFactory.MAPNIK
                MapLayerStyle.SATELLITE -> EsriWorldImageryTileSource
            }
            runCatching { mapView.tileProvider.clearTileCache() }
            mapView.setTileSource(targetSource)
            mapView.invalidate()
            appliedLayerStyle = mapLayerStyle
        }
    }

    // Pulse animation ticker for the Google-Maps-style live location halo
    LaunchedEffect(mapView, hasLiveGpsFix) {
        if (hasLiveGpsFix) {
            var tick = 0
            while (true) {
                delay(45L)
                tick = (tick + 1) % 40
                pulsePhase = tick / 40f
                beaconOverlay.pulseFraction = pulsePhase
                mapView.postInvalidate()
            }
        }
    }

    // Lifecycle binding for OSMdroid MapView
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Sync gesture tap/drag detection and map scroll/zoom listener
    DisposableEffect(mapView) {
        var downX = 0f
        var downY = 0f
        var touchDragged = false
        val tapDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    latestOnTapMap?.invoke()
                    return latestOnTapMap != null
                }
            }
        )

        mapView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    touchDragged = false
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    if (!touchDragged && (abs(event.x - downX) > 16f || abs(event.y - downY) > 16f)) {
                        touchDragged = true
                        latestOnUserDraggedMap()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            tapDetector.onTouchEvent(event)
            false
        }

        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                val c = mapView.mapCenter
                latestOnCameraChanged(LatLon(c.latitude, c.longitude), mapView.zoomLevelDouble)
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                val c = mapView.mapCenter
                latestOnCameraChanged(LatLon(c.latitude, c.longitude), mapView.zoomLevelDouble)
                return false
            }
        }
        mapView.addMapListener(listener)

        onDispose {
            mapView.removeMapListener(listener)
            mapView.setOnTouchListener(null)
        }
    }

    // Handle explicit zoom button presses
    LaunchedEffect(zoomCommandNonce) {
        if (zoomCommandNonce > 0) {
            val targetZoom = (mapView.zoomLevelDouble + zoomCommandDelta)
                .coerceIn(MIN_INTERACTIVE_ZOOM, MAX_INTERACTIVE_ZOOM)
            mapView.controller.setZoom(targetZoom)
        }
    }

    // Handle explicit Re-center / My Location press or initial live GPS lock
    LaunchedEffect(recenterNonce) {
        if (recenterNonce > 0) {
            val targetZoom = max(mapView.zoomLevelDouble, DEFAULT_GPS_ZOOM)
            mapView.controller.animateTo(
                GeoPoint(currentLocation.lat, currentLocation.lon),
                targetZoom,
                420L
            )
        }
    }

    // Smooth auto-follow camera updates as the user walks/travels
    LaunchedEffect(isFollowingUser, currentLocation, hasLiveGpsFix) {
        if (isFollowingUser && hasLiveGpsFix) {
            mapView.controller.animateTo(GeoPoint(currentLocation.lat, currentLocation.lon))
        }
    }

    // Fit route bounding box cleanly when displaying route comparisons
    LaunchedEffect(routes.size, autoFollowByDefault, hasFittedRoutes) {
        if (!autoFollowByDefault && routes.isNotEmpty() && !hasFittedRoutes && !isFollowingUser) {
            val geoPoints = allOverviewPoints.map { GeoPoint(it.lat, it.lon) }
            if (geoPoints.size >= 2) {
                val box = BoundingBox.fromGeoPoints(geoPoints)
                mapView.post {
                    runCatching { mapView.zoomToBoundingBox(box, true, 72) }
                    val center = box.centerWithDateLine
                    onFittedInitialBounds(
                        LatLon(center.latitude, center.longitude),
                        mapView.zoomLevelDouble.coerceIn(MIN_INTERACTIVE_ZOOM, MAX_INTERACTIVE_ZOOM)
                    )
                }
            }
        }
    }

    // Sync route polylines, breadcrumbs, facility pins, and live blue GPS dot overlay
    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            // Remove previous route/marker overlays while keeping LiveLocationBeaconOverlay at top
            routeOverlays.forEach { view.overlays.remove(it) }
            routeOverlays.clear()

            // Alternative routes first, selected route on top
            val ordered = routes.sortedBy { it.id == selectedRouteId }
            ordered.forEach { route ->
                val isSelected = route.id == selectedRouteId
                val activeGeom = if (isSelected && !remainingPolyline.isNullOrEmpty() && remainingPolyline.size >= 2) {
                    remainingPolyline
                } else {
                    route.geometry
                }
                if (activeGeom.size >= 2) {
                    val geoPts = activeGeom.map { GeoPoint(it.lat, it.lon) }
                    if (isSelected || mapLayerStyle == MapLayerStyle.SATELLITE) {
                        val casing = Polyline(view).apply {
                            setPoints(geoPts)
                            outlinePaint.color = if (isSelected) 0xEBFFFFFF.toInt() else 0xB8FFFFFF.toInt()
                            outlinePaint.strokeWidth = if (isSelected) 18f else 11f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                        }
                        routeOverlays.add(casing)
                    }
                    if (isSelected && route.coloredSegments.isNotEmpty() && remainingPolyline.isNullOrEmpty()) {
                        route.coloredSegments.forEach { seg ->
                            if (seg.points.size >= 2) {
                                val segLine = Polyline(view).apply {
                                    setPoints(seg.points.map { GeoPoint(it.lat, it.lon) })
                                    outlinePaint.color = when (seg.safetyLevel) {
                                        SegmentSafetyLevel.HIGH_CROWD_LIT -> POSITIVE_GREEN_INT
                                        SegmentSafetyLevel.MODERATE_ACTIVITY -> PRIMARY_TEAL_INT
                                        SegmentSafetyLevel.LOW_CROWD_ISOLATED -> CAUTION_AMBER_INT
                                    }
                                    outlinePaint.strokeWidth = 11f
                                    outlinePaint.strokeCap = Paint.Cap.ROUND
                                    outlinePaint.strokeJoin = Paint.Join.ROUND
                                    title = route.label
                                }
                                routeOverlays.add(segLine)
                            }
                        }
                    } else {
                        val line = Polyline(view).apply {
                            setPoints(geoPts)
                            outlinePaint.color = when {
                                !isSelected -> ALT_SLATE_INT
                                route.safetyTier == SafetyTier.LIMITED_DATA -> CAUTION_AMBER_INT
                                route.safetyTier == SafetyTier.HIGHER_CONTEXT -> POSITIVE_GREEN_INT
                                else -> PRIMARY_TEAL_INT
                            }
                            outlinePaint.strokeWidth = if (isSelected) 11f else 6.5f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                            if (!isSelected) {
                                outlinePaint.pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
                            }
                            title = route.label
                        }
                        routeOverlays.add(line)
                    }
                }
            }

            // Active breadcrumb trail
            if (breadcrumbs.size >= 2) {
                val crumbLine = Polyline(view).apply {
                    setPoints(breadcrumbs.map { GeoPoint(it.lat, it.lon) })
                    outlinePaint.color = NAVY_DARK_INT
                    outlinePaint.strokeWidth = 7f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                }
                routeOverlays.add(crumbLine)
            }

            // Verified safety facility pins
            facilities.take(10).forEach { fac ->
                val marker = Marker(view).apply {
                    position = GeoPoint(fac.lat, fac.lon)
                    title = fac.name
                    snippet = "${fac.type} • ${fac.phone}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                routeOverlays.add(marker)
            }

            // Real nearby OSM Overpass Police Stations
            nearbyPoliceStations.take(8).forEach { st ->
                val marker = Marker(view).apply {
                    position = GeoPoint(st.lat, st.lon)
                    title = st.name
                    val distKm = String.format(Locale.US, "%.1f km", st.distanceMeters / 1000.0)
                    snippet = buildString {
                        append("Police • $distKm")
                        if (!st.phone.isNullOrBlank()) append(" • ${st.phone}")
                    }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                routeOverlays.add(marker)
            }

            // Destination pin on selected route
            routes.firstOrNull { it.id == selectedRouteId }?.geometry?.lastOrNull()?.let { dest ->
                val destMarker = Marker(view).apply {
                    position = GeoPoint(dest.lat, dest.lon)
                    title = "Destination"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                routeOverlays.add(destMarker)
            }

            // Insert route/marker overlays below the live GPS beacon overlay
            val insertCount = routeOverlays.size
            routeOverlays.forEachIndexed { idx, overlay ->
                view.overlays.add(idx.coerceAtMost(view.overlays.size - 1).coerceAtLeast(0), overlay)
            }
            if (insertCount == 0 && !view.overlays.contains(beaconOverlay)) {
                view.overlays.add(beaconOverlay)
            }

            // Update Google-Maps-style Live GPS Beacon state
            beaconOverlay.apply {
                location = animatedMarkerLocation
                this.headingDegrees = headingDegrees
                this.accuracyMeters = accuracyMeters
                this.isDeviated = isDeviated
                visible = hasLiveGpsFix || !locationPermissionGranted
            }
            view.invalidate()
        }
    )
}

private class LiveLocationBeaconOverlay : Overlay() {
    var location: LatLon = LatLon(0.0, 0.0)
    var headingDegrees: Float? = null
    var accuracyMeters: Double = 15.0
    var pulseFraction: Float = 0.25f
    var isDeviated: Boolean = false
    var visible: Boolean = true

    private val screenPoint = Point()
    private val accuracyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val accuracyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val whiteHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arrowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeJoin = Paint.Join.ROUND
    }
    private val arrowPath = AndroidPath()
    private val arcRect = RectF()

    override fun draw(canvas: AndroidCanvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !visible) return
        val proj = mapView.projection ?: return
        proj.toPixels(GeoPoint(location.lat, location.lon), screenPoint)
        val cx = screenPoint.x.toFloat()
        val cy = screenPoint.y.toFloat()
        val baseColor = if (isDeviated) EMERGENCY_RED_INT else LIVE_LOCATION_BLUE_INT

        // Accuracy radius halo
        val accPx = proj.metersToPixels(accuracyMeters.toFloat()).coerceIn(26f, 110f)
        accuracyPaint.color = baseColor
        accuracyPaint.alpha = 34
        canvas.drawCircle(cx, cy, accPx, accuracyPaint)
        accuracyBorderPaint.color = baseColor
        accuracyBorderPaint.alpha = 90
        canvas.drawCircle(cx, cy, accPx, accuracyBorderPaint)

        // Pulsing ring
        val pulseR = 24f + pulseFraction * 28f
        val pulseAlpha = ((1f - pulseFraction) * 95f).toInt().coerceIn(8, 95)
        pulsePaint.color = baseColor
        pulsePaint.alpha = pulseAlpha
        canvas.drawCircle(cx, cy, pulseR, pulsePaint)

        // Directional heading beam + arrow
        val heading = headingDegrees
        if (heading != null) {
            val coneR = 68f
            beamPaint.shader = RadialGradient(
                cx,
                cy,
                coneR,
                intArrayOf((baseColor and 0x00FFFFFF) or 0x7A000000, (baseColor and 0x00FFFFFF)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            arcRect.set(cx - coneR, cy - coneR, cx + coneR, cy + coneR)
            canvas.drawArc(arcRect, heading - 90f - 28f, 56f, true, beamPaint)

            // Crisp directional chevron ahead of the blue dot
            canvas.save()
            canvas.rotate(heading, cx, cy)
            arrowPath.reset()
            arrowPath.moveTo(cx, cy - 30f)
            arrowPath.lineTo(cx + 10f, cy - 15f)
            arrowPath.lineTo(cx, cy - 19f)
            arrowPath.lineTo(cx - 10f, cy - 15f)
            arrowPath.close()
            arrowFillPaint.color = baseColor
            canvas.drawPath(arrowPath, arrowFillPaint)
            canvas.drawPath(arrowPath, arrowStrokePaint)
            canvas.restore()
        }

        // White border + live GPS blue dot
        canvas.drawCircle(cx, cy, 15.5f, whiteHaloPaint)
        dotPaint.color = baseColor
        canvas.drawCircle(cx, cy, 10.5f, dotPaint)
    }
}

@Composable
private fun InteractiveFallbackCanvasLayer(
    currentLocation: LatLon,
    animatedMarkerLocation: LatLon,
    headingDegrees: Float?,
    hasLiveGpsFix: Boolean,
    routes: List<RouteOption>,
    selectedRouteId: String?,
    breadcrumbs: List<LatLon>,
    facilities: List<EmergencyFacility>,
    isDeviated: Boolean,
    mapLayerStyle: MapLayerStyle,
    tileCache: MapTileCache?,
    cacheStats: MapTileCacheStats,
    decodedTiles: MutableMap<String, Pair<ImageBitmap, TileOrigin>>,
    cameraCenter: LatLon,
    cameraZoom: Float,
    isFollowingUser: Boolean,
    hasFittedRoutes: Boolean,
    autoFollowByDefault: Boolean,
    allOverviewPoints: List<LatLon>,
    onFittedInitialBounds: (LatLon, Float) -> Unit,
    onUserGestureCamera: (LatLon, Float, Boolean) -> Unit,
    onTapMap: (() -> Unit)?,
    remainingPolyline: List<LatLon>? = null,
    nearbyPoliceStations: List<NearbyPoliceStation> = emptyList(),
    modifier: Modifier = Modifier,
    overlays: @Composable androidx.compose.foundation.layout.BoxScope.(renderedTileCount: Int, isOnline: Boolean) -> Unit
) {
    val isInspection = LocalInspectionMode.current
    val density = LocalDensity.current

    val isRobolectric = remember {
        Build.FINGERPRINT.contains("robolectric", ignoreCase = true) ||
            runCatching { Class.forName("org.robolectric.Robolectric") }.isSuccess
    }
    val pulseFractionState: State<Float> = if (!isInspection && !isRobolectric && hasLiveGpsFix) {
        val infiniteTransition = rememberInfiniteTransition(label = "live_gps_pulse")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "gps_ring"
        )
    } else {
        remember { mutableFloatStateOf(0.35f) }
    }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val heightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }

        LaunchedEffect(routes.size, autoFollowByDefault, widthPx, heightPx) {
            if (!autoFollowByDefault && routes.isNotEmpty() && !hasFittedRoutes && !isFollowingUser) {
                val (fittedCenter, fittedZoom) = SlippyMapMath.fitCameraState(
                    points = allOverviewPoints,
                    fallbackCenter = currentLocation,
                    widthPx = widthPx,
                    heightPx = heightPx
                )
                onFittedInitialBounds(fittedCenter, fittedZoom)
            }
        }

        val projection = remember(cameraCenter, cameraZoom, widthPx, heightPx) {
            SlippyMapMath.buildCameraProjection(
                center = cameraCenter,
                continuousZoom = cameraZoom,
                widthPx = widthPx,
                heightPx = heightPx
            )
        }
        val latestProjection by rememberUpdatedState(projection)
        val latestZoom by rememberUpdatedState(cameraZoom)
        val latestOnTapMap by rememberUpdatedState(onTapMap)

        val visibleTiles = remember(projection) { projection.visibleTiles() }
        val visibleTilesKey = remember(visibleTiles) { visibleTiles.joinToString("|") { it.coord.key } }

        LaunchedEffect(tileCache, visibleTilesKey, cacheStats.lastCachedAtMs) {
            if (tileCache == null || visibleTiles.isEmpty()) return@LaunchedEffect
            val newlyLoaded = withContext(Dispatchers.IO) {
                val map = LinkedHashMap<String, Pair<ImageBitmap, TileOrigin>>()
                visibleTiles.forEach { placement ->
                    val key = placement.coord.key
                    if (!decodedTiles.containsKey(key)) {
                        val (bmp, origin) = tileCache.getTileBitmap(placement.coord)
                        if (bmp != null) {
                            map[key] = bmp.asImageBitmap() to origin
                        }
                    }
                }
                map
            }
            if (newlyLoaded.isNotEmpty()) {
                decodedTiles.putAll(newlyLoaded)
                if (decodedTiles.size > 96) {
                    val visibleSet = visibleTiles.map { it.coord.key }.toSet()
                    val removable = decodedTiles.keys.filter { it !in visibleSet }
                    removable.take(decodedTiles.size - 72).forEach { decodedTiles.remove(it) }
                }
            }
        }

        val isOnline = remember(tileCache, visibleTilesKey, cacheStats.lastCachedAtMs) {
            tileCache?.isCurrentlyOnline() == true
        }
        val renderedTileCount by remember(visibleTilesKey) {
            derivedStateOf { visibleTiles.count { decodedTiles.containsKey(it.coord.key) } }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onTapMap != null) {
                    if (latestOnTapMap != null) {
                        detectTapGestures(onTap = { latestOnTapMap?.invoke() })
                    }
                }
                .pointerInput(widthPx, heightPx) {
                    detectTransformGestures(panZoomLock = false) { centroid, pan, zoomChange, _ ->
                        val curProj = latestProjection
                        val movedEnough = pan.getDistance() > 0.5f
                        val zoomedEnough = abs(zoomChange - 1f) > 0.002f
                        if (!movedEnough && !zoomedEnough) return@detectTransformGestures

                        val curZoom = latestZoom
                        val zoomDelta = ln(zoomChange.coerceIn(0.5f, 2.0f).toDouble()).toFloat() / ln(2.0).toFloat()
                        val nextZoom = (curZoom + zoomDelta).coerceIn(10.5f, 17.5f)

                        val pannedCenter = curProj.panByScreenDelta(-pan.x, -pan.y)
                        val finalCenter = if (zoomedEnough) {
                            val anchorLatLon = curProj.unproject(centroid.x - pan.x, centroid.y - pan.y)
                            val zoomedProj = SlippyMapMath.buildCameraProjection(
                                center = pannedCenter,
                                continuousZoom = nextZoom,
                                widthPx = curProj.widthPx,
                                heightPx = curProj.heightPx
                            )
                            val (ax, ay) = zoomedProj.project(anchorLatLon)
                            zoomedProj.panByScreenDelta(ax - centroid.x, ay - centroid.y)
                        } else {
                            pannedCenter
                        }

                        onUserGestureCamera(finalCenter, nextZoom, movedEnough)
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            val gridStep = 48f
            var gx = 0f
            while (gx < w) {
                drawLine(Color(0xFFDDE7F0), Offset(gx, 0f), Offset(gx, h), strokeWidth = 1.5f)
                gx += gridStep
            }
            var gy = 0f
            while (gy < h) {
                drawLine(Color(0xFFDDE7F0), Offset(0f, gy), Offset(w, gy), strokeWidth = 1.5f)
                gy += gridStep
            }

            visibleTiles.forEach { tile ->
                val imageEntry = decodedTiles[tile.coord.key]
                if (imageEntry != null) {
                    val drawSize = ceil(tile.sizePx).toInt().coerceAtLeast(1) + 1
                    drawImage(
                        image = imageEntry.first,
                        dstOffset = IntOffset(tile.leftPx.toInt(), tile.topPx.toInt()),
                        dstSize = IntSize(drawSize, drawSize)
                    )
                }
            }

            fun project(pt: LatLon): Offset {
                val (px, py) = projection.project(pt)
                return Offset(px, py)
            }

            val ordered = routes.sortedBy { it.id == selectedRouteId }
            ordered.forEach { route ->
                val isSelected = route.id == selectedRouteId
                val activeGeom = if (isSelected && !remainingPolyline.isNullOrEmpty() && remainingPolyline.size >= 2) {
                    remainingPolyline
                } else {
                    route.geometry
                }
                if (activeGeom.size >= 2) {
                    val path = Path().apply {
                        val first = project(activeGeom.first())
                        moveTo(first.x, first.y)
                        activeGeom.drop(1).forEach { pt ->
                            val o = project(pt)
                            lineTo(o.x, o.y)
                        }
                    }
                    if (isSelected) {
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = 0.88f),
                            style = Stroke(width = 16f, cap = StrokeCap.Round)
                        )
                    }
                    if (isSelected && route.coloredSegments.isNotEmpty() && remainingPolyline.isNullOrEmpty()) {
                        route.coloredSegments.forEach { seg ->
                            if (seg.points.size >= 2) {
                                val segPath = Path().apply {
                                    val first = project(seg.points.first())
                                    moveTo(first.x, first.y)
                                    seg.points.drop(1).forEach { pt ->
                                        val o = project(pt)
                                        lineTo(o.x, o.y)
                                    }
                                }
                                val segColor = when (seg.safetyLevel) {
                                    SegmentSafetyLevel.HIGH_CROWD_LIT -> NirapadPositiveGreen
                                    SegmentSafetyLevel.MODERATE_ACTIVITY -> NirapadPrimaryTeal
                                    SegmentSafetyLevel.LOW_CROWD_ISOLATED -> NirapadCautionAmber
                                }
                                drawPath(
                                    path = segPath,
                                    color = segColor,
                                    style = Stroke(width = 10f, cap = StrokeCap.Round)
                                )
                            }
                        }
                    } else {
                        val color = when {
                            !isSelected -> Color(0xFF627D98)
                            route.safetyTier == SafetyTier.LIMITED_DATA -> NirapadCautionAmber
                            route.safetyTier == SafetyTier.HIGHER_CONTEXT -> NirapadPositiveGreen
                            else -> NirapadPrimaryTeal
                        }
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(
                                width = if (isSelected) 10f else 5f,
                                cap = StrokeCap.Round,
                                pathEffect = if (isSelected) null else PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
                            )
                        )
                    }
                }
            }

            if (breadcrumbs.size >= 2) {
                val crumbPath = Path().apply {
                    val first = project(breadcrumbs.first())
                    moveTo(first.x, first.y)
                    breadcrumbs.drop(1).forEach { pt ->
                        val o = project(pt)
                        lineTo(o.x, o.y)
                    }
                }
                drawPath(
                    path = crumbPath,
                    color = NirapadNavyDark.copy(alpha = 0.85f),
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )
            }

            facilities.take(8).forEach { fac ->
                val o = project(LatLon(fac.lat, fac.lon))
                drawCircle(Color.White, radius = 11f, center = o)
                drawCircle(NirapadPositiveGreen, radius = 7f, center = o)
            }

            nearbyPoliceStations.take(8).forEach { st ->
                val o = project(LatLon(st.lat, st.lon))
                drawCircle(Color.White, radius = 12f, center = o)
                drawCircle(NirapadNavyDark, radius = 8f, center = o)
            }

            routes.firstOrNull { it.id == selectedRouteId }?.geometry?.lastOrNull()?.let { dest ->
                val d = project(dest)
                drawCircle(Color.White, radius = 16f, center = d)
                drawCircle(NirapadNavyDark, radius = 10.5f, center = d)
            }

            val me = project(animatedMarkerLocation)
            val beaconColor = if (isDeviated) NirapadEmergencyRed else LiveLocationBlue

            if (headingDegrees != null) {
                val coneRadius = 56f
                val startAngle = headingDegrees - 90f - 28f
                drawArc(
                    brush = Brush.radialGradient(
                        colors = listOf(beaconColor.copy(alpha = 0.42f), beaconColor.copy(alpha = 0.0f)),
                        center = me,
                        radius = coneRadius
                    ),
                    startAngle = startAngle,
                    sweepAngle = 56f,
                    useCenter = true,
                    topLeft = Offset(me.x - coneRadius, me.y - coneRadius),
                    size = Size(coneRadius * 2f, coneRadius * 2f)
                )
                rotate(degrees = headingDegrees, pivot = me) {
                    val arrow = Path().apply {
                        moveTo(me.x, me.y - 28f)
                        lineTo(me.x + 9f, me.y - 14f)
                        lineTo(me.x, me.y - 18f)
                        lineTo(me.x - 9f, me.y - 14f)
                        close()
                    }
                    drawPath(arrow, color = beaconColor)
                }
            }

            val pulseFraction = pulseFractionState.value
            val pulseRadius = 22f + pulseFraction * 24f
            val pulseAlpha = ((1f - pulseFraction) * 0.34f).coerceIn(0.03f, 0.34f)
            drawCircle(
                color = beaconColor.copy(alpha = pulseAlpha),
                radius = pulseRadius,
                center = me
            )
            drawCircle(
                color = beaconColor.copy(alpha = 0.16f),
                radius = 24f,
                center = me
            )
            drawCircle(Color.White, radius = 14.5f, center = me)
            drawCircle(beaconColor, radius = 10f, center = me)
        }

        overlays(renderedTileCount, isOnline)
    }
}

@Composable
private fun ReCenterFloatingButton(
    isFollowingUser: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isFollowingUser) NirapadSurface.copy(alpha = 0.96f) else LiveLocationBlue
    val fgColor = if (isFollowingUser) LiveLocationBlue else Color.White
    val borderColor = if (isFollowingUser) LiveLocationBlue.copy(alpha = 0.45f) else LiveLocationBlue

    Surface(
        onClick = onClick,
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 4.dp,
        modifier = modifier.minimumInteractiveComponentSize()
    ) {
        AnimatedContent(
            targetState = isFollowingUser,
            label = "recenter_button_state"
        ) { following ->
            Row(
                modifier = Modifier.padding(
                    horizontal = if (compact && following) 10.dp else 12.dp,
                    vertical = if (compact) 8.dp else 10.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (following) Icons.Default.GpsFixed else Icons.Default.MyLocation,
                    contentDescription = if (following) "Auto-following live GPS" else "Re-center map on my location",
                    tint = fgColor,
                    modifier = Modifier.size(18.dp)
                )
                if (!compact || !following) {
                    Text(
                        text = if (following) "Following" else "Re-center",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = fgColor
                    )
                }
            }
        }
    }
}

@Composable
private fun MapLayerToggleButton(
    mapLayerStyle: MapLayerStyle,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSatellite = mapLayerStyle == MapLayerStyle.SATELLITE
    val bgColor = if (isSatellite) NirapadNavyDark.copy(alpha = 0.94f) else NirapadSurface.copy(alpha = 0.96f)
    val fgColor = if (isSatellite) Color.White else Color(0xFF0F172A)
    val borderColor = if (isSatellite) NirapadPrimaryTeal else NirapadBorderSubtle

    Surface(
        onClick = onClick,
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 4.dp,
        modifier = modifier.minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 7.dp else 9.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = if (isSatellite) Icons.Default.Map else Icons.Default.SatelliteAlt,
                contentDescription = if (isSatellite) "Switch to Street View" else "Switch to Satellite View",
                tint = fgColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (isSatellite) "Street" else "Satellite",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = fgColor
            )
        }
    }
}

@Composable
private fun MapCacheStatusBadge(
    isOnline: Boolean,
    mapLayerStyle: MapLayerStyle,
    renderedTileCount: Int,
    cachedTileCount: Int,
    modifier: Modifier = Modifier
) {
    val statusBg: Color
    val statusFg: Color
    val statusLabel: String
    val statusIcon = when {
        !isOnline -> Icons.Default.CloudOff
        mapLayerStyle == MapLayerStyle.SATELLITE -> Icons.Default.Layers
        else -> Icons.Default.Map
    }
    when {
        !isOnline && renderedTileCount > 0 -> {
            statusBg = NirapadCautionContainer.copy(alpha = 0.95f)
            statusFg = Color(0xFF0F172A)
            statusLabel = "Offline Cache • $cachedTileCount tiles"
        }
        mapLayerStyle == MapLayerStyle.SATELLITE -> {
            statusBg = NirapadSurface.copy(alpha = 0.96f)
            statusFg = Color(0xFF0F172A)
            statusLabel = "Satellite • Esri"
        }
        isOnline && renderedTileCount > 0 -> {
            statusBg = NirapadSurface.copy(alpha = 0.94f)
            statusFg = NirapadPrimaryTeal
            statusLabel = if (cachedTileCount > 0) "OSM Live • $cachedTileCount cached" else "OSM Street Map"
        }
        cachedTileCount > 0 -> {
            statusBg = NirapadSurface.copy(alpha = 0.94f)
            statusFg = Color(0xFF0F172A)
            statusLabel = "Offline Grid • $cachedTileCount cached"
        }
        else -> {
            statusBg = NirapadSurface.copy(alpha = 0.94f)
            statusFg = Color(0xFF475569)
            statusLabel = if (isOnline) "OSM Street Map" else "Offline Grid"
        }
    }
    Surface(
        color = statusBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NirapadBorderSubtle),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(statusIcon, contentDescription = null, tint = statusFg, modifier = Modifier.size(13.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = statusFg
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF0F172A))
    }
}

private fun computeBearingDegrees(from: LatLon, to: LatLon): Float {
    val lat1 = Math.toRadians(from.lat)
    val lat2 = Math.toRadians(to.lat)
    val dLon = Math.toRadians(to.lon - from.lon)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val deg = Math.toDegrees(atan2(y, x))
    return ((deg + 360.0) % 360.0).toFloat()
}

private fun shortestAngleDelta(fromDeg: Float, toDeg: Float): Float {
    return ((toDeg - fromDeg + 540f) % 360f) - 180f
}
