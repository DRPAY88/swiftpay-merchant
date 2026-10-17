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
existing pooled SwiftPay merchant account. Each verified self-service merchant email has a
separate app-managed PHP ledger; it is not a separate SwiftPay account or segregated provider balance.
Bank account details are represented as ciphertext fields and must be encrypted before storage.
Ledger batches can only be posted when debit and credit totals balance per currency; posted batches
and their entries are immutable, so corrections must use a separate reversal batch.

The API refuses to start without SwiftPay credentials, an operator allowlist, a session-signing key, and a Resend key/from address. Email OTP sign-in is used for merchants and operators. Active operator emails retain their configured role; a new email becomes a self-service merchant only after its code is verified. `AUTH_ALLOWED_EMAILS` and `AUTH_ADDITIONAL_ALLOWED_EMAILS` configure operator accounts, not signup eligibility. Codes expire after ten minutes, are single-use, and allow five attempts. The server issues an eight-hour scoped JWT after successful email verification.

Foreign-currency QR payments (including KRW-priced Toss/Kakao/KBank and CNY-priced Alipay) require `CURRENCYAPI_KEY` from a CurrencyAPI plan that supports the intended commercial use. The Render blueprint declares this as a secret placeholder, so it must be supplied in the deployment environment: open the `swiftpay-merchant-backend` service in the Render Dashboard, go to **Environment**, add or update `CURRENCYAPI_KEY` with the key from the CurrencyAPI account, then save and redeploy the service. Do not put this key in the Android app, repository, or a chat message. Until the secret is set, the quote endpoint returns HTTP 503 and foreign-currency QR generation remains unavailable; PHP-denominated collection is unaffected. CurrencyAPI rate refresh intervals depend on the selected plan and may be daily; the backend accepts rates up to 26 hours old and rejects older data. Merchant-bound quotes are valid for five minutes, and SwiftPay receives only the corresponding locked PHP amount. The payer's bank can apply its own conversion and fees; a displayed customer-currency amount is not a guarantee of the payer's final bank debit.

The Render blueprint configures `OTP_FROM_EMAIL` as `SwiftPay <login@swiftpay.site>`, matching the Resend DKIM and sending records for `swiftpay.site`. Keep `RESEND_API_KEY` configured as a Render secret with a valid production Resend API key.

Android account creation and sign-in use email OTP delivered through Resend. A new self-service merchant account is activated immediately after successful email verification; no KYB form, document upload, or manual approval is required. New merchants use an app-managed internal PHP ledger, while existing customer identities remain personal wallets and configured operator identities retain their merchant or super-admin roles and permissions.

Each newly verified merchant email starts with a zero PHP ledger. Wallet balances and activity are isolated by email; the prior shared provider balance, manual-deposit history, and webhook history are not assigned to new users. Merchants can create QR Ph collections; a ledger credit is posted only when a valid signed SwiftPay webhook confirms the merchant's matching reference, payment ID, PHP amount, and currency as `EXECUTED`. Payouts reserve the merchant's ledger amount before contacting SwiftPay; a confirmed rejection releases it, while an unknown provider outcome stays reserved for reconciliation. Provider funds remain pooled: an app ledger is not a separate Netbank/SwiftPay virtual account, bank account, custodial account, or guarantee that SwiftPay has made collection funds available for disbursement. Confirm collection-to-disbursement settlement behavior with SwiftPay before enabling live payouts.

Phone-authenticated customer records and ledgers from the earlier sign-in flow are not automatically linked to email accounts. Their database records are left intact; verify ownership and perform an explicit account-linking migration before moving any existing wallet balance or activity.

## Marketing site and custom domain

The marketing site is served from `backend/public/marketing/` at `/`; the protected admin console remains at `/admin/`. The site's download button uses `/download`, which redirects to the versioned APK in that public directory. When publishing a new APK, add the signed release APK there, then update the redirect in `backend/src/app.js` and the displayed version in `backend/public/marketing/index.html`.

The marketing page also serves a 15-second vertical promotional video at `/drpay-promo.mp4` with its poster image at `/drpay-promo-poster.jpg`. Keep the video's availability messaging aligned with the current product: direct KRW-to-PHP or KRW-to-USDT exchange is not available in the current app.

