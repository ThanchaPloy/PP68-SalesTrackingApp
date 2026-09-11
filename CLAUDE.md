# PP68 Sales Tracking App — Project Reference

> **อัปเดตล่าสุด**: เอกสารนี้เคยเขียนไว้ว่า "frontend-only ไม่มี backend ในเรพโพนี้" และถือว่า PostgREST คือ API หลัก — ข้อมูลนั้น**ล้าสมัยแล้ว** ตอนนี้มีเรพโพ backend แยก (`C:\Users\pc\StudioProjects\tmp\backend`) เขียนด้วย **Kotlin + Ktor** ซึ่งกำลังทำหน้าที่แทนที่ PostgREST เดิม (ดูหัวข้อ "Backend Services" ด้านล่างที่แก้ไขแล้ว)
>
> สำหรับภาพรวมทั้งระบบ (frontend + backend) แบบละเอียดกว่านี้ รวมถึง schema, business logic ทีละขั้นตอน, known issues — ดูที่ **`PROJECT_CONTEXT.md`** (อยู่โฟลเดอร์เดียวกับไฟล์นี้ และมีสำเนาที่ `backend/PROJECT_CONTEXT.md` ด้วย) เอกสารนี้ (`CLAUDE.md`) เก็บไว้เป็น quick reference เฉพาะฝั่ง Android

## Overview

Android CRM/field sales management app for tracking customer interactions, projects, sales activities, and reporting. Built with Jetpack Compose + MVVM + clean architecture.

- **Platform**: Android (minSdk 26 / targetSdk 35)
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM + Repository pattern + Hilt DI
- **Package**: `com.example.pp68_salestrackingapp`

---

## Backend Services

⚠️ **แก้ไขจากเดิม**: นี่**ไม่ใช่** frontend-only แล้ว — มีเรพโพ backend แยกที่ `C:\Users\pc\StudioProjects\tmp\backend` เขียนด้วย **Kotlin + Ktor** (Netty, Exposed ORM, PostgreSQL/Cloud SQL, Koin DI) กำลังเขียนขึ้นมาแทนที่ PostgREST เดิมทีละส่วน โดยจงใจเลียนแบบ query convention ของ PostgREST (`eq.`, `in.(a,b,c)`, header `Prefer: resolution=merge-duplicates`, endpoint แบบ `/rpc/...`) เพื่อไม่ต้องแก้โค้ดฝั่ง Android มาก

| Service | URL (ที่พบใน config) | หมายเหตุ |
|---|---|---|
| PostgREST (เดิม) | `https://postgrest-279493695905.asia-southeast1.run.app/` | API หลักเดิม — กำลังถูกแทนที่ด้วย Ktor backend |
| pp68-backend (Ktor, ปัจจุบัน) | ดู `SERVER_INFO.md` / `backend/.env.remote` — รันเป็น systemd service `pp68-backend.service` บน VM, DB คือ Cloud SQL instance `practical-project-sales-tracking` (GCP project `algebraic-ratio-490214-r0`) | Auth (`login-api`, `register-api`, `change-password-api`) + กำลังรับหน้าที่ endpoint อื่นๆ ที่เดิมเป็น PostgREST |
| upload-visit-photo | `https://upload-visit-photo-279493695905.asia-southeast1.run.app/` (หรือ endpoint เทียบเท่าใน Ktor backend `POST /upload-visit-photo`) | อัปโหลดรูปเข้าเยี่ยม — ตรวจ EXIF ต้องเป็นรูปถ่ายจากกล้องจริง |
| Firebase Realtime DB | (configured via `google-services.json`) | Sync สถานะโปรเจคไป web dashboard ภายนอก |
| Firebase Messaging | FCM | Push notifications |

**Auth**: JWT Bearer token stored in SharedPreferences via `TokenManager` (`di/TokenManager.kt`).
**PostgREST-compat headers**: `Accept-Profile: public`, `Content-Profile: public` injected by `AuthInterceptor` — ยังคงส่งอยู่แม้ backend จริงจะเป็น Ktor แล้ว เพราะเลียนแบบ convention เดิมไว้

**รายละเอียด API endpoints ทั้งหมดของ backend (Kotlin+Ktor)** — ดูหัวข้อ 5 ใน `PROJECT_CONTEXT.md`

---

## Project Structure

