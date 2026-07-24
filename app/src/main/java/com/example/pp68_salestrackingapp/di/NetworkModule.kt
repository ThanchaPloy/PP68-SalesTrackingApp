package com.example.pp68_salestrackingapp.di

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
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

    // Only serialize fields that have @SerializedName — local-only Room fields (isSynced,
    // projectName, companyName, etc.) have no @SerializedName and must not reach PostgREST.
    private val gson = GsonBuilder()
        .addSerializationExclusionStrategy(object : ExclusionStrategy {
            override fun shouldSkipField(f: FieldAttributes) =
                f.getAnnotation(SerializedName::class.java) == null
            override fun shouldSkipClass(clazz: Class<*>) = false
        })
        .create()

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenManager: TokenManager): Interceptor {
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
                !path.contains("register-api") && 
                !path.contains("change-password-api")) {
                
                if (originalRequest.url.host.contains("postgrest") || originalRequest.url.encodedPath.contains("/db/")) {
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
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
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