The homepage's live rate panel requests `GET /api/marketing/rates`. It prefers backend CurrencyAPI KRW/PHP and USD/PHP rates and combines them with CoinGecko's USDT/USD market price. If CurrencyAPI is unavailable, the public reference panel falls back to Frankfurter's daily central-bank reference rates for KRW/PHP and KRW/USD; fallback data older than four days is rejected and the source is labeled in the UI. The fallback is only for indicative homepage rates: executable foreign-currency payment quotes continue to require the configured CurrencyAPI key and fail closed when that service is unavailable. The panel excludes fees, spreads, eligibility, and network costs, and does not substitute sample values.

The Render blueprint sets `PUBLIC_BASE_URL` to `https://denpay.cc` so generated payment links use the custom domain. To connect it:

1. Deploy the service from this repository to Render.
2. In the `swiftpay-merchant-backend` Render service, open **Settings > Custom Domains** and add `denpay.cc`. Render also supports the corresponding `www` hostname and redirects it to the root domain.
3. In Hostinger's DNS Zone Editor for `denpay.cc`, add the DNS records Render shows for the custom domain. Remove conflicting `AAAA` records as Render directs, but keep unrelated mail records such as `MX` and email-verification `TXT` records.
4. Return to Render and verify the domain. Render provisions HTTPS after DNS verification; then check `https://denpay.cc` and `https://denpay.cc/download`.

## USDT buy and sell

The wallet opens Transak's hosted, non-custodial checkout for USDT on TRON. The backend creates a single-use widget URL; BUY requires the customer's TRON destination address, while SELL sends the entered USDT amount to Transak's sell flow. The app does not custody USDT or treat the SwiftPay PHP wallet as a crypto balance. Transak performs its own KYC, quotes, fees, and transaction handling.

The Render blueprint is configured for the live Transak production environment, but the backend remains fail-closed until `TRANSAK_API_KEY` and `TRANSAK_API_SECRET` are provided as Render secrets. Set the approved partner credentials, ensure the deployed service's static egress IP is allowlisted by Transak, and confirm the merchant account has PHP/Philippines USDT TRON buy/sell support before enabling live traffic. Without these settings, the app reports checkout as unavailable and cannot submit a trade.

If sign-in requests return HTTP 503, check the backend logs for the Resend HTTP status and provider error code. Verify the `swiftpay.site` domain is marked verified in the same Resend account as `RESEND_API_KEY`; redeploy after updating the key or sender. The API intentionally returns a generic error and never exposes provider details to the app.

## Super-admin backoffice

The protected dashboard is served at `/admin/`. It uses the existing one-time email sign-in and requires the signed-in address to have the `super_admin` role in PostgreSQL. `SUPER_ADMIN_EMAILS` bootstraps configured administrators; the production blueprint initially assigns `support@swiftpay.site`. Merchant activation, suspension, removal, admin adjustments, and transfers are audited. Removed users remain as records and can be reactivated by adding them again.

The backoffice supports the existing single shared SwiftPay account: balance and disbursement history are shared by all merchant users. An internal balance adjustment is a bookkeeping record only and never changes SwiftPay funds. Super admins can record verified manual bank deposits for merchant wallet history; those receipt records do not change SwiftPay funds or the wallet balance. A submitted transfer is a real InstaPay disbursement; the dashboard requires explicit confirmation and stores an audit intent before contacting the provider.

The Render blueprint provisions a PostgreSQL database (`swiftpay-merchant-db`, `basic-256mb`) and binds `DATABASE_URL` to the backend. Sync the blueprint in Render to create and attach the database before using `/admin/`. Until `DATABASE_URL` is configured, the backend remains available but disables persistent admin access. PostgreSQL stores merchant status, adjustments, and the audit log. Do not use this shared-account deployment as if it provided isolated merchant balances or credentials.

### Legacy merchant applications

The Android self-service sign-up no longer uses KYB applications. Existing merchant applications, their review data, and admin review tools are retained for compatibility; they are not required to create a new self-service account. If admins need to review or access legacy applicant documents, configure a stable `KYB_ENCRYPTION_KEY` as a Render secret. Do not commit this key or reuse `SESSION_SIGNING_KEY`.

Legacy applicant profiles and uploaded KYB documents are encrypted with AES-256-GCM before PostgreSQL storage. The previous application flow accepted a business registration certificate, representative government ID, and selfie for manual review only. These legacy records remain admin-only. Do not use this legacy flow for new customer onboarding.

Keep the encryption key stable and backed up in the deployment secret manager: losing or changing it without re-encrypting stored applications makes existing KYB files unreadable. Key rotation requires a deliberate migration of encrypted records. Keep HTTPS enabled for every deployed client/backend connection.

