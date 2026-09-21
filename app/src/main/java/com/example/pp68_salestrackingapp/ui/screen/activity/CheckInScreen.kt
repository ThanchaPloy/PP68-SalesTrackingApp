package com.example.pp68_salestrackingapp.ui.screen.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ActivityDetailViewModel
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ActivityDetailUiState
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pp68_salestrackingapp.utils.OsmMapnikTileSource
import com.example.pp68_salestrackingapp.utils.fetchCurrentLocation
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

private val RedPrimary = Color(0xFFCC1D1D)
private val White      = Color.White
private val TextDark   = Color(0xFF1A1A1A)
private val TextGray   = Color(0xFF888888)
private val BgLight    = Color(0xFFF5F5F5)
private val WarningOrange = Color(0xFFF59E0B)

@Composable
fun CheckInScreen(
    activityId: String,
    onBack: () -> Unit,
    viewModel: ActivityDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val s by viewModel.uiState.collectAsState()
    
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLng by remember { mutableStateOf<Double?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchCurrentLocation(context) { lat, lng ->
                currentLat = lat
                currentLng = lng
                viewModel.updateCurrentLocation(lat, lng)
            }
        }
    }

    LaunchedEffect(activityId) {
        viewModel.loadActivity(activityId)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            isFetchingLocation = true
            fetchCurrentLocation(context) { lat, lng ->
                currentLat = lat
                currentLng = lng
                viewModel.updateCurrentLocation(lat, lng)
                isFetchingLocation = false
            }
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    if (s.activity?.status == "checked_in") {
        LaunchedEffect(Unit) { onBack() }
    }

    // Confirmation Dialog for Mismatch
    if (s.showCheckinDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowCheckinDialog(false) },
            icon = { Icon(Icons.Default.Warning, null, tint = WarningOrange) },
            title = { Text("ยืนยันการเช็คอินนอกพื้นที่") },
            text = { 
                Text("คุณอยู่ห่างจากจุดนัดหมายเป็นระยะทางประมาณ ${"%.0f".format(s.currentDistance)} เมตร " +
                     "ซึ่งเกินกว่าระยะที่กำหนด (200ม.) ต้องการยืนยันการเช็คอินใช่หรือไม่?") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (currentLat != null && currentLng != null) {
                            viewModel.confirmCheckin(currentLat!!, currentLng!!)
                        }
                    }
                ) { Text("ยืนยัน", color = RedPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowCheckinDialog(false) }) { Text("ยกเลิก") }
            }
        )
    }

    CheckInContent(
        uiState = s,
        currentLat = currentLat,
        currentLng = currentLng,
        isFetchingLocation = isFetchingLocation,
        onBack = onBack,
        onRefreshLocation = {
            isFetchingLocation = true
            fetchCurrentLocation(context) { lat, lng ->
                currentLat = lat
                currentLng = lng
                viewModel.updateCurrentLocation(lat, lng)
                isFetchingLocation = false
            }
        },
        onConfirmCheckin = { lat, lng ->
            if (s.isLocationMismatch) {
                viewModel.setShowCheckinDialog(true)
            } else {
                viewModel.confirmCheckin(lat, lng)
            }
        }
    )
}

