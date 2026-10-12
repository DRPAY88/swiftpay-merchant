# Copilot instructions for swiftpay-merchant

## Project overview

This repository contains a multi-part payment stack:

- `app/` is the Android client. It is a Kotlin + Jetpack Compose app with Material 3, DataStore-backed settings/session state, and a repository/use-case architecture layered over Retrofit clients.
- `backend/` is the primary production API. It is a Node.js/Express service that handles OTP authentication, JWT validation, SwiftPay payment order creation, payment status polling, and disbursement flows.
- `server/` is a lightweight approval demo service and is separate from the main payment backend.

Treat the backend as the trusted boundary for merchant credentials, session signing, and production API secrets. The Android app should never hold production secrets in source or Gradle config.

## Build, test, and lint commands

### Android app (`app/`)

Run from the repo root:

- Debug build: `./gradlew :app:assembleDebug`
- Unit tests: `./gradlew :app:testDebugUnitTest`
- Lint: `./gradlew :app:lintDebug`
- Release APK: `./gradlew :app:assembleRelease`

Release builds require Android SDK 36 / JDK 21 and signing properties such as `PAYMENT_BACKEND_URL`, `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`. These are expected in `local.properties`, Gradle properties, or CI secrets.

### Main backend (`backend/`)

Run from `backend/`:

- Install dependencies: `npm install`
- Copy env template if needed: `cp .env.example .env`
- Run tests: `npm test`
- Start service: `npm start`

The backend has Node tests via the built-in test runner (`node --test`); there is no dedicated lint script in `backend/package.json`.

### Approval demo server (`server/`)

Run from `server/`:

- Install dependencies: `npm install`
- Start: `node index.js`

The demo server is not the main production backend and is mainly used for local approval-flow testing.

## High-level architecture

### Android app architecture

The app follows a Compose-first UI with a navigation shell in `MainActivity.kt` and screen-level ViewModels such as `MiniAppViewModel`.

Important layers and patterns:

- `MainActivity.kt` owns the root navigation state and app-wide login/session awareness.
- `SettingsManager` and `SessionManager` store persisted session and preference state using DataStore.
- `DIContainer.kt` centralizes service/repository/use-case creation.
- `data/repository/*` and `domain/usecase/*` separate the Retrofit API clients from screen logic.
- `SwiftPayBackendClient.kt` and `BackendAuthService.kt` are the app’s HTTP clients; they rely on `BuildConfig.PAYMENT_BACKEND_URL` instead of hardcoded backend URLs.
- The app integrates deep links and a lightweight local transaction store for payment state and wallet/transaction flows.

When changing behavior, remember that a screen change often involves several layers: UI screen -> ViewModel -> repository/usecase -> Retrofit API client -> backend endpoint.

### Backend architecture

The backend is the trust boundary for both authentication and payment processing.

Key responsibilities:

- OTP-based sign-in flow with allowlisted email checks and JWT issuance.
- Verified JWT enforcement on `/v1` routes.
- SwiftPay integration for creating signed payment orders, checking status, and generating hosted payment links.
- Disbursement handling for InstaPay/PESONet via the SwiftPay provider and audit trail logic.
- Admin dashboard support for merchant activation, suspension, and transfers, backed by PostgreSQL when configured.

The backend depends on environment variables such as `SWIFTPAY_*`, `AUTH_ALLOWED_EMAILS`, `SUPER_ADMIN_EMAILS`, `RESEND_API_KEY`, `DATABASE_URL`, and the session-signing secret. Do not invent or hardcode these values in app code or config files.

## Key conventions and repo-specific patterns

- Prefer `BuildConfig.PAYMENT_BACKEND_URL` and environment/local Gradle properties for backend URLs; do not hardcode hostnames in Android code.
- Keep all live secrets and signing keys in backend env/secret-manager configuration, not in `app/` or checked-in files.
- The Android app stores session/auth state via DataStore, not just in memory. If a feature affects login persistence or logout, check `SettingsManager` and `SessionManager` together.
- The backend’s `/v1` routes require a valid issuer/audience JWT. Changes to auth routes or token handling should be reviewed alongside the JWT validation path.
- For payment workflows, treat the backend as authoritative: the app should poll backend status endpoints rather than trusting a locally cached wallet value.
- The app’s release flow is production-sensitive: only build `:app:assembleRelease` against a deployed HTTPS backend and a merchant-controlled signing key.
- The Node demo server in `server/` uses `APP_SERVER_KEY` and is meant for local or demo verification, not as the main production service.

## Working style for this repo

- Keep changes scoped to the correct layer: Android app work belongs under `app/src/main/java`, backend API work belongs under `backend`, and demo/server changes stay in `server/`.
- If a fix touches auth, wallets, or payment flows, expect to validate both the client-side state and the backend contract.
- Match the project’s existing patterns: Compose screens, ViewModels, repository/use-case layers for Android; Express route/service patterns for backend endpoints.
