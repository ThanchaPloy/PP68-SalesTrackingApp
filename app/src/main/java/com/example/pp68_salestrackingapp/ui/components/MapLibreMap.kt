package com.example.pp68_salestrackingapp.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.pp68_salestrackingapp.utils.MapConfig
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.plugins.annotation.LineManager
import org.maplibre.android.plugins.annotation.LineOptions
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions

/** หมุดหนึ่งจุดบนแผนที่ */
data class MapMarker(
    val latitude: Double,
    val longitude: Double,
    val color: Int = 0xFFCC1D1D.toInt()
)

private const val MARKER_ICON_PREFIX = "pp68-pin-"

/**
 * แผนที่ MapLibre + OpenFreeMap ใช้ร่วมกันทุกหน้าจอ
 *
 * Attribution ของ OpenFreeMap / OpenMapTiles / OpenStreetMap แสดงโดย MapLibre เองตาม style
 * ห้ามปิด (`attributionEnabled`) เพราะเป็นเงื่อนไขการใช้ข้อมูล
 *
 * @param markers หมุดที่จะแสดง (ว่างได้)
 * @param cameraTarget ตำแหน่งที่อยากให้กล้องไป — เปลี่ยนค่าเมื่อไหร่กล้องถึงจะขยับ
 *                     ไม่ได้บังคับ center ทุก recompose เพื่อไม่ให้ทับการ pan/zoom ของผู้ใช้
 * @param onMapTap แตะแผนที่เพื่อเลือกพิกัด — null = ปิดการแตะเลือก
 */
@Composable
fun MapLibreMapView(
    modifier: Modifier = Modifier,
    markers: List<MapMarker> = emptyList(),
    cameraTarget: LatLng? = null,
    cameraZoom: Double = MapConfig.DEFAULT_ZOOM,
    /** เส้นเชื่อมระหว่างจุด (เช่น ตำแหน่งปัจจุบัน → จุดนัดหมายในหน้าเช็คอิน) */
    line: List<LatLng>? = null,
    lineColor: Int = 0xFF10B981.toInt(),
    /** ซูมให้เห็นทุกจุดที่ระบุ — ใช้แทน cameraTarget เมื่ออยากเห็นทั้งสองจุดพร้อมกัน */
    fitBounds: List<LatLng>? = null,
    onMapTap: ((Double, Double) -> Unit)? = null,
    onMapReady: ((MapLibreMap) -> Unit)? = null
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val symbolManagerRef = remember { mutableStateOf<SymbolManager?>(null) }
    val lineManagerRef = remember { mutableStateOf<LineManager?>(null) }
    val lastCameraTarget = remember { mutableStateOf<LatLng?>(null) }
    val lastFitBounds = remember { mutableStateOf<List<LatLng>?>(null) }
    val onMapTapState = rememberUpdatedState(onMapTap)
    val onMapReadyState = rememberUpdatedState(onMapReady)

    // MapView เป็น View ของ Android ที่ต้องได้รับ lifecycle callback ครบ ไม่งั้น render ค้าง/รั่ว
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mapView = mapViewRef.value ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            symbolManagerRef.value?.onDestroy()
            lineManagerRef.value?.onDestroy()
            mapViewRef.value?.let {
                it.onPause()
                it.onStop()
                it.onDestroy()
            }
            symbolManagerRef.value = null
            lineManagerRef.value = null
            mapViewRef.value = null
            mapRef.value = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapLibre.getInstance(ctx)
            MapView(ctx).apply {
                mapViewRef.value = this
                onCreate(null)
                onStart()
                onResume()
                getMapAsync { map ->
                    mapRef.value = map
                    map.setStyle(MapConfig.STYLE_URL) { style ->
                        // LineManager ต้องสร้างก่อน SymbolManager ไม่งั้นเส้นจะทับหมุด
                        val lineManager = LineManager(this, map, style)
                        lineManagerRef.value = lineManager
                        val symbolManager = SymbolManager(this, map, style).apply {
                            iconAllowOverlap = true
                            iconIgnorePlacement = true
                        }
                        symbolManagerRef.value = symbolManager
                        drawMarkers(style, symbolManager, markers)
                        drawLine(lineManager, line, lineColor)
                    }

                    val initial = cameraTarget ?: LatLng(MapConfig.DEFAULT_LAT, MapConfig.DEFAULT_LNG)
                    map.cameraPosition = CameraPosition.Builder()
                        .target(initial)
                        .zoom(cameraZoom)
                        .build()
                    lastCameraTarget.value = cameraTarget

                    map.addOnMapClickListener { point ->
                        val handler = onMapTapState.value
                        if (handler != null) {
                            handler(point.latitude, point.longitude)
                            true
                        } else {
                            false
                        }
                    }

                    onMapReadyState.value?.invoke(map)
                }
            }
        },
        update = {
            val map = mapRef.value ?: return@AndroidView
            val style = map.style

            val symbolManager = symbolManagerRef.value
            if (style != null && symbolManager != null) {
                drawMarkers(style, symbolManager, markers)
            }
            lineManagerRef.value?.let { drawLine(it, line, lineColor) }

            // ขยับกล้องเฉพาะตอนเป้าหมายเปลี่ยนจริง ไม่งั้นแผนที่จะดีดกลับทุกครั้งที่ recompose
            // (เช่น แค่พิมพ์ในช่องค้นหา) ทับการ pan/zoom ที่ผู้ใช้เพิ่งทำเอง
            if (fitBounds != null && fitBounds.size >= 2) {
                if (fitBounds != lastFitBounds.value) {
                    lastFitBounds.value = fitBounds
                    runCatching {
                        val bounds = LatLngBounds.Builder().includes(fitBounds).build()
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, FIT_BOUNDS_PADDING_PX))
                    }
                }
            } else if (cameraTarget != null && cameraTarget != lastCameraTarget.value) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(cameraTarget, cameraZoom))
                lastCameraTarget.value = cameraTarget
            }
        }
    )
}