@Composable
fun CheckInContent(
    uiState: ActivityDetailUiState,
    currentLat: Double?,
    currentLng: Double?,
    isFetchingLocation: Boolean,
    onBack: () -> Unit,
    onRefreshLocation: () -> Unit,
    onConfirmCheckin: (Double, Double) -> Unit
) {
    Scaffold(
        topBar = {
            Surface(shadowElevation = 1.dp, color = White) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    Text("Check-in", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(BgLight),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (currentLat != null && currentLng != null) {
                    val context = LocalContext.current
                    val currentPos = GeoPoint(currentLat, currentLng)
                    val targetLat = uiState.activity?.plannedLat
                    val targetLng = uiState.activity?.plannedLong
                    val lineColor = if (uiState.isLocationMismatch) RedPrimary else Color(0xFF10B981)
                    val hasLocationPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val myLocationOverlayRef = remember { mutableStateOf<MyLocationNewOverlay?>(null) }
                    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
                    val lastFittedBounds = remember { mutableStateOf<Pair<GeoPoint, GeoPoint>?>(null) }

                    DisposableEffect(Unit) {
                        onDispose {
                            myLocationOverlayRef.value?.disableMyLocation()
                            mapViewRef.value?.onDetach()
                        }
                    }

                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(OsmMapnikTileSource)
                                setMultiTouchControls(true)
                                setBuiltInZoomControls(false)
                                controller.setZoom(15.0)
                                controller.setCenter(currentPos)

                                val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                                overlays.add(myLocationOverlay)
                                myLocationOverlayRef.value = myLocationOverlay
                                if (hasLocationPermission) myLocationOverlay.enableMyLocation()

                                mapViewRef.value = this
                            }
                        },
                        update = { mapView ->
                            mapView.overlays.removeAll { it !== myLocationOverlayRef.value }

                            val myLocationOverlay = myLocationOverlayRef.value
                            if (myLocationOverlay != null) {
                                if (hasLocationPermission && !myLocationOverlay.isMyLocationEnabled) {
                                    myLocationOverlay.enableMyLocation()
                                } else if (!hasLocationPermission && myLocationOverlay.isMyLocationEnabled) {
                                    myLocationOverlay.disableMyLocation()
                                }
                            }

                            val currentMarker = Marker(mapView).apply {
                                position = currentPos
                                title = "ตำแหน่งปัจจุบันของคุณ"
                                snippet = "คุณอยู่ที่นี่"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            mapView.overlays.add(currentMarker)

                            if (targetLat != null && targetLng != null) {
                                val targetPos = GeoPoint(targetLat, targetLng)

                                val targetMarker = Marker(mapView).apply {
                                    position = targetPos
                                    title = "จุดนัดหมาย"
                                    snippet = "เป้าหมายการเช็คอิน"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    icon = androidx.core.content.ContextCompat.getDrawable(
                                        mapView.context, org.osmdroid.library.R.drawable.marker_default
                                    )?.mutate()?.apply {
                                        setTint(android.graphics.Color.parseColor("#2196F3"))
                                    }
                                }
                                mapView.overlays.add(targetMarker)

                                val line = Polyline(mapView).apply {
                                    setPoints(listOf(currentPos, targetPos))
                                    outlinePaint.color = lineColor.toArgb()
                                    outlinePaint.strokeWidth = 5f
                                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                                }
                                mapView.overlays.add(0, line)

                                // เช็คก่อนว่าจุดสองจุดนี้เปลี่ยนจริงไหม — ไม่งั้นจะรีเซ็ตซูม/แพนที่ผู้ใช้
                                // ปรับเองทุกครั้งที่ recompose (เช่น isFetchingLocation เปลี่ยน)
                                val boundsKey = currentPos to targetPos
                                if (lastFittedBounds.value != boundsKey) {
                                    lastFittedBounds.value = boundsKey
                                    val bounds = BoundingBox.fromGeoPoints(listOf(currentPos, targetPos))
                                    mapView.post { mapView.zoomToBoundingBox(bounds, true, 150) }
                                }
                            }

                            mapView.invalidate()
                        }
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = RedPrimary)
                    }
                }
                
                FloatingActionButton(
                    onClick = onRefreshLocation,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    containerColor = White,
                    contentColor = RedPrimary,
                    shape = CircleShape
                ) {
                    if (isFetchingLocation) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = RedPrimary, strokeWidth = 2.dp)
                    else Icon(Icons.Default.MyLocation, "ตำแหน่งของฉัน")
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = White,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = uiState.activity?.detail ?: "กำลังโหลดข้อมูล...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextDark,
                        textAlign = TextAlign.Center
                    )
                    
                    Column(
                        modifier = Modifier.fillMaxWidth().background(BgLight, RoundedCornerShape(12.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = RedPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("ตำแหน่งปัจจุบัน", fontSize = 12.sp, color = TextGray)
                                Text(
                                    if (currentLat != null && currentLng != null) "Lat: %.5f, Lng: %.5f".format(currentLat, currentLng)
                                    else "กำลังค้นหาตำแหน่ง...",
                                    fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextDark
                                )
                            }
                        }
                        
                        // แสดงระยะห่างและคำเตือน
                        if (currentLat != null && uiState.activity?.plannedLat != null) {
                            Divider(color = Color.LightGray.copy(alpha = 0.5f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (uiState.isLocationMismatch) Icons.Default.Warning else Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (uiState.isLocationMismatch) WarningOrange else Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("ระยะห่างจากจุดนัดหมาย", fontSize = 12.sp, color = TextGray)
                                    Text(
                                        text = "${"%.0f".format(uiState.currentDistance)} เมตร",
                                        fontSize = 14.sp, 
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.isLocationMismatch) RedPrimary else Color(0xFF10B981)
                                    )
                                    if (uiState.isLocationMismatch) {
                                        Text(
                                            "อยู่นอกระยะที่กำหนด (เกิน 200ม.)",
                                            fontSize = 11.sp,
                                            color = RedPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.error != null) {
                        Text(uiState.error, color = RedPrimary, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            if (currentLat != null && currentLng != null) {
                                onConfirmCheckin(currentLat, currentLng)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                        shape = RoundedCornerShape(14.dp),
                        enabled = currentLat != null && !uiState.isCheckingIn
                    ) {
                        if (uiState.isCheckingIn) CircularProgressIndicator(color = White, modifier = Modifier.size(24.dp))
                        else Text("ยืนยันการเช็คอิน", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CheckInContentPreview() {
    SalesTrackingTheme {
        CheckInContent(
            uiState = ActivityDetailUiState(
                activity = SalesActivity(
                    activityId = "1",
                    userId = "user1",
                    customerId = "cust1",
                    activityType = "Meeting",
                    activityDate = "2023-10-27",
                    detail = "Meeting with ABC Company",
                    status = "pending",
                    plannedLat = 13.7563,
                    plannedLong = 100.5018
                ),
                currentDistance = 50.0,
                isLocationMismatch = false
            ),
            currentLat = 13.7565,
            currentLng = 100.5020,
            isFetchingLocation = false,
            onBack = {},
            onRefreshLocation = {},
            onConfirmCheckin = { _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CheckInContentMismatchPreview() {
    SalesTrackingTheme {
        CheckInContent(
            uiState = ActivityDetailUiState(
                activity = SalesActivity(
                    activityId = "1",
                    userId = "user1",
                    customerId = "cust1",
                    activityType = "Meeting",
                    activityDate = "2023-10-27",
                    detail = "Meeting with ABC Company",
                    status = "pending",
                    plannedLat = 13.7563,
                    plannedLong = 100.5018
                ),
                currentDistance = 350.0,
                isLocationMismatch = true
            ),
            currentLat = 13.7600,
            currentLng = 100.5050,
            isFetchingLocation = false,
            onBack = {},
            onRefreshLocation = {},
            onConfirmCheckin = { _, _ -> }
        )
    }
}
