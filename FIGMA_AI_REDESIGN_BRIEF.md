# SwiftPay Merchant - Figma AI App Redesign Brief

## How to use this brief

Paste the prompt below into Figma AI / Figma Make. Generate a complete Android mobile app design system and screen set, not just a dashboard concept. Keep screens editable and organize components, variants, and flows into clearly named pages.

## Paste-ready Figma AI prompt

Redesign the complete SwiftPay Merchant Android app as a polished, production-minded merchant payments product for small businesses in the Philippines. Create a cohesive, trustworthy, calm, modern mobile experience. The design must work as a real app UI, not a marketing website, and must cover every screen and meaningful state listed below.

### Product and design principles

- The primary users are Filipino merchants who collect payments, monitor a provider-reported PHP wallet, send bank or e-wallet disbursements, review transaction history, and manage their account.
- Make money movement clear and confidence-inspiring. Use strong hierarchy, readable amounts, clear recipient and fee confirmation, and explicit pending/success/failure states.
- Keep the visual identity recognizably SwiftPay / DRPay: deep navy and bank blue as the brand foundation, light neutral surfaces, and a restrained warm brass accent. Use green only for confirmed success, amber for caution or pending review, and red for errors or destructive actions. Do not use crypto-neon gradients or decorative color without meaning.
- Use a consistent 8 dp spacing rhythm, generous but efficient mobile spacing, clear type hierarchy, accessible contrast, large touch targets (at least 48 dp), and readable form labels and helper copy.
- Design for Android phone frames around 393 x 852 dp, with responsive behavior for smaller phones, large font settings, keyboard-visible forms, and system bars. Use native-feeling Material 3 patterns, rounded surfaces, restrained elevation, clear focus states, and predictable back navigation.
- Supply complete Light and Dark themes with intentional contrast; do not merely invert colors. Keep the same semantic color meaning in both themes.
- Make the main shell consistent across Compose screens and embedded payment screens. The app has four primary destinations: Home, Wallet, Crypto, and Profile. Secondary tools are reached from Home or Profile and should have clear back navigation.
- Use realistic-looking but clearly fictional sample data in mockups. Never use real customer information, credentials, API keys, bank account numbers, or actual identity documents.
- Preserve the meaning of each existing workflow. This is a visual redesign, not a product-scope change. Do not invent features, imply that money is held by SwiftPay when it is not, or imply that an application is approved when it is not.
- Never present internal adjustments or manual deposit receipts as provider funds. Show the existing provider wallet balance as provider-reported PHP. Show crypto as an external wallet with no connected on-chain balance. Explain that USDT buy/sell opens in Transak and requires partner availability.
- Merchant registration verification starts an application only. KYB documents and the selfie are submitted for manual administrator review. The selfie is not face matching, automated identity verification, or liveness detection. Merchant access must not appear approved until the actual backend status grants it.
- Treat balances, recipient details, currencies, fees, and payment statuses as high-consequence information. Avoid ambiguous icon-only actions and make the final confirmation step visually distinct from data entry.

### Global app shell and design system

Create a Figma page named `00 - Design System` containing:

1. Color variables for light and dark themes: brand navy, bank blue, neutral backgrounds/surfaces, primary/on-primary, secondary text, outline, success, warning/pending, error, and disabled states.
2. Type styles for display balance, screen title, section title, body, field label, helper/error text, tab label, and compact metadata.
3. Spacing, corner-radius, elevation, icon-size, and minimum touch-target tokens.
4. Reusable components with variants:
   - Top app bar and four-item bottom navigation.
   - Primary, secondary, text, destructive, icon, and loading buttons.
   - Text, amount, email, OTP, account-number, search, and multiline fields, including focus, error, disabled, and keyboard-visible states.
   - Provider-balance card with visibility toggle, loading skeleton, unavailable state, and retry action.
   - Transaction row, status badge, filter chip, empty state, error banner, snackbar, confirmation dialog, and bottom sheet.
   - Institution picker row with optional provider logo placeholder; do not create unofficial bank logos.
   - Document-upload tile with empty, uploading, uploaded, replacement, and validation-error variants.
   - KYB application status card for draft, submitted, under review, needs attention, approved, and rejected states, with appropriate copy and no false promises.
   - Step indicator, OTP digit input, biometric/MPIN explanation panel, and secure-action confirmation panel.

Create a page named `01 - App Screens` and organize frames by the screen groups below. Include both theme variants for key screens and all important loading, empty, error, disabled, and success states. Make the prototype links reflect the listed user flows.

