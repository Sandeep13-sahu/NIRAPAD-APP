package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.firestore.UiState
import com.example.data.model.DataProvenance
import com.example.data.model.DirectoryFacilityItem
import com.example.data.model.EmergencyDirectoryCategory
import com.example.data.model.EmergencyFacility
import com.example.data.model.NearbyPoliceStation
import com.example.data.repository.NirapadRepository
import com.example.data.service.LocationReading
import com.example.data.service.RoutingAndSafetyEngine
import com.example.localization.NirapadCopy
import com.example.ui.components.NirapadOutlinedTextField
import com.example.ui.components.ProvenanceChip
import com.example.ui.theme.NirapadBorderSubtle
import com.example.ui.theme.NirapadCautionAmber
import com.example.ui.theme.NirapadCautionContainer
import com.example.ui.theme.NirapadEmergencyContainer
import com.example.ui.theme.NirapadEmergencyRed
import com.example.ui.theme.NirapadNavyDark
import com.example.ui.theme.NirapadPositiveContainer
import com.example.ui.theme.NirapadPositiveGreen
import com.example.ui.theme.NirapadPrimaryTeal
import com.example.ui.theme.NirapadPrimaryTealContainer
import com.example.ui.theme.NirapadSurface
import com.example.ui.theme.NirapadSurfaceVariant
import com.example.ui.theme.NirapadTextSecondary
import java.util.Locale

private val RADIUS_OPTIONS_KM: List<Pair<Double?, Pair<String, String>>> = listOf(
    5.0 to ("5 km" to "dir_radius_5"),
    15.0 to ("15 km" to "dir_radius_15"),
    50.0 to ("50 km" to "dir_radius_50"),
    null to ("All Areas" to "dir_radius_all")
)

