package com.example.pp68_salestrackingapp.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteTest {

    @Test
    fun `static routes should match expected paths`() {
        assertEquals("login", Route.Login.path)
        assertEquals("register", Route.Register.path)
        assertEquals("home", Route.Home.path)
        assertEquals("stats", Route.Stats.path)
        assertEquals("monthly_report", Route.MonthlyReport.path)
    }

    @Test
    fun `dynamic customer routes should build correctly`() {
        assertEquals("customer_detail/{customerId}", Route.CustomerDetail.path)
        assertEquals("customer_detail/C-1", Route.CustomerDetail.createRoute("C-1"))
        assertEquals("edit_customer/C-2", Route.EditCustomer.createRoute("C-2"))
    }

    @Test
    fun `dynamic project routes should build correctly`() {
        assertEquals("project_detail/P-1", Route.ProjectDetail.createRoute("P-1"))
        assertEquals("project_inventory/P-2", Route.ProjectInventory.createRoute("P-2"))
        assertEquals("edit_project/P-3", Route.EditProject.createRoute("P-3"))
        assertEquals("add_product/P-4", Route.AddProduct.createRoute("P-4"))
    }

    @Test
    fun `dynamic activity routes should build correctly`() {
        assertEquals("activity_detail/A-1", Route.ActivityDetail.createRoute("A-1"))
        assertEquals("create_activity/P-9", Route.CreateActivityWithProject.createRoute("P-9"))
        assertEquals("edit_activity/A-2", Route.EditActivity.createRoute("A-2"))
        assertEquals("check_in/A-3", Route.CheckIn.createRoute("A-3"))
        assertEquals("sales_result/A-4", Route.SalesResult.createRoute("A-4"))
    }

    @Test
    fun `dynamic contact routes should build correctly`() {
        assertEquals("edit_contact/{contactId}", Route.EditContact.path)
        assertEquals("edit_contact/CT-1", Route.EditContact.createRoute("CT-1"))
    }
}