### Screen inventory and requirements

#### A. Authentication and account setup

1. **Welcome / sign in**
   - SwiftPay identity, concise explanation for merchant sign-in, email field, continue/request-code action, loading/error states, and a secondary merchant-registration entry when registration is enabled.
   - Do not imply that entering an email creates an account.
2. **Sign-in OTP**
   - Masked destination email, six-digit code entry, resend countdown, edit-email/back action, invalid/expired code states, and explicit progress.
3. **New merchant onboarding**
   - A short, skippable/readable introduction to the merchant dashboard, provider wallet, payment collection, and account safety. Treat it as product guidance, not account approval.
4. **MPIN setup and confirmation**
   - Explain local app unlock, show digit-entry progress, confirmation mismatch/error state, and recovery/back behavior without displaying entered digits.
5. **Biometric opt-in and unlock**
   - Show device biometric capability and clear opt-in/skip choices. Explain that device biometrics unlock the app locally and are distinct from KYB selfie review. Include unavailable, cancelled, and failed states.
6. **Preparing account / onboarding check**
   - Loading and retry states with calm progress language.
7. **Waiting for account approval**
   - Pending status, what is awaiting review, what remains unavailable, and a clear way to return or check later. Do not show an active merchant dashboard or success treatment before approval.
8. **Registration email verification**
   - Business contact email, clear "verify email to start an application" explanation, validation errors, and loading state.
9. **Registration OTP**
   - Reuse the OTP system but identify the purpose as merchant application verification, not sign-in.

#### B. KYB application and review

10. **KYB application form**
    - Use a compact stepper/progress indicator and grouped sections:
      - Business details: legal/business name, registration number, business address, and business type where present in the current form.
      - Representative details: representative name and contact details where present.
      - Review documents: business certificate, representative ID, and selfie.
    - Make required/optional fields explicit. Keep labels persistent above fields; never rely on placeholder-only labels.
    - Show upload guidance for accepted PDF, JPEG, or PNG files, per-document uploaded/replacement states, progress, validation errors, and a final review-before-submit action.
    - Place privacy/handling information near the document upload area. Clearly disclose that the selfie is for manual admin review only and is not an automated face scan, liveness check, or biometric identity match.
11. **Review and submit**
    - Summarize entered business and representative details and each document's status. Allow editing before submission. Use a deliberate final submit action.
12. **Application submitted / result**
    - Confirmation that the application was submitted for review, expected next step without promising an exact review time, and a return-to-sign-in action.
13. **Application status**
    - Variants for submitted, under review, needs attention, approved, and rejected. For changes requested, surface the reviewer note and a clear resubmission path. Only approved status may imply merchant access.

#### C. Main navigation and merchant work

14. **Home dashboard**
    - Greeting and profile/help shortcuts.
    - Provider-reported PHP wallet balance card with show/hide balance control, loading skeleton, unavailable/error + retry state.
    - Clear quick actions: Collect, Transfer, Payment link, Scan QR, plus a route to transactions/help where appropriate.
    - Merchant tools / payment-link callout.
    - Three recent transactions with readable amount, date, counterparty/reference, and status.
    - Empty history and partial/unavailable history states. Do not replace a missing live balance with a fabricated amount.
15. **Wallet and transaction list**
    - Provider balance and relevant receiving-account information only when the app has actual values to display.
    - Filter chips for All, Income, Expense, Bank Deposits, and Failed; clear selected state and horizontal overflow behavior.
    - Transaction rows with sign, currency, date, source, and unmistakable status.
    - Loading, no transactions, source unavailable, and refresh states.
16. **Transaction detail**
    - Amount, transaction state, method, provider/reference ID, timestamps, and available details in a scannable hierarchy.
    - Keep pending, executed, failed, and reversed states distinct. Use copy/reference actions only when available.
17. **Crypto / USDT**
    - Explain that the USDT wallet is external and SwiftPay does not custody USDT or read its on-chain balance.
    - Distinguish Buy and Sell panels, show availability/loading/error states, and route to Transak only as the existing product does.
    - Include an explicit pre-trade caution to verify destination address, network, quote, and fees. Never show a fake token balance or price chart.
18. **USDT trade result / external handoff**
    - Show a clear transition to the third-party provider and return/error/cancel states only if supported by the current flow. Do not invent a successful settlement state.

#### D. Collect, links, transfer, QR, and deposits (embedded payment experience)

