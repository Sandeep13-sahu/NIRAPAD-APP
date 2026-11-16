package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.auth.AuthManager
import com.example.auth.AuthManager.authStateFlow
import com.example.data.cache.MapTileCache
import com.example.data.firestore.UiState
import com.example.data.model.JourneyState
import com.example.data.repository.AiAdvisorRepository
import com.example.data.repository.NirapadRepository
import com.example.data.service.DeviceCompassTracker
import com.example.data.service.LocationTracker
import com.example.data.service.RoutingAndSafetyEngine
import com.example.localization.NirapadStrings
import com.example.service.SosLocationSharingService
import com.example.service.SosSirenPlayer
import com.example.ui.components.ActiveSosBroadcastBanner
import com.example.ui.components.ActiveSosPulseOverlay
import com.example.ui.components.AiAdvisorModal
import com.example.ui.components.DeliberateSosFab
import com.example.ui.screens.AdminModerationWorkspaceScreen
import com.example.ui.screens.AdvisoryAiNavigationScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.EmergencyScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.JourneyScreen
import com.example.ui.screens.PlanRouteScreen
import com.example.ui.screens.ProfileAdminScreen
import com.example.ui.screens.SosActivationScreen
import com.example.ui.screens.TrustedContactsScreen
import com.example.ui.screens.VerifiedEmergencyDirectoryScreen
import com.example.ui.theme.NirapadCanvas
import com.example.ui.theme.NirapadNavyDark
import com.example.ui.theme.NirapadPrimaryTeal
import com.example.ui.theme.NirapadPrimaryTealContainer
import com.example.ui.theme.NirapadSurface
import com.example.ui.theme.NirapadTheme
import com.example.viewmodel.NirapadViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object NirapadRoutes {
    const val HOME = "home"
    const val PLAN = "plan"
    const val ADVISORY_NAVIGATION = "advisory_navigation"
    const val JOURNEY = "journey"
    const val EMERGENCY = "emergency"
    const val SOS_ACTIVATION = "sos_activation"
    const val EMERGENCY_DIRECTORY = "emergency_directory"
    const val PROFILE = "profile"
    const val TRUSTED_CONTACTS = "trusted_contacts"
    const val ADMIN_MODERATION_WORKSPACE = "admin_moderation_workspace"

    fun sosActivationRoute(autoStart: Boolean = false): String =
        "$SOS_ACTIVATION?autoStart=$autoStart"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    AnimatedContent(
        targetState = currentUser?.uid,
        transitionSpec = {
            (fadeIn(animationSpec = tween(320)) + scaleIn(initialScale = 0.97f, animationSpec = tween(320)))
                .togetherWith(fadeOut(animationSpec = tween(220)))
        },
        label = "auth_gate_transition"
    ) { uid ->
        val activeUser = auth.currentUser
        if (uid == null || activeUser == null) {
            AuthScreen(onAuthSuccess = { /* AuthStateListener drives navigation */ })
        } else {
            key(uid) {
                AuthenticatedNirapadShell(
                    currentUserId = uid,
                    userName = activeUser.displayName ?: "Traveller",
                    userEmail = activeUser.email ?: ""
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedNirapadShell(
    currentUserId: String,
    userName: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: NirapadViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                NirapadViewModel(
                    repository = NirapadRepository(db),
                    aiAdvisor = AiAdvisorRepository(),
                    currentUserId = currentUserId,
                    currentUserName = userName,
                    currentUserEmail = userEmail,
                    mapTileCache = MapTileCache.getInstance(app)
                )
            }
        }
    )

    val language by viewModel.language.collectAsStateWithLifecycle()
    val copy = NirapadStrings.get(language)
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val profile by viewModel.profileState.collectAsStateWithLifecycle()
    val contactsState by viewModel.contactsState.collectAsStateWithLifecycle()
    val journeysState by viewModel.journeysState.collectAsStateWithLifecycle()
    val sosState by viewModel.sosSessionsState.collectAsStateWithLifecycle()
    val myReports by viewModel.myReportsState.collectAsStateWithLifecycle()
    val adminReports by viewModel.adminReportsState.collectAsStateWithLifecycle()
    val adminReportsUiState by viewModel.adminReportsUiState.collectAsStateWithLifecycle()
    val directoryFacilitiesState by viewModel.directoryFacilitiesState.collectAsStateWithLifecycle()
    val firestoreFacilities by viewModel.firestoreFacilitiesState.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogsState.collectAsStateWithLifecycle()

    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val hasLiveGpsFix by viewModel.hasLiveGpsFix.collectAsStateWithLifecycle()
    val compassHeading by viewModel.compassHeading.collectAsStateWithLifecycle()
    val locationPermissionGranted by viewModel.locationPermissionGranted.collectAsStateWithLifecycle()
    val effectiveLocation = remember(currentLocation, compassHeading) {
        val preferGpsBearing = currentLocation.bearingDegrees != null && (currentLocation.speedMps ?: 0f) >= 0.8f
        val heading = if (preferGpsBearing) currentLocation.bearingDegrees else (compassHeading ?: currentLocation.bearingDegrees)
        currentLocation.copy(bearingDegrees = heading)
    }
    val destinationQuery by viewModel.destinationQuery.collectAsStateWithLifecycle()
    val routeSearchState by viewModel.routeSearchState.collectAsStateWithLifecycle()
    val selectedRoute by viewModel.selectedRoute.collectAsStateWithLifecycle()
    val breadcrumbs by viewModel.activeBreadcrumbs.collectAsStateWithLifecycle()
    val deviationDistanceM by viewModel.deviationDistanceM.collectAsStateWithLifecycle()
    val shareLookupState by viewModel.shareLookupState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val advisoryTravelWindow by viewModel.advisoryTravelWindow.collectAsStateWithLifecycle()
    val advisoryComparisonState by viewModel.advisoryComparisonState.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val tileCacheStats by viewModel.tileCacheStats.collectAsStateWithLifecycle()

    val contacts = (contactsState as? UiState.Success)?.data ?: emptyList()
    val journeys = (journeysState as? UiState.Success)?.data ?: emptyList()
    val activeJourney = journeys.firstOrNull { !JourneyState.fromValue(it.state).isTerminal }
    val sosSessions = (sosState as? UiState.Success)?.data ?: emptyList()
    val activeSos = sosSessions.firstOrNull { it.status == "ACTIVE" }
    val routes = (routeSearchState as? UiState.Success)?.data?.routes ?: emptyList()
    val allFacilities = remember(firestoreFacilities) {
        RoutingAndSafetyEngine.verifiedNationalHelplines + firestoreFacilities
    }

    val isSosServiceRunning by SosLocationSharingService.isRunning.collectAsStateWithLifecycle()
    val sosBroadcastCount by SosLocationSharingService.broadcastCount.collectAsStateWithLifecycle()
    val sosLastBroadcastReading by SosLocationSharingService.lastBroadcastReading.collectAsStateWithLifecycle()
    val isSirenPlaying by SosSirenPlayer.isPlaying.collectAsStateWithLifecycle()
    val isSirenMuted by SosSirenPlayer.isMuted.collectAsStateWithLifecycle()

    var showAiAdvisor by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Service notification updates if granted; background location tracking works regardless */ }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.setLocationPermission(granted)
    }

    fun requestLocationAndNotificationPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        locationPermissionLauncher.launch(perms.toTypedArray())
    }

    LaunchedEffect(Unit) {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        viewModel.setLocationPermission(hasPerm)
    }

    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted) {
            val tracker = LocationTracker(context)
            tracker.observeLocationUpdates(1500L).collect { reading ->
                viewModel.onLiveLocationReading(reading)
            }
        }
    }

    LaunchedEffect(Unit) {
        val compass = DeviceCompassTracker(context)
        compass.observeCompassAzimuth().collect { azimuth ->
            viewModel.onCompassHeading(azimuth)
        }
    }

    LaunchedEffect(activeSos?.id, activeSos?.shareTokenId, locationPermissionGranted) {
        if (activeSos != null && locationPermissionGranted) {
            SosLocationSharingService.start(
                context = context,
                sosId = activeSos.id,
                shareTokenId = activeSos.shareTokenId,
                journeyId = activeSos.journeyId
            )
        } else if (activeSos == null) {
            if (isSosServiceRunning) {
                SosLocationSharingService.stop(context)
            }
            if (isSirenPlaying) {
                SosSirenPlayer.stopSiren(context)
            }
        }
    }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: NirapadRoutes.HOME

    val destinations = listOf(
        NavDestination(NirapadRoutes.HOME, copy.navHome, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
        NavDestination(NirapadRoutes.PLAN, copy.navPlan, Icons.AutoMirrored.Filled.AltRoute, Icons.AutoMirrored.Outlined.AltRoute, "nav_plan"),
        NavDestination(NirapadRoutes.JOURNEY, copy.navJourney, Icons.Filled.Navigation, Icons.Outlined.Navigation, "nav_journey"),
        NavDestination(NirapadRoutes.EMERGENCY, copy.navEmergency, Icons.Filled.Warning, Icons.Outlined.Warning, "nav_emergency"),
        NavDestination(NirapadRoutes.PROFILE, copy.navContacts, Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
    )

    fun navigateTab(route: String) {
        navController.navigate(route) {
            popUpTo(NirapadRoutes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openSosActivation(autoStart: Boolean) {
        navController.navigate(NirapadRoutes.sosActivationRoute(autoStart = autoStart)) {
            launchSingleTop = true
        }
    }

    val isOnSosActivationScreen = currentRoute.startsWith(NirapadRoutes.SOS_ACTIVATION)

    NirapadTheme(darkTheme = false) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(NirapadCanvas)) {
        val isWideScreen = maxWidth >= 680.dp

        Scaffold(
            containerColor = NirapadCanvas,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = NirapadSurface,
                            titleContentColor = NirapadNavyDark
                        ),
                        title = {
                            Column {
                                Text(copy.appTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(copy.tagline, style = MaterialTheme.typography.labelSmall, color = NirapadPrimaryTeal)
                            }
                        },
                        actions = {
                            if (isAdmin) {
                                IconButton(
                                    onClick = {
                                        navController.navigate(NirapadRoutes.ADMIN_MODERATION_WORKSPACE) {
                                            launchSingleTop = true
                                        }
                                    },
                                    modifier = Modifier.testTag("topbar_admin_moderation_button")
                                ) {
                                    Icon(Icons.Default.AdminPanelSettings, contentDescription = copy.adminModerationTitle, tint = NirapadNavyDark)
                                }
                            }
                            IconButton(
                                onClick = {
                                    navController.navigate(NirapadRoutes.TRUSTED_CONTACTS) {
                                        launchSingleTop = true
                                    }
                                },
                                modifier = Modifier.testTag("topbar_contacts_button")
                            ) {
                                Icon(Icons.Default.Group, contentDescription = copy.trustedContactsTitle, tint = NirapadNavyDark)
                            }
                            IconButton(
                                onClick = {
                                    navController.navigate(NirapadRoutes.ADVISORY_NAVIGATION) {
                                        launchSingleTop = true
                                    }
                                },
                                modifier = Modifier.testTag("topbar_ai_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = copy.advisoryNavTitle, tint = NirapadPrimaryTeal)
                            }
                        }
                    )
                    AnimatedVisibility(
                        visible = activeSos != null,
                        enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                        exit = shrinkVertically(tween(250)) + fadeOut(tween(250))
                    ) {
                        ActiveSosBroadcastBanner(
                            title = copy.sosBroadcastingTitle,
                            subtitle = copy.sosBroadcastingSubtitle,
                            isLiveSharingRunning = isSosServiceRunning,
                            broadcastCount = sosBroadcastCount,
                            onClick = { openSosActivation(autoStart = false) }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (!isOnSosActivationScreen) {
                    DeliberateSosFab(
                        label = copy.emergencySosLabel,
                        activeLabel = copy.sosActiveFabLabel,
                        isSosActive = activeSos != null,
                        isLiveSharingRunning = isSosServiceRunning,
                        onClick = {
                            if (activeSos != null) {
                                openSosActivation(autoStart = false)
                            } else {
                                openSosActivation(autoStart = true)
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(containerColor = NirapadSurface) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route ||
                                (dest.route == NirapadRoutes.PLAN && currentRoute == NirapadRoutes.ADVISORY_NAVIGATION) ||
                                (dest.route == NirapadRoutes.PROFILE && (currentRoute == NirapadRoutes.TRUSTED_CONTACTS || currentRoute == NirapadRoutes.ADMIN_MODERATION_WORKSPACE)) ||
                                (dest.route == NirapadRoutes.EMERGENCY && (currentRoute == NirapadRoutes.EMERGENCY_DIRECTORY || isOnSosActivationScreen))
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        maxLines = 1,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NirapadPrimaryTeal,
                                    selectedTextColor = Color(0xFF0F172A),
                                    indicatorColor = NirapadPrimaryTealContainer,
                                    unselectedIconColor = Color(0xFF64748B),
                                    unselectedTextColor = Color(0xFF475569)
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = NirapadSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route ||
                                (dest.route == NirapadRoutes.PLAN && currentRoute == NirapadRoutes.ADVISORY_NAVIGATION) ||
                                (dest.route == NirapadRoutes.PROFILE && (currentRoute == NirapadRoutes.TRUSTED_CONTACTS || currentRoute == NirapadRoutes.ADMIN_MODERATION_WORKSPACE)) ||
                                (dest.route == NirapadRoutes.EMERGENCY && (currentRoute == NirapadRoutes.EMERGENCY_DIRECTORY || isOnSosActivationScreen))
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = NirapadPrimaryTeal,
                                    selectedTextColor = Color(0xFF0F172A),
                                    indicatorColor = NirapadPrimaryTealContainer,
                                    unselectedIconColor = Color(0xFF64748B),
                                    unselectedTextColor = Color(0xFF475569)
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = NirapadRoutes.HOME,
                        modifier = Modifier.widthIn(max = 820.dp).fillMaxSize()
                    ) {
                        composable(NirapadRoutes.HOME) {
                            HomeScreen(
                                copy = copy,
                                currentLocation = effectiveLocation,
                                hasLiveGpsFix = hasLiveGpsFix,
                                locationPermissionGranted = locationPermissionGranted,
                                destinationQuery = destinationQuery,
                                routes = routes,
                                selectedRoute = selectedRoute,
                                activeJourney = activeJourney,
                                facilities = allFacilities,
                                onRequestLocationPermission = { requestLocationAndNotificationPermissions() },
                                onDestinationQueryChange = viewModel::updateDestinationQuery,
                                onSearchRoutes = { preset ->
                                    viewModel.searchRoutes(preset)
                                    navigateTab(NirapadRoutes.PLAN)
                                },
                                onOpenJourney = { navigateTab(NirapadRoutes.JOURNEY) },
                                onOpenEmergencyDirectory = {
                                    navController.navigate(NirapadRoutes.EMERGENCY_DIRECTORY) {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenAiAdvisor = {
                                    navController.navigate(NirapadRoutes.ADVISORY_NAVIGATION) {
                                        launchSingleTop = true
                                    }
                                },
                                onRequestSosConfirm = { openSosActivation(autoStart = true) }
                            )
                        }

                        composable(NirapadRoutes.PLAN) {
                            PlanRouteScreen(
                                copy = copy,
                                currentLocation = effectiveLocation,
                                hasLiveGpsFix = hasLiveGpsFix,
                                locationPermissionGranted = locationPermissionGranted,
                                destinationQuery = destinationQuery,
                                routeSearchState = routeSearchState,
                                selectedRoute = selectedRoute,
                                contacts = contacts,
                                facilities = allFacilities,
                                onDestinationQueryChange = viewModel::updateDestinationQuery,
                                onSearchRoutes = { viewModel.searchRoutes() },
                                onRequestLocationPermission = { requestLocationAndNotificationPermissions() },
                                onSelectRoute = viewModel::selectRoute,
                                onStartJourney = { share, count, mins ->
                                    viewModel.startJourney(share, count, mins)
                                    navigateTab(NirapadRoutes.JOURNEY)
                                },
                                onOpenAiAdvisor = { showAiAdvisor = true },
                                onOpenAdvisoryAiNavigation = {
                                    navController.navigate(NirapadRoutes.ADVISORY_NAVIGATION) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable(NirapadRoutes.ADVISORY_NAVIGATION) {
                            AdvisoryAiNavigationScreen(
                                copy = copy,
                                currentLocation = effectiveLocation,
                                hasLiveGpsFix = hasLiveGpsFix,
                                locationPermissionGranted = locationPermissionGranted,
                                destinationQuery = destinationQuery,
                                travelWindowLabel = advisoryTravelWindow,
                                routeSearchState = routeSearchState,
                                advisoryComparisonState = advisoryComparisonState,
                                selectedRoute = selectedRoute,
                                facilities = allFacilities,
                                aiChatState = aiState,
                                onDestinationQueryChange = viewModel::updateDestinationQuery,
                                onTravelWindowChange = viewModel::updateAdvisoryTravelWindow,
                                onRequestLocationPermission = { requestLocationAndNotificationPermissions() },
                                onCompareWithAi = { query, window ->
                                    viewModel.compareRoutesWithAi(query, window)
                                },
                                onSelectRoute = viewModel::selectRoute,
                                onStartRouteJourney = { route ->
                                    viewModel.selectRoute(route)
                                    navigateTab(NirapadRoutes.PLAN)
                                },
                                onAskFollowUp = viewModel::askAiAdvisor,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(NirapadRoutes.JOURNEY) {
                            JourneyScreen(
                                copy = copy,
                                currentLocation = effectiveLocation,
                                hasLiveGpsFix = hasLiveGpsFix,
                                locationPermissionGranted = locationPermissionGranted,
                                activeJourney = activeJourney,
                                journeysHistory = journeys.filter { JourneyState.fromValue(it.state).isTerminal },
                                selectedRoute = selectedRoute,
                                breadcrumbs = breadcrumbs,
                                deviationDistanceM = deviationDistanceM,
                                facilities = allFacilities,
                                shareLookupState = shareLookupState,
                                onRequestLocationPermission = { requestLocationAndNotificationPermissions() },
                                onSimulateStep = viewModel::simulateStepAlongOrOffRoute,
                                onConfirmDeviationOk = viewModel::confirmDeviationIntentional,
                                onReroute = viewModel::rerouteActiveJourney,
                                onRequestSosConfirm = { openSosActivation(autoStart = true) },
                                onRevokeShare = viewModel::revokeShare,
                                onEndJourney = viewModel::endJourney,
                                onLookupShareToken = viewModel::lookupShareToken,
                                onGoToPlan = { navigateTab(NirapadRoutes.PLAN) }
                            )
                        }

                        composable(NirapadRoutes.EMERGENCY) {
                            EmergencyScreen(
                                copy = copy,
                                currentLocation = currentLocation,
                                sosSessions = sosSessions,
                                firestoreFacilities = firestoreFacilities,
                                myReports = myReports,
                                isBackgroundServiceRunning = isSosServiceRunning,
                                broadcastCount = sosBroadcastCount,
                                lastBroadcastReading = sosLastBroadcastReading,
                                locationPermissionGranted = locationPermissionGranted,
                                onRequestLocationPermission = { requestLocationAndNotificationPermissions() },
                                onRequestSosConfirm = { openSosActivation(autoStart = true) },
                                onOpenSosActivation = { openSosActivation(autoStart = false) },
                                onOpenDirectory = {
                                    navController.navigate(NirapadRoutes.EMERGENCY_DIRECTORY) {
                                        launchSingleTop = true
                                    }
                                },
                                onUpdateSosStatus = { session, status ->
                                    SosSirenPlayer.stopSiren(context)
                                    SosSirenPlayer.resetMuteState()
                                    SosLocationSharingService.stop(context)
                                    viewModel.resolveOrCancelSos(session, status)
                                },
                                onSubmitReport = viewModel::submitReport
                            )
                        }

                        composable(
                            route = "${NirapadRoutes.SOS_ACTIVATION}?autoStart={autoStart}",
                            arguments = listOf(
                                navArgument("autoStart") {
                                    type = NavType.BoolType
                                    defaultValue = false
                                }
                            )
                        ) { entry ->
                            val autoStart = entry.arguments?.getBoolean("autoStart") ?: false
                            SosActivationScreen(
                                copy = copy,
                                currentLocation = currentLocation,
                                hasLiveGpsFix = hasLiveGpsFix,
                                locationPermissionGranted = locationPermissionGranted,
                                contactsState = contactsState,
                                activeSos = activeSos,
                                isBackgroundServiceRunning = isSosServiceRunning,
                                broadcastCount = sosBroadcastCount,
                                lastBroadcastReading = sosLastBroadcastReading,
                                autoStartCountdown = autoStart,
                                isSirenPlaying = isSirenPlaying,
                                isSirenMuted = isSirenMuted,
                                onToggleSiren = { muted ->
                                    SosSirenPlayer.setMuted(
                                        context = context,
                                        muted = muted,
                                        resumeIfActive = activeSos != null
                                    )
                                },
                                onRequestLocationPermission = { requestLocationAndNotificationPermissions() },
                                onManageContacts = {
                                    navController.navigate(NirapadRoutes.TRUSTED_CONTACTS) {
                                        launchSingleTop = true
                                    }
                                },
                                onActivateSos = { alertReason ->
                                    if (!isSirenMuted) {
                                        SosSirenPlayer.startSiren(context)
                                    }
                                    viewModel.triggerSos(activeJourney, alertReason) { session ->
                                        if (locationPermissionGranted) {
                                            SosLocationSharingService.start(
                                                context = context,
                                                sosId = session.id,
                                                shareTokenId = session.shareTokenId,
                                                journeyId = session.journeyId
                                            )
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        } else {
                                            requestLocationAndNotificationPermissions()
                                        }
                                    }
                                },
                                onUpdateSosStatus = { session, status ->
                                    SosSirenPlayer.stopSiren(context)
                                    SosSirenPlayer.resetMuteState()
                                    SosLocationSharingService.stop(context)
                                    viewModel.resolveOrCancelSos(session, status)
                                },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(NirapadRoutes.EMERGENCY_DIRECTORY) {
                            VerifiedEmergencyDirectoryScreen(
                                copy = copy,
                                currentLocation = currentLocation,
                                hasLiveGpsFix = hasLiveGpsFix,
                                locationPermissionGranted = locationPermissionGranted,
                                facilitiesState = directoryFacilitiesState,
                                isAdmin = isAdmin,
                                onRequestLocationPermission = { requestLocationAndNotificationPermissions() },
                                onRefreshDirectory = viewModel::refreshEmergencyDirectory,
                                onAddVerifiedFacility = viewModel::addVerifiedFacility,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(NirapadRoutes.PROFILE) {
                            ProfileAdminScreen(
                                copy = copy,
                                profile = profile,
                                contacts = contacts,
                                language = language,
                                isAdmin = isAdmin,
                                adminReports = adminReports,
                                auditLogs = auditLogs,
                                onSaveContact = viewModel::saveContact,
                                onDeleteContact = viewModel::deleteContact,
                                onOpenTrustedContacts = {
                                    navController.navigate(NirapadRoutes.TRUSTED_CONTACTS) {
                                        launchSingleTop = true
                                    }
                                },
                                onUpdateSettings = viewModel::updateSettings,
                                onPurgeHistory = viewModel::purgeHistory,
                                onModerateReport = viewModel::moderateReport,
                                onOpenModerationWorkspace = {
                                    navController.navigate(NirapadRoutes.ADMIN_MODERATION_WORKSPACE) {
                                        launchSingleTop = true
                                    }
                                },
                                onAddVerifiedFacility = viewModel::addVerifiedFacility,
                                onSignOut = {
                                    SosSirenPlayer.stopSiren(context)
                                    SosSirenPlayer.resetMuteState()
                                    AuthManager.signOut(credentialManager, scope)
                                },
                                tileCacheStats = tileCacheStats,
                                onPrefetchOfflineMap = viewModel::prefetchCurrentAreaTiles,
                                onClearMapTileCache = viewModel::clearMapTileCache
                            )
                        }

                        composable(NirapadRoutes.TRUSTED_CONTACTS) {
                            TrustedContactsScreen(
                                copy = copy,
                                contactsState = contactsState,
                                onSaveContact = viewModel::saveContact,
                                onDeleteContact = viewModel::deleteContact,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(NirapadRoutes.ADMIN_MODERATION_WORKSPACE) {
                            AdminModerationWorkspaceScreen(
                                copy = copy,
                                isAdmin = isAdmin,
                                reportsState = adminReportsUiState,
                                auditLogs = auditLogs,
                                onRefreshReports = viewModel::refreshAdminReports,
                                onModerateReport = viewModel::moderateReport,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = activeSos != null,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250))
        ) {
            ActiveSosPulseOverlay(isLiveSharingRunning = isSosServiceRunning)
        }
    }
    }

    if (showAiAdvisor) {
        AiAdvisorModal(
            copy = copy,
            aiState = aiState,
            onAsk = viewModel::askAiAdvisor,
            onDismiss = { showAiAdvisor = false }
        )
    }
}
