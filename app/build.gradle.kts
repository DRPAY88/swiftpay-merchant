import java.util.Properties
import org.gradle.api.GradleException

val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
}

fun optionalProperty(name: String): String {
    return providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: localProps.getProperty(name)
        ?: ""
}

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val PAYMENT_BACKEND_URL_OVERRIDE: String? = providers.gradleProperty("PAYMENT_BACKEND_URL").orNull
    ?: providers.environmentVariable("PAYMENT_BACKEND_URL").orNull
    ?: localProps.getProperty("PAYMENT_BACKEND_URL")
    ?: null

fun paymentBackendUrl(defaultUrl: String): String =
    PAYMENT_BACKEND_URL_OVERRIDE?.takeIf { it.isNotBlank() } ?: defaultUrl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.swiftpay.site"
        minSdk = 24
        targetSdk = 37
        versionCode = 24
        versionName = "1.0.23"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FCM_PROJECT_ID", buildConfigString(optionalProperty("FCM_PROJECT_ID")))
        buildConfigField("String", "FCM_APPLICATION_ID", buildConfigString(optionalProperty("FCM_APPLICATION_ID")))
        buildConfigField("String", "FCM_SENDER_ID", buildConfigString(optionalProperty("FCM_SENDER_ID")))
        buildConfigField("String", "FCM_API_KEY", buildConfigString(optionalProperty("FCM_API_KEY")))
    }

    signingConfigs {
        create("release") {
            storeFile = optionalProperty("RELEASE_STORE_FILE").takeIf { it.isNotBlank() }?.let { file(it) }
            storePassword = optionalProperty("RELEASE_STORE_PASSWORD").takeIf { it.isNotBlank() }
            keyAlias = optionalProperty("RELEASE_KEY_ALIAS").takeIf { it.isNotBlank() }
            keyPassword = optionalProperty("RELEASE_KEY_PASSWORD").takeIf { it.isNotBlank() }
        }
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "PAYMENT_BACKEND_URL",
                "\"${paymentBackendUrl("http://10.0.2.2:3000/")}\""
            )
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            val backendUrl = paymentBackendUrl("https://swiftpay-merchant.onrender.com/")
            buildConfigField("String", "PAYMENT_BACKEND_URL", "\"$backendUrl\"")
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!backendUrl.startsWith("https://")) {
                throw GradleException("PAYMENT_BACKEND_URL must use HTTPS for release builds")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation)
    implementation(libs.androidx.compose.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.converter.moshi)
    implementation(libs.converter.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logging.interceptor)
    implementation(libs.material)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.retrofit)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}
