package com.example.pp68_salestrackingapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pp68_salestrackingapp.BuildConfig
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.maps.android.compose.*
import com.example.pp68_salestrackingapp.ui.theme.AppColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RedPrimary  = Color(0xFFCC1D1D)
private val TextDark    = Color(0xFF1A1A1A)
private val TextGray    = Color(0xFF888888)
private val BgField     = Color(0xFFF8F8F8)
private val BorderGray  = Color(0xFFE8E8E8)

@Composable
fun GoogleMapPickerField(
    lat: Double,
    lng: Double,
    onLocationPicked: (Double, Double) -> Unit
) {
    val context      = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope        = rememberCoroutineScope()
    val isPreview    = LocalInspectionMode.current

    // ── State ────────────────────────────────────────────────
    var searchQuery       by remember { mutableStateOf("") }
    var suggestions       by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    var isSearching       by remember { mutableStateOf(false) }
    var showSuggestions   by remember { mutableStateOf(false) }
    var searchJob:  Job?  = remember { null }

    val currentLatLng = LatLng(if (lat == 0.0) 13.7563 else lat, if (lng == 0.0) 100.5018 else lng)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLatLng, 15f)
    }
    val markerState = rememberMarkerState(position = currentLatLng)

    // Sync camera if lat/lng changes from outside
    LaunchedEffect(lat, lng) {
        val newPoint = LatLng(lat, lng)
        if (lat != 0.0 && lng != 0.0) {
            markerState.position = newPoint
            cameraPositionState.position = CameraPosition.fromLatLngZoom(newPoint, 15f)
        }
    }

    // ── Places Client ────────────────────────────────────────
    val placesClient = remember {
        if (isPreview) null else {
            try {
                if (!Places.isInitialized()) {
                    Places.initialize(context, BuildConfig.MAPS_API_KEY)
                }
                Places.createClient(context)
            } catch (e: Exception) {
                null
            }
        }
    }

    // ── Search function (debounce 400ms) ──────────────────────
    fun searchPlaces(query: String) {
        val client = placesClient ?: return
        searchJob?.cancel()
        if (query.length < 2) {
            suggestions     = emptyList()
            showSuggestions = false
            return
        }
        searchJob = scope.launch {
            delay(400)
            isSearching = true
            try {
                val request = FindAutocompletePredictionsRequest.builder()
                    .setQuery(query)
                    .setCountries("TH")
                    .build()

                client.findAutocompletePredictions(request)
                    .addOnSuccessListener { response ->
                        suggestions     = response.autocompletePredictions
                        showSuggestions = suggestions.isNotEmpty()
                        isSearching     = false
                    }
                    .addOnFailureListener {
                        suggestions     = emptyList()
                        showSuggestions = false
                        isSearching     = false
                    }
            } catch (e: Exception) {
                isSearching = false
            }
        }
    }

    fun selectPlace(prediction: AutocompletePrediction) {
        val client = placesClient ?: return
        val placeFields = listOf(Place.Field.LAT_LNG, Place.Field.NAME)
        val request     = FetchPlaceRequest.newInstance(prediction.placeId, placeFields)

        client.fetchPlace(request)
            .addOnSuccessListener { response ->
                val place  = response.place
                val latLng = place.latLng ?: return@addOnSuccessListener

                markerState.position = latLng
                scope.launch {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                }
                onLocationPicked(latLng.latitude, latLng.longitude)

                searchQuery     = place.name ?: prediction.getPrimaryText(null).toString()
                showSuggestions = false
                suggestions     = emptyList()
                focusManager.clearFocus()
            }
    }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // Search Bar
        OutlinedTextField(
            value         = searchQuery,
            onValueChange = {
                searchQuery = it
                searchPlaces(it)
            },
            placeholder = { Text("ค้นหาสถานที่...", color = TextGray, fontSize = 14.sp) },
            leadingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(color = RedPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Search, null, tint = RedPrimary)
                }
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = {
                        searchQuery = ""; suggestions = emptyList(); showSuggestions = false
                    }) { Icon(Icons.Default.Clear, null, tint = TextGray) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(
                topStart = 10.dp, topEnd = 10.dp,
                bottomStart = if (showSuggestions) 0.dp else 10.dp,
                bottomEnd = if (showSuggestions) 0.dp else 10.dp
            ),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderGray,
                focusedBorderColor = RedPrimary,
                unfocusedContainerColor = BgField,
                focusedContainerColor = Color.White
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); showSuggestions = false })
        )

        // Suggestion Dropdown
        if (showSuggestions && suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(suggestions) { prediction ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectPlace(prediction) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, null, tint = RedPrimary, modifier = Modifier.size(18.dp))
                            Column {
                                Text(prediction.getPrimaryText(null).toString(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextDark, maxLines = 1)
                                Text(prediction.getSecondaryText(null).toString(), fontSize = 12.sp, color = TextGray, maxLines = 1)
                            }
                        }
                        if (suggestions.last() != prediction) HorizontalDivider(color = BorderGray, thickness = 0.5.dp)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Map
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
        ) {
            if (isPreview) {
                Box(modifier = Modifier.fillMaxSize().background(Color.LightGray), contentAlignment = Alignment.Center) {
                    Text("Map Preview Not Available", color = Color.DarkGray)
                }
            } else {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = false, // ตั้งเป็น true หากขอ permission แล้ว
                        mapType = MapType.NORMAL
                    ),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = true,
                        myLocationButtonEnabled = false
                    ),
                    onMapClick = { latLng ->
                        markerState.position = latLng
                        onLocationPicked(latLng.latitude, latLng.longitude)
                        focusManager.clearFocus()
                        showSuggestions = false
                    }
                ) {
                    Marker(
                        state = markerState,
                        title = "Location",
                        snippet = "${"%.4f".format(markerState.position.latitude)}, ${"%.4f".format(markerState.position.longitude)}"
                    )
                }
            }

            // hint overlay
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.85f),
                shadowElevation = 2.dp
            ) {
                Text("ค้นหาหรือแตะแผนที่เพื่อปักหมุด", fontSize = 11.sp, color = TextGray, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }

        // Selected coordinates
        Text(
            "📍 ${"%.6f".format(markerState.position.latitude)}, ${"%.6f".format(markerState.position.longitude)}",
            fontSize = 11.sp, color = TextGray, modifier = Modifier.padding(top = 4.dp, start = 4.dp)
        )
    }
}
