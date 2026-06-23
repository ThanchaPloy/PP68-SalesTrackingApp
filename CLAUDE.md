# PP68 Sales Tracking App — Project Reference

## Overview

Android CRM/field sales management app for tracking customer interactions, projects, sales activities, and reporting. Built with Jetpack Compose + MVVM + clean architecture.

- **Platform**: Android (minSdk 26 / targetSdk 35)
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM + Repository pattern + Hilt DI
- **Package**: `com.example.pp68_salestrackingapp`

---

## Backend Services

This is a **frontend-only Android app** — there is no backend code in this repository.

| Service | URL | Purpose |
|---|---|---|
| PostgREST | `https://postgrest-279493695905.asia-southeast1.run.app/` | Main REST API (auto-generated from PostgreSQL) |
| pp68-backend | `https://pp68-backend-279493695905.asia-southeast1.run.app/` | Auth (`login-api`, `register-api`, `change-password-api`) |
| upload-visit-photo | `https://upload-visit-photo-279493695905.asia-southeast1.run.app/` | Visit photo upload |
| Firebase Realtime DB | (configured via `google-services.json`) | Project status sync to web dashboard |
| Firebase Messaging | FCM | Push notifications |

**Auth**: JWT Bearer token stored in SharedPreferences via `TokenManager`.  
**PostgREST headers**: `Accept-Profile: public`, `Content-Profile: public` injected by `AuthInterceptor`.

---

## Project Structure

```
app/src/main/java/com/example/pp68_salestrackingapp/
├── data/
│   ├── local/          # Room DAOs + AppDatabase
│   ├── model/          # Data classes (Room entities + API DTOs)
│   ├── remote/         # Retrofit interfaces (ApiService, AuthService)
│   └── repository/     # Repository layer (offline-first)
├── di/                 # Hilt modules (NetworkModule, AppModule, DatabaseModule)
│                       # + TokenManager (SharedPreferences wrapper)
├── service/            # SalesTrackingFirebaseService (FCM handler)
├── ui/
│   ├── components/     # Reusable Compose components
│   ├── navigation/     # Route.kt + NavGraph.kt
│   ├── screen/         # Screen composables (grouped by feature)
│   ├── theme/          # Color, Type, Theme
│   └── viewmodels/     # ViewModels (grouped by feature)
├── utils/              # ExportHelper, ProjectProgressUtils
├── MainActivity.kt
└── SalesTrackingApplication.kt  # @HiltAndroidApp entry point
```

---

## Key Dependencies

| Library | Version | Use |
|---|---|---|
| Jetpack Compose BOM | latest | UI framework |
| Hilt | via KSP | Dependency injection |
| Retrofit 2 | — | API calls |
| OkHttp 3 | — | HTTP client + logging |
| Ktor Client (okhttp engine) | 2.3.7 | Secondary HTTP client in DI |
| Room | 2.6.1 | Local SQLite database |
| Navigation Compose | — | Screen navigation |
| Google Maps Compose | — | Map display + check-in |
| Places API | 3.3.0 | Location search |
| Firebase BOM | — | Realtime DB + Messaging + Analytics |
| Coil | 2.5.0 | Image loading |
| ExifInterface | 1.3.7 | GPS from photo EXIF |
| Mockk + Turbine | test only | Unit testing |

---

## Data Layer

### Room Database — `AppDatabase` (version 33)

9 entities, all in `data/model/`:

| Entity | Table | Primary Key | Notes |
|---|---|---|---|
| `Customer` | `customer` | `cust_id` | Company info, lat/long, status |
| `Project` | `project` | `project_id` | Sales project, status, opportunity score, progress_pct |
| `SalesActivity` | `activity_table` | `appointment_id` | Appointment/visit with check-in GPS |
| `ContactPerson` | `contact_person` | `contact_id` | Contact per customer |
| `Branch` | `branch` | `branch_id` | Branch master |
| `ActivityResult` | `activity_result` | `result_id` | Sales outcome, photo URL, loss reason |
| `ActivityPlanItem` | `activity_plan_item` | (composite) | Checklist items per activity |
| `ProjectContact` | `project_contact` | — | M2M project ↔ contact |
| `AppointmentContact` | `appointment_contact` | — | M2M activity ↔ contact |