@Composable
fun VerifiedEmergencyDirectoryScreen(
    copy: NirapadCopy,
    currentLocation: LocationReading,
    hasLiveGpsFix: Boolean,
    locationPermissionGranted: Boolean,
    facilitiesState: UiState<List<EmergencyFacility>>,
    isAdmin: Boolean,
    onRequestLocationPermission: () -> Unit,
    onRefreshDirectory: (EmergencyDirectoryCategory, Double?) -> Unit,
    onAddVerifiedFacility: (String, String, String, String, String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    nearbyPoliceStations: List<NearbyPoliceStation> = emptyList(),
    isLoadingPoliceStations: Boolean = false,
    onRefreshPoliceStations: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(EmergencyDirectoryCategory.ALL) }
    var selectedRadiusKm by remember { mutableStateOf<Double?>(15.0) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddFacilityDialog by remember { mutableStateOf(false) }

    val rawFirestoreFacilities = (facilitiesState as? UiState.Success)?.data ?: emptyList()

    val categoryCounts = remember(rawFirestoreFacilities, currentLocation.lat, currentLocation.lon, selectedRadiusKm) {
        EmergencyDirectoryCategory.entries.associateWith { cat ->
            NirapadRepository.filterAndSortEmergencyDirectory(
                facilities = rawFirestoreFacilities,
                category = cat,
                centerLat = currentLocation.lat,
                centerLon = currentLocation.lon,
                radiusKm = selectedRadiusKm
            ).size
        }
    }

    val filteredLocalItems = remember(
        rawFirestoreFacilities,
        selectedCategory,
        selectedRadiusKm,
        currentLocation.lat,
        currentLocation.lon,
        searchQuery
    ) {
        val base = NirapadRepository.filterAndSortEmergencyDirectory(
            facilities = rawFirestoreFacilities,
            category = selectedCategory,
            centerLat = currentLocation.lat,
            centerLon = currentLocation.lon,
            radiusKm = selectedRadiusKm
        )
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            base
        } else {
            base.filter { item ->
                val f = item.facility
                f.name.lowercase().contains(q) ||
                    f.address.lowercase().contains(q) ||
                    f.phone.lowercase().contains(q) ||
                    f.verifiedSource.lowercase().contains(q)
            }
        }
    }

    val filteredNationalHelplines = remember(selectedCategory, searchQuery) {
        val q = searchQuery.trim().lowercase()
        RoutingAndSafetyEngine.verifiedNationalHelplines
            .filter { helpline ->
                selectedCategory == EmergencyDirectoryCategory.ALL ||
                    helpline.type.uppercase() in selectedCategory.firestoreTypes
            }
            .filter { helpline ->
                q.isEmpty() ||
                    helpline.name.lowercase().contains(q) ||
                    helpline.phone.lowercase().contains(q) ||
                    helpline.address.lowercase().contains(q)
            }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("verified_emergency_directory_screen"),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("directory_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = NirapadNavyDark
                        )
                    }
                    Column {
                        Text(
                            text = copy.verifiedDirectoryTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = NirapadNavyDark
                        )
                        Text(
                            text = copy.verifiedDirectorySubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = NirapadTextSecondary
                        )
                    }
                }
                if (isAdmin) {
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { showAddFacilityDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NirapadPrimaryTeal,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dir_add_facility_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }
        }

        // Location & Firestore Sync Card + Category/Radius Filters
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                border = BorderStroke(1.dp, NirapadBorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Location status & sync controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = if (hasLiveGpsFix) NirapadPositiveContainer else NirapadCautionContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (hasLiveGpsFix) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                                    contentDescription = null,
                                    tint = if (hasLiveGpsFix) NirapadPositiveGreen else NirapadCautionAmber,
                                    modifier = Modifier.padding(6.dp).size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (hasLiveGpsFix) "Using Live GPS Location" else "Reference Area Coordinates",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = NirapadNavyDark
                                )
                                Text(
                                    text = String.format(Locale.US, "%.4f, %.4f", currentLocation.lat, currentLocation.lon),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NirapadTextSecondary
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!locationPermissionGranted) {
                                OutlinedButton(
                                    onClick = onRequestLocationPermission,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadPrimaryTeal),
                                    border = BorderStroke(1.dp, NirapadPrimaryTeal),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("dir_enable_gps_button")
                                ) {
                                    Text("Enable GPS", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            IconButton(
                                onClick = {
                                    onRefreshDirectory(selectedCategory, selectedRadiusKm)
                                    onRefreshPoliceStations()
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("dir_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync directory from Firestore",
                                    tint = NirapadPrimaryTeal
                                )
                            }
                        }
                    }

                    // Search Bar
                    NirapadOutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search police station, ambulance, center, or street") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.testTag("dir_clear_search_button")
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("directory_search_input")
                    )

                    // Category Filters
                    Text(
                        text = "Emergency Category",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NirapadTextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EmergencyDirectoryCategory.entries.forEach { cat ->
                            val count = categoryCounts[cat] ?: 0
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                colors = dirFilterChipColors(),
                                border = dirFilterChipBorder(selectedCategory == cat),
                                leadingIcon = {
                                    Icon(
                                        imageVector = categoryIcon(cat),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("${cat.label} ($count)") },
                                modifier = Modifier.testTag("dir_cat_${cat.name}")
                            )
                        }
                    }

                    // Radius Filters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Distance Radius",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NirapadTextSecondary
                        )
                        Surface(
                            color = NirapadPositiveContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = NirapadPositiveGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Firestore Live",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NirapadPositiveGreen
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RADIUS_OPTIONS_KM.forEach { (kmValue, meta) ->
                            val (label, tag) = meta
                            FilterChip(
                                selected = selectedRadiusKm == kmValue,
                                onClick = { selectedRadiusKm = kmValue },
                                colors = dirFilterChipColors(),
                                border = dirFilterChipBorder(selectedRadiusKm == kmValue),
                                label = { Text(label) },
                                modifier = Modifier.testTag(tag)
                            )
                        }
                    }
                }
            }
        }

        // Real-time OSM Overpass Police Stations (8 km)
        if ((selectedCategory == EmergencyDirectoryCategory.ALL || selectedCategory == EmergencyDirectoryCategory.POLICE) &&
            (nearbyPoliceStations.isNotEmpty() || isLoadingPoliceStations)
        ) {
            val filteredPolice = nearbyPoliceStations.filter { st ->
                val q = searchQuery.trim().lowercase()
                q.isEmpty() || st.name.lowercase().contains(q) || st.locality.lowercase().contains(q)
            }
            if (filteredPolice.isNotEmpty() || isLoadingPoliceStations) {
                item {
                    Text(
                        text = "Nearby Police Stations via OpenStreetMap (${filteredPolice.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NirapadNavyDark
                    )
                }
                if (isLoadingPoliceStations && filteredPolice.isEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NirapadPrimaryTeal)
                            Text("Finding police stations within 8 km...", style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
                        }
                    }
                } else {
                    items(filteredPolice, key = { "osm_${it.id}" }) { st ->
                        val dialNum = st.phone?.takeIf { it.isNotBlank() } ?: "112"
                        val callLabel = if (!st.phone.isNullOrBlank()) "Call Station (${st.phone})" else "Call 112"
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                            border = BorderStroke(1.dp, NirapadBorderSubtle),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().testTag("dir_police_card_${st.id}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(st.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = NirapadNavyDark, modifier = Modifier.weight(1f))
                                    Surface(color = NirapadPrimaryTealContainer, shape = RoundedCornerShape(8.dp)) {
                                        Text(
                                            text = String.format(Locale.US, "%.1f km away", st.distanceMeters / 1000.0),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = NirapadPrimaryTeal,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Text(st.locality, style = MaterialTheme.typography.bodySmall, color = NirapadTextSecondary)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialNum"))) },
                                        colors = ButtonDefaults.buttonColors(containerColor = NirapadNavyDark, contentColor = Color.White),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("dir_call_police_${st.id}")
                                    ) {
                                        Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(callLabel, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val geoUri = Uri.parse("geo:${st.lat},${st.lon}?q=${st.lat},${st.lon}(${Uri.encode(st.name)})")
                                            context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadNavyDark),
                                        border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Map, contentDescription = "Open map for ${st.name}", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Map")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Local Firestore Verified Facilities Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Local Verified Facilities (${filteredLocalItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NirapadNavyDark
                )
                Text(
                    text = "Sorted by distance",
                    style = MaterialTheme.typography.labelSmall,
                    color = NirapadPrimaryTeal,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        when (facilitiesState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp)
                            .testTag("directory_loading"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = NirapadPrimaryTeal)
                            Text(
                                text = "Fetching nearby verified emergency centers...",
                                style = MaterialTheme.typography.bodySmall,
                                color = NirapadTextSecondary
                            )
                        }
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Surface(
                        color = NirapadEmergencyContainer,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, NirapadEmergencyRed.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("directory_error")
                    ) {
                        Text(
                            text = facilitiesState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NirapadEmergencyRed,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                if (filteredLocalItems.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NirapadSurface),
                            border = BorderStroke(1.dp, NirapadBorderSubtle),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("directory_empty_local")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = categoryIcon(selectedCategory),
                                    contentDescription = null,
                                    tint = NirapadPrimaryTeal,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "No local verified ${selectedCategory.label.lowercase()} centers within radius",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NirapadNavyDark
                                )
                                Text(
                                    text = "Expand the distance radius, reset category filters, or use the verified 24x7 national helplines below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NirapadTextSecondary
                                )
                                if (selectedRadiusKm != null || selectedCategory != EmergencyDirectoryCategory.ALL || searchQuery.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedRadiusKm = null
                                            selectedCategory = EmergencyDirectoryCategory.ALL
                                            searchQuery = ""
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("dir_expand_filters_button")
                                    ) {
                                        Text("Show All Areas & Categories")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(filteredLocalItems, key = { it.facility.id }) { item ->
                        DirectoryFacilityCard(
                            item = item,
                            showDistance = true,
                            onDial = { phone ->
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                            },
                            onOpenMap = { fac ->
                                val geoUri = Uri.parse(
                                    "geo:${fac.lat},${fac.lon}?q=${fac.lat},${fac.lon}(${Uri.encode(fac.name)})"
                                )
                                context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
                            }
                        )
                    }
                }
            }
        }

        // Authoritative 24x7 National Helplines Section
        if (filteredNationalHelplines.isNotEmpty()) {
            item {
                HorizontalDivider(color = NirapadBorderSubtle, modifier = Modifier.padding(top = 6.dp))
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "National 24x7 Emergency Helplines",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NirapadNavyDark
                    )
                    Text(
                        text = "Always available pan-India regardless of local facility coverage",
                        style = MaterialTheme.typography.bodySmall,
                        color = NirapadTextSecondary
                    )
                }
            }
            items(filteredNationalHelplines, key = { it.id }) { helpline ->
                DirectoryFacilityCard(
                    item = DirectoryFacilityItem(
                        facility = helpline,
                        distanceMeters = 0.0,
                        category = EmergencyDirectoryCategory.fromFacilityType(helpline.type)
                    ),
                    showDistance = false,
                    onDial = { phone ->
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                    },
                    onOpenMap = {}
                )
            }
        }
    }

    if (showAddFacilityDialog) {
        AddVerifiedFacilityDialog(
            onPublish = { type, name, phone, address, source ->
                onAddVerifiedFacility(type, name, phone, address, source)
                showAddFacilityDialog = false
            },
            onDismiss = { showAddFacilityDialog = false }
        )
    }
}