Set Android `PAYMENT_BACKEND_URL` to this service’s HTTPS base URL. Android debug builds default to the emulator host `http://10.0.2.2:3000/` when `PAYMENT_BACKEND_URL` is unset; release builds default to `https://swiftpay-merchant.onrender.com/` and can be overridden with a Gradle property, environment variable, or `local.properties` value.

- `swiftpay:payments:read`
- `swiftpay:payments:write`
- `swiftpay:disbursements:read`
- `swiftpay:disbursements:write`

The old app login creates a local UUID and is not accepted by this backend. The mobile client must use the backend OTP endpoints and store the returned bearer token securely.

## Routes

- `POST /v1/payments/orders` creates an HMAC-signed SwiftPay order and returns a hosted checkout URL for pooled merchant/operator accounts. Self-service internal merchants use the ledger-backed QR Ph collection route instead; orders are disabled for them until settlement can be assigned safely. The `details` field is sent as a JSON-encoded string, as required by the provider contract.
- `POST /v1/payments/qrph/quote` returns a signed, merchant-bound five-minute KRW/USD/EUR/CNY-to-PHP quote using the latest available CurrencyAPI rate.
- `POST /v1/payments/qrph` requests a signed P2M QR Ph payment in PHP and its PNG image. A KRW, USD, EUR, or CNY customer price must include the signed quote token; SwiftPay QR Ph is charged the locked PHP amount, and the customer price and rate are retained in the payment record. QR Ph itself settles only in PHP.
- `GET /v1/payments/:paymentToken/status` checks payment status using the payment-scoped token.
- `GET /v1/wallet/balance` returns the authenticated internal merchant's own PHP ledger balance and never returns the shared SwiftPay account number or pooled provider balance to that session. Existing customer sessions receive their separate personal ledger. Pooled operator sessions continue to use the provider account endpoint.
- `GET /v1/wallet/activity` returns only the authenticated customer's or internal merchant's own ledger activity.
- `POST /v1/wallet/topups/qrph` creates a uniquely referenced customer QR Ph top-up. The top-up remains pending until a signed SwiftPay `EXECUTED` webhook matches its reference, PHP amount, and payment identity. `GET /v1/wallet/topups/:referenceNo` returns only that customer's top-up status.
- `GET /v1/wallet/manual-deposits` remains for pooled merchant/operator history; personal and internal merchant wallets receive no shared manual-deposit records. `POST /v1/admin/manual-bank-deposits` is super-admin-only and remains history-only.
- `GET /v1/wallet/payment-transactions` remains a pooled merchant/operator history view. Personal and internal merchant wallets use `/v1/wallet/activity` so unrelated provider receipts are never exposed.
- `POST /v1/notifications/push-token` stores the authenticated device token; `DELETE` removes it. Merchant incoming-payment alerts remain merchant-wide. Customer-wallet-specific push delivery is not enabled, and customer tokens are deliberately excluded from shared merchant broadcasts.
- `GET /v1/wallet/krw-receipts` returns the total of verified, executed KRW payment webhooks. This is an app-side receipt total, not a provider-reported spendable KRW account balance; the provider callback must include signed `x_amount` and `x_currency` fields for a payment to contribute.
- `POST /v1/payment-links` creates a fixed-amount hosted payment link in PHP, USD, or EUR for pooled merchant/operator accounts. Internal merchant wallets cannot create, read, or invalidate provider-wide payment links because those settlements are not yet ledger-backed.
- `GET /v1/payment-links/:code` reads a pooled-account link; `DELETE` invalidates it.
- The payment-link response `paymentUrl` is a public DRPAY checkout page at `/checkout/:code`. It displays the fixed amount/currency and lists enabled banks and wallets only when SwiftPay provides an official logo from a trusted SwiftPay asset host; the logos are loaded from those original provider assets, never substituted with generated marks. The primary payment action continues to SwiftPay's secure hosted page for the full supported payment-method list. The separate QR Ph action warns customers to scan only with an app that supports QR Ph. USD/EUR prices are converted to PHP when the QR is requested. SwiftPay payment-link creation supports PHP, USD, and EUR; KRW/USD/EUR/CNY QR-priced payments are supported by the signed quote API and settle in PHP. Set `PUBLIC_BASE_URL` to the backend's public HTTPS origin; do not derive share URLs from request `Host` headers.
- `POST /v1/disbursements` submits an InstaPay or PESONET transfer. Internal merchants and existing customers can transfer only from their own PHP ledger; the amount is reserved before contacting SwiftPay. Unknown outcomes remain reserved until reconciled and callers must not retry automatically. Pooled operator accounts continue to use the provider account.
- `GET /v1/disbursements` returns the authenticated customer's or internal merchant's own transfers or, for pooled merchant/admin sessions, the provider's first page (up to 100 records). `GET /v1/disbursements/:id` is unavailable to ledger-wallet sessions.
- `GET /v1/disbursement-institutions` retrieves provider institution codes.
- `GET /v1/payment-institutions` returns enabled SwiftPay payment institutions and their validated official logo URLs for branded bank selection UI.
- `GET /healthz` is an unauthenticated health check.