```
app/src/main/java/com/example/pp68_salestrackingapp/
├── data/
│   ├── local/          # Room DAOs + AppDatabase (+ TokenManager สำรองที่ไม่ได้ใช้จริง — ดู Known Issues)
│   ├── model/          # Data classes (Room entities + API DTOs)
│   ├── remote/         # Retrofit interfaces (ApiService, AuthService)
│   └── repository/     # Repository layer (offline-first)
├── di/                 # Hilt modules (NetworkModule, AppModule, DatabaseModule)
│                       # + TokenManager (SharedPreferences wrapper — ตัวจริงที่ใช้งาน)
├── service/            # SalesTrackingFirebaseService (FCM handler)
├── ui/
│   ├── components/     # Reusable Compose components
│   ├── navigation/     # Route.kt + NavGraph.kt
│   ├── screen/         # Screen composables (grouped by feature)
│   ├── theme/          # Color, Type, Theme
│   └── viewmodels/     # ViewModels (grouped by feature)
├── utils/              # ExportHelper, ProjectProgressUtils, SyncManager (upload/outbox sync — คนละตัวกับ data/repository/SyncManager)
├── MainActivity.kt
└── SalesTrackingApplication.kt  # @HiltAndroidApp entry point
```

⚠️ **ที่ root ของเรพโพนี้มีไฟล์ที่ไม่ใช่ส่วนของแอป** (ไม่ต้องอ่าน/แก้): สคริปต์ Python แก้โค้ดแบบ regex (`patch_*.py` 23 ไฟล์), สคริปต์ debug API (`test_*.py`, `fetch_swagger.py`), ไฟล์ `.sqlite`/`sales_tracking_db`, ไฟล์ `ui*.xml` (uiautomator dump), log ไฟล์ต่างๆ, `exportToExcel_old.kt`/`MainActivity.kt` ที่ root (ไฟล์เก่าไม่ใช่ตัวจริง), และ `test_backup/` (test เก่าที่ไม่อยู่ใน Gradle test source set แล้ว)

---

## Key Dependencies

| Library | Version | Use |
|---|---|---|
| Jetpack Compose BOM | latest | UI framework |
| Hilt | via KSP | Dependency injection |
| Retrofit 2 | — | API calls |
| OkHttp 3 | — | HTTP client + logging |
| Ktor Client (okhttp engine) | 2.3.12 | Secondary HTTP client in DI (ดูเหมือนไม่ค่อยได้ใช้จริง — ตรวจสอบก่อนพึ่งพา) |
| Room | 2.6.1 | Local SQLite database |
| Navigation Compose | — | Screen navigation |
| Google Maps Compose | — | Map display + check-in |
| Places API | 3.3.0 | Location search |
| Firebase BOM | — | Realtime DB + Messaging + Analytics |
| Coil | 2.5.0 | Image loading |
| ExifInterface | 1.3.7 | GPS from photo EXIF |
| Apache POI | 5.2.5 | Excel export (weekly/monthly report) |
| WorkManager | 2.9.0 | Background sync (`SyncWorker`) |
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
| `ActivityResult` | `activity_result` | `result_id` | Sales outcome, photo URL, loss reason, version/is_latest |
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
| `ExportRepository` | Report data aggregation (ดูเหมือนถูกแทนที่บางส่วนด้วย logic ใน `ExportViewModel` — ตรวจสอบก่อนใช้) |
| `CallLogRepository` | Sync device call logs, match to customers |
| `data/repository/SyncManager` | **Download sync** — ดึงข้อมูลจาก server เข้า Room ตอนล็อกอิน |
| `utils/SyncManager` | ⚠️ **คนละคลาสกับด้านบน** — Upload/outbox sync ที่ดันข้อมูล local ที่ยังไม่ sync ขึ้น server |

---

## API Service Interfaces

### `ApiService` (ปัจจุบันชี้ไป Ktor backend ที่เลียนแบบ PostgREST convention — Retrofit)

Key endpoint groups: `user`, `branch`, `customer`/`lead_customer`, `contact_person`, `project` (+ member & contact management), `appointment`, `activity_master`, `activity_result`, `appointment_checklist`, `activity_result_photo`, `project_product`, `project_sales_member`, `products`/`product`/`product_brand`, `call_log`, `rpc/set_app_context`, `rpc/get_branch_members`

**Filter syntax**: PostgREST-style `eq.value`, `in.(a,b,c)` format passed as `@Query` parameters — รายละเอียด endpoint ทั้งหมดพร้อม input/output ดูใน `PROJECT_CONTEXT.md` หัวข้อ 5

### `AuthService` (Ktor backend — Retrofit)

- `POST login-api` → returns JWT token + user info
- `POST register-api` → creates new user
- `POST change-password-api` → password update

### `UploadApiService` (Retrofit, Multipart)

- `POST upload-visit-photo` → multipart upload, returns `{ photo_url: "..." }`, server ปฏิเสธรูปที่ไม่มี EXIF ของกล้อง

---

## DI — Hilt Modules

### `NetworkModule` (`di/NetworkModule.kt`)

Provides:
- `AuthInterceptor` — injects `Authorization: Bearer <token>`, PostgREST-compat profile headers, skips auth for login/register routes, skips `Content-Type` for multipart
- `OkHttpClient` (singleton, 60s timeouts)
- `@PostgRestRetrofit` — Retrofit for main data API
- `@LoginRetrofit` — Retrofit for auth endpoints
- `ApiService`, `AuthService`, `UploadApiService`
- `HttpClient` (Ktor, reuses OkHttpClient engine)

### `DatabaseModule` (`di/DatabaseModule.kt`)

Provides `AppDatabase` and all DAOs as singletons.

### `TokenManager` (`di/TokenManager.kt` — ตัวจริงที่ใช้งาน)

SharedPreferences wrapper storing:
- `token` (JWT)
- `userId`, `email`, `role`, `teamId`, `fullName`, `branchId`
- `fcmToken`
- `pushNotificationsEnabled`, `visitReminderEnabled`
- session-expiry `SharedFlow`, version-bump forced re-login logic

⚠️ มีคลาสชื่อ `TokenManager` อีกตัวที่ `data/local/TokenManager.kt` เป็น stub เก่าที่ดูเหมือนไม่ได้ใช้งานจริง (ใช้ SharedPreferences คนละไฟล์ `"auth_prefs"`) — อย่าสับสนกับตัวจริงข้างบน

---

## UI Layer

### Navigation — `Route.kt` (sealed class)

All routes defined as `sealed class Route(val path: String)`:

| Route | Path | Notes |
|---|---|---|
| `Login` | `login` | — |
| `Register` | `register` | สร้างเสร็จแล้วแต่ **ปิดใช้งานอยู่** (comment ออกจาก `NavGraph.kt`) |
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
- **activity/**: `HomeViewModel` (activities grouped by month), `ActivityDetailViewModel`, `CreateAppointmentViewModel`, `SalesResultViewModel`, `ResultHistoryViewModel`, `EditProfileViewModel`, `ChangePasswordViewModel`, `SettingsViewModel`, `NotificationViewModel`
- **customer/**: `CustomerListViewModel` (search + filter), `CustomerDetailViewModel`, `AddCustomerViewModel`
- **project/**: `ProjectListViewModel` (search + status/score filter), `ProjectDetailViewModel`, `AddProjectViewModel`, `ProjectInventoryViewModel`, `AddProductViewModel`
- **dashboard/**: `DashboardViewModel`, `StatViewModel`
- **contact/**: `ContactListViewModel`, `AddContactViewModel`
- **export/**: `ExportViewModel`
- **notification/**: `NotificationViewModel`

### Screens

Located under `ui/screen/`, grouped by feature:

- `auth/` — LoginScreen, RegisterScreen
- `activity/` — HomeScreen, ActivityDetailScreen, CheckInScreen, SalesResultScreen, ResultHistoryScreen, CreateAppointmentScreen, NotificationScreen, SettingScreen
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

(รายละเอียดทีละขั้นตอนแบบเต็ม พร้อมชื่อไฟล์/ฟังก์ชัน — ดู `PROJECT_CONTEXT.md` หัวข้อ 6)

### Login Flow

1. POST `login-api` → receive JWT + userId (response รองรับ 2 รูปแบบเพราะ backend เดิม/ใหม่คืนโครงสร้างต่างกัน)
2. Save to `TokenManager`
3. Clear local Room DB
4. `SyncManager.syncAll()` (`data/repository/SyncManager`) — download all data for user's branch
5. Update FCM token on server
6. Navigate to Home

### Check-In Flow

1. Get current GPS location
2. Calculate distance from `planned_lat/long` (Haversine, threshold 200m)
3. Save `check_in_time`, `check_in_lat/long`, `distance_deviation`, `is_location_verified`
4. PATCH appointment on backend

### Sales Result Flow

1. Record outcome fields: `new_status`, `opportunity_score`, `dm_involved`, `is_proposal_sent`, `competitor_count`, `deal_position`, `loss_reason`, etc. — บันทึกเป็น **version ใหม่ทุกครั้ง** (`result_group_id`/`is_latest`), ไม่ทับของเดิม
2. Optionally upload visit photo (multipart, ตรวจ EXIF กล้อง, threshold Haversine 500m — ต่างจาก check-in ที่ 200m)
3. PATCH/upsert `activity_result` on backend
4. Backend มี **DB trigger** ที่ผลัก `opportunity_score` ไปอัปเดต `project` โดยอัตโนมัติ (ไม่ต้องมีโค้ด client เพิ่ม)
5. ถ้า project status เปลี่ยน → sync ไป Firebase Realtime DB via `FirebaseRealtimeService`

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

⚠️ `AndroidManifest.xml` ยังมี `android:usesCleartextTraffic="true"` เปิดทั้งแอป (ไม่ใช่แค่ debug build)

---

## Build Configuration

Keys loaded from `local.properties` or `gradle.properties`:
- `MAPS_API_KEY` — Google Maps (also in `AndroidManifest.xml` meta-data)
- `POSTGREST_URL` — via `BuildConfig.POSTGREST_URL`
- `JWT_SECRET` — via `BuildConfig.JWT_SECRET`
- `BASE_AUTH_URL`, `UPLOAD_URL` — auth/upload endpoints

⚠️ **ไม่มี `.env.example`** — ถ้าไม่มี `local.properties` เลย `app/build.gradle.kts` จะ fallback ไปใช้ URL production จริง (`api-ploy.cskmitl.com`) ทันที dev ใหม่ที่ build โดยไม่ตั้งค่าอะไรจะยิงตรงเข้า production โดยไม่รู้ตัว

**Note**: `google-services.json` must be present in `app/` for Firebase to work.

---

## Testing

- Unit tests: `app/src/test/` — JUnit 4 + Mockk + Turbine + `kotlinx-coroutines-test`
- Instrumented tests: `app/src/androidTest/` — Room integration tests, UI tests
- Coverage: Jacoco via `./gradlew jacocoTestReport` → HTML report in `build/reports/`
- ⚠️ `test_backup/` ที่ root ของเรพโพ**ไม่ใช่** ส่วนหนึ่งของ Gradle test source set จริง — เป็น test เก่าที่ archive ทิ้งไว้ อย่าเข้าใจผิดว่าเป็น coverage ปัจจุบัน

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

### PostgREST-compat filter syntax (ยังใช้กับ Ktor backend อยู่)

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

## Important Notes / Known Issues (อัปเดตจากการอ่านโค้ดจริง)

- **SalesActivity local-only fields**: `projectName`, `companyName`, `contactName`, `weeklyNote` are stored in Room but **not sent to API** (no `@SerializedName`)
- **PostgREST-compat `Prefer` headers**: `return=representation` makes API return the updated/inserted row; `resolution=merge-duplicates` enables upsert — ยัง apply กับ Ktor backend เพราะเลียนแบบ convention เดิมไว้
- **Multipart requests**: `AuthInterceptor` skips Content-Type header injection for multipart bodies to avoid corrupting file uploads
- **Database version**: currently **33** — always write a migration when changing schema; do not bump version without one
- **Firebase project**: `project-fdfd9e00-ddd6-4f28-a13` (new GCP project after migration)
- **TokenManager ซ้ำสองคลาส** — ใช้ `di/TokenManager.kt` เท่านั้น, `data/local/TokenManager.kt` เป็นของเก่าที่ไม่ได้ใช้งาน
- **SyncManager ซ้ำชื่อคนละความหมาย** — `data/repository/SyncManager` (download) vs `utils/SyncManager` (upload/outbox)
- **Haversine threshold ไม่ตรงกันสองจุด** — check-in ใช้ 200m, ตรวจรูป EXIF ใน SalesResult ใช้ 500m
- **Register ทำเสร็จแต่ปิดใช้งาน** ใน `NavGraph.kt`
- **`CustomerRepository.kt`** มี diagnostic ชั่วคราวที่ยังไม่ได้ revert (เปลี่ยน error-swallowing เป็น error-surfacing ระหว่าง debug การย้ายระบบไป api-ploy)
- ดู known issues ฝั่ง backend (auth/security/dead code) ใน `PROJECT_CONTEXT.md` หัวข้อ 8