@Composable
private fun DirectoryFacilityCard(
    item: DirectoryFacilityItem,
    showDistance: Boolean,
    onDial: (String) -> Unit,
    onOpenMap: (EmergencyFacility) -> Unit
) {
    val facility = item.facility
    val (accentColor, containerColor) = categoryColors(item.category)
    val distanceLabel = remember(item.distanceMeters) {
        if (item.distanceMeters < 1000.0) {
            "${item.distanceMeters.toInt()} m away"
        } else {
            String.format(Locale.US, "%.1f km away", item.distanceMeters / 1000.0)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = NirapadSurface),
        border = BorderStroke(1.dp, NirapadBorderSubtle),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("directory_facility_card_${facility.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(containerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon(item.category),
                        contentDescription = item.category.label,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ProvenanceChip(provenance = DataProvenance.VERIFIED)
                            Surface(
                                color = NirapadSurfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = displayTypeLabel(facility.type),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (showDistance) {
                            Surface(
                                color = NirapadPrimaryTealContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = distanceLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NirapadPrimaryTeal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = facility.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NirapadNavyDark
                    )

                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = NirapadTextSecondary,
                            modifier = Modifier.padding(top = 2.dp).size(14.dp)
                        )
                        Text(
                            text = facility.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = NirapadTextSecondary
                        )
                    }

                    Text(
                        text = "Verified by ${facility.verifiedSource} • ${facility.verifiedAtLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NirapadPrimaryTeal
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onDial(facility.phone) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NirapadNavyDark,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("dir_call_${facility.id}")
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Call ${facility.phone}", color = Color.White, fontWeight = FontWeight.Bold)
                }

                if (showDistance) {
                    OutlinedButton(
                        onClick = { onOpenMap(facility) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NirapadNavyDark),
                        border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dir_map_${facility.id}")
                    ) {
                        Icon(Icons.Default.Map, contentDescription = "Open map for ${facility.name}", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Map")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddVerifiedFacilityDialog(
    onPublish: (String, String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf("POLICE") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = NirapadSurface,
            border = BorderStroke(1.dp, NirapadBorderSubtle),
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(min = 280.dp, max = 480.dp)
                .fillMaxWidth()
                .testTag("add_facility_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Publish Verified Facility",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NirapadNavyDark
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "POLICE" to "Police",
                        "AMBULANCE" to "Ambulance",
                        "WOMENS_SUPPORT" to "Women's Support",
                        "HOSPITAL" to "Hospital",
                        "SAFE_HUB" to "Safe Hub"
                    ).forEach { (code, label) ->
                        FilterChip(
                            selected = type == code,
                            onClick = { type = code },
                            colors = dirFilterChipColors(),
                            border = dirFilterChipBorder(type == code),
                            label = { Text(label) }
                        )
                    }
                }

                NirapadOutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMsg = null
                    },
                    label = { Text("Facility Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dir_fac_name_input")
                )
                NirapadOutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        errorMsg = null
                    },
                    label = { Text("Verified Phone *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dir_fac_phone_input")
                )
                NirapadOutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Street Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dir_fac_address_input")
                )
                NirapadOutlinedTextField(
                    value = source,
                    onValueChange = {
                        source = it
                        errorMsg = null
                    },
                    label = { Text("Authoritative Verification Source *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dir_fac_source_input")
                )

                errorMsg?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = NirapadEmergencyRed
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.trim().length < 2 || phone.trim().length < 3 || source.trim().length < 2) {
                                errorMsg = "Facility name, phone, and authoritative source are required."
                            } else {
                                onPublish(type, name, phone, address, source)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NirapadPrimaryTeal,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("dir_fac_publish_button")
                    ) {
                        Text("Publish")
                    }
                }
            }
        }
    }
}