**Migrations**: `28→29` (project table restructure), `29→30` (added `updated_at` to project).  
**On new login**: entire local DB is cleared and re-synced from server.

### Repository Pattern

All repositories follow **offline-first**: read from Room, write to Room + API, catch `IOException` gracefully.

| Repository | Key responsibilities |
|---|---|
| `AuthRepository` | Login/register/logout, JWT storage, FCM token update |
| `CustomerRepository` | Customer CRUD, search by branch |
| `ProjectRepository` | Project CRUD, member management, Firebase status sync |
| `ActivityRepository` | Appointment CRUD, check-in, checklist, photo upload |
| `ContactRepository` | Contact person CRUD |
| `ProductRepository` | Product master + project products |
| `BranchRepository` | Branch data |
| `DashBoardRepository` | Statistics aggregation |
| `ExportRepository` | Report data aggregation |
| `CallLogRepository` | Sync device call logs, match to customers |
| `SyncManager` | Orchestrates full sync on login/refresh |

---

## API Service Interfaces

### `ApiService` (PostgREST — Retrofit)

Main data API. All endpoints hit PostgREST directly as REST over PostgreSQL `public` schema.

Key endpoint groups:
- `user` — get by ID, by branch, update FCM token, update profile
- `branch` — get list
- `customer` — full CRUD + by branch/ID
- `contact_person` — full CRUD + by customer
- `project` — full CRUD + member & contact management
- `appointment` — full CRUD + by user/project
- `activity_master` — master activity types
- `activity_result` — insert/upsert/get by activity or user
- `appointment_checklist` — checklist per appointment
- `project_product` — product inventory per project
- `project_sales_member` — team members per project
- `products` — product master with type/group joins
- `call_log` — insert call log records
- `rpc/set_app_context` — set PostgREST JWT context

**Filter syntax**: PostgREST uses `eq.value`, `in.(a,b,c)` format passed as `@Query` parameters.

### `AuthService` (Cloud Functions — Retrofit)

- `POST login-api` → returns JWT token + user info
- `POST register-api` → creates new user
- `POST change-password-api` → password update

### `UploadApiService` (Cloud Functions — Retrofit, Multipart)

- `POST upload-visit-photo` → multipart upload, returns `{ photo_url: "..." }`

---

## DI — Hilt Modules

### `NetworkModule` (`di/NetworkModule.kt`)

Provides:
- `AuthInterceptor` — injects `Authorization: Bearer <token>`, PostgREST profile headers, skips auth for login/register routes, skips `Content-Type` for multipart
- `OkHttpClient` (singleton, 60s timeouts)
- `@PostgRestRetrofit` — Retrofit for PostgREST URL
- `@LoginRetrofit` — Retrofit for Cloud Functions URL
- `ApiService`, `AuthService`, `UploadApiService`
- `HttpClient` (Ktor, reuses OkHttpClient engine)

### `DatabaseModule` (`di/DatabaseModule.kt`)

Provides `AppDatabase` and all DAOs as singletons.

### `TokenManager` (`di/TokenManager.kt`)

SharedPreferences wrapper storing:
- `token` (JWT)
- `userId`, `email`, `role`, `teamId`, `fullName`, `branchId`
- `fcmToken`
- `pushNotificationsEnabled`, `visitReminderEnabled`

---

## UI Layer

### Navigation — `Route.kt` (sealed class)

All routes defined as `sealed class Route(val path: String)`:

