package com.example.pp68_salestrackingapp.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SalesTrackingAppViewModel @Inject constructor(
    private val authRepo: AuthRepository
) : ViewModel() {
    fun isLoggedIn(): Boolean = authRepo.isUserLoggedIn()
    val sessionExpired = authRepo.sessionExpired

    /**
     * AuthRepository.logout() ปฏิเสธการออกจากระบบถ้ายังมีข้อมูลค้างไม่ได้ซิงค์ เพื่อไม่ให้เช็คอิน
     * หรือบันทึกผลที่ทำตอนออฟไลน์หายไป — ต้องรอผลก่อนแล้วค่อยพาไปหน้า Login
     * เดิมหน้าจอส่วนใหญ่เรียก logout() แล้ว navigate ทันทีโดยไม่ดูผล ทำให้ผู้ใช้เห็นหน้า Login
     * ทั้งที่ token กับข้อมูลยังอยู่ในเครื่องครบ กลไกป้องกันจึงไม่เคยทำงาน
     */
    fun logout(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            authRepo.logout().fold(
                onSuccess = { onSuccess() },
                onFailure = { onFailure(it.message ?: "ออกจากระบบไม่สำเร็จ") }
            )
        }
    }
}
