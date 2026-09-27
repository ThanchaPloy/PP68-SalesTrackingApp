package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.model.PlaceSuggestion
import com.example.pp68_salestrackingapp.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** สถานะของการค้นหาสถานที่ — หน้าจอ map ทุกที่ใช้ชุดเดียวกัน */
sealed class PlaceSearchState {
    object Idle : PlaceSearchState()
    object Loading : PlaceSearchState()
    data class Success(val places: List<PlaceSuggestion>) : PlaceSearchState()
    object Empty : PlaceSearchState()
    object Offline : PlaceSearchState()
    object RateLimited : PlaceSearchState()
    data class Error(val message: String) : PlaceSearchState()
}

/**
 * ค้นหาสถานที่ผ่าน backend proxy (ซึ่งเรียก Geoapify ต่อให้อีกที)
 *
 * การคุมโควตาแบ่งเป็นสองชั้น: ชั้นนี้กันไม่ให้ยิงซ้ำคำเดิม ส่วน debounce/ยกเลิก request เก่า
 * อยู่ที่ UI ที่รู้จังหวะพิมพ์ของผู้ใช้ และ backend ยังมี cache + เพดานรายวันอีกชั้น
 */
@Singleton
class PlaceSearchRepository @Inject constructor(
    private val apiService: ApiService
) {
    private val cache = LinkedHashMap<String, List<PlaceSuggestion>>()

    suspend fun search(query: String): PlaceSearchState {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return PlaceSearchState.Idle

        cache[trimmed.lowercase()]?.let { cached ->
            return if (cached.isEmpty()) PlaceSearchState.Empty else PlaceSearchState.Success(cached)
        }

        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.searchPlaces(trimmed)
                when {
                    response.isSuccessful -> {
                        val places = (response.body() ?: emptyList()).take(RESULT_LIMIT)
                        remember(trimmed.lowercase(), places)
                        if (places.isEmpty()) PlaceSearchState.Empty else PlaceSearchState.Success(places)
                    }
                    response.code() == 429 -> PlaceSearchState.RateLimited
                    else -> PlaceSearchState.Error("ไม่สามารถเชื่อมต่อระบบค้นหาสถานที่ได้ กรุณาลองใหม่")
                }
            } catch (e: IOException) {
                PlaceSearchState.Offline
            } catch (e: Exception) {
                PlaceSearchState.Error("ไม่สามารถเชื่อมต่อระบบค้นหาสถานที่ได้ กรุณาลองใหม่")
            }
        }
    }

    /** แปลงพิกัดเป็นชื่อสถานที่ — เรียกหลังผู้ใช้ยืนยันตำแหน่งแล้วเท่านั้น ห้ามเรียกระหว่างเลื่อนแผนที่ */
    suspend fun reverseGeocode(lat: Double, lon: Double): PlaceSuggestion? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.reverseGeocode(lat, lon)
                if (response.isSuccessful) response.body()?.firstOrNull() else null
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun remember(key: String, places: List<PlaceSuggestion>) {
        if (cache.size >= CACHE_MAX_ENTRIES) {
            cache.keys.firstOrNull()?.let { cache.remove(it) }
        }
        cache[key] = places
    }

    companion object {
        const val MIN_QUERY_LENGTH = 3
        const val RESULT_LIMIT = 5
        private const val CACHE_MAX_ENTRIES = 50
    }
}
