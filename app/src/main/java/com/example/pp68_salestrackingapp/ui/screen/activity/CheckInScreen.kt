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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

private val RedPrimary = Color(0xFFCC1D1D)
private val White      = Color.White
private val TextDark   = Color(0xFF1A1A1A)
private val TextGray   = Color(0xFF888888)
private val BgLight    = Color(0xFFF5F5F5)

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

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchLocation(fusedLocationClient) { lat, lng ->
                currentLat = lat
                currentLng = lng
            }
        }
    }

    LaunchedEffect(activityId) {
        viewModel.loadActivity(activityId)
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            isFetchingLocation = true
            fetchLocation(fusedLocationClient) { lat, lng ->
                currentLat = lat
                currentLng = lng
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

    CheckInContent(
        uiState = s,
        currentLat = currentLat,
        currentLng = currentLng,
        isFetchingLocation = isFetchingLocation,
        onBack = onBack,
        onRefreshLocation = {
            isFetchingLocation = true
            fetchLocation(fusedLocationClient) { lat, lng ->
                currentLat = lat
                currentLng = lng
                isFetchingLocation = false
            }
        },
        onConfirmCheckin = { lat, lng ->
            viewModel.confirmCheckin(lat, lng)
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
                    val cameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(LatLng(currentLat, currentLng), 17f)
                    }

                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState
                    ) {
                        Marker(
                            state = rememberMarkerState(position = LatLng(currentLat, currentLng)),
                            title = "ตำแหน่งปัจจุบันของคุณ"
                        )
                    }
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
                    else Icon(Icons.Default.MyLocation, "My Location")
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
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().background(BgLight, RoundedCornerShape(12.dp)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = RedPrimary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Current Location", fontSize = 12.sp, color = TextGray)
                            Text(
                                if (currentLat != null && currentLng != null) "Lat: %.5f, Lng: %.5f".format(currentLat, currentLng)
                                else "กำลังค้นหาตำแหน่ง...",
                                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextDark
                            )
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
                        else Text("Confirm Check-in", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun fetchLocation(
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    onResult: (Double, Double) -> Unit
) {
    fusedLocationClient.getCurrentLocation(
        Priority.PRIORITY_HIGH_ACCURACY,
        CancellationTokenSource().token
    ).addOnSuccessListener { location ->
        location?.let {
            onResult(it.latitude, it.longitude)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CheckInScreenPreview() {
    val sampleActivity = SalesActivity(
        activityId = "1",
        projectId = "P1",
        customerId = "C1",
        userId = "U1",
        activityType = "Check-in",
        activityDate = "2023-10-27",
        detail = "เยี่ยมชมลูกค้าประจำสัปดาห์",
        status = "Planned",
        projectName = "โครงการก่อสร้างตึก A",
        companyName = "บริษัท ตัวอย่าง จำกัด"
    )
    
    SalesTrackingTheme {
        CheckInContent(
            uiState = ActivityDetailUiState(activity = sampleActivity),
            currentLat = 13.7563,
            currentLng = 100.5018,
            isFetchingLocation = false,
            onBack = {},
            onRefreshLocation = {},
            onConfirmCheckin = { _, _ -> }
        )
    }
}
