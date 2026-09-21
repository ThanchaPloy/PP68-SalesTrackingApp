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
```

`app/src/androidTest/` exists but is **empty** — there are no instrumented tests, only JVM unit tests under `app/src/test/` (JUnit4 + MockK + Turbine + coroutines-test).

### Backend (`../backend`)

```bash
./gradlew run                 # local server on :8080, reads .env from repo root
./gradlew build               # compile + package
./gradlew compileKotlin       # fastest correctness check
```

Config comes from `application.conf` with every value overridable by env var (`DATABASE_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `PORT`, `UPLOAD_DIR`). `Application.kt` loads a root `.env` into system properties at startup. `.env.remote` points at the deployed Cloud SQL instance. There are **no backend tests**.

## Architecture

### The PostgREST inheritance — read this first

The backend used to be PostgREST. The Ktor rewrite **deliberately imitates PostgREST's wire format** so the Android client did not have to change. This explains almost everything that looks strange in the network layer:

- Filters are query params carrying an operator prefix: `?cust_id=eq.C001`, `?cust_id=in.(C001,C002)`. `RouteUtils.stripEq()` on the server unwraps them.
- Single-object endpoints still return a **JSON array of one element**.
- `Prefer: return=representation` (respond with the written row) and `Prefer: resolution=merge-duplicates` (upsert) are honored.
- `Accept-Profile: public` / `Content-Profile: public` headers are still sent by `AuthInterceptor`, and still ignored.
- `POST /rpc/set_app_context` is a no-op stub that exists only so the client's login flow keeps working.

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

Room is at **version 48** with a continuous migration chain from 28. `AppDatabase` holds 10 entities. Never bump the version without adding the matching `Migration` object and registering it in `DatabaseModule`.

On login the entire local DB is cleared and re-synced, so local-only fields do not survive a re-login.

### Auth

`POST login-api` returns a JWT plus user info; `AuthRepository` tolerates **two response shapes** because the old and new backends differ. The token and the user's `userId`/`branchId`/`role` go into `di/TokenManager` (a SharedPreferences wrapper that also owns the session-expiry `SharedFlow` and a version-bump forced-relogin check).

Server-side, the JWT is HMAC256 with issuer `pp68-backend`, audience `pp68-mobile`, 168-hour expiry, and a required **`user_id` claim** — routes read it to scope queries (e.g. `CustomerRoutes` branches on whether the caller is project sales). Identity is the `Employee` row keyed by `empCode`, which is the **lowercased, trimmed email**. Passwords are BCrypt, with a plaintext-comparison fallback for rows not yet migrated.

### Backend layering

`routes/` (HTTP + PostgREST-compat parsing) → `usecase/` (only for auth, project, appointment) → `repository/*Impl` (Exposed queries via the `dbQuery` helper) → `database/tables/` (Exposed table definitions). `domain/entity/` types are serialized directly as responses; there is almost no DTO layer.

Two files define what actually exists at runtime — check them before assuming a feature is wired:

- `application/plugins/Routing.kt` — the only place routes are registered.
- `application/di/AppModule.kt` — the only place repositories and use cases are constructed.

### Domain rules

- **Check-in**: capture GPS, Haversine distance from the appointment's `planned_lat/long`, flag a mismatch beyond **200 m** (`ActivityDetailViewModel`).
- **Sales result photo**: EXIF GPS must be within **500 m** of the planned location (`SalesResultViewModel`) — deliberately looser than check-in. The upload endpoint also rejects images without camera EXIF.
- **Proximity reminders**: `ProximityMonitorService` notifies within a **500 m** radius.
- **Sales results are versioned, never overwritten.** Each save writes a new `activity_result` row sharing a `result_group_id`, with `version` incremented and `is_latest` moved to the new row. Queries for "current" state must filter `is_latest = 1`; history screens read the whole group.
- **Opportunity score propagates via a DB trigger**, not client code — writing `activity_result` updates the parent `project` server-side.
- **Project status changes are mirrored to Firebase Realtime DB** (`FirebaseRealtimeService`) for an external web dashboard. Failures are logged and swallowed.

## Traps

- **A build with no `local.properties` silently targets production.** `app/build.gradle.kts` falls back to `https://api-ploy.cskmitl.com/` for all three base URLs. There is no `.env.example` for the Android side.
- `AndroidManifest.xml` sets `usesCleartextTraffic="true"` for every build type, not just debug.
- Register is fully implemented (screen, ViewModel, backend route) but **commented out of `NavGraph.kt`**.
- Both repo roots are littered with one-off `patch_*.py` / `fix_*.py` scripts, uiautomator XML dumps, `.sqlite` files and crash logs. None are part of either build. In this repo they are untracked; `test_backup/` is likewise not in any source set.
- `../backend/.claude/worktrees/` holds two stale full copies of the backend source, including files deleted from the real tree. Never edit there.
- `PROJECT_CONTEXT.md` (untracked, in both repos) predates recent refactors and describes removed features — verify anything read from it against the code.

## Known dead code

Inert, harmless, not yet removed — nothing references any of it:

- `Route.ProjectInventory` / `AddProduct` / `EditProduct` in `ui/navigation/Route.kt` — their screens were deleted with the product-catalog feature.
- Nine product/team-member functions at the bottom of `ApiService.kt` (`getProductMaster`, `getProductBrands`, `getProjectTeamMemberCodes`, …) plus their `Product*Dto` types. The backend no longer serves these paths.
- A duplicate `libs.versions.toml` at the repo root; Gradle only reads `gradle/libs.versions.toml`.

## Conventions

JSON and SQL use `snake_case`, Kotlin properties use `camelCase`, bridged per-field with `@SerializedName` (Gson) and `@ColumnInfo` (Room). The two annotations do not always agree — `Project.createdBy` maps to the `user_id` column, for example — so check both before renaming anything.

Package must match directory for every file. Several files previously drifted (a ViewModel under `ui/viewmodels/` declaring `package ui.screen.…`), which made same-package references resolve to the wrong class.
