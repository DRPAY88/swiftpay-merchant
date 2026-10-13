# Denshitx Bank

Denshitx Bank is an Android merchant app backed by the SwiftPay API. It includes a PHP wallet, transaction history, payment checkout, and merchant account tools.

## Download

[Download the latest Android APK](https://github.com/stden16/swiftpay-merchant/releases/latest/download/denshitx-bank-release-1.0.6.apk) or view the [GitHub Releases](https://github.com/stden16/swiftpay-merchant/releases).

> **Installation note:** Version 1.0.6 is signed with a new Android release certificate and cannot update version 1.0.5 in place. Uninstall the older app before installing this APK; uninstalling may erase app-local data. Future releases must use the same signing key as version 1.0.6.

## Backend

The Express API is in [`backend/`](./backend/). It handles authentication, SwiftPay integration, payment checkout, and incoming payment notifications. See the [backend README](./backend/README.md) for setup and deployment instructions.

## Build the Android app

Use JDK 21 and Android SDK Platform 36 with Build Tools 36.0.0:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Release builds use `./gradlew :app:assembleRelease` and require the app's persistent release keystore. Keep the keystore and signing properties out of source control; use the same keystore for every update.
