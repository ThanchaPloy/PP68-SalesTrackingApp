package com.example.pp68_salestrackingapp.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.pp68_salestrackingapp.data.remote.NominatimClient
import com.example.pp68_salestrackingapp.data.remote.NominatimPlace
import com.example.pp68_salestrackingapp.utils.fetchCurrentLocation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

private val RedPrimary  = Color(0xFFCC1D1D)
private val TextDark    = Color(0xFF1A1A1A)
private val TextGray    = Color(0xFF888888)
private val BgField     = Color(0xFFF8F8F8)
private val BorderGray  = Color(0xFFE8E8E8)

private const val DEFAULT_ZOOM = 15.0

@Composable
fun MapPickerField(
    lat: Double?,
    lng: Double?,
    onLocationPicked: (Double, Double) -> Unit,
    onResetToCurrentLocation: (() -> Unit)? = null,
    onClearLocation: (() -> Unit)? = null
) {
    val context      = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope        = rememberCoroutineScope()
    val isPreview    = LocalInspectionMode.current

    var hasLocationPermission by remember {
        mutableStateOf(
            if (isPreview) false else (
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            )
        )
    }

    // ── State ────────────────────────────────────────────────
    var searchQuery       by remember { mutableStateOf("") }
    var suggestions       by remember { mutableStateOf<List<NominatimPlace>>(emptyList()) }
    var isSearching       by remember { mutableStateOf(false) }
    var showSuggestions   by remember { mutableStateOf(false) }
    var searchJob:  Job?  = remember { null }

    val hasLocation = lat != null && lng != null && lat != 0.0 && lng != 0.0
    val effectiveLat = if (lat == null || lat == 0.0) 13.7563 else lat
    val effectiveLng = if (lng == null || lng == 0.0) 100.5018 else lng

    val onLocationPickedState = rememberUpdatedState(onLocationPicked)
    val markerRef = remember { mutableStateOf<Marker?>(null) }
    val myLocationOverlayRef = remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val lastCenteredPoint = remember { mutableStateOf<GeoPoint?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            myLocationOverlayRef.value?.disableMyLocation()
            mapViewRef.value?.onDetach()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            fetchCurrentLocation(context) { fetchedLat, fetchedLng ->
                onLocationPicked(fetchedLat, fetchedLng)
            }
        }
    }

    LaunchedEffect(lat, lng) {
        if (!hasLocation) {
            searchQuery = ""
        }
    }

    val actualResetToCurrentLocation = onResetToCurrentLocation ?: {
        if (hasLocationPermission) {
            fetchCurrentLocation(context) { fetchedLat, fetchedLng ->
                onLocationPicked(fetchedLat, fetchedLng)
            }
        } else if (!isPreview) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    // ── Search function (debounce 400ms, Nominatim) ──────────
    fun searchPlaces(query: String) {
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
                val results = NominatimClient.service.search(query = query)
                suggestions     = results
                showSuggestions = results.isNotEmpty()
            } catch (e: Exception) {
                Log.e("MapComponents", "Nominatim search failed", e)
                suggestions     = emptyList()
                showSuggestions = false
            } finally {
                isSearching = false
            }
        }
    }

    fun selectPlace(place: NominatimPlace) {
        val placeLat = place.lat.toDoubleOrNull() ?: return
        val placeLng = place.lon.toDoubleOrNull() ?: return

        onLocationPicked(placeLat, placeLng)
        searchQuery     = place.displayName.ifBlank { "%.4f, %.4f".format(placeLat, placeLng) }
        showSuggestions = false
        suggestions     = emptyList()
        focusManager.clearFocus()
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
                    items(suggestions) { place ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectPlace(place) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, null, tint = RedPrimary, modifier = Modifier.size(18.dp))
                            Text(place.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextDark, maxLines = 2)
                        }
                        if (suggestions.last() != place) HorizontalDivider(color = BorderGray, thickness = 0.5.dp)
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
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(DEFAULT_ZOOM)
                            controller.setCenter(GeoPoint(effectiveLat, effectiveLng))

                            val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                    onLocationPickedState.value(p.latitude, p.longitude)
                                    focusManager.clearFocus()
                                    showSuggestions = false
                                    return true
                                }
                                override fun longPressHelper(p: GeoPoint): Boolean = false
                            })
                            overlays.add(eventsOverlay)

                            val marker = Marker(this).apply {
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                position = GeoPoint(effectiveLat, effectiveLng)
                            }
                            if (hasLocation) overlays.add(marker)
                            markerRef.value = marker

                            val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                            overlays.add(myLocationOverlay)
                            myLocationOverlayRef.value = myLocationOverlay
                            if (hasLocationPermission) myLocationOverlay.enableMyLocation()

                            mapViewRef.value = this
                            lastCenteredPoint.value = GeoPoint(effectiveLat, effectiveLng)
                        }
                    },
                    update = { mapView ->
                        val point = GeoPoint(effectiveLat, effectiveLng)
                        val marker = markerRef.value
                        if (marker != null) {
                            marker.position = point
                            if (hasLocation) {
                                if (!mapView.overlays.contains(marker)) mapView.overlays.add(marker)
                            } else {
                                mapView.overlays.remove(marker)
                            }
                        }
                        // เช็คก่อนว่า point เปลี่ยนจริงไหม — ไม่งั้นแผนที่จะดีดกลับตำแหน่งเดิมทุกครั้งที่
                        // recompose (เช่น แค่พิมพ์ในช่องค้นหา) ทับการ pan/zoom ที่ผู้ใช้ทำเองอยู่
                        if (lastCenteredPoint.value != point) {
                            mapView.controller.setCenter(point)
                            lastCenteredPoint.value = point
                        }

                        val myLocationOverlay = myLocationOverlayRef.value
                        if (myLocationOverlay != null) {
                            if (hasLocationPermission && !myLocationOverlay.isMyLocationEnabled) {
                                myLocationOverlay.enableMyLocation()
                            } else if (!hasLocationPermission && myLocationOverlay.isMyLocationEnabled) {
                                myLocationOverlay.disableMyLocation()
                            }
                        }

                        mapView.invalidate()
                    }
                )
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

        // Selected coordinates & actions
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasLocation) {
                Text(
                    "📍 ${"%.6f".format(lat)}, ${"%.6f".format(lng)}",
                    fontSize = 11.sp,
                    color = TextGray
                )
            } else {
                Text(
                    "📍 ยังไม่ได้ระบุตำแหน่ง",
                    fontSize = 11.sp,
                    color = TextGray
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasLocation && onClearLocation != null) {
                    TextButton(
                        onClick = onClearLocation,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = RedPrimary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("ล้างตำแหน่ง", fontSize = 11.sp, color = RedPrimary)
                    }
                }

                TextButton(
                    onClick = actualResetToCurrentLocation,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = RedPrimary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("รีเซ็ตเป็นตำแหน่งปัจจุบัน", fontSize = 11.sp, color = RedPrimary)
                }
            }
        }
    }
}
