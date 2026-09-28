package com.example.pp68_salestrackingapp.ui.viewmodels.place

import com.example.pp68_salestrackingapp.data.model.PlaceSuggestion
import com.example.pp68_salestrackingapp.data.repository.PlaceSearchRepository
import com.example.pp68_salestrackingapp.data.repository.PlaceSearchState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceSearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository: PlaceSearchRepository = mockk()
    private lateinit var viewModel: PlaceSearchViewModel

    private fun place(name: String) = PlaceSuggestion(
        name = name,
        formattedAddress = "$name, กรุงเทพมหานคร",
        area = "กรุงเทพมหานคร",
        latitude = 13.75,
        longitude = 100.5
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = PlaceSearchViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `พิมพ์ไม่ถึง 3 ตัวอักษรไม่เรียก repository`() = runTest {
        viewModel.onQueryChanged("เซ")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.search(any()) }
        assertTrue(viewModel.state.value is PlaceSearchState.Idle)
    }

    @Test
    fun `debounce ทำงาน — พิมพ์รัวหลายตัวยิงแค่ครั้งเดียวด้วยคำสุดท้าย`() = runTest {
        coEvery { repository.search(any()) } returns PlaceSearchState.Success(listOf(place("สยาม")))

        // พิมพ์ทีละตัวเร็วกว่า debounce
        viewModel.onQueryChanged("สยา")
        advanceTimeBy(100)
        viewModel.onQueryChanged("สยาม")
        advanceTimeBy(100)
        viewModel.onQueryChanged("สยามพา")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.search("สยามพา") }
        coVerify(exactly = 0) { repository.search("สยา") }
        coVerify(exactly = 0) { repository.search("สยาม") }
    }

    @Test
    fun `คำค้นใหม่ยกเลิกงานเก่าที่ยังไม่เสร็จ`() = runTest {
        coEvery { repository.search("เซ็นทรัลลาด") } coAnswers {
            delay(5_000)
            PlaceSearchState.Success(listOf(place("ผลเก่า")))
        }
        coEvery { repository.search("สุวรรณภูมิ") } returns
            PlaceSearchState.Success(listOf(place("สนามบินสุวรรณภูมิ")))

        viewModel.onQueryChanged("เซ็นทรัลลาด")
        advanceTimeBy(PlaceSearchViewModel.DEBOUNCE_MS + 100) // เริ่มยิงคำแรกแล้ว
        viewModel.onQueryChanged("สุวรรณภูมิ")                 // เปลี่ยนคำระหว่างรอผล
        advanceUntilIdle()

        // ผลของคำเก่าต้องไม่เขียนทับคำใหม่
        val state = viewModel.state.value
        assertTrue(state is PlaceSearchState.Success)
        assertEquals("สนามบินสุวรรณภูมิ", (state as PlaceSearchState.Success).places.first().name)
    }

    @Test
    fun `คำค้นเดิมซ้ำไม่ยิงใหม่`() = runTest {
        coEvery { repository.search(any()) } returns PlaceSearchState.Success(listOf(place("สจล.")))

        viewModel.onQueryChanged("ลาดกระบัง")
        advanceUntilIdle()
        viewModel.onQueryChanged("ลาดกระบัง")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.search("ลาดกระบัง") }
    }

    @Test
    fun `สถานะ RateLimited ส่งต่อถึง UI`() = runTest {
        coEvery { repository.search(any()) } returns PlaceSearchState.RateLimited

        viewModel.onQueryChanged("สยามพารากอน")
        advanceUntilIdle()

        assertTrue(viewModel.state.value is PlaceSearchState.RateLimited)
    }

    @Test
    fun `วางพิกัดได้ผลทันทีโดยไม่เรียก API และพิกัดตรงเป๊ะ`() = runTest {
        viewModel.onQueryChanged("13.540690, 100.613096")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.search(any()) }
        val state = viewModel.state.value
        assertTrue(state is PlaceSearchState.Success)
        val place = (state as PlaceSearchState.Success).places.single()
        assertEquals(13.540690, place.latitude, 0.0)
        assertEquals(100.613096, place.longitude, 0.0)
    }

    @Test
    fun `เลือกสถานที่แล้วปิดผลการค้นหา`() = runTest {
        coEvery { repository.search(any()) } returns PlaceSearchState.Success(listOf(place("สยามพารากอน")))

        viewModel.onQueryChanged("สยามพารากอน")
        advanceUntilIdle()
        assertTrue(viewModel.state.value is PlaceSearchState.Success)

        viewModel.onPlaceSelected("สยามพารากอน")
        advanceUntilIdle()

        assertTrue(viewModel.state.value is PlaceSearchState.Idle)
    }
}
