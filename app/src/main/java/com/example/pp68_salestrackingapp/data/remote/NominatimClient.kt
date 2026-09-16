package com.example.pp68_salestrackingapp.data.remote

import com.example.pp68_salestrackingapp.BuildConfig
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class NominatimPlace(
    @SerializedName("place_id") val placeId: Long? = null,
    @SerializedName("lat") val lat: String = "",
    @SerializedName("lon") val lon: String = "",
    @SerializedName("display_name") val displayName: String = ""
)

interface NominatimService {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("countrycodes") countryCodes: String = "th",
        @Query("limit") limit: Int = 5
    ): List<NominatimPlace>

    @GET("reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json"
    ): NominatimPlace
}

/**
 * Nominatim usage policy (https://operations.osmfoundation.org/policies/nominatim/) ต้องระบุ
 * User-Agent ที่บอกตัวตนแอป/ทีมที่ติดต่อได้ — ค่ามาจาก BuildConfig.OSM_USER_AGENT
 */
object NominatimClient {
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", BuildConfig.OSM_USER_AGENT)
                    .build()
            )
        }
        .build()

    val service: NominatimService by lazy {
        Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimService::class.java)
    }
}
