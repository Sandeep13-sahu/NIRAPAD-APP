package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cache.MapTileCache
import com.example.data.cache.MapTileCacheStats
import com.example.data.firestore.UiState
import com.example.data.model.AdvisoryNavigationComparison
import com.example.data.model.AuditLog
import com.example.data.model.EmergencyContact
import com.example.data.model.EmergencyDirectoryCategory
import com.example.data.model.EmergencyFacility
import com.example.data.model.Journey
import com.example.data.model.JourneyState
import com.example.data.model.JourneyStateMachine
import com.example.data.model.LatLon
import com.example.data.model.NearbyPoliceStation
import com.example.data.model.PlaceSuggestion
import com.example.data.model.RouteOption
import com.example.data.model.RouteProgress
import com.example.data.model.RouteSearchResult
import com.example.data.model.SafetyReport
import com.example.data.model.ShareToken
import com.example.data.model.SosSession
import com.example.data.model.UserProfile
import com.example.data.model.WeatherContext
import com.example.data.repository.AdvisoryAiClient
import com.example.data.repository.AiAdvisorUiState
import com.example.data.repository.NirapadRepository
import com.example.data.service.FreeSafetyApisClient
import com.example.data.service.LocationReading
import com.example.data.service.RoutingAndSafetyEngine
import com.example.localization.AppLang
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class NirapadViewModel(
    private val repository: NirapadRepository,
    private val aiAdvisor: AdvisoryAiClient,
    val currentUserId: String,
    private val currentUserName: String,
    private val currentUserEmail: String,
    private val mapTileCache: MapTileCache? = null,
    private val freeApisClient: FreeSafetyApisClient = RoutingAndSafetyEngine.defaultApiClient
) : ViewModel() {

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _adminCheckCompleted = MutableStateFlow(false)
    private val _adminRefreshTick = MutableStateFlow(0L)

    private val _language = MutableStateFlow(AppLang.EN)
    val language: StateFlow<AppLang> = _language.asStateFlow()

    private val _currentLocation = MutableStateFlow(
        LocationReading(
            lat = 28.6315,
            lon = 77.2167,
            accuracyMeters = 25.0,
            timestampMillis = 0L // 0 => not yet live GPS fix
        )
    )
    val currentLocation: StateFlow<LocationReading> = _currentLocation.asStateFlow()

    private val _hasLiveGpsFix = MutableStateFlow(false)
    val hasLiveGpsFix: StateFlow<Boolean> = _hasLiveGpsFix.asStateFlow()

    private val _compassHeading = MutableStateFlow<Float?>(null)
    val compassHeading: StateFlow<Float?> = _compassHeading.asStateFlow()

    private val _locationPermissionGranted = MutableStateFlow(false)
    val locationPermissionGranted: StateFlow<Boolean> = _locationPermissionGranted.asStateFlow()

    private val _destinationQuery = MutableStateFlow("Hauz Khas Metro Gate 1")
    val destinationQuery: StateFlow<String> = _destinationQuery.asStateFlow()

    private val _autocompleteInput = MutableStateFlow("")
    private val _selectedPlaceSuggestion = MutableStateFlow<PlaceSuggestion?>(null)
    val selectedPlaceSuggestion: StateFlow<PlaceSuggestion?> = _selectedPlaceSuggestion.asStateFlow()

    private val _placeSuggestions = MutableStateFlow<List<PlaceSuggestion>>(emptyList())
    val placeSuggestions: StateFlow<List<PlaceSuggestion>> = _placeSuggestions.asStateFlow()

    private val _isSearchingPlaces = MutableStateFlow(false)
    val isSearchingPlaces: StateFlow<Boolean> = _isSearchingPlaces.asStateFlow()

    private val _currentAddressLabel = MutableStateFlow<String?>(null)
    val currentAddressLabel: StateFlow<String?> = _currentAddressLabel.asStateFlow()

    private val _weatherContext = MutableStateFlow<WeatherContext?>(null)
    val weatherContext: StateFlow<WeatherContext?> = _weatherContext.asStateFlow()

    private val _nearbyPoliceStations = MutableStateFlow<List<NearbyPoliceStation>>(emptyList())
    val nearbyPoliceStations: StateFlow<List<NearbyPoliceStation>> = _nearbyPoliceStations.asStateFlow()

    private val _isLoadingPoliceStations = MutableStateFlow(false)
    val isLoadingPoliceStations: StateFlow<Boolean> = _isLoadingPoliceStations.asStateFlow()

    private val _routeSearchState = MutableStateFlow<UiState<RouteSearchResult>?>(null)
    val routeSearchState: StateFlow<UiState<RouteSearchResult>?> = _routeSearchState.asStateFlow()

    private val _selectedRoute = MutableStateFlow<RouteOption?>(null)
    val selectedRoute: StateFlow<RouteOption?> = _selectedRoute.asStateFlow()

    private val _routeProgress = MutableStateFlow<RouteProgress?>(null)
    val routeProgress: StateFlow<RouteProgress?> = _routeProgress.asStateFlow()

    private val _arrivedJourneyId = MutableStateFlow<String?>(null)
    val arrivedJourneyId: StateFlow<String?> = _arrivedJourneyId.asStateFlow()

    private val _activeBreadcrumbs = MutableStateFlow<List<LatLon>>(emptyList())
    val activeBreadcrumbs: StateFlow<List<LatLon>> = _activeBreadcrumbs.asStateFlow()

    private val _deviationDistanceM = MutableStateFlow(0)
    val deviationDistanceM: StateFlow<Int> = _deviationDistanceM.asStateFlow()

    private val _shareLookupState = MutableStateFlow<UiState<ShareToken>?>(null)
    val shareLookupState: StateFlow<UiState<ShareToken>?> = _shareLookupState.asStateFlow()

    private val _aiState = MutableStateFlow<AiAdvisorUiState>(AiAdvisorUiState.Idle)
    val aiState: StateFlow<AiAdvisorUiState> = _aiState.asStateFlow()

    private val _advisoryTravelWindow = MutableStateFlow("Current Time")
    val advisoryTravelWindow: StateFlow<String> = _advisoryTravelWindow.asStateFlow()

    private val _advisoryComparisonState = MutableStateFlow<UiState<AdvisoryNavigationComparison>?>(null)
    val advisoryComparisonState: StateFlow<UiState<AdvisoryNavigationComparison>?> = _advisoryComparisonState.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private var lastDeviationPromptAtMs = 0L
    private var locationSequence = 0L
    private var shareLookupJob: Job? = null
    private var lastPrefetchedLocation: LatLon? = null
    private var prefetchJob: Job? = null
    private var lastReverseGeocodeLoc: LatLon? = null
    private var lastReverseGeocodeAtMs = 0L
    private var lastContextFetchLoc: LatLon? = null
    private var lastContextFetchAtMs = 0L
    private val dismissedArrivalJourneyIds = mutableSetOf<String>()

    val tileCacheStats: StateFlow<MapTileCacheStats> = (mapTileCache?.statsFlow ?: flowOf(MapTileCacheStats()))
        .catch { e ->
            if (e is CancellationException) throw e
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), MapTileCacheStats())

    val profileState: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { e ->
            if (e is CancellationException && e.cause !is FirebaseFirestoreException) throw e
            Log.w(TAG, "Profile observe error", e)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val contactsState: StateFlow<UiState<List<EmergencyContact>>> = repository.observeContacts(currentUserId)
        .map<List<EmergencyContact>, UiState<List<EmergencyContact>>> { UiState.Success(it) }
        .catch { error ->
            val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fe == null && error is CancellationException) throw error
            emit(UiState.Error((fe ?: error).message ?: "Failed to load trusted contacts"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val journeysState: StateFlow<UiState<List<Journey>>> = repository.observeJourneys(currentUserId)
        .map<List<Journey>, UiState<List<Journey>>> { UiState.Success(it) }
        .catch { error ->
            val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fe == null && error is CancellationException) throw error
            emit(UiState.Error((fe ?: error).message ?: "Failed to load journeys"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val sosSessionsState: StateFlow<UiState<List<SosSession>>> = repository.observeSosSessions(currentUserId)
        .map<List<SosSession>, UiState<List<SosSession>>> { UiState.Success(it) }
        .catch { error ->
            val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fe == null && error is CancellationException) throw error
            emit(UiState.Error((fe ?: error).message ?: "Failed to load SOS history"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val myReportsState: StateFlow<List<SafetyReport>> = repository.observeMySafetyReports(currentUserId)
        .catch { e ->
            if (e is CancellationException && e.cause !is FirebaseFirestoreException) throw e
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val verifiedReportsState: StateFlow<List<SafetyReport>> = repository.observeVerifiedSafetyReports()
        .catch { e ->
            if (e is CancellationException && e.cause !is FirebaseFirestoreException) throw e
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val directoryFacilitiesState: StateFlow<UiState<List<EmergencyFacility>>> = repository.observeActiveEmergencyFacilities()
        .map<List<EmergencyFacility>, UiState<List<EmergencyFacility>>> { UiState.Success(it) }
        .catch { error ->
            val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fe == null && error is CancellationException) throw error
            emit(UiState.Error((fe ?: error).message ?: "Failed to load verified emergency directory"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val firestoreFacilitiesState: StateFlow<List<EmergencyFacility>> = directoryFacilitiesState
        .map { (it as? UiState.Success)?.data ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val adminReportsUiState: StateFlow<UiState<List<SafetyReport>>> =
        combine(_adminCheckCompleted, _isAdmin, _adminRefreshTick) { checked, admin, _ ->
            checked to admin
        }.flatMapLatest { (checked, admin) ->
            when {
                !checked -> flowOf(UiState.Loading)
                !admin -> flowOf(UiState.Success(emptyList()))
                else -> repository.observeAllReportsForAdmin()
                    .map<List<SafetyReport>, UiState<List<SafetyReport>>> { UiState.Success(it) }
                    .catch { error ->
                        val fe = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fe == null && error is CancellationException) throw error
                        emit(UiState.Error((fe ?: error).message ?: "Failed to load safety reports"))
                    }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val adminReportsState: StateFlow<List<SafetyReport>> = adminReportsUiState
        .map { (it as? UiState.Success)?.data ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val auditLogsState: StateFlow<List<AuditLog>> = combine(_adminCheckCompleted, _isAdmin) { checked, admin ->
        checked to admin
    }.flatMapLatest { (checked, admin) ->
        if (!checked) {
            flowOf(emptyList())
        } else {
            repository.observeAuditLogs(currentUserId, isAdmin = admin)
                .catch { e ->
                    if (e is CancellationException && e.cause !is FirebaseFirestoreException) throw e
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            try {
                val profile = repository.ensureUserProfile(currentUserId, currentUserName, currentUserEmail)
                _language.value = if (profile.language == "hi") AppLang.HI else AppLang.EN
                _isAdmin.value = repository.checkOrBootstrapAdmin(currentUserId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Profile/admin init failed: ${e.message}")
            } finally {
                _adminCheckCompleted.value = true
            }
        }

        // Debounced Nominatim Place Autocomplete
        viewModelScope.launch {
            _autocompleteInput
                .map { it.trim() }
                .distinctUntilChanged()
                .debounce(420L)
                .collect { query ->
                    if (query.length < 2) {
                        _placeSuggestions.value = emptyList()
                        _isSearchingPlaces.value = false
                        return@collect
                    }
                    _isSearchingPlaces.value = true
                    try {
                        val remote = freeApisClient.searchPlacesIndia(query)
                        val presetFallback = RoutingAndSafetyEngine.destinationPresets
                            .filter { it.label.contains(query, ignoreCase = true) }
                            .map {
                                PlaceSuggestion(
                                    placeId = "preset_${it.label}",
                                    shortName = it.label,
                                    displayName = "${it.label}, ${it.areaNote}",
                                    locality = it.areaNote,
                                    lat = it.lat,
                                    lon = it.lon,
                                    isFromPreset = true
                                )
                            }
                        _placeSuggestions.value = (remote + presetFallback)
                            .distinctBy { it.shortName.lowercase() + ":" + (it.lat * 100).toInt() }
                            .take(6)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Nominatim autocomplete failed: ${e.message}")
                    } finally {
                        _isSearchingPlaces.value = false
                    }
                }
        }
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun setLocationPermission(granted: Boolean) {
        _locationPermissionGranted.value = granted
    }

    fun onCompassHeading(degrees: Float) {
        _compassHeading.value = degrees
    }

    fun onLiveLocationReading(reading: LocationReading) {
        _currentLocation.value = reading
        _hasLiveGpsFix.value = true
        val pt = LatLon(reading.lat, reading.lon)
        if (reading.accuracyMeters <= 250.0) {
            maybePrefetchArea(pt)
            maybeRefreshEnvironmentAndLocality(pt)
        }

        val activeJourney = (journeysState.value as? UiState.Success)?.data
            ?.firstOrNull { !JourneyState.fromValue(it.state).isTerminal }

        if (activeJourney == null) {
            _routeProgress.value = null
            _arrivedJourneyId.value = null
            return
        }

        // Update dynamic remaining polyline, distance, ETA, turn-by-turn step & 35m arrival detection
        val progress = RoutingAndSafetyEngine.computeRouteProgress(
            currentLocation = pt,
            route = _selectedRoute.value,
            destination = LatLon(activeJourney.destinationLat, activeJourney.destinationLon),
            arrivalThresholdMeters = RoutingAndSafetyEngine.DESTINATION_ARRIVAL_THRESHOLD_METERS
        )
        _routeProgress.value = progress
        if (progress.hasArrived && activeJourney.id !in dismissedArrivalJourneyIds) {
            _arrivedJourneyId.value = activeJourney.id
        }

        // Avoid noisy excessive GPS writes if jitter accuracy is very poor (> 150m)
        if (reading.accuracyMeters > 150.0) return

        _activeBreadcrumbs.value = (_activeBreadcrumbs.value + pt).takeLast(50)

        val geom = _selectedRoute.value?.geometry ?: listOf(
            LatLon(activeJourney.originLat, activeJourney.originLon),
            LatLon(activeJourney.destinationLat, activeJourney.destinationLon)
        )
        val distM = RoutingAndSafetyEngine.minDistanceToPolylineMeters(pt, geom).toInt()
        _deviationDistanceM.value = distM

        val now = System.currentTimeMillis()
        val threshold = activeJourney.deviationThresholdMeters.toInt()
        val shouldFlagDeviation = distM > threshold &&
            activeJourney.state == JourneyState.ACTIVE.value &&
            (now - lastDeviationPromptAtMs) > 45_000L // Cooldown prevents uncontrolled alert loops

        if (shouldFlagDeviation) {
            lastDeviationPromptAtMs = now
        }

        locationSequence += 1L
        val seq = locationSequence
        val readableLoc = _currentAddressLabel.value.orEmpty()
        viewModelScope.launch {
            try {
                repository.recordLocationUpdate(
                    journey = activeJourney,
                    lat = reading.lat,
                    lon = reading.lon,
                    accuracyMeters = reading.accuracyMeters,
                    sequence = seq,
                    nextState = if (shouldFlagDeviation) JourneyState.DEVIATION_DETECTED else null,
                    locationLabel = readableLoc
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record location point: ${e.message}")
            }
        }
    }

    private fun maybeRefreshEnvironmentAndLocality(center: LatLon) {
        val now = System.currentTimeMillis()
        val prevRev = lastReverseGeocodeLoc
        val movedRev = prevRev == null ||
            RoutingAndSafetyEngine.haversineMeters(prevRev.lat, prevRev.lon, center.lat, center.lon) >= 120.0
        if (movedRev && (now - lastReverseGeocodeAtMs) >= 18_000L) {
            lastReverseGeocodeLoc = center
            lastReverseGeocodeAtMs = now
            viewModelScope.launch {
                val placeName = freeApisClient.reverseGeocode(center.lat, center.lon)
                if (!placeName.isNullOrBlank()) {
                    _currentAddressLabel.value = placeName
                }
            }
        }

        val prevCtx = lastContextFetchLoc
        val movedCtx = prevCtx == null ||
            RoutingAndSafetyEngine.haversineMeters(prevCtx.lat, prevCtx.lon, center.lat, center.lon) >= 800.0
        if (movedCtx && (now - lastContextFetchAtMs) >= 60_000L) {
            lastContextFetchLoc = center
            lastContextFetchAtMs = now
            refreshOverpassAndWeather(center.lat, center.lon)
        }
    }

    fun refreshOverpassAndWeather(
        lat: Double = _currentLocation.value.lat,
        lon: Double = _currentLocation.value.lon
    ) {
        viewModelScope.launch {
            _isLoadingPoliceStations.value = true
            try {
                val police = freeApisClient.fetchNearbyPoliceStations(lat, lon, radiusMeters = 8000)
                if (police.isNotEmpty() || _nearbyPoliceStations.value.isEmpty()) {
                    _nearbyPoliceStations.value = police
                }
                val weather = freeApisClient.fetchWeatherContext(lat, lon)
                if (weather != null) {
                    _weatherContext.value = weather
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Police/weather refresh failed: ${e.message}")
            } finally {
                _isLoadingPoliceStations.value = false
            }
        }
    }

    fun updateDestinationQuery(query: String) {
        _destinationQuery.value = query
        _selectedPlaceSuggestion.value = null
        _autocompleteInput.value = query
    }

    fun selectPlaceSuggestion(suggestion: PlaceSuggestion) {
        _selectedPlaceSuggestion.value = suggestion
        _destinationQuery.value = suggestion.shortName
        _autocompleteInput.value = ""
        _placeSuggestions.value = emptyList()
    }

    fun dismissPlaceSuggestions() {
        _placeSuggestions.value = emptyList()
    }

    fun searchRoutes(queryOverride: String? = null) {
        val query = (queryOverride ?: _destinationQuery.value).trim()
        if (query.isBlank()) {
            _bannerMessage.value = "Enter a destination to compare routes."
            return
        }
        val activeSelectedPlace = if (queryOverride == null) {
            _selectedPlaceSuggestion.value?.takeIf { it.shortName.equals(query, ignoreCase = true) }
        } else {
            null
        }
        _destinationQuery.value = query
        _placeSuggestions.value = emptyList()
        _routeSearchState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val loc = _currentLocation.value
                val originLabel = _currentAddressLabel.value
                    ?: if (_hasLiveGpsFix.value) "Current GPS Location" else "Central Reference Point"
                val combinedFacilities = RoutingAndSafetyEngine.verifiedNationalHelplines + firestoreFacilitiesState.value
                val result = RoutingAndSafetyEngine.searchRoutes(
                    originLabel = originLabel,
                    origin = LatLon(loc.lat, loc.lon),
                    destinationQuery = query,
                    verifiedReports = verifiedReportsState.value,
                    facilities = combinedFacilities,
                    selectedPlace = activeSelectedPlace,
                    apiClient = freeApisClient
                )
                result.weatherContext?.let { _weatherContext.value = it }
                _routeSearchState.value = UiState.Success(result)
                _selectedRoute.value = result.routes.firstOrNull()
                prefetchRouteCorridorTiles(result.routes)
                refreshOverpassAndWeather(loc.lat, loc.lon)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _routeSearchState.value = UiState.Error(e.localizedMessage ?: "Failed to search routes")
            }
        }
    }

    fun selectRoute(route: RouteOption) {
        _selectedRoute.value = route
    }

    fun updateAdvisoryTravelWindow(windowLabel: String) {
        _advisoryTravelWindow.value = windowLabel
    }

    fun compareRoutesWithAi(
        destinationOverride: String? = null,
        travelWindowLabel: String = _advisoryTravelWindow.value
    ) {
        val query = (destinationOverride ?: _destinationQuery.value).trim()
        if (query.isBlank()) {
            _advisoryComparisonState.value = UiState.Error("Enter a destination to analyze and compare routes.")
            return
        }
        _destinationQuery.value = query
        _advisoryTravelWindow.value = travelWindowLabel
        _placeSuggestions.value = emptyList()
        _routeSearchState.value = UiState.Loading
        _advisoryComparisonState.value = UiState.Loading

        viewModelScope.launch {
            try {
                val loc = _currentLocation.value
                val originLabel = _currentAddressLabel.value
                    ?: if (_hasLiveGpsFix.value) "Current GPS Location" else "Central Reference Point"
                val combinedFacilities = RoutingAndSafetyEngine.verifiedNationalHelplines + firestoreFacilitiesState.value
                val searchResult = RoutingAndSafetyEngine.searchRoutes(
                    originLabel = originLabel,
                    origin = LatLon(loc.lat, loc.lon),
                    destinationQuery = query,
                    verifiedReports = verifiedReportsState.value,
                    facilities = combinedFacilities,
                    selectedPlace = _selectedPlaceSuggestion.value,
                    apiClient = freeApisClient
                )
                searchResult.weatherContext?.let { _weatherContext.value = it }
                _routeSearchState.value = UiState.Success(searchResult)

                val comparison = aiAdvisor.compareRoutes(
                    originLabel = originLabel,
                    destinationLabel = searchResult.destinationLabel,
                    travelWindowLabel = travelWindowLabel,
                    routes = searchResult.routes,
                    languageCode = _language.value.code
                )
                _advisoryComparisonState.value = UiState.Success(comparison)
                _selectedRoute.value = searchResult.routes.firstOrNull { it.id == comparison.recommendedRouteId }
                    ?: searchResult.routes.firstOrNull()
                prefetchRouteCorridorTiles(searchResult.routes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Advisory AI navigation comparison failed", e)
                _advisoryComparisonState.value = UiState.Error(
                    e.localizedMessage ?: "Failed to compare routes with Gemini AI."
                )
            }
        }
    }

    fun startJourney(
        sharingEnabled: Boolean,
        selectedContactCount: Long,
        shareDurationMinutes: Long
    ) {
        val searchResult = (_routeSearchState.value as? UiState.Success)?.data
        val route = _selectedRoute.value ?: searchResult?.routes?.firstOrNull()
        if (searchResult == null || route == null) {
            _bannerMessage.value = "Search and select a route first."
            return
        }
        val profile = profileState.value
        val threshold = profile?.deviationThresholdMeters ?: 250L
        val ownerName = profile?.displayName?.ifBlank { currentUserName } ?: currentUserName.ifBlank { "Traveller" }

        viewModelScope.launch {
            try {
                val (journeyId, _) = repository.startProtectedJourney(
                    ownerDisplayName = ownerName,
                    originLabel = searchResult.originLabel,
                    originLat = searchResult.origin.lat,
                    originLon = searchResult.origin.lon,
                    destinationLabel = searchResult.destinationLabel,
                    destinationLat = searchResult.destination.lat,
                    destinationLon = searchResult.destination.lon,
                    selectedRouteId = route.id,
                    selectedRouteLabel = route.label,
                    providerName = route.providerName,
                    deviationThresholdMeters = threshold,
                    sharingEnabled = sharingEnabled,
                    sharedContactCount = selectedContactCount,
                    shareDurationMinutes = shareDurationMinutes
                )
                _activeBreadcrumbs.value = listOf(searchResult.origin)
                _deviationDistanceM.value = 0
                _arrivedJourneyId.value = null
                _routeProgress.value = RoutingAndSafetyEngine.computeRouteProgress(
                    currentLocation = searchResult.origin,
                    route = route,
                    destination = searchResult.destination,
                    arrivalThresholdMeters = RoutingAndSafetyEngine.DESTINATION_ARRIVAL_THRESHOLD_METERS
                )
                _bannerMessage.value = "Protected journey started (${journeyId.take(6)})."
                maybePrefetchArea(searchResult.origin, force = true)
                prefetchRouteCorridorTiles(listOf(route))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Failed to start journey"
            }
        }
    }

    fun simulateStepAlongOrOffRoute(journey: Journey, offRoute: Boolean) {
        val geom = _selectedRoute.value?.geometry ?: listOf(
            LatLon(journey.originLat, journey.originLon),
            LatLon(journey.destinationLat, journey.destinationLon)
        )
        val target = if (offRoute) {
            // Offset ~450m east to test deviation prompt cleanly in emulator
            LatLon(journey.lastLat + 0.0032, journey.lastLon + 0.0038)
        } else {
            val mid = geom[geom.size / 2]
            LatLon(
                (journey.lastLat + mid.lat) / 2.0,
                (journey.lastLon + mid.lon) / 2.0
            )
        }
        onLiveLocationReading(
            LocationReading(
                lat = target.lat,
                lon = target.lon,
                accuracyMeters = 12.0,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }

    fun confirmDeviationIntentional(journey: Journey) {
        viewModelScope.launch {
            try {
                repository.transitionJourneyState(journey, JourneyState.ACTIVE)
                _deviationDistanceM.value = 0
                _bannerMessage.value = "Confirmed intentional route change. Monitoring continues."
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Failed to update journey state"
            }
        }
    }

    fun rerouteActiveJourney(journey: Journey) {
        viewModelScope.launch {
            try {
                repository.transitionJourneyState(journey, JourneyState.ACTIVE)
                searchRoutes(journey.destinationLabel)
                _deviationDistanceM.value = 0
                _bannerMessage.value = "Re-routed toward ${journey.destinationLabel}."
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Failed to re-route"
            }
        }
    }

    fun dismissArrivalModal(journeyId: String) {
        dismissedArrivalJourneyIds.add(journeyId)
        _arrivedJourneyId.value = null
    }

    fun endJourney(journey: Journey) {
        viewModelScope.launch {
            try {
                repository.transitionJourneyState(journey, JourneyState.COMPLETED)
                _arrivedJourneyId.value = null
                _routeProgress.value = null
                _activeBreadcrumbs.value = emptyList()
                _bannerMessage.value = "Destination reached — Journey completed & live sharing closed."
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Failed to end journey"
            }
        }
    }

    fun revokeShare(journey: Journey) {
        viewModelScope.launch {
            try {
                repository.revokeShareToken(journey)
                _bannerMessage.value = "Share link revoked immediately."
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Failed to revoke share link"
            }
        }
    }

    fun lookupShareToken(tokenId: String) {
        val clean = tokenId.trim()
        if (clean.length < 16) {
            _shareLookupState.value = UiState.Error("Enter a valid high-entropy share token (16+ characters).")
            return
        }
        shareLookupJob?.cancel()
        _shareLookupState.value = UiState.Loading
        shareLookupJob = viewModelScope.launch {
            repository.observeShareTokenById(clean)
                .map<ShareToken?, UiState<ShareToken>> { token ->
                    if (token == null) {
                        UiState.Error("Share link not found, expired, or revoked.")
                    } else {
                        UiState.Success(token)
                    }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    emit(UiState.Error((fe ?: error).localizedMessage ?: "Access denied or token revoked."))
                }
                .collect { state ->
                    _shareLookupState.value = state
                }
        }
    }

    fun triggerSos(
        activeJourney: Journey?,
        alertReason: String = "",
        onActivated: (SosSession) -> Unit = {}
    ) {
        val contacts = (contactsState.value as? UiState.Success)?.data ?: emptyList()
        val loc = _currentLocation.value
        val label = activeJourney?.let { "Near ${it.destinationLabel} corridor" }
            ?: if (_hasLiveGpsFix.value) "Current GPS Fix" else "Reference Coordinates"
        val ownerName = profileState.value?.displayName?.ifBlank { currentUserName } ?: currentUserName.ifBlank { "Traveller" }

        viewModelScope.launch {
            try {
                val session = repository.triggerSosWithDedupe(
                    journeyId = activeJourney?.id.orEmpty(),
                    lat = loc.lat,
                    lon = loc.lon,
                    locationLabel = label,
                    contacts = contacts,
                    ownerDisplayName = ownerName,
                    existingShareTokenId = activeJourney?.shareTokenId.orEmpty(),
                    alertReason = alertReason
                )
                if (activeJourney != null &&
                    JourneyStateMachine.canTransition(
                        JourneyState.fromValue(activeJourney.state),
                        JourneyState.SOS_TRIGGERED
                    )
                ) {
                    repository.transitionJourneyState(activeJourney, JourneyState.SOS_TRIGGERED)
                }
                _bannerMessage.value = "High-priority SOS active (${session.id.take(6)}). Live GPS sharing initiated."
                onActivated(session)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "SOS trigger failed — call 112 immediately!"
            }
        }
    }

    fun resolveOrCancelSos(session: SosSession, newStatus: String) {
        viewModelScope.launch {
            try {
                repository.updateSosStatus(
                    sosId = session.id,
                    newStatus = newStatus,
                    summaryNote = "${session.deliverySummary} • Marked $newStatus by user.",
                    shareTokenId = session.shareTokenId,
                    standaloneSosOnly = session.journeyId.isBlank()
                )
                _bannerMessage.value = "SOS session marked $newStatus. Background sharing stopped."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Failed to update SOS status"
            }
        }
    }

    fun saveContact(
        existingId: String,
        name: String,
        phoneNumber: String,
        email: String,
        relationship: String,
        verified: Boolean,
        notifyOnStart: Boolean,
        notifyOnSos: Boolean
    ) {
        val cleanName = name.trim()
        val cleanPhone = phoneNumber.trim()
        val cleanEmail = email.trim()
        if (cleanName.isBlank() || (cleanPhone.isBlank() && cleanEmail.isBlank())) {
            _bannerMessage.value = "Contact name and at least a phone number or email address are required."
            return
        }
        val isUpdate = existingId.isNotBlank()
        viewModelScope.launch {
            try {
                repository.upsertContact(
                    EmergencyContact(
                        id = existingId,
                        ownerUserId = currentUserId,
                        name = cleanName,
                        phoneNumber = cleanPhone,
                        email = cleanEmail,
                        contactChannel = cleanPhone.ifBlank { cleanEmail },
                        channelType = if (cleanPhone.isNotBlank()) "PHONE" else "EMAIL",
                        relationship = relationship.trim(),
                        verified = verified,
                        notifyOnStart = notifyOnStart,
                        notifyOnSos = notifyOnSos
                    )
                )
                _bannerMessage.value = if (isUpdate) "Trusted contact updated." else "Trusted contact added."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Failed to save contact"
            }
        }
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            try {
                repository.deleteContact(contactId)
                _bannerMessage.value = "Trusted contact removed."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Failed to remove contact"
            }
        }
    }

    fun submitReport(category: String, title: String, description: String, locationLabel: String) {
        if (title.trim().length < 3 || description.trim().length < 5) {
            _bannerMessage.value = "Provide a descriptive title and at least 5 characters of details."
            return
        }
        val loc = _currentLocation.value
        viewModelScope.launch {
            try {
                repository.submitSafetyReport(
                    category = category,
                    title = title,
                    description = description,
                    locationLabel = locationLabel.ifBlank { "Current map area" },
                    lat = loc.lat,
                    lon = loc.lon
                )
                _bannerMessage.value = "Report submitted as Pending Review. Thank you."
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Failed to submit report"
            }
        }
    }

    fun refreshAdminReports() {
        viewModelScope.launch {
            try {
                if (!_isAdmin.value) {
                    _isAdmin.value = repository.checkOrBootstrapAdmin(currentUserId)
                }
                _adminCheckCompleted.value = true
                _adminRefreshTick.value += 1L
                if (_isAdmin.value) {
                    val reports = repository.fetchSafetyReportsForAdmin()
                    _bannerMessage.value = "Moderation queue synced (${reports.size} reports)."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Failed to sync safety reports"
            }
        }
    }

    fun moderateReport(reportId: String, status: String, note: String) {
        viewModelScope.launch {
            try {
                repository.moderateSafetyReport(reportId, status, note)
                val statusLabel = com.example.data.model.SafetyReportStatus.fromValue(status).label
                _bannerMessage.value = "Safety report marked as $statusLabel."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Moderation failed"
            }
        }
    }

    fun refreshEmergencyDirectory(
        category: EmergencyDirectoryCategory = EmergencyDirectoryCategory.ALL,
        radiusKm: Double? = null
    ) {
        val loc = _currentLocation.value
        viewModelScope.launch {
            try {
                val results = repository.fetchEmergencyDirectoryByLocationAndCategory(
                    category = category,
                    centerLat = loc.lat,
                    centerLon = loc.lon,
                    radiusKm = radiusKm
                )
                _bannerMessage.value = "Directory synced (${results.size} verified nearby)."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Failed to refresh emergency directory"
            }
        }
    }

    fun addVerifiedFacility(type: String, name: String, phone: String, address: String, source: String) {
        if (name.trim().length < 2 || phone.trim().length < 3 || source.trim().length < 2) {
            _bannerMessage.value = "Name, phone, and authoritative source are required."
            return
        }
        val loc = _currentLocation.value
        viewModelScope.launch {
            try {
                repository.upsertVerifiedEmergencyFacility(
                    EmergencyFacility(
                        type = type,
                        name = name,
                        phone = phone,
                        address = address.ifBlank { "Verified service area" },
                        lat = loc.lat,
                        lon = loc.lon,
                        verifiedSource = source,
                        verifiedAtLabel = "2026-11-13",
                        activeStatus = true
                    )
                )
                _bannerMessage.value = "Verified emergency facility published."
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Failed to add facility"
            }
        }
    }

    fun updateSettings(language: AppLang, thresholdMeters: Long, retentionDays: Long, displayName: String) {
        _language.value = language
        viewModelScope.launch {
            try {
                repository.ensureUserProfile(
                    uid = currentUserId,
                    displayName = displayName.ifBlank { currentUserName },
                    email = currentUserEmail
                )
                repository.updateUserSettings(
                    uid = currentUserId,
                    displayName = displayName,
                    language = language.code,
                    deviationThresholdMeters = thresholdMeters,
                    retentionDays = retentionDays
                )
                _bannerMessage.value = "Settings saved."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Failed to save settings"
            }
        }
    }

    fun purgeHistory() {
        viewModelScope.launch {
            try {
                repository.purgeCompletedLocationBreadcrumbs(currentUserId)
                _bannerMessage.value = "Completed journey breadcrumbs purged."
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Failed to purge history"
            }
        }
    }

    fun askAiAdvisor(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return
        val activeJourney = (journeysState.value as? UiState.Success)?.data
            ?.firstOrNull { !JourneyState.fromValue(it.state).isTerminal }

        viewModelScope.launch {
            aiAdvisor.askStream(
                question = trimmed,
                selectedRoute = _selectedRoute.value,
                activeJourneyState = activeJourney?.state,
                languageCode = _language.value.code
            ).collect { state ->
                _aiState.value = state
            }
        }
    }

    fun prefetchCurrentAreaTiles() {
        val cache = mapTileCache ?: return
        val loc = _currentLocation.value
        val center = LatLon(loc.lat, loc.lon)
        lastPrefetchedLocation = center
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            try {
                val areaFetched = cache.prefetchSurroundingTiles(
                    center = center,
                    zoom = 15,
                    radiusTiles = 1
                )
                val selectedGeom = _selectedRoute.value?.geometry.orEmpty()
                val corridorFetched = if (selectedGeom.isNotEmpty()) {
                    cache.prefetchRouteCorridor(selectedGeom)
                } else {
                    0
                }
                val total = areaFetched + corridorFetched
                _bannerMessage.value = when {
                    total > 0 -> "Cached $total map tiles in DataStore for offline navigation."
                    cache.isCurrentlyOnline() -> "Current area map tiles are already cached offline."
                    else -> "Offline mode: existing cached tiles remain available."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Failed to cache map tiles"
            }
        }
    }

    fun clearMapTileCache() {
        val cache = mapTileCache ?: return
        viewModelScope.launch {
            try {
                cache.clearCache()
                lastPrefetchedLocation = null
                _bannerMessage.value = "Offline map tile cache cleared."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bannerMessage.value = e.localizedMessage ?: "Failed to clear map tile cache"
            }
        }
    }

    private fun maybePrefetchArea(center: LatLon, force: Boolean = false) {
        val cache = mapTileCache ?: return
        val prev = lastPrefetchedLocation
        if (!force && prev != null && RoutingAndSafetyEngine.haversineMeters(prev.lat, prev.lon, center.lat, center.lon) < 350.0) {
            return
        }
        lastPrefetchedLocation = center
        viewModelScope.launch {
            try {
                cache.prefetchSurroundingTiles(center = center, zoom = 15, radiusTiles = 1)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private fun prefetchRouteCorridorTiles(routes: List<RouteOption>) {
        val cache = mapTileCache ?: return
        val points = routes.take(2).flatMap { it.geometry }
        if (points.isEmpty()) return
        viewModelScope.launch {
            try {
                cache.prefetchRouteCorridor(points = points)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private companion object {
        const val TAG = "NirapadViewModel"
    }
}