All `/v1` routes require an issuer- and audience-verified JWT. Order creation requires a caller-provided reference number for provider idempotency. The backend returns disbursements as `PENDING` after SwiftPay accepts them; acceptance is not settlement. Poll the disbursement status until a terminal state.

Email OTP challenges are currently held in process memory. Run a single backend instance until a shared persistent email challenge store is configured; otherwise a restart or load balancing between instances can invalidate email sign-in challenges.

Self-service merchant wallets are app-managed ledgers over one pooled SwiftPay merchant account, not provider-issued subaccounts or individual virtual accounts. Historical shared balances and activity remain operator-only and are never divided among merchants. Only merchant-owned QR collections confirmed by signed settlement webhooks with matching payment ID, reference, amount, and PHP currency create ledger credit. This internal accounting does not guarantee that SwiftPay has moved a QR collection into its disbursement balance; verify that provider settlement path and maintain sufficient pooled liquidity before live payouts. The legacy KRW receipt view is also a webhook-derived total, not a spendable KRW balance.

Deploy behind HTTPS, keep the environment variables in a secrets manager, and configure production only with `SWIFTPAY_BASE_URL=https://api.pay.live.swiftpay.ph/api/` and `SWIFTPAY_ALLOW_LIVE=true`. Do not place SwiftPay credentials or a shared backend secret in the Android build.

Incoming-money merchant notifications are sent through Firebase Cloud Messaging after a signed SwiftPay payment webhook reports `EXECUTED`. The backend persists a deduplicated delivery outbox and retries temporary send failures. Configure `FCM_SERVICE_ACCOUNT_JSON` as a Render/backend secret using a Firebase service account with Firebase Cloud Messaging permission. The Android build also needs its Firebase project's non-secret client settings (`FCM_PROJECT_ID`, `FCM_APPLICATION_ID`, `FCM_SENDER_ID`, and `FCM_API_KEY`) supplied through Gradle properties, environment variables, or untracked `local.properties`. Request notification permission on Android 13+ and deploy the backend before expecting merchant alerts. Customer-wallet tokens are stored separately and excluded from those shared merchant broadcasts; personalized wallet notifications are not yet enabled.

## Render

The repository-root `render.yaml` defines the live SwiftPay Docker web service. In Render, provide the rotated production credentials, session-signing key, allowed operator email, and verified Resend settings as secrets. The blueprint adds `support@swiftpay.site` through `AUTH_ADDITIONAL_ALLOWED_EMAILS` without replacing the existing `AUTH_ALLOWED_EMAILS` list. Validate settlement crediting and pooled payout liquidity with SwiftPay before deploying the customer wallet to production. The Android release build defaults to the service HTTPS URL as `PAYMENT_BACKEND_URL`.

## Android release APK

Build a production APK only after the live backend is deployed and its `/healthz` endpoint is reachable. The Android build needs JDK 21, Android SDK Platform 36 and Build Tools 36.0.0 (`ANDROID_HOME` or `sdk.dir` in `local.properties`), and a merchant-controlled Android signing key. Provide these Gradle properties through a CI secret store or an untracked local configuration; never commit the signing key or its passwords:

- `PAYMENT_BACKEND_URL` — the HTTPS URL of the deployed backend.
- `RELEASE_STORE_FILE` — path to the Android keystore.
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Then run `./gradlew :app:assembleRelease`. The APK is written to `app/build/outputs/apk/release/app-release.apk`. SwiftPay access keys and the session-signing key belong only in the backend's secret manager; they must never be passed to Gradle or included in the APK. Do not distribute an APK signed with a temporary or debug key as a production app.