# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Android CRM/field-sales app for tracking customers, projects, appointments and sales outcomes. Jetpack Compose + MVVM + Hilt, offline-first on Room.

The server lives in a **separate repo** at `../backend` (`C:\Users\pc\StudioProjects\tmp\backend`) — Kotlin + Ktor + Exposed + PostgreSQL, DI via Koin. The two are developed together; a change to an endpoint usually needs edits in both.

## Commands

### Android (this repo)

```bash
./gradlew :app:compileDebugKotlin          # fastest correctness check
./gradlew :app:assembleDebug               # build APK
./gradlew :app:testDebugUnitTest           # run all unit tests
./gradlew :app:testDebugUnitTest --tests "*ProjectListViewModelTest"        # single class
./gradlew :app:testDebugUnitTest --tests "*ProjectListViewModelTest.filters*"  # single method
./gradlew jacocoTestReport                 # coverage -> app/build/reports/jacoco/
./gradlew :app:lintDebug                   # lint (abortOnError is off)
./gradlew :app:connectedDebugAndroidTest   # instrumented tests, incl. every Room migration
```

Release builds are signed from `app/PP68.jks` with credentials read from the environment — nothing is stored in the repo:

```bash
KEYSTORE_PATH="$PWD/app/PP68.jks" STORE_PASSWORD=… KEY_ALIAS=key0 KEY_PASSWORD=… \
  ./gradlew :app:assembleRelease           # -> app/build/outputs/apk/release/
```

`app/src/androidTest/` contains device/instrumented checks (including PDF output and Phase 2 database baselines). JVM unit tests remain under `app/src/test/` (JUnit4 + MockK + Turbine + coroutines-test).

### Backend (`../backend`)

```bash
./gradlew run                 # local server on :8080, reads .env from repo root
./gradlew build               # compile + package
./gradlew compileKotlin       # fastest correctness check
```

Config comes from `application.conf` with every value overridable by env var (`DATABASE_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `PORT`, `UPLOAD_DIR`, `GEOAPIFY_API_KEY`, `APPOINTMENT_POLICY_V2`). `Application.kt` loads a root `.env` into system properties at startup.

`./gradlew test` runs a JVM-only suite (no database, no HTTP): pure policy objects, query-param parsing, and a few guards that read source and migration files to catch wiring that silently stops working — for example that every `migrations/*.sql` the code depends on is registered in `DatabaseFactory`, and that every password endpoint sits inside the rate-limit block.

`DatabaseFactory` builds a plain HikariCP pool over `org.postgresql.Driver` from `DATABASE_URL`. The Cloud SQL socket-factory dependency in `build.gradle.kts` is **not wired up** — both `.env` and `.env.remote` currently point at the same self-hosted PostgreSQL on the LAN, not at a managed instance.

## Architecture

### The PostgREST inheritance — read this first

The backend used to be PostgREST. The Ktor rewrite **deliberately imitates PostgREST's wire format** so the Android client did not have to change. This explains almost everything that looks strange in the network layer:

- Filters are query params carrying an operator prefix: `?cust_id=eq.C001`, `?cust_id=in.(C001,C002)`. `RouteUtils.stripEq()` on the server unwraps them.
- Single-object endpoints still return a **JSON array of one element**.
- `Prefer: return=representation` (respond with the written row) and `Prefer: resolution=merge-duplicates` (upsert) are honored.
- `Accept-Profile: public` / `Content-Profile: public` headers are still sent by `AuthInterceptor`, and still ignored.
- `POST /rpc/set_app_context` is a no-op stub that exists only so the client's login flow keeps working.

Newer endpoints deliberately **break** this convention and are plain REST: `/sync/v2/cursor|changes|snapshot/{entity}`, `/customer/search`, `/customer/lookup`, `/account/complete-initial-setup`, `/places/autocomplete|reverse`. Do not add PostgREST-style operator prefixes to those.

ERP customers are remote-only: `GET /customer` answers **410 Gone** on purpose and only app-created leads (`/lead_customer`) reach Room. A 404 from `/customer/search` or a 405 from `GET /lead_customer` means the deployed backend predates this, not that the client is wrong.

Do not "clean this up" on one side alone — the convention is the contract between the two repos.

### Network layer (`di/NetworkModule.kt`)

Three Retrofit-backed services over one shared `OkHttpClient`:

| Service | Base URL | Qualifier |
|---|---|---|
| `ApiService` | `BuildConfig.POSTGREST_URL` | `@PostgRestRetrofit` |
| `AuthService` | `BuildConfig.BASE_AUTH_URL` | `@LoginRetrofit` |
| `UploadApiService` | `BuildConfig.UPLOAD_URL` | — |

Two non-obvious behaviors live in this file:

- **Gson is configured with an exclusion strategy that drops every field without `@SerializedName`.** This is how local-only Room columns (`isSynced`, `projectName`, `companyName`, `locationName`, …) are kept out of request bodies. Adding `@SerializedName` to a model field silently starts sending it to the server.
- **`AuthInterceptor` treats any 401 outside `login-api`/`register-api` as an expired session**, clears the token and emits on `TokenManager.sessionExpired`, which `NavGraph` collects to bounce the user to Login. It also skips `Content-Type` injection for multipart bodies so photo uploads are not corrupted.

### Data layer

Repositories are offline-first: read from Room, write to Room and the API, and swallow `IOException` so the local DB stays authoritative when offline.

**There are two unrelated classes named `SyncManager`** — the most common source of confusion in this codebase:

- `data/repository/SyncManager` — **download**. Pulls the user's branch data into Room after login.
- `utils/SyncManager` — **upload/outbox**. Pushes locally-modified rows to the server; also driven by `worker/SyncWorker` (WorkManager). `AuthRepository` imports it aliased as `OutboxSyncManager` to keep the two apart.

The outbox is event-driven: every save enqueues a unique one-time `SyncWorker` constrained to `NetworkType.CONNECTED`, so it runs when connectivity returns **without the app being reopened**. `schedulePeriodicSync()` adds a 6-hour safety net for work that falls out of the queue. Neither survives the user or an OEM battery manager force-stopping the app — Android will not wake a force-stopped app by any means, and nothing in the app can change that.

Delta sync v2 (`data/repository/DeltaSyncRepository` ↔ backend `/sync/v2/*`) downloads an initial keyset snapshot into a shadow table, swaps it in atomically, then replays an ordered change feed by cursor. Server-side the feed is maintained by database triggers installed by `migrations/add_sync_change_feed.sql`, inside the same transaction as the business write.

**A successful response carrying an empty list is not a failure.** `refreshActivities` and `refreshResults` fetch children (appointment contacts, result photos) in batches of 50; an appointment with no participants and a result with no photos are both normal. Treating the empty list as an error once made the whole refresh fail and the stats screen show nothing.

**The download banner and the upload outbox are separate workers.** `_failedParts` in `data/repository/SyncManager` is set when the download worker fails and cleared only when that same worker succeeds. Connectivity returning wakes only the upload worker, so `HomeViewModel` re-schedules a download after an upload succeeds — otherwise the banner sticks forever while data syncs fine behind it.

`utils/SyncDiagnostics` keeps the last 100 events in SharedPreferences and exports them as JSON from the settings screen. Besides the sync workers it now records every non-2xx response (hooked once in `AuthInterceptor`, so no ViewModel has to care) and uncaught exceptions. It stores metadata only — method, path without query string, status code, exception class — because the export leaves the device. **Fields not listed in `ALLOWED_FIELDS` are dropped silently**, so adding a field to a `record()` call means adding it there too.

Room is at **version 64** with a continuous migration chain from 28. `AppDatabase` holds 18 entities. Never bump the version without adding the matching `Migration` object, registering it in `DatabaseModule`, and testing it against the exported Room schema. Each recent migration has a matching `MigrationNNToMMTest` under `app/src/androidTest/`; follow that pattern — the schema JSON alone does not prove the hand-written SQL runs.

On login the entire local DB is cleared and re-synced, so local-only fields do not survive a re-login.

### Auth

`POST login-api` returns a JWT plus user info; `AuthRepository` tolerates **two response shapes** because the old and new backends differ. The token and the user's `userId`/`branchId`/`role` go into `di/TokenManager` (a SharedPreferences wrapper that also owns the session-expiry `SharedFlow` and a version-bump forced-relogin check).

Server-side, the JWT is HMAC256 with issuer `pp68-backend`, audience `pp68-mobile`, 720-hour (30-day) expiry, `jwt.expireHours` in `application.conf`, and a required **`user_id` claim** — routes read it to scope queries (e.g. `GET /lead_customer` returns only the rows the caller owns). Identity is the `Employee` row keyed by `empCode`, which is the **lowercased, trimmed email**. Passwords are BCrypt, with a plaintext-comparison fallback for rows not yet migrated.

**Two JWT realms, not one.** `Security.kt` installs `jwt-auth` for business routes and `jwt-setup` for the forced-setup route only. A token carries `credential_version` and `setup_only`:

- `jwt-auth` rejects a token whose `credential_version` no longer matches the employee row, and rejects any `setup_only` token. Completing setup bumps the version, so every token issued before it stops working.
- A token with neither claim (issued before this existed) is still accepted as version 1 — old clients keep working.
- `POST /account/complete-initial-setup` runs under `jwt-setup`, which only accepts a `setup_only` token whose owner still has `password_change_required`.

All password-accepting endpoints sit inside one `rateLimit(AUTH_RATE_LIMIT)` block, 30/min keyed by the caller's IP read from `CF-Connecting-IP`/`X-Forwarded-For`. Keyed by source, not by `emp_code`, because per-account throttling lets anyone lock a colleague out, and the actual threat is guessing employee codes against a shared default password.

**Forced initial setup is off until switched on.** The migration only adds columns (`password_change_required` defaults to `FALSE`); enabling accounts is a separate hand-run `migrations/oneoff_enable_required_account_setup.sql`. Never run it before the Android build with the setup gate is out — older clients can log in but have no screen to complete setup, which locks every user out.

### Backend layering

`routes/` (HTTP + PostgREST-compat parsing) → `usecase/` (only for auth, project, appointment) → `repository/*Impl` (Exposed queries via the `dbQuery` helper) → `database/tables/` (Exposed table definitions). `domain/entity/` types are serialized directly as responses; there is almost no DTO layer.

Two files define what actually exists at runtime — check them before assuming a feature is wired:

- `application/plugins/Routing.kt` — the only place routes are registered.
- `application/di/AppModule.kt` — the only place repositories and use cases are constructed.

### Domain rules

- **Appointment edit/delete/result windows live in one policy object per repo** — `utils/AppointmentPolicy.kt` here and `domain/policy/AppointmentPolicy.kt` in the backend. Both are pure, take a `Clock`, pin `Asia/Bangkok`, and return the same reason codes (`APPOINTMENT_EDIT_WINDOW_CLOSED`, `DELETE_WINDOW_CLOSED`, `ONSITE_CHECKIN_REQUIRED`, `MISSED_ONSITE_RESULT_NOT_ALLOWED`, `APPOINTMENT_TIME_MISSING`, `APPOINTMENT_STATUS_LOCKED`). Their two test files encode the same table; change one side and the other must follow. The rules: plan edits close at the start minute, deletes close at midnight of the appointment day, and an onsite appointment with no check-in cannot be written up after its day. This **replaced** the old "locked within 7 days of the appointment" rule, which was the opposite shape.
  - Server-side enforcement is behind `APPOINTMENT_POLICY_V2`, **off by default**. While off it only logs what it would have rejected.
  - The server decides "is this a plan edit?" from which fields the PATCH touches (`AppointmentPolicy.PLAN_FIELDS`), never a client flag. Check-in, status changes and the result's project binding share `PATCH /appointment` and must keep working after the start time.
  - The check runs inside the write transaction on a `SELECT … FOR UPDATE` row, so a concurrent reschedule cannot slip between read and write.
  - A client may send `client_modified_at` + `client_time_trusted` for work done offline; the policy then re-evaluates at that moment. Android derives that timestamp from `utils/ServerTimeAnchor`, which anchors the server's HTTP `Date` header to `SystemClock.elapsedRealtime()` and reports "untrusted" after a reboot or a clock change rather than guessing.
- **"วันที่ต้องการสินค้า" is a month, not a day.** It reuses `project.desired_completion_date` (`Project.desiredCompletionDate`), stored as the 1st of the chosen month; `utils/DesiredMonth` owns formatting and the rule. `AddProjectViewModel` requires it when a project *enters* `Quotation`, not when a project that was already at `Quotation` is edited (old rows have no month; `ProjectDetailScreen` shows a banner instead). Server-side enforcement is deliberately not built yet — turn it on only after this build is out, or older clients cannot save Quotation at all. Both PATCH writers (`ProjectRepository.updateProject`, outbox `SyncManager`) and the backend `update()` must carry the field; it was missing from all three before.
- **Place search resolves coordinates on-device before ever calling the backend/Geoapify**, in a fixed order inside `PlaceSearchViewModel.onQueryChanged`: raw `lat, lon` pasted from Google Maps (`utils/CoordinateParser`), then a Plus Code / Open Location Code like `MC4H+F8 Bangkok` (`utils/PlusCodeParser`), then finally a real Geoapify text search. Both parsers return instantly, cost no API credit, and work offline. Plus Codes are **not geocoding data** — no provider's database "contains" one; it's a grid coordinate the Plus Code spec encodes/decodes with pure math, so it is decoded locally with Google's own reference library, `com.google.openlocationcode:openlocationcode` ([github.com/google/open-location-code](https://github.com/google/open-location-code), Apache-2.0 — the spec's source of truth, maintained by the team that defines Plus Codes, with its own `test_data/*.csv` vectors to verify a decode against). A *short* code (e.g. the `MC4H+F8` form, missing its area prefix) needs a reference point within roughly the same 1° to `recover()` correctly; `PlaceSearchViewModel` passes the device's last-known location when one is cached, falling back to a Bangkok-center constant otherwise, since every customer this app serves is in Thailand anyway.
  - Confirmed by direct testing against `nominatim.openstreetmap.org/search` (2026-11-06): it returns `[]` for both a Plus Code and an SME business name ("Tangnam Glass") at a real address on Rama II Rd — same root cause as Geoapify missing it (both sit on the OpenStreetMap POI dataset), so switching the *search* provider to another OSM-backed one (Nominatim, Photon) would not fix SME-name search; only a provider with its own business-POI database (e.g. Google Places) would, which is why `google-maps-migration-plan.md` exists as a non-OSS fallback option.
- **Check-in**: capture GPS, Haversine distance from the appointment's `planned_lat/long`, flag a mismatch beyond **200 m** (`ActivityDetailViewModel`).
- **Sales result photo**: EXIF GPS must be within **500 m** of the planned location (`SalesResultViewModel`) — deliberately looser than check-in. The upload endpoint also rejects images without camera EXIF.
- **Proximity reminders**: `ProximityMonitorService` notifies within a **500 m** radius.
- **Sales results are versioned, never overwritten.** Each save writes a new `activity_result` row sharing a `result_group_id`, with `version` incremented and `is_latest` moved to the new row. Queries for "current" state must filter `is_latest = 1`; history screens read the whole group.
- **Opportunity score propagates via a DB trigger**, not client code — writing `activity_result` updates the parent `project` server-side.
- **An appointment carries its customer's name from the server.** ERP customers are remote-only, so there is no local `customer` row to join for the company name. The backend attaches `customer_name` to every appointment it returns (resolved from `customer` and `lead_customer` in two queries per result set, not per row), and `SalesActivity.companyName` is the one otherwise-local column with a `@SerializedName` so it can read that. It is still never sent back: every appointment write builds its JSON map explicitly. The form also stamps the chosen company and project names onto the row at save time, so a brand-new appointment shows them before the next sync and while offline.
- **Everything is scoped to the row's owner, and that rule lives in eight places that must agree.** Projects, appointments, results, contacts and app-created leads are each visible only to the person who created them — not the branch, not the project-sales team. The server's `resolveScope` is still *branch*-based, so narrowing happens per entity, not centrally:
  - backend `GET /lead_customer` (`findLeadsOwnedBy`), `PATCH /lead_customer` (owner compare, not `scope.allows`), `SyncChangeRepositoryImpl.findChanges` (`owner_code = ?`) and `.findSnapshot` (`COALESCE(salesperson_code, create_by) = ?`)
  - client `CustomerDao.getAllLeads/getAllCustomers/getCustomersPaging`, `ProjectDao.getProjectsOwnedBy/getProjectsPaging`, `ActivityDao.getActivitiesOwnedBy`, `ActivityResultDao.getResultsOwnedBy` — all fed from `TokenManager` inside the repository, never passed in by each ViewModel
  - `LeadScopeContractTest` (backend) fails if any of the server three widens again
  Change one side only and the legacy refresh and delta sync hand Room different row sets, which then overwrite each other — `replaceLeadSnapshot` deletes what delta sync just inserted, forever.
- **"Resolving an id to a name" is not a visibility boundary.** `CustomerDao.getAllForNameLookup()` and `ProjectRepository.getAllProjectsForNameLookup()` deliberately do *not* filter by owner, so an appointment that references a teammate's customer or project still shows its name instead of a blank. Everything that renders a *list* or fills a dropdown uses the filtered query. Keep the two apart when adding a screen.
- **The owner column is named differently in every table, and never matches the Kotlin property.** `customer.user_id` (`Customer.createdBy`, JSON `salesperson_code`), `project.create_by` (`Project.createBy` — while `project.user_id` is `requestBy`, a different person), `activity_table.user_id` (`SalesActivity.userId`, JSON `emp_code`), `activity_result.created_by`. Read both annotations before writing a filter.
- **The sales branch is not a required field when creating a project.** The branch list only ever comes from the server, so offline the dropdown is empty; requiring it blocked saving entirely. On save the branch falls back to the caller's own branch from the token.
- **Two different draft mechanisms, on purpose.** Appointment drafts are many-per-account rows in Room (`appointment_draft`, `AppointmentDraftRepository`), capped at 20, expiring after 30 days, every query scoped by a SHA-256 `owner_key` — the DAO deliberately has no "all accounts" method, because phones are shared between sales staff. The other four forms (customer, project, contact, sales result) still use the single-slot SharedPreferences `utils/DraftStore`. `DraftController` is shared by all five but the appointment form now uses only its baseline/dirty tracking, not its storage.
- **Appointment reminders are local only** — `AppointmentAlarmScheduler` sets `AlarmManager` alarms at 30/15/0 minutes before the planned time when the appointment is created, and `AppointmentAlarmReceiver` checks `TokenManager.isVisitReminderEnabled()` at fire time. There is no server-initiated push: Firebase (Cloud Messaging, Realtime DB, Analytics) was removed from both repos, along with the status mirror that fed an external web dashboard. A dashboard that needs status history should read `project_stage_log`, which holds strictly more than the mirror ever did. `employee.fcm_token` is still in the database but nothing reads or writes it.

## Traps

- **A build with no `local.properties` silently targets production.** `app/build.gradle.kts` falls back to `https://api-ploy.cskmitl.com/` for all three base URLs. There is no `.env.example` for the Android side.
- `AndroidManifest.xml` sets `usesCleartextTraffic="true"` for every build type, not just debug.
- Register is fully implemented (screen, ViewModel, backend route) but **commented out of `NavGraph.kt`**.
- Both repo roots are littered with one-off `patch_*.py` / `fix_*.py` scripts, uiautomator XML dumps, `.sqlite` files and crash logs. None are part of either build. In this repo they are untracked; `test_backup/` is likewise not in any source set.
- `../backend/.claude/worktrees/` holds two stale full copies of the backend source, including files deleted from the real tree. Never edit there.
- `PROJECT_CONTEXT.md` (untracked, in both repos) and `TECHNICAL_DOCUMENTATION.txt` predate recent refactors and describe removed features — verify anything read from them against the code. `CLAUDE.md` and `SERVER_INFO.md` are the two kept current.
- **Do not bump `versionCode` casually.** `TokenManager.checkAppVersionAndForceRelogin()` compares it to the stored value and calls `clearToken()` when they differ, which routes the next login through `clearAllTables()` — offline work that never reached the server is gone, with no warning. Shipping a new build is not by itself a reason to bump it.
- **Migrations are a hand-maintained list.** `DatabaseFactory` runs only the `migrations/*.sql` paths written in its `scripts` list, resolved relative to the process working directory. A file that exists but is not listed never runs and logs nothing; a file that is listed but missing logs a warning and is skipped. If the systemd unit's `WorkingDirectory` is not the repo root, every migration silently skips.
- **Source files mix LF and CRLF line endings,** sometimes within one file. Multi-line search-and-replace that assumes `\n` will match nothing in half the file.
- **The client asks for batches as `in.(a,b,c)`, and `stripEq()` does not understand it.** A route that only strips `eq.` will take the whole string as one id and answer 404 while the data is sitting right there. Unpack with `parseInList()` (`routes/RouteUtils.kt`). Seven other routes still parse it inline, each slightly differently.
- **Latches that are easy to set and hard to clear — the client's most expensive bug family.** Four of them shipped, and every one looked identical from outside: *the server accepts the request now, but the app will not even try*. The server log is the fastest way to tell them apart — **no request at all** means it is one of these, not a server bug. All four are fixed; the shape is what to watch for when adding the fifth.
  - `scheduleSync`/`scheduleDownload` used `ExistingWorkPolicy.KEEP` for every trigger, so a one-time worker parked in WorkManager's exponential backoff (cap **5 hours**) swallowed every later request — opening the app and tapping "ลองใหม่" did nothing. `MANUAL` and `APP_FOREGROUND` now use `REPLACE`; `SAVE`/`LOGIN`/`PERIODIC` keep `KEEP` so frequent offline edits cannot reset the retry clock.
  - `_failedParts` (the home-screen "ดาวน์โหลดข้อมูลไม่ครบ" banner) is set by the download worker and cleared only by that same worker succeeding. The upload outbox is a different worker, so the banner can stick while uploads flow fine.
  - `retriedThisSession` gives a row in `sync_rejection` exactly one more attempt per **process lifetime** (it is an in-memory set on a `@Singleton`). Once spent, only a force-stop freed it. `scheduleSync(MANUAL)` now clears it, so the retry button means something.
  - `sync_rejection` rows were cleared only by successfully pushing that exact `(entityType, id)` while it was still unsynced. A row that synced by another path, got deleted, or had its `TEMP-` id remapped left an orphan, and "เซิร์ฟเวอร์ปฏิเสธ N รายการ" counted it forever. `pruneStaleRejections()` now reconciles against the real pending queue at the end of every sync run.
- **Replaying a row is not the same as editing it, and the server has to know the difference.** The outbox re-sends whole rows, so a catch-up `PATCH /appointment` always carries every plan field. `AppointmentPolicy.touchesPlan` read that as a plan edit and returned 422 once the appointment was `completed` — permanently stranding the row. `changesPlan(appointment, updates)` now compares values (time formats, numbers as strings, booleans as strings all normalised) and treats *unreadable* as changed, so normalisation slips fail closed. Same class of bug: `AppointmentUseCase.saveResult` ran the policy *before* `idempotentCreate`, so a replay of a create that had already succeeded was judged against the state it had itself caused.
- **Postgres `text` cannot hold a NUL byte, so `chr(0)` throws.** The `appointment_contact` sync-feed trigger joined its composite key with `chr(0)`, so every insert/update/delete on that table raised `null character not permitted` and `POST /appointment_contact` answered 500 — appointments *with participants* never got marked synced and retried forever, while appointments without participants synced fine. The separator must stay byte `0x00` because the client's `encodeRelationId` uses it, so concatenate in `bytea`: `convert_to(a,'UTF8') || '\x00'::bytea || convert_to(b,'UTF8')`.
- **The same timestamp reaches the app in three different string shapes, and `Instant.parse` only reads one of them.** `2026-11-06T23:22:20.244199Z` from a REST route (Exposed `timestamp()` → `Instant.toString()`), `2026-11-07T06:18:16.371395+07:00` from delta sync (the feed trigger builds its payload with `to_jsonb(NEW)`, which renders `timestamptz` in the DB session's timezone), and `2026-11-07T05:50:54.990561` with no zone at all from anything reading `v_unified_customer`, whose SQL casts `updated_at::TIMESTAMP` and throws the zone away. The app sets `minSdk = 26` with **no core library desugaring**, so `java.time` comes from the platform, and on older Android `Instant.parse` accepts only the `Z` form — offset tolerance arrived in JDK 12. Every caller wrapped the parse in a `catch` that swallowed the failure, so `isCheckInLate` silently returned `false` and the "ช้ากว่าเวลานัด" badge never appeared for rows that came down through sync. Read server timestamps only through `utils/IsoTime.parseServerInstant`, which tries all three and reads a zone-less value as **Asia/Bangkok** — the server's own zone, not the device's, because a value with no offset was written by the server JVM's default zone and a salesperson abroad would otherwise see it shift.
- **`ESCAPE '\\'` inside a Kotlin raw string sends two backslashes.** Postgres rejects the whole query — "escape string must be empty or one character" — so the endpoint answers 500 every time, not just for odd input. Raw strings do not process escapes; write `ESCAPE '\'`.
- **User-facing wording for ERP customers is "ลูกค้าเก่า(dynamic)", not "ERP".** The code still uses ERP in identifiers and comments; only the strings people read were changed.

## Known dead code

Inert, harmless, not yet removed — nothing references any of it:

- `Route.ProjectInventory` / `AddProduct` / `EditProduct` in `ui/navigation/Route.kt` — their screens were deleted with the product-catalog feature.
- Nine product/team-member functions at the bottom of `ApiService.kt` (`getProductMaster`, `getProductBrands`, `getProjectTeamMemberCodes`, …) plus their `Product*Dto` types. The backend no longer serves these paths.
- A duplicate `libs.versions.toml` at the repo root; Gradle only reads `gradle/libs.versions.toml`.
- `ApiService.addActivity(@Body SalesActivity, …)` — the live path is `addActivityMap`, which builds the JSON map explicitly. This matters beyond tidiness: it is why putting a `@SerializedName` on a local-only column cannot leak that column to the server.

## Conventions

JSON and SQL use `snake_case`, Kotlin properties use `camelCase`, bridged per-field with `@SerializedName` (Gson) and `@ColumnInfo` (Room). The two annotations do not always agree, and neither always matches the property name: on `Project`, `createBy` is the `create_by` column while `requestBy` is the `user_id` column. Check both annotations before renaming anything, and never guess the owner column from the property name.

Package must match directory for every file. Several files previously drifted (a ViewModel under `ui/viewmodels/` declaring `package ui.screen.…`), which made same-package references resolve to the wrong class.