@Composable
private fun dirFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color(0xFFF1F5F9),
    labelColor = Color(0xFF0F172A),
    selectedContainerColor = NirapadPrimaryTealContainer,
    selectedLabelColor = NirapadNavyDark,
    selectedLeadingIconColor = NirapadPrimaryTeal
)

@Composable
private fun dirFilterChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = Color(0xFF94A3B8),
    selectedBorderColor = NirapadPrimaryTeal
)

private fun categoryIcon(category: EmergencyDirectoryCategory): ImageVector = when (category) {
    EmergencyDirectoryCategory.ALL -> Icons.Default.GridView
    EmergencyDirectoryCategory.POLICE -> Icons.Default.LocalPolice
    EmergencyDirectoryCategory.AMBULANCE -> Icons.Default.MedicalServices
    EmergencyDirectoryCategory.WOMENS_SUPPORT -> Icons.Default.SupportAgent
}

private fun categoryColors(category: EmergencyDirectoryCategory): Pair<Color, Color> = when (category) {
    EmergencyDirectoryCategory.ALL -> NirapadPrimaryTeal to NirapadPrimaryTealContainer
    EmergencyDirectoryCategory.POLICE -> NirapadNavyDark to NirapadPrimaryTealContainer
    EmergencyDirectoryCategory.AMBULANCE -> NirapadEmergencyRed to NirapadEmergencyContainer
    EmergencyDirectoryCategory.WOMENS_SUPPORT -> NirapadPositiveGreen to NirapadPositiveContainer
}

private fun displayTypeLabel(rawType: String): String = when (rawType.uppercase()) {
    "POLICE" -> "Police"
    "AMBULANCE" -> "Ambulance"
    "WOMENS_SUPPORT" -> "Women's Support"
    "HOSPITAL" -> "Hospital / Ambulance"
    "SAFE_HUB" -> "Safe Support Hub"
    "HELPLINE" -> "Helpline"
    else -> rawType
}