| Route | Path | Notes |
|---|---|---|
| `Login` | `login` | — |
| `Register` | `register` | — |
| `Home` | `home` | Activity list |
| `CustomerList/Detail/Add/Edit` | `customer_*` | Customer CRUD |
| `ProjectList/Detail/Add/Edit` | `project_*` | Project CRUD |
| `ProjectInventory` | `project_inventory/{projectId}` | Product list |
| `AddProduct/EditProduct` | `add_product/{projectId}` | Product CRUD |
| `ActivityDetail/CreateActivity/EditActivity` | `activity_*` | Activity CRUD |
| `CheckIn` | `check_in/{activityId}` | GPS check-in |
| `SalesResult` | `sales_result/{activityId}` | Record outcome |
| `StandaloneSalesResult` | `standalone_sales_result/{projectId}` | Outcome without activity |
| `ContactList/AddContact/EditContact` | `contact_*` | Contact CRUD |
| `Stats` | `stats` | Dashboard |
| `ExportMenu/WeeklyReport/MonthlyReport` | `export_*` | Reports |
| `Notification/Settings` | — | — |

### ViewModels

Located under `ui/viewmodels/`, grouped by feature:

- **auth/**: `LoginViewModel`, `RegisterViewModel`
- **activity/**: `HomeViewModel` (activities grouped by month), `ActivityDetailViewModel`, `CreateAppointmentViewModel`, `SalesResultViewModel`, `EditProfileViewModel`, `ChangePasswordViewModel`, `SettingsViewModel`, `NotificationViewModel`
- **customer/**: `CustomerListViewModel` (search + filter), `CustomerDetailViewModel`, `AddCustomerViewModel`
- **project/**: `ProjectListViewModel` (search + status/score filter), `ProjectDetailViewModel`, `AddProjectViewModel`, `ProjectInventoryViewModel`
- **dashboard/**: `DashboardViewModel`, `StatViewModel`
- **contact/**: `ContactListViewModel`, `AddContactViewModel`
- **export/**: `ExportViewModel`
- **notification/**: `NotificationViewModel`

### Screens

Located under `ui/screen/`, grouped by feature:

- `auth/` — LoginScreen, RegisterScreen
- `activity/` — HomeScreen, ActivityDetailScreen, CheckInScreen, SalesResultScreen, CreateAppointmentScreen, NotificationScreen, SettingScreen
- `customer/` — CustomerListScreen, CustomerDetailScreen, AddCustomerScreen
- `project/` — ProjectListScreen, ProjectDetailScreen, ProjectInventoryScreen, AddProductScreen, AddProjectScreen
- `contact/` — ContactListScreen, AddContactScreen
- `export/` — ExportMenuScreen, WeeklyReportScreen, MonthlyReportScreen
- `dashboard/` — DashboardScreen

### Reusable Components (`ui/components/`)

- `AppTopBar.kt` — top header with back/menu
- `BottomNavBar.kt` — 5-tab bottom navigation (Home, Customer, Contact, Project, Stats)
- `FabComponents.kt` — FAB buttons
- `FormComponents.kt` — input fields, buttons, pickers
- `MapComponents.kt` — Google Maps in Compose
- `ProjectProgressBar.kt` — progress visualization
- `CustomerColors.kt` — status color mapping

---

## Key Business Logic

### Login Flow

1. POST `login-api` → receive JWT + userId
2. Save to `TokenManager`
3. Clear local Room DB
4. Fetch user profile (branch, full name) from `user` table
5. `SyncManager.syncAll()` — download all data for user's branch
6. Update FCM token on server
7. Navigate to Home

### Check-In Flow

1. Get current GPS location
2. Calculate distance from `planned_lat/long`
3. Save `check_in_time`, `check_in_lat/long`, `distance_deviation`, `is_location_verified`
4. PATCH appointment on PostgREST

### Sales Result Flow

1. Record outcome fields: `new_status`, `opportunity_score`, `dm_involved`, `is_proposal_sent`, `competitor_count`, `deal_position`, `loss_reason`, etc.
2. Optionally upload visit photo (multipart to Cloud Functions)
3. PATCH/upsert `activity_result` on PostgREST
4. If project status changed → sync to Firebase Realtime DB via `FirebaseRealtimeService`

### Project Status Sync to Firebase

`FirebaseRealtimeService`:
- `updateProjectStatus(projectId, status)` → writes to Firebase path
- `pushStatusChangeEvent(...)` → appends status change event log for web dashboard

---

## Permissions

```
INTERNET
ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION   # GPS check-in
ACCESS_MEDIA_LOCATION                           # EXIF GPS from photos (Android 10+)
POST_NOTIFICATIONS                              # FCM push notifications
READ_CALL_LOG                                   # Call log sync
READ_CONTACTS                                   # Contact matching
READ_MEDIA_IMAGES                               # Photo picker
```

---

## Build Configuration

Keys loaded from `local.properties` or `gradle.properties`:
- `MAPS_API_KEY` — Google Maps (also in `AndroidManifest.xml` meta-data)
- `POSTGREST_URL` — via `BuildConfig.POSTGREST_URL`
- `JWT_SECRET` — via `BuildConfig.JWT_SECRET`
- `GCP_ENDPOINT` — hardcoded: `https://postgrest-279493695905.asia-southeast1.run.app`

**Note**: `google-services.json` must be present in `app/` for Firebase to work.

---

## Testing

- Unit tests: `app/src/test/` — JUnit 4 + Mockk + Turbine + `kotlinx-coroutines-test`
- Instrumented tests: `app/src/androidTest/` — Room integration tests, UI tests
- Coverage: Jacoco via `./gradlew jacocoTestReport` → HTML report in `build/reports/`

---

## Common Patterns

### State management in ViewModels

```kotlin
private val _uiState = MutableStateFlow(SomeUiState())
val uiState: StateFlow<SomeUiState> = _uiState.asStateFlow()
```

### Repository API call pattern

```kotlin
suspend fun doSomething(): Result<T> {
    return try {
        val response = apiService.endpoint(...)
        if (response.isSuccessful) {
            val body = response.body()!!
            dao.upsert(body)
            Result.success(body)
        } else {
            Result.failure(Exception("HTTP ${response.code()}"))
        }
    } catch (e: IOException) {
        Result.failure(e)  // Offline — local DB still valid
    }
}
```

### PostgREST filter syntax

```
// Single value
@Query("project_id") id: String  →  ?project_id=eq.abc123

// Multiple values (in-list)
@Query("cust_id") ids: String    →  ?cust_id=in.(id1,id2,id3)

// Boolean
@Query("is_active") val: String = "eq.true"
```

---

## Data Model Field Naming

The project uses **snake_case** for JSON/DB column names and **camelCase** for Kotlin properties, bridged with `@SerializedName` (Gson) and `@ColumnInfo` (Room):

```kotlin
@ColumnInfo(name = "project_id")
@SerializedName("project_id")
val projectId: String
```

Some models use `@ColumnInfo` with different names than the `@SerializedName` (e.g., `Project.createdBy` uses `@ColumnInfo(name = "user_id")` but `@SerializedName("user_id")`).

---

## Important Notes

- **SalesActivity local-only fields**: `projectName`, `companyName`, `contactName`, `weeklyNote` are stored in Room but **not sent to API** (no `@SerializedName`)
- **PostgREST `Prefer` headers**: `return=representation` makes API return the updated/inserted row; `resolution=merge-duplicates` enables upsert
- **Multipart requests**: `AuthInterceptor` skips Content-Type header injection for multipart bodies to avoid corrupting file uploads
- **Database version**: currently **33** — always write a migration when changing schema; do not bump version without one
- **Firebase project**: `project-fdfd9e00-ddd6-4f28-a13` (new GCP project after migration)