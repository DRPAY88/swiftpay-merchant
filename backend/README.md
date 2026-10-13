# SwiftPay backend

This service is the trusted boundary between the Android client and SwiftPay. Merchant credentials and order signing stay on the server. The service defaults to the SwiftPay sandbox; live mode requires an explicit opt-in.

## Run

Use Node.js 20.6 or newer. Copy `.env.example` to `.env`, configure sandbox credentials, an allowlisted merchant email, and a verified Resend sender, then run:

```sh
npm install
npm test
npm run db:migrate
npm start
```

`npm run db:migrate` applies versioned, additive PostgreSQL migrations using `DATABASE_URL`.
The initial migration creates the multi-currency account, receiving, invoice, payment, payout,
FX quote, provider-event, and double-entry ledger schema for PHP, USDT, and KRW. Run migrations
explicitly before deploying code that uses these tables; the current backend continues to use its
existing shared SwiftPay merchant account and does not yet create isolated customer balances.
Bank account details are represented as ciphertext fields and must be encrypted before storage.
Ledger batches can only be posted when debit and credit totals balance per currency; posted batches
and their entries are immutable, so corrections must use a separate reversal batch.

The API refuses to start without SwiftPay credentials, an allowlist, a session-signing key, and a Resend key/from address. Sign-in codes are sent only to addresses in `AUTH_ALLOWED_EMAILS` or the optional `AUTH_ADDITIONAL_ALLOWED_EMAILS`; non-allowlisted addresses receive the same accepted response without an email. Codes expire after ten minutes, are single-use, and allow five attempts. The server issues an eight-hour scoped JWT after successful verification.

KRW-priced QR payments require `CURRENCYAPI_KEY` from the paid CurrencyAPI commercial plan. The quote endpoint fails with an actionable service error until this server-side secret is configured. CurrencyAPI rates are hourly, quotes are valid for five minutes, and SwiftPay receives only the corresponding locked PHP amount. The payer's bank can apply its own KRW/PHP conversion and fees; the quoted KRW amount is a displayed price, not a guarantee of the payer's final bank debit.

The Render blueprint configures `OTP_FROM_EMAIL` as `SwiftPay <login@swiftpay.site>`, matching the Resend DKIM and sending records for `swiftpay.site`. Keep `RESEND_API_KEY` configured as a Render secret with a valid production Resend API key.

## USDT buy and sell

The wallet opens Transak's hosted, non-custodial checkout for USDT on TRON. The backend creates a single-use widget URL; BUY requires the customer's TRON destination address, while SELL sends the entered USDT amount to Transak's sell flow. The app does not custody USDT or treat the SwiftPay PHP wallet as a crypto balance. Transak performs its own KYC, quotes, fees, and transaction handling.

The Render blueprint is configured for the live Transak production environment, but the backend remains fail-closed until `TRANSAK_API_KEY` and `TRANSAK_API_SECRET` are provided as Render secrets. Set the approved partner credentials, ensure the deployed service's static egress IP is allowlisted by Transak, and confirm the merchant account has PHP/Philippines USDT TRON buy/sell support before enabling live traffic. Without these settings, the app reports checkout as unavailable and cannot submit a trade.

If sign-in requests return HTTP 503, check the backend logs for the Resend HTTP status and provider error code. Verify the `swiftpay.site` domain is marked verified in the same Resend account as `RESEND_API_KEY`; redeploy after updating the key or sender. The API intentionally returns a generic error and never exposes provider details to the app.

## Super-admin backoffice

The protected dashboard is served at `/admin/`. It uses the existing one-time email sign-in and requires the signed-in address to have the `super_admin` role in PostgreSQL. `SUPER_ADMIN_EMAILS` bootstraps configured administrators; the production blueprint initially assigns `support@swiftpay.site`. Merchant activation, suspension, removal, admin adjustments, and transfers are audited. Removed users remain as records and can be reactivated by adding them again.

The backoffice supports the existing single shared SwiftPay account: balance and disbursement history are shared by all merchant users. An internal balance adjustment is a bookkeeping record only and never changes SwiftPay funds. Super admins can record verified manual bank deposits for merchant wallet history; those receipt records do not change SwiftPay funds or the wallet balance. A submitted transfer is a real InstaPay disbursement; the dashboard requires explicit confirmation and stores an audit intent before contacting the provider.

The Render blueprint provisions a PostgreSQL database (`swiftpay-merchant-db`, `basic-256mb`) and binds `DATABASE_URL` to the backend. Sync the blueprint in Render to create and attach the database before using `/admin/`. Until `DATABASE_URL` is configured, the backend remains available but disables persistent admin access. PostgreSQL stores merchant status, adjustments, and the audit log. Do not use this shared-account deployment as if it provided isolated merchant balances or credentials.

Set Android `PAYMENT_BACKEND_URL` to this service’s HTTPS base URL. Android debug builds default to the emulator host `http://10.0.2.2:3000/` when `PAYMENT_BACKEND_URL` is unset; release builds default to `https://swiftpay-merchant.onrender.com/` and can be overridden with a Gradle property, environment variable, or `local.properties` value.

- `swiftpay:payments:read`
- `swiftpay:payments:write`
- `swiftpay:disbursements:read`
- `swiftpay:disbursements:write`

The old app login creates a local UUID and is not accepted by this backend. The mobile client must use the backend OTP endpoints and store the returned bearer token securely.

## Routes

- `POST /v1/payments/orders` creates an HMAC-signed SwiftPay order and returns a hosted checkout URL. The Android wallet embeds this secure provider checkout for PHP deposits while retaining direct account-number transfers. The provider page presents the merchant-enabled payment methods. The `details` field is sent as a JSON-encoded string, as required by the provider contract.
- `POST /v1/payments/qrph/quote` returns a signed, merchant-bound five-minute KRW/USD/EUR-to-PHP quote using the latest available CurrencyAPI rate.
- `POST /v1/payments/qrph` requests a signed P2M QR Ph payment in PHP and its PNG image. A KRW, USD, or EUR customer price must include the signed quote token; SwiftPay QR Ph is charged the locked PHP amount, and the customer price and rate are retained in the payment record. QR Ph itself settles only in PHP.
- `GET /v1/payments/:paymentToken/status` checks payment status using the payment-scoped token.
- `GET /v1/wallet/balance` uses SwiftPay's documented `GET /api/disbursements/account` endpoint with HTTP Basic authentication. SwiftPay returns the inbound top-up account in `number` and the available disbursement-wallet funds in `balance`; the backend returns those as `accountNumber` and `balance`. If SwiftPay returns `404` because no disbursement account is configured for the merchant, the backend returns an actionable error instead of an unavailable balance.
- `GET /v1/wallet/manual-deposits` returns recorded PHP bank deposit receipt details for the wallet. `POST /v1/admin/manual-bank-deposits` is super-admin-only and records a confirmed deposit for history; it does not change provider funds or either balance.
- `GET /v1/wallet/payment-transactions` returns PHP payment transactions recorded from verified SwiftPay webhooks, including their payment reference and status.
- `POST /v1/notifications/push-token` registers the authenticated Android device for incoming-payment alerts; `DELETE` removes that device token.
- `GET /v1/wallet/krw-receipts` returns the total of verified, executed KRW payment webhooks. This is an app-side receipt total, not a provider-reported spendable KRW account balance; the provider callback must include signed `x_amount` and `x_currency` fields for a payment to contribute.
- `POST /v1/payment-links` creates a fixed-amount hosted payment link in PHP, USD, or EUR.
- `GET /v1/payment-links/:code` reads a link; `DELETE` invalidates it.
- The payment-link response `paymentUrl` is a public DRPAY checkout page at `/checkout/:code`. It displays the fixed amount/currency and offers Alipay, Naver Pay, Kakao Pay, and Toss Bank as named shortcuts to the same SwiftPay QR Ph flow; they do not imply those apps have native QR Ph support. The page warns customers to scan only with an app that supports QR Ph. USD/EUR prices are converted to PHP when the QR is requested. SwiftPay payment-link creation supports PHP, USD, and EUR; KRW/USD/EUR QR-priced payments are supported by the signed quote API and settle in PHP. Other checkout methods continue through SwiftPay's hosted payment page. Set `PUBLIC_BASE_URL` to the backend's public HTTPS origin; do not derive share URLs from request `Host` headers.
- `POST /v1/disbursements` submits an InstaPay or PESONet transfer. InstaPay may specify SwiftPay's documented `P2P`, `QR_P2P`, `QR_P2M`, or `QR_P2MICRO` transfer type; PESONet supports only `P2P`. The backend supplies `Bank transfer` as the required nonblank provider remark when the caller omits remarks or sends only whitespace.
- `GET /v1/disbursements` requests the first provider page (up to 100 records) and returns its `result` array; `GET /v1/disbursements/:id` reads one transfer.
- `GET /v1/disbursement-institutions` retrieves provider institution codes.
- `GET /v1/payment-institutions` returns enabled SwiftPay payment institutions and their validated official logo URLs for branded bank selection UI.
- `GET /healthz` is an unauthenticated health check.

All `/v1` routes require an issuer- and audience-verified JWT. Order creation requires a caller-provided reference number for provider idempotency. The backend returns disbursements as `PENDING` after SwiftPay accepts them; acceptance is not settlement. Poll the disbursement status until a terminal state.

OTP challenges are currently held in process memory. Run a single backend instance until a shared persistent challenge store is configured; otherwise a restart or load balancing between instances can invalidate sign-in challenges.

The published SwiftPay API exposes the PHP disbursement-wallet ledger balance, not a combined settlement or card balance. The KRW receipt view is derived only from verified executed QR payment webhooks that SwiftPay reports in KRW; it is separate from KRW-priced QR sales, which settle in PHP and are recorded with their quoted KRW reference metadata. Neither view represents a separate provider-reported spendable KRW balance. The wallet account number can be used as the destination for inbound top-ups, but the published API does not provide the receiving institution or beneficiary name and does not initiate or confirm inbound transfers; users must verify transfer details with their merchant administrator. Payment status, disbursement history, and the PHP disbursement balance are read from SwiftPay; the app does not treat a locally cached value as authoritative.

Deploy behind HTTPS, keep the environment variables in a secrets manager, and configure production only with `SWIFTPAY_BASE_URL=https://api.pay.live.swiftpay.ph/api/` and `SWIFTPAY_ALLOW_LIVE=true`. Do not place SwiftPay credentials or a shared backend secret in the Android build.

Incoming-money push notifications are sent through Firebase Cloud Messaging after a signed SwiftPay payment webhook reports `EXECUTED`. The backend persists a deduplicated delivery outbox and retries temporary send failures. Configure `FCM_SERVICE_ACCOUNT_JSON` as a Render/backend secret using a Firebase service account with Firebase Cloud Messaging permission. The Android build also needs its Firebase project's non-secret client settings (`FCM_PROJECT_ID`, `FCM_APPLICATION_ID`, `FCM_SENDER_ID`, and `FCM_API_KEY`) supplied through Gradle properties, environment variables, or untracked `local.properties`. Request notification permission on Android 13+ and deploy the backend before expecting alerts. Push token registration is scoped to the active signed-in merchant account; signing out removes that device registration.

## Render

The repository-root `render.yaml` defines the live SwiftPay Docker web service. In Render, provide the rotated production credentials, session-signing key, allowed operator email, and verified Resend settings as secrets. The blueprint adds `support@swiftpay.site` through `AUTH_ADDITIONAL_ALLOWED_EMAILS` without replacing the existing `AUTH_ALLOWED_EMAILS` list. Apply the updated blueprint and deploy the backend before requesting a sign-in code for the added account. The Android release build defaults to the service HTTPS URL as `PAYMENT_BACKEND_URL`.

## Android release APK

Build a production APK only after the live backend is deployed and its `/healthz` endpoint is reachable. The Android build needs JDK 21, Android SDK Platform 36 and Build Tools 36.0.0 (`ANDROID_HOME` or `sdk.dir` in `local.properties`), and a merchant-controlled Android signing key. Provide these Gradle properties through a CI secret store or an untracked local configuration; never commit the signing key or its passwords:

- `PAYMENT_BACKEND_URL` — the HTTPS URL of the deployed backend.
- `RELEASE_STORE_FILE` — path to the Android keystore.
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Then run `./gradlew :app:assembleRelease`. The APK is written to `app/build/outputs/apk/release/app-release.apk`. SwiftPay access keys and the session-signing key belong only in the backend's secret manager; they must never be passed to Gradle or included in the APK. Do not distribute an APK signed with a temporary or debug key as a production app.