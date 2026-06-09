package com.example.pp68_salestrackingapp.di

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

    private const val POSTGREST_URL = "https://postgrest-451670558907.asia-southeast1.run.app/"
    private const val LOGIN_URL = "https://asia-southeast1-algebraic-ratio-490214-r0.cloudfunctions.net/"

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenManager: TokenManager): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()

            // ✅ แก้ไข: ตรวจสอบ Content-Type เพื่อไม่ให้ทับ Multipart (อัปโหลดรูป)
            val body = originalRequest.body
            val contentType = body?.contentType()
            val isMultipart = contentType?.type == "multipart"

            if (!isMultipart && originalRequest.header("Content-Type") == null) {
                requestBuilder.header("Content-Type", "application/json")
            }

            val path = originalRequest.url.encodedPath
            if (!path.contains("login-api") && !path.contains("register-api")) {
                
                // ✅ ส่ง PostgREST headers เฉพาะเมื่อเรียก PostgREST URL
                if (originalRequest.url.host.contains("postgrest")) {
                    requestBuilder
                        .header("Accept-Profile", "public")
                        .header("Content-Profile", "public")
                }

                val token = tokenManager.getToken()
                if (!token.isNullOrEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
            }

            chain.proceed(requestBuilder.build())
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: Interceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
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
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @LoginRetrofit
    fun provideLoginRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(LOGIN_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
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
    fun provideKtorHttpClient(okHttpClient: OkHttpClient): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                preconfigured = okHttpClient
            }
            install(ContentNegotiation) {
                gson()
            }
        }
    }

    @Provides
    @Singleton
    @Named("upload")
    fun provideUploadRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(LOGIN_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideUploadApiService(@Named("upload") retrofit: Retrofit): UploadApiService {
        return retrofit.create(UploadApiService::class.java)
    }
}
