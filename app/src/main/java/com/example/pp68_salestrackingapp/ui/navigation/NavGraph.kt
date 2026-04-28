package com.example.pp68_salestrackingapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.pp68_salestrackingapp.ui.screen.activity.*
import com.example.pp68_salestrackingapp.ui.screen.auth.LoginScreen
import com.example.pp68_salestrackingapp.ui.screen.auth.RegisterScreen
import com.example.pp68_salestrackingapp.ui.screen.customer.*
import com.example.pp68_salestrackingapp.ui.screen.dashboard.DashboardScreen
import com.example.pp68_salestrackingapp.ui.screen.export.ExportMenuScreen
import com.example.pp68_salestrackingapp.ui.screen.export.MonthlyReportScreen
import com.example.pp68_salestrackingapp.ui.screen.export.WeeklyReportScreen
import com.example.pp68_salestrackingapp.ui.screen.project.*
import com.example.pp68_salestrackingapp.ui.screen.contact.*

@Composable
fun SalesTrackingApp() {
    val navController = rememberNavController()
    
    // ✅ ติดตาม Route ปัจจุบันเพื่อให้ Bottom Bar อัปเดตถูกต้องเสมอ
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentTab = remember(currentRoute) { getTabIndex(currentRoute) }

    val appViewModel: SalesTrackingAppViewModel = hiltViewModel()
    val isLoggedIn = remember { appViewModel.isLoggedIn() }

    val onLogout = {
        navController.navigate(Route.Login.path) {
            popUpTo(0) { inclusive = true }
        }
    }

    NavHost(
        navController    = navController,
        startDestination = if (isLoggedIn) Route.Home.path else Route.Login.path
    ) {

        // --- Auth ---
        composable(Route.Login.path) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Route.Home.path) {
                        popUpTo(Route.Login.path) { inclusive = true }
                    }
                },
                onRegisterClick = {
                    navController.navigate(Route.Register.path)
                }
            )
        }

        composable(Route.Register.path) {
            RegisterScreen(
                onBack = { navController.popBackStack() },
                onRegisterSuccess = {
                    navController.navigate(Route.Home.path) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // --- Activity / Home ---
        composable(Route.Home.path) {
            HomeScreen(
                onAddClick  = { navController.navigate(Route.CreateActivity.path) },
                onCardClick = { id -> navController.navigate(Route.ActivityDetail.createRoute(id)) },
                onCheckin = { id -> navController.navigate(Route.CheckIn.createRoute(id)) },
                onFinish  = { id -> navController.navigate(Route.ActivityDetail.createRoute(id)) },
                onReport  = { id -> navController.navigate(Route.SalesResult.createRoute(id)) },
                onNotificationClick = { navController.navigate(Route.Notification.path) },
                onSettingsClick     = { navController.navigate(Route.Settings.path) },
                onLogoutClick       = onLogout,
                currentTab          = currentTab,
                onTabChange         = { tab -> navigateToTab(navController, tab) }
            )
        }

        composable(Route.CreateActivity.path) {
            CreateAppointmentScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Route.CreateActivityWithProject.path,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId")
            CreateAppointmentScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Route.EditActivity.path,
            arguments = listOf(navArgument("activityId") { type = NavType.StringType })
        ) { backStackEntry ->
            val activityId = backStackEntry.arguments?.getString("activityId") ?: ""
            CreateAppointmentScreen(
                activityId = activityId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Route.ActivityDetail.path,
            arguments = listOf(navArgument("activityId") { type = NavType.StringType })
        ) { backStackEntry ->
            val activityId = backStackEntry.arguments?.getString("activityId") ?: ""
            ActivityDetailScreen(
                activityId = activityId,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Route.EditActivity.createRoute(id)) },
                onCheckin = { id -> navController.navigate(Route.CheckIn.createRoute(id)) },
                onFinish = { navController.popBackStack() },
                onNotificationClick = { navController.navigate(Route.Notification.path) },
                onSettingsClick = { navController.navigate(Route.Settings.path) },
                onLogoutClick = onLogout
            )
        }

        composable(
            route = Route.CheckIn.path,
            arguments = listOf(navArgument("activityId") { type = NavType.StringType })
        ) { backStackEntry ->
            val activityId = backStackEntry.arguments?.getString("activityId") ?: ""
            CheckInScreen(
                activityId = activityId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Route.SalesResult.path,
            arguments = listOf(navArgument("activityId") { type = NavType.StringType })
        ) {
            SalesResultScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        // --- Customers ---
        composable(Route.CustomerList.path) {
            CustomerListScreen(
                onCustomerClick = { id -> navController.navigate(Route.CustomerDetail.createRoute(id)) },
                onAddClick = { navController.navigate(Route.AddCustomer.path) },
                onNotificationClick = { navController.navigate(Route.Notification.path) },
                onSettingsClick = { navController.navigate(Route.Settings.path) },
                onLogoutClick = onLogout,
                currentTab = currentTab,
                onTabChange = { tab -> navigateToTab(navController, tab) }
            )
        }

        composable(Route.AddCustomer.path) {
            AddCustomerScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Route.EditCustomer.path,
            arguments = listOf(navArgument("customerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
            AddCustomerScreen(
                custId = customerId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Route.CustomerDetail.path,
            arguments = listOf(navArgument("customerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
            CustomerDetailScreen(
                custId = customerId,
                onBack = { navController.popBackStack() },
                onEditCustomer = { id -> navController.navigate(Route.EditCustomer.createRoute(id)) },
                onEditContact = { id -> navController.navigate(Route.EditContact.createRoute(id)) },
                onProjectClick = { id -> navController.navigate(Route.ProjectDetail.createRoute(id)) },
                onNotificationClick = { navController.navigate(Route.Notification.path) },
                onSettingsClick = { navController.navigate(Route.Settings.path) },
                onTabChange = { tab -> navigateToTab(navController, tab) }
            )
        }

        // --- Projects ---
        composable(Route.ProjectList.path) {
            ProjectListScreen(
                onProjectClick = { id -> navController.navigate(Route.ProjectDetail.createRoute(id)) },
                onAddClick = { navController.navigate(Route.AddProject.path) },
                onNotificationClick = { navController.navigate(Route.Notification.path) },
                onSettingsClick = { navController.navigate(Route.Settings.path) },
                onLogoutClick = onLogout,
                currentTab = currentTab,
                onTabChange = { tab -> navigateToTab(navController, tab) }
            )
        }

        composable(Route.AddProject.path) {
            AddProjectScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Route.EditProject.path,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            AddProjectScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Route.ProjectDetail.path,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            ProjectDetailScreen(
                onBack = { navController.popBackStack() },
                onEditProject = { id -> navController.navigate(Route.EditProject.createRoute(id)) },
                onCreateActivity = { id -> navController.navigate(Route.CreateActivityWithProject.createRoute(id)) },
                onSalesResultClick = { navController.navigate(Route.StandaloneSalesResult.createRoute(it)) },
                onInventoryClick = { id -> navController.navigate(Route.ProjectInventory.createRoute(id)) },
                onRecordResult = { pId, activityId, resultId -> 
                    if (activityId != null) {
                        navController.navigate(Route.SalesResult.createRoute(activityId))
                    } else if (pId != null) {
                        // ✅ ปรับให้พาไปหน้า StandaloneSalesResult และส่ง resultId ไปด้วย
                        navController.navigate(Route.StandaloneSalesResult.createRoute(pId, resultId))
                    }
                },
                onActivityClick = { id -> navController.navigate(Route.ActivityDetail.createRoute(id)) },
                onCheckin = { id -> navController.navigate(Route.CheckIn.createRoute(id)) },
                onFinish = { id -> navController.navigate(Route.SalesResult.createRoute(id)) },
                onNotificationClick = { navController.navigate(Route.Notification.path) },
                onSettingsClick = { navController.navigate(Route.Settings.path) },
                onLogoutClick = onLogout
            )
        }

        //สำหรับบบันทึกผลการขาย
        composable(
            route = Route.StandaloneSalesResult.path,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("resultId") { type = NavType.StringType; nullable = true }
            )
        ) {
            SalesResultScreen(
                onBack     = { navController.popBackStack() },
                onSaved    = { navController.popBackStack() }
            )
        }

        // ✅ เพิ่ม Composable สำหรับ ProjectInventory
        composable(
            route = Route.ProjectInventory.path,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            ProjectInventoryScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onAddProduct = { id -> navController.navigate(Route.AddProduct.createRoute(id)) },
                onNotificationClick = { navController.navigate(Route.Notification.path) },
                onSettingsClick = { navController.navigate(Route.Settings.path) },
                onLogoutClick = onLogout,
                currentTab = currentTab,
                onTabChange = { tab -> navigateToTab(navController, tab) }
            )
        }

        // ✅ เพิ่ม Composable สำหรับ AddProduct
        composable(
            route = Route.AddProduct.path,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            AddProductScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        // --- Others ---
        composable(Route.Stats.path) {
            DashboardScreen(
                currentTab = currentTab,
                onTabChange = { tab -> navigateToTab(navController, tab) },
                onNotificationClick = { navController.navigate(Route.Notification.path) },
                onSettingsClick = { navController.navigate(Route.Settings.path) },
                onExportClick = { navController.navigate(Route.ExportMenu.path) },
                onWeeklyClick = { navController.navigate(Route.WeeklyReport.path) },
                onLogoutClick = onLogout
            )
        }

        composable(Route.ExportMenu.path) {
            ExportMenuScreen(
                onBack = { navController.popBackStack() },
                onWeeklyClick = { navController.navigate(Route.WeeklyReport.path) },
                onMonthlyClick = { navController.navigate(Route.MonthlyReport.path) },
                currentTab = currentTab,
                onTabChange = { tab -> navigateToTab(navController, tab) },
                onNotificationClick = { navController.navigate(Route.Notification.path) },
                onSettingsClick = { navController.navigate(Route.Settings.path) },
                onLogoutClick = onLogout
            )
        }

        // --- Contacts ---
        composable(Route.ContactList.path) {
            ContactListScreen(
                onAddClick = { navController.navigate(Route.AddContact.path) },
                onEditClick = { id -> navController.navigate(Route.EditContact.createRoute(id)) },
                onNotificationClick = { navController.navigate(Route.Notification.path) },
                onSettingsClick = { navController.navigate(Route.Settings.path) },
                onLogoutClick = onLogout,
                currentTab = currentTab,
                onTabChange = { tab -> navigateToTab(navController, tab) }
            )
        }
        
        composable(Route.AddContact.path) {
            AddContactScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Route.EditContact.path,
            arguments = listOf(navArgument("contactId") { type = NavType.StringType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId")
            AddContactScreen(
                contactId = contactId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(Route.WeeklyReport.path) {
            WeeklyReportScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.MonthlyReport.path) {
            MonthlyReportScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.Notification.path) {
            NotificationScreen(
                onBackClick = { navController.popBackStack() },
                onReport = { id -> navController.navigate(Route.SalesResult.createRoute(id)) },
                onCheckIn = { id -> navController.navigate(Route.CheckIn.createRoute(id)) },
                onWeeklyReport = { navController.navigate(Route.WeeklyReport.path) },
                onMonthlyReport = { navController.navigate(Route.MonthlyReport.path) },
                onViewDetails = { id -> navController.navigate(Route.ActivityDetail.createRoute(id)) } // ✅ เพิ่ม View Details
            )
        }

        composable(Route.Settings.path) {
            SettingScreen(
                onBack = { navController.popBackStack() },
                onLogout = onLogout
            )
        }
    }
}

private fun getTabIndex(route: String?): Int {
    return when {
        route == null -> 0
        route.contains("home") || route.contains("activity") || route.contains("check_in") || route.contains("sales_result") -> 0
        route.contains("customer") -> 1
        route.contains("contact") -> 2
        route.contains("project") -> 3
        route.contains("stats") || route.contains("export") -> 4
        else -> 0
    }
}

private fun navigateToTab(navController: NavHostController, tabIndex: Int) {
    val route = when (tabIndex) {
        0 -> Route.Home.path
        1 -> Route.CustomerList.path
        2 -> Route.ContactList.path
        3 -> Route.ProjectList.path
        4 -> Route.Stats.path
        else -> Route.Home.path
    }
    
    navController.navigate(route) {
        popUpTo(Route.Home.path) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
