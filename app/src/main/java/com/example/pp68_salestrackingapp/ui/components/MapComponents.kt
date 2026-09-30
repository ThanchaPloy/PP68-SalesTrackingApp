package com.example.pp68_salestrackingapp.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pp68_salestrackingapp.data.model.PlaceSuggestion
import com.example.pp68_salestrackingapp.data.repository.PlaceSearchState
import com.example.pp68_salestrackingapp.ui.viewmodels.place.PlaceSearchViewModel
import com.example.pp68_salestrackingapp.utils.MapConfig
import com.example.pp68_salestrackingapp.utils.fetchCurrentLocation
import org.maplibre.android.geometry.LatLng

private val RedPrimary  = Color(0xFFCC1D1D)
private val TextDark    = Color(0xFF1A1A1A)
private val TextGray    = Color(0xFF888888)
private val BgField     = Color(0xFFF8F8F8)
private val BorderGray  = Color(0xFFE8E8E8)

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
    val isPreview    = LocalInspectionMode.current

    // @Preview ไม่มี Hilt graph — เรียก hiltViewModel() แล้วจะพัง
    val searchViewModel: PlaceSearchViewModel? = if (isPreview) null else hiltViewModel()
    val searchState by (searchViewModel?.state?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(PlaceSearchState.Idle) })

    var hasLocationPermission by remember {
        mutableStateOf(
            if (isPreview) false else (
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            )
        )
    }

    var searchQuery     by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    var selectedPlaceLabel by remember { mutableStateOf<String?>(null) }
    // ✅ ปิด location service ทั้งเครื่อง/อยู่ในอาคารจนจับดาวเทียมไม่ได้ = fetchCurrentLocation()
    // ไม่เรียก onResult กลับมาเลย เดิมไม่ได้ส่ง onError มาด้วย ปุ่ม "รีเซ็ตเป็นตำแหน่งปัจจุบัน"
    // จึงกดแล้วเงียบสนิท ไม่มีอะไรเกิดขึ้นและไม่มีทางรู้ว่าต้องไปเปิด location ก่อน
    // (CheckInScreen ผูก onError ไว้แล้ว เหลือจุดนี้จุดเดียว)
    var locationError by remember { mutableStateOf(false) }

    val hasLocation  = lat != null && lng != null && lat != 0.0 && lng != 0.0
    val effectiveLat = if (lat == null || lat == 0.0) MapConfig.DEFAULT_LAT else lat
    val effectiveLng = if (lng == null || lng == 0.0) MapConfig.DEFAULT_LNG else lng

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            locationError = false
            fetchCurrentLocation(context, onError = { locationError = true }) { fetchedLat, fetchedLng ->
                locationError = false
                onLocationPicked(fetchedLat, fetchedLng)
            }
        }
    }

    LaunchedEffect(lat, lng) {
        if (!hasLocation) {
            searchQuery = ""
            selectedPlaceLabel = null
            searchViewModel?.clear()
        }
    }

    val actualResetToCurrentLocation = onResetToCurrentLocation ?: {
        if (hasLocationPermission) {
            locationError = false
            fetchCurrentLocation(context, onError = { locationError = true }) { fetchedLat, fetchedLng ->
                locationError = false
                onLocationPicked(fetchedLat, fetchedLng)
            }
        } else if (!isPreview) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    fun selectPlace(place: PlaceSuggestion) {
        // ใช้พิกัดจากผลค้นหาเลย ไม่ยิง Place Details ซ้ำ — ประหยัด credit
        onLocationPicked(place.latitude, place.longitude)
        searchQuery = place.name
        selectedPlaceLabel = listOfNotNull(
            place.name.takeIf { it.isNotBlank() },
            place.formattedAddress.takeIf { it.isNotBlank() && it != place.name }
        ).joinToString(" · ")
        showSuggestions = false
        searchViewModel?.onPlaceSelected(place.name)
        focusManager.clearFocus()
    }

    val suggestions = (searchState as? PlaceSearchState.Success)?.places.orEmpty()
    val statusMessage = when (searchState) {
        PlaceSearchState.Empty -> "ไม่พบสถานที่ กรุณาลองใช้คำค้นอื่นหรือเลือกตำแหน่งบนแผนที่"
        PlaceSearchState.Offline -> "ไม่พบการเชื่อมต่ออินเทอร์เน็ต"
        PlaceSearchState.RateLimited -> "ใช้งานการค้นหาสถานที่เกินจำนวนที่กำหนด กรุณาลองใหม่ภายหลัง"
        is PlaceSearchState.Error -> (searchState as PlaceSearchState.Error).message
        else -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        OutlinedTextField(
            value         = searchQuery,
            onValueChange = {
                searchQuery = it
                showSuggestions = true
                searchViewModel?.onQueryChanged(it)
            },
            placeholder = { Text("ค้นหาสถานที่ หรือวางพิกัด...", color = TextGray, fontSize = 14.sp) },
            leadingIcon = {
                if (searchState is PlaceSearchState.Loading) {
                    CircularProgressIndicator(color = RedPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Search, null, tint = RedPrimary)
                }
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        showSuggestions = false
                        searchViewModel?.clear()
                    }) { Icon(Icons.Default.Clear, null, tint = TextGray) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(
                topStart = 10.dp, topEnd = 10.dp,
                bottomStart = if (showSuggestions && suggestions.isNotEmpty()) 0.dp else 10.dp,
                bottomEnd = if (showSuggestions && suggestions.isNotEmpty()) 0.dp else 10.dp
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

        // บอกสาเหตุเสมอเมื่อค้นไม่ได้ผล ผู้ใช้จะได้แยกออกว่า "ไม่เจอ" กับ "ระบบมีปัญหา/เกินโควตา"
        if (showSuggestions && statusMessage != null) {
            Text(
                statusMessage,
                fontSize = 11.sp,
                color = RedPrimary,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        if (showSuggestions && suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    itemsIndexed(suggestions) { index, place ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectPlace(place) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, null, tint = RedPrimary, modifier = Modifier.size(18.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    place.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    listOfNotNull(
                                        place.formattedAddress.takeIf { it.isNotBlank() && it != place.name },
                                        place.area?.takeIf { it.isNotBlank() && !place.formattedAddress.contains(it) }
                                    ).joinToString(" · "),
                                    fontSize = 11.sp,
                                    color = TextGray,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (index < suggestions.lastIndex) HorizontalDivider(color = BorderGray, thickness = 0.5.dp)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

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
                MapLibreMapView(
                    modifier = Modifier.fillMaxSize(),
                    markers = if (hasLocation) listOf(MapMarker(effectiveLat, effectiveLng)) else emptyList(),
                    cameraTarget = LatLng(effectiveLat, effectiveLng),
                    cameraZoom = if (hasLocation) MapConfig.PLACE_SELECTED_ZOOM else MapConfig.DEFAULT_ZOOM,
                    onMapTap = { tappedLat, tappedLng ->
                        // ปักหมุดเองเมื่อค้นหาไม่พบ — ไม่ต้องมี place id ก็บันทึกพิกัดได้
                        onLocationPicked(tappedLat, tappedLng)
                        selectedPlaceLabel = null
                        showSuggestions = false
                        focusManager.clearFocus()
                    }
                )
            }

            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.85f),
                shadowElevation = 2.dp
            ) {
                Text("ค้นหาหรือแตะแผนที่เพื่อปักหมุด", fontSize = 11.sp, color = TextGray, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }

        // ชื่อ/ที่อยู่ของสถานที่ที่เลือกจากผลค้นหา
        selectedPlaceLabel?.let { label ->
            Text(
                label,
                fontSize = 11.sp,
                color = TextDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (locationError) {
                Text(
                    "หาตำแหน่งปัจจุบันไม่ได้ เปิด GPS/ตำแหน่งที่ตั้งแล้วลองอีกครั้ง หรือแตะบนแผนที่เพื่อปักหมุดเอง",
                    fontSize = 11.sp,
                    color = RedPrimary,
                    modifier = Modifier.weight(1f)
                )
            } else if (hasLocation) {
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
