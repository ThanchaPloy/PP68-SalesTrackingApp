package com.example.pp68_salestrackingapp.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * นาฬิกาของแอป — ฉีดเข้าไปแทนการเรียก LocalDate.now() ตรง ๆ ในที่ที่เทสต์ต้องคุมเวลา
     *
     * มีไว้เพราะเดิม NotificationViewModel ใช้ System.getProperty("is_test") เป็นทางแยกให้
     * เทสต์บังคับให้การเตือน "ส่งรายงานรายสัปดาห์" โผล่ขึ้นมาได้ตลอด ซึ่งเท่ากับโค้ดที่ผู้ใช้รัน
     * มีกิ่งที่มีอยู่เพื่อเอาใจเทสต์ และชื่อ property นั้นไม่มีใครจอง ใครไปตั้งก็เปลี่ยนพฤติกรรมแอปได้
     *
     * โซนเวลาตรึงไว้ที่กรุงเทพฯ ตามที่ ViewModel เคยตรึงเอาไว้เอง — ผู้ใช้ทั้งหมดอยู่ไทย
     * และการเตือนต้องอ้างวันแบบเดียวกันกับที่ทีมเห็นในรายงาน ไม่ใช่ตามโซนของเครื่อง
     */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.system(ZoneId.of("Asia/Bangkok"))
}