Give these screens the same typography, palette, form behavior, and component language as native Compose screens. Use a consistent mini-app top bar and back action.

19. **Mini-app dashboard**
    - Compact service/balance overview and the same core collect, transfer, payment-link, and scanner actions. Do not show a second conflicting navigation system.
20. **Collect a payment**
    - Amount, optional reference/description, currency where supported, live summary, validation, continue-to-checkout action, loading, and provider-error states.
21. **Payment link**
    - Item/service description and amount entry, generated link result, copy/share affordances, expiry/status details only when returned by the backend, and loading/error states.
22. **Deposit to wallet**
    - Clearly separate instructions from a confirmed deposit. Show the available provider account information only when returned by the API; do not invent the receiving bank/institution or beneficiary. Tell users to verify details with their merchant administrator when the service cannot provide them.
23. **Deposit wizard and checkout**
    - Design each existing step and result state, including amount/instructions, provider handoff, pending, completed, cancelled, and error. Never represent a user-reported or manually recorded deposit as provider-confirmed money.
24. **Send money / disbursement**
    - Institution search/selection, account or wallet number, recipient name, amount, transfer rail/type where applicable, review step, final confirmation, submitted/pending state, and terminal result/error.
    - Emphasize exact recipient and amount confirmation; distinguish provider acceptance from settlement. Do not imply a transfer is settled merely because it was accepted.
25. **QR scanner and QR payment**
    - Camera permission, scanning frame, supported-QR guidance, invalid/unsupported QR, amount/method review when present, and confirmation/result states.
    - Keep scanner guidance accessible and avoid unauthorized bank logos.
26. **Payment service / card payments**
    - Status and configuration screens with explicit available, unavailable, checking, and error states. Do not imply card acceptance or service activation when the provider says it is unavailable.

#### E. Developer tools and profile

27. **API credentials**
    - Explain the purpose and status of credentials without exposing secrets in mockups. Create masked-value, reveal-on-demand (if supported), copied, unavailable, and error states. Avoid a "live" badge unless backed by the actual status.
28. **API documentation**
    - Readable developer landing screen with endpoint/topic navigation, code snippets, copy affordance, and inline warning/error treatment. Optimize for mobile scanning rather than dense desktop-like tables.
29. **Profile**
    - Account email and actual merchant alias with accurate labels; never infer a legal business name from an email or payment alias.
    - Clear sign-in/session state, account settings, app theme selection (System/Light/Dark if supported), biometric unlock setting and MPIN prerequisites, developer links, diagnostics/network details if present, and sign-out confirmation.
    - Provide long-email and small-screen layouts without overflow.

### Required prototype flows

1. Returning merchant: Sign in -> OTP -> optional biometric unlock -> Home -> Wallet -> Transaction detail -> Home.
2. New applicant: Welcome -> registration email -> application OTP -> KYB form -> review -> submit -> pending application result.
3. Admin-reviewed applicant: Pending status -> needs-attention with reviewer note -> edit/resubmit; separately show approved status leading to merchant access. Do not imply that an in-app user approves their own application.
4. Collect payment: Home -> Collect -> enter amount/reference -> review/continue -> provider checkout handoff -> pending/result.
5. Pay by link: Home -> Payment link -> enter amount/description -> generated result -> copy/share.
6. Disbursement: Home -> Transfer -> choose institution -> enter recipient/amount -> review exact details -> submit -> pending -> terminal status.
7. Scan: Home -> Scan QR -> camera permission/scanner -> parsed payment review or unsupported QR -> result.
8. Settings: Profile -> theme selection; Profile -> enable biometric (with MPIN prerequisite and device prompt); Profile -> sign out confirmation.
9. Crypto: Crypto -> Buy or Sell -> third-party handoff -> return/cancel/error, without showing an invented on-chain balance.

### Figma deliverables

- A complete design system page and organized screen pages with descriptive frame names.
- Full Light and Dark theme examples for Home, Wallet, Profile, OTP, KYB form, Transfer, and transaction detail; use semantic tokens for the remaining screens.
- Essential interaction variants for loading, empty, error, disabled, pending, success, and permission-denied states.
- Auto Layout components and variants suitable for developer handoff.
- Prototype connections for all required flows above.
- A final review checklist page confirming: contrast and readability, touch targets, keyboard/small-phone behavior, long text, financial status accuracy, KYB privacy language, and no invented provider capabilities.

Before finalizing, check that all screens feel like one coherent merchant app; the embedded collection/transfer tools must not look like an unrelated website inside it.
