package com.example.pp68_salestrackingapp.ui.viewmodels.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.PlaceSearchRepository
import com.example.pp68_salestrackingapp.data.repository.PlaceSearchState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * คุมจังหวะการค้นหาสถานที่ให้อยู่ในโควตา Geoapify free plan (3,000 credits/วัน ใช้ร่วมกัน 50 คน)
 *
 * - พิมพ์อย่างน้อย 3 ตัวอักษรถึงเริ่มค้น
 * - debounce 800ms แล้วค่อยยิง ไม่ยิงทุกตัวอักษร
 * - คำค้นใหม่มา ยกเลิกงานเก่าทิ้ง
 * - คำเดิมซ้ำไม่ยิงใหม่
 * - ผลลัพธ์ที่กลับมาช้ากว่าคำค้นปัจจุบันจะถูกทิ้ง (กัน race condition)
 */
@HiltViewModel
class PlaceSearchViewModel @Inject constructor(
    private val repository: PlaceSearchRepository
) : ViewModel() {

    private val _state = MutableStateFlow<PlaceSearchState>(PlaceSearchState.Idle)
    val state: StateFlow<PlaceSearchState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var lastQuery: String = ""

    fun onQueryChanged(query: String) {
        val trimmed = query.trim()

        searchJob?.cancel()

        if (trimmed.length < PlaceSearchRepository.MIN_QUERY_LENGTH) {
            lastQuery = trimmed
            _state.value = PlaceSearchState.Idle
            return
        }

        // ข้อความไม่เปลี่ยนและมีผลอยู่แล้ว — ไม่ต้องค้นซ้ำ
        if (trimmed == lastQuery && _state.value !is PlaceSearchState.Idle) return
        lastQuery = trimmed

        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _state.value = PlaceSearchState.Loading
            val result = repository.search(trimmed)
            // ถ้าผู้ใช้พิมพ์ต่อระหว่างรอผล คำค้นจะเปลี่ยนไปแล้ว — ผลเก่าห้ามเขียนทับ
            if (trimmed == lastQuery) {
                _state.value = result
            }
        }
    }

    /** ผู้ใช้เลือกผลลัพธ์แล้ว — ปิด dropdown และหยุดค้นคำเดิมซ้ำ */
    fun onPlaceSelected(displayText: String) {
        searchJob?.cancel()
        lastQuery = displayText.trim()
        _state.value = PlaceSearchState.Idle
    }

    fun clear() {
        searchJob?.cancel()
        lastQuery = ""
        _state.value = PlaceSearchState.Idle
    }

    companion object {
        const val DEBOUNCE_MS = 800L
    }
}
