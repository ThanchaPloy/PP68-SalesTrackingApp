package com.example.pp68_salestrackingapp.ui.navigation

sealed class Route(val path: String) {
    object Login    : Route("login")
    object Register : Route("register")
    object Home     : Route("home")

    object CustomerList : Route("customer_list")
    object CustomerDetail : Route("customer_detail/{customerId}") {
        fun createRoute(customerId: String) = "customer_detail/$customerId"
    }
    object AddCustomer : Route("add_customer")
    object EditCustomer : Route("edit_customer/{customerId}") {
        fun createRoute(customerId: String) = "edit_customer/$customerId"
    }

    object ContactList : Route("contact_list")
    object AddContact  : Route("add_contact")
    object EditContact : Route("edit_contact/{contactId}") {
        fun createRoute(contactId: String) = "edit_contact/$contactId"
    }

    object ProjectList : Route("project_list")
    object ProjectDetail : Route("project_detail/{projectId}") {
        fun createRoute(projectId: String) = "project_detail/$projectId"
    }
    object ProjectInventory : Route("project_inventory/{projectId}") {
        fun createRoute(projectId: String) = "project_inventory/$projectId"
    }
    object AddProject : Route("add_project")
    object EditProject : Route("edit_project/{projectId}") {
        fun createRoute(projectId: String) = "edit_project/$projectId"
    }

    // ── Product ──────────────────────────────────────────────────
    object AddProduct : Route("add_product/{projectId}") {
        fun createRoute(projectId: String) = "add_product/$projectId"
    }
    // ✅ EditProduct รับทั้ง projectId และ productId + optional query params สำหรับ pre-fill
    object EditProduct : Route("edit_product/{projectId}/{productId}?quantity={quantity}&wantedDate={wantedDate}&shippingBranchId={shippingBranchId}") {
        fun createRoute(
            projectId: String,
            productId: String,
            quantity: String? = null,
            wantedDate: String? = null,
            shippingBranchId: String? = null
        ): String {
            var path = "edit_product/$projectId/$productId"
            val params = mutableListOf<String>()
            if (quantity != null) params.add("quantity=$quantity")
            if (wantedDate != null) params.add("wantedDate=$wantedDate")
            if (shippingBranchId != null) params.add("shippingBranchId=$shippingBranchId")
            if (params.isNotEmpty()) {
                path += "?" + params.joinToString("&")
            }
            return path
        }
    }

    object ActivityDetail : Route("activity_detail/{activityId}") {
        fun createRoute(activityId: String) = "activity_detail/$activityId"
    }
    object CreateActivity : Route("create_activity")
    object CreateActivityWithProject : Route("create_activity/{projectId}") {
        fun createRoute(projectId: String) = "create_activity/$projectId"
    }
    object EditActivity : Route("edit_activity/{activityId}") {
        fun createRoute(activityId: String) = "edit_activity/$activityId"
    }
    object CheckIn : Route("check_in/{activityId}") {
        fun createRoute(activityId: String) = "check_in/$activityId"
    }
    object SalesResult : Route("sales_result/{activityId}") {
        fun createRoute(activityId: String) = "sales_result/$activityId"
    }
    object StandaloneSalesResult : Route("standalone_sales_result/{projectId}?resultId={resultId}") {
        fun createRoute(projectId: String, resultId: String? = null) =
            "standalone_sales_result/$projectId" + (resultId?.let { "?resultId=$it" } ?: "")
    }

    object Stats         : Route("stats")
    object ExportMenu    : Route("export_menu")
    object WeeklyReport  : Route("weekly_report")
    object MonthlyReport : Route("monthly_report")
    object Notification  : Route("notification")
    object Settings      : Route("settings")
}
