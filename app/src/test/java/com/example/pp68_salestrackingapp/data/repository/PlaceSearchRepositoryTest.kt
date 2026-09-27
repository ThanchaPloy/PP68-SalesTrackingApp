package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.model.PlaceSuggestion
import com.example.pp68_salestrackingapp.data.remote.ApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceSearchRepositoryTest {

    private val apiService: ApiService = mockk()
    private lateinit var repo: PlaceSearchRepository

    private fun place(name: String, lat: Double = 13.75, lon: Double = 100.5) =
        PlaceSuggestion(
            name = name,
            formattedAddress = "$name, กรุงเทพมหานคร",
            area = "กรุงเทพมหานคร",
            latitude = lat,
            longitude = lon
        )

    private fun errorBody(code: Int) =
        Response.error<List<PlaceSuggestion>>(code, "{}".toResponseBody("application/json".toMediaType()))

    @Before
    fun setUp() {
        repo = PlaceSearchRepository(apiService)
    }

    @Test
    fun `คำค้นสั้นกว่า 3 ตัวอักษรไม่ยิง API`() = runTest {
        val state = repo.search("เซ")

        assertTrue(state is PlaceSearchState.Idle)
        coVerify(exactly = 0) { apiService.searchPlaces(any()) }
    }

    @Test
    fun `ค้นเจอแล้วคืนผลลัพธ์`() = runTest {
        coEvery { apiService.searchPlaces("เซ็นทรัลลาดพร้าว") } returns
            Response.success(listOf(place("เซ็นทรัลลาดพร้าว")))

        val state = repo.search("เซ็นทรัลลาดพร้าว")

        assertTrue(state is PlaceSearchState.Success)
        assertEquals("เซ็นทรัลลาดพร้าว", (state as PlaceSearchState.Success).places.first().name)
    }

    @Test
    fun `ผลลัพธ์ถูกจำกัดไม่เกิน 5 รายการ`() = runTest {
        val many = (1..10).map { place("สถานที่ $it") }
        coEvery { apiService.searchPlaces(any()) } returns Response.success(many)

        val state = repo.search("สถานที่")

        assertEquals(5, (state as PlaceSearchState.Success).places.size)
    }

    @Test
    fun `ค้นคำเดิมซ้ำใช้ cache ไม่ยิง API อีก`() = runTest {
        coEvery { apiService.searchPlaces("สยามพารากอน") } returns
            Response.success(listOf(place("สยามพารากอน")))

        repo.search("สยามพารากอน")
        repo.search("สยามพารากอน")

        coVerify(exactly = 1) { apiService.searchPlaces("สยามพารากอน") }
    }

    @Test
    fun `ไม่พบผลลัพธ์คืนสถานะ Empty`() = runTest {
        coEvery { apiService.searchPlaces(any()) } returns Response.success(emptyList())

        val state = repo.search("สถานที่ที่ไม่มีอยู่จริงเลย")

        assertTrue(state is PlaceSearchState.Empty)
    }

    @Test
    fun `HTTP 429 คืนสถานะ RateLimited`() = runTest {
        coEvery { apiService.searchPlaces(any()) } returns errorBody(429)

        val state = repo.search("สุวรรณภูมิ")

        assertTrue(state is PlaceSearchState.RateLimited)
    }

    @Test
    fun `HTTP 401 คีย์ผิดคืนสถานะ Error ไม่ทำให้พัง`() = runTest {
        coEvery { apiService.searchPlaces(any()) } returns errorBody(401)

        val state = repo.search("สุวรรณภูมิ")

        assertTrue(state is PlaceSearchState.Error)
    }

    @Test
    fun `ไม่มีอินเทอร์เน็ตคืนสถานะ Offline`() = runTest {
        coEvery { apiService.searchPlaces(any()) } throws IOException("no network")

        val state = repo.search("ลาดกระบัง")

        assertTrue(state is PlaceSearchState.Offline)
    }

    @Test
    fun `timeout คืนสถานะ Offline ไม่ crash`() = runTest {
        coEvery { apiService.searchPlaces(any()) } throws SocketTimeoutException("timeout")

        val state = repo.search("ลาดกระบัง")

        assertTrue(state is PlaceSearchState.Offline)
    }

    @Test
    fun `reverse geocode คืน null เมื่อ API ล้มเหลว ไม่ crash`() = runTest {
        coEvery { apiService.reverseGeocode(any(), any()) } throws IOException("no network")

        assertEquals(null, repo.reverseGeocode(13.75, 100.5))
    }

    @Test
    fun `reverse geocode คืนผลแรกเมื่อสำเร็จ`() = runTest {
        coEvery { apiService.reverseGeocode(any(), any()) } returns
            Response.success(listOf(place("สจล.")))

        assertEquals("สจล.", repo.reverseGeocode(13.72, 100.77)?.name)
    }
}
