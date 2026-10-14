# DRPAY

DRPAY is an Android merchant app backed by the SwiftPay API. It includes a PHP wallet, transaction history, payment checkout, and merchant account tools.

## Download

[Download the latest Android APK](https://github.com/stden16/swiftpay-merchant/releases/latest/download/denshitx-bank-release-1.0.10.apk) or view the [GitHub Releases](https://github.com/stden16/swiftpay-merchant/releases).

> **Installation note:** Version 1.0.6 introduced a new Android signing certificate, so users upgrading directly from version 1.0.5 must uninstall the older app first; uninstalling may erase app-local data. Versions 1.0.7, 1.0.8, and 1.0.9 keep the version 1.0.6 signing key and can update it in place. Future releases must continue using this key.

## Backend

The Express API is in [`backend/`](./backend/). It handles authentication, SwiftPay integration, payment checkout, and incoming payment notifications. See the [backend README](./backend/README.md) for setup and deployment instructions.

## Build the Android app

Use JDK 21 and Android SDK Platform 36 with Build Tools 36.0.0:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Release builds use `./gradlew :app:assembleRelease` and require the app's persistent release keystore. Keep the keystore and signing properties out of source control; use the same keystore for every update.
