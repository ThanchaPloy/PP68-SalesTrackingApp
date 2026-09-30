package com.example.pp68_salestrackingapp.ui.viewmodels.customer

import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * กลไก draft autosave ถูกคัดลอกไว้เหมือนกันใน ViewModel ฟอร์มทั้ง 5 ตัว แต่ AddCustomer เป็นตัวเดียว
 * ที่ไม่มีเทสต์คุมเลยสักข้อ — แก้ที่ไฟล์นี้แล้วลืมไฟล์อื่นเคยเกิดขึ้นมาแล้วจริง (บั๊กจังหวะ baseline
 * ที่ CreateAppointmentViewModel ถูกแก้ไปไฟล์เดียว) จึงต้องมีตาข่ายรับไว้ก่อนจะไปรวมโค้ดชุดนี้เป็นตัวเดียว
 *
 * ชุดนี้ล้อตาม AddContactViewModelTest ซึ่งเป็นตัวที่คุมกลไกครบที่สุดอยู่แล้ว
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddCustomerDraftTest {

    private val customerRepo: CustomerRepository = mockk(relaxed = true)
    private val authRepo: AuthRepository = mockk(relaxed = true)
    private val draftStore = mockk<com.example.pp68_salestrackingapp.utils.DraftStore>(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { draftStore.load<Any>(any(), any()) } returns null
        every { authRepo.currentUser() } returns AuthUser("U1", "u@test.com", "sale")
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = AddCustomerViewModel(customerRepo, authRepo, draftStore)

    // ถ้าข้อนี้พัง = เปิดหน้าเปล่าแล้วกดย้อนกลับจะโดนถาม "มีข้อมูลที่ยังไม่บันทึก" ทั้งที่ไม่ได้แตะอะไร
    // ซึ่งเป็นบั๊กเดียวกับที่เคยเกิดกับ GPS auto-fill ในหน้าสร้างนัดหมาย
    @Test
    fun `an untouched form is not dirty, and editing a field makes it dirty`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        assertFalse(viewModel.isDirty())

        viewModel.onEvent(AddCustomerEvent.CompanyNameChanged("บริษัท ทดสอบ จำกัด"))
        assertTrue(viewModel.isDirty())
    }

    @Test
    fun `saveDraft writes the current form under the new-customer key`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.onEvent(AddCustomerEvent.CompanyNameChanged("บริษัท ทดสอบ จำกัด"))
        viewModel.onEvent(AddCustomerEvent.AddressChanged("123 ถนนทดสอบ"))
        viewModel.saveDraft()

        val saved = slot<AddCustomerDraft>()
        verify { draftStore.save("add_customer:new", capture(saved)) }
        assertEquals("บริษัท ทดสอบ จำกัด", saved.captured.companyName)
        assertEquals("123 ถนนทดสอบ", saved.captured.address)
    }

    @Test
    fun `discardDraft clears the same key it was saved under`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.discardDraft()

        verify { draftStore.clear("add_customer:new") }
    }

    @Test
    fun `a saved draft is offered and restoring it fills the form back in`() = runTest {
        val stored = AddCustomerDraft(
            companyName = "บริษัท ที่กรอกค้างไว้",
            address = "456 ถนนกู้คืน",
            custType = "Owner"
        )
        every { draftStore.load("add_customer:new", AddCustomerDraft::class.java) } returns stored

        val viewModel = vm()
        advanceUntilIdle()
        viewModel.onEvent(AddCustomerEvent.CheckDraft)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.draftAvailable)

        viewModel.onEvent(AddCustomerEvent.RestoreDraft)
        advanceUntilIdle()

        assertEquals("บริษัท ที่กรอกค้างไว้", viewModel.uiState.value.companyName)
        assertEquals("456 ถนนกู้คืน", viewModel.uiState.value.address)
        assertFalse(viewModel.uiState.value.draftAvailable)
    }

    // ไม่มี draft เก็บไว้ ต้องไม่ขึ้นแถบชวนกู้คืน — เดิมไม่มีอะไรกันไม่ให้แถบโผล่มาเองตอนฟอร์มเปล่า
    @Test
    fun `no stored draft means no prompt`() = runTest {
        val viewModel = vm()
        advanceUntilIdle()
        viewModel.onEvent(AddCustomerEvent.CheckDraft)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.draftAvailable)
    }

    // ปฏิเสธคำชวนแล้วแถบต้องหายไป ไม่ใช่ค้างอยู่จนกว่าจะออกจากหน้า
    @Test
    fun `dismissing the prompt hides it`() = runTest {
        every { draftStore.load("add_customer:new", AddCustomerDraft::class.java) } returns
            AddCustomerDraft(companyName = "ค้างไว้")

        val viewModel = vm()
        advanceUntilIdle()
        viewModel.onEvent(AddCustomerEvent.CheckDraft)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.draftAvailable)

        viewModel.onEvent(AddCustomerEvent.DismissDraftPrompt)

        assertFalse(viewModel.uiState.value.draftAvailable)
    }
}
