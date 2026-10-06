package com.example.pp68_salestrackingapp.di

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.annotations.SerializedName
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import com.example.pp68_salestrackingapp.BuildConfig
import com.example.pp68_salestrackingapp.data.remote.ApiService
import com.example.pp68_salestrackingapp.data.remote.AuthService
import com.example.pp68_salestrackingapp.data.remote.UploadApiService
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.gson.*
import javax.inject.Named

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PostgRestRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LoginRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val POSTGREST_URL = BuildConfig.POSTGREST_URL
    private val BASE_AUTH_URL = BuildConfig.BASE_AUTH_URL
    private val UPLOAD_URL    = BuildConfig.UPLOAD_URL

    // Gson สร้าง object ผ่าน reflection ตรงๆ ไม่ผ่าน constructor ของ Kotlin เลย ทำให้ทะลุ
    // null-safety ได้ปกติ — ถ้า response มี "planned_date"/"plan_status" เป็น null จริงๆ
    // SalesActivity.activityDate/status (ประกาศเป็น non-null String) จะกลายเป็น null ได้เฉยๆ
    // แล้วไปพังตรงจุดที่เรียก .status.lowercase() ฯลฯ ตรงๆ (มีหลายที่ในแอป) แบบไม่มี compile
    // error เตือนเลยเพราะ compiler เชื่อว่ามันเป็น non-null อยู่แล้ว — กันไว้จุดเดียวตรงนี้แทน
    // การไล่แก้ทุก call site ทั่วแอป ใช้ delegate ธรรมดา (ไม่มี custom adapter) กัน infinite recursion
    private val plainGson = Gson()
    private val salesActivityDeserializer = JsonDeserializer<SalesActivity> { json, _, _ ->
        val obj = json.asJsonObject
        listOf("type", "planned_date", "plan_status").forEach { key ->
            if (!obj.has(key) || obj.get(key).isJsonNull) obj.addProperty(key, "")
        }
        plainGson.fromJson(obj, SalesActivity::class.java)
    }

    // Only serialize fields that have @SerializedName — local-only Room fields (isSynced,
    // projectName, locationName, etc.) have no @SerializedName and must not reach PostgREST.
    //
    // ข้อยกเว้นที่ตั้งใจ: SalesActivity.companyName มี @SerializedName("customer_name") เพื่อ
    // "อ่าน" ชื่อบริษัทที่ server แนบมากับนัดหมาย (ลูกค้า ERP ไม่ได้อยู่ใน Room อีกแล้ว)
    // ค่านี้ไม่หลุดขึ้น server เพราะทุกทางที่เขียนนัดหมายประกอบ Map เอง ไม่ได้ส่ง entity ให้ Gson
    // internal เพื่อให้เทสต์ยิง gson ตัวเดียวกับที่ Retrofit ใช้จริง — ถ้าเทสต์สร้าง Gson ของตัวเอง
    // มันจะไม่ได้ทดสอบ adapter ตัวจริงเลย กลายเป็นเทสต์ที่ผ่านแต่ไม่กันอะไร
    internal val gson = GsonBuilder()
        .addSerializationExclusionStrategy(object : ExclusionStrategy {
            override fun shouldSkipField(f: FieldAttributes) =
                f.getAnnotation(SerializedName::class.java) == null
            override fun shouldSkipClass(clazz: Class<*>) = false
        })
        .registerTypeAdapter(SalesActivity::class.java, salesActivityDeserializer)
        .create()

    @Provides
    @Singleton
    fun provideGson(): Gson = gson

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        tokenManager: TokenManager,
        serverTimeAnchor: com.example.pp68_salestrackingapp.utils.ServerTimeAnchor
    ): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()

            val body = originalRequest.body
            val contentType = body?.contentType()
            val isMultipart = contentType?.type == "multipart"

            if (!isMultipart && originalRequest.header("Content-Type") == null) {
                requestBuilder.header("Content-Type", "application/json")
            }

            val path = originalRequest.url.encodedPath
            // ✅ ไม่ใส่ Header สำหรับ API ที่เกี่ยวกับการยืนยันตัวตน
            if (!path.contains("login-api") &&
                !path.contains("register-api")) {
                
                val postgrestHost = try { java.net.URL(POSTGREST_URL).host } catch (e: Exception) { "" }
                val requestHost = originalRequest.url.host
                if (requestHost.contains("postgrest") || 
                    (postgrestHost.isNotEmpty() && requestHost == postgrestHost) || 
                    originalRequest.url.encodedPath.contains("/db/")) {
                    
                    if (!path.contains("upload-visit-photo")) {
                        requestBuilder
                            .header("Accept-Profile", "public")
                            .header("Content-Profile", "public")
                    }
                }

                val token = tokenManager.getToken()
                if (!token.isNullOrEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
            }

            val response = chain.proceed(requestBuilder.build())
            // ทุก HTTP response มี header Date จากเซิร์ฟเวอร์อยู่แล้ว จึงได้เวลาอ้างอิงฟรี
            // โดยไม่ต้องเพิ่มฟิลด์ server_time ในทุก endpoint (แผนงาน B.4 ข้อ 1)
            response.headers.getDate("Date")?.let { serverTimeAnchor.record(it.time) }
            // ✅ 401 จาก endpoint ที่ต้อง auth หมายถึง token หมดอายุ/ไม่ถูกต้องเสมอ (ต่างจาก
            // login-api ที่ 401 หมายถึงรหัสผ่านผิด) — เคลียร์ token แล้วแจ้งให้เด้งไปหน้า Login
            // change-password-api ก็ 401 ตอนกรอกรหัสผ่านเดิมผิดเหมือนกัน (คนละความหมายกับ token
            // หมดอายุ) ไม่งั้นพิมพ์รหัสเดิมผิดจะโดนเด้งออกจากระบบทั้งที่ยัง login อยู่ปกติ
            if (response.code == 401 && !path.contains("login-api") && !path.contains("register-api") &&
                !path.contains("change-password-api") && !path.contains("complete-initial-setup")
            ) {
                tokenManager.notifySessionExpired()
            }
            response
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: Interceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
            // ✅ Level.BODY log ทุก header รวม Authorization — APK debug ถูกแจกให้ทีมทดสอบใช้จริง
            // ใครต่อ USB ดู logcat ก็ได้ JWT ของคนอื่นไปใช้ต่อได้เลย (อายุ token 168 ชั่วโมง)
            redactHeader("Authorization")
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @PostgRestRetrofit
    fun providePostgRestRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(POSTGREST_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    @LoginRetrofit
    fun provideLoginRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_AUTH_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(@PostgRestRetrofit retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthService(@LoginRetrofit retrofit: Retrofit): AuthService {
        return retrofit.create(AuthService::class.java)
    }

    @Provides
    @Singleton
    @Named("upload")
    fun provideUploadRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(UPLOAD_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideUploadApiService(@Named("upload") retrofit: Retrofit): UploadApiService {
        return retrofit.create(UploadApiService::class.java)
    }
}