private const val FIT_BOUNDS_PADDING_PX = 120

private fun drawLine(lineManager: LineManager, points: List<LatLng>?, color: Int) {
    lineManager.deleteAll()
    if (points == null || points.size < 2) return
    lineManager.create(
        LineOptions()
            .withLatLngs(points)
            .withLineColor(colorToHex(color))
            .withLineWidth(4f)
    )
}

/** LineOptions รับสีเป็นสตริง hex ไม่ใช่ int แบบ Android color */
private fun colorToHex(color: Int): String =
    String.format("#%06X", 0xFFFFFF and color)

private fun drawMarkers(
    style: org.maplibre.android.maps.Style,
    symbolManager: SymbolManager,
    markers: List<MapMarker>
) {
    symbolManager.deleteAll()
    markers.forEach { marker ->
        val iconId = MARKER_ICON_PREFIX + Integer.toHexString(marker.color)
        if (style.getImage(iconId) == null) {
            style.addImage(iconId, pinBitmap(marker.color))
        }
        symbolManager.create(
            SymbolOptions()
                .withLatLng(LatLng(marker.latitude, marker.longitude))
                .withIconImage(iconId)
                // ยึดปลายล่างของหมุดไว้ที่พิกัด เหมือนหมุดแผนที่ทั่วไป
                .withIconAnchor("bottom")
        )
    }
}

/**
 * วาดหมุดเองเป็น bitmap แทนที่จะพึ่ง drawable — โปรเจกต์นี้ไม่มีไอคอนหมุดอยู่ใน res
 * และเลี่ยงการเพิ่มไฟล์ asset ใหม่โดยไม่จำเป็น
 */
private fun pinBitmap(color: Int): Bitmap {
    val width = 48
    val height = 64
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE }

    val cx = width / 2f
    val cy = width / 2f
    val radius = width / 2f - 3f

    canvas.drawCircle(cx, cy, radius, body)
    canvas.drawCircle(cx, cy, radius, outline)

    // ขาหมุดชี้ลงไปที่พิกัดจริง
    val tail = android.graphics.Path().apply {
        moveTo(cx - 8f, cy + radius - 4f)
        lineTo(cx + 8f, cy + radius - 4f)
        lineTo(cx, height - 2f)
        close()
    }
    canvas.drawPath(tail, body)
    canvas.drawCircle(cx, cy, radius / 2.6f, hole)

    return bitmap
}
