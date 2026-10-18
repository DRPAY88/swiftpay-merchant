package com.example.myapplication

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.ui.DeepLinkHandlerActivity
import com.example.myapplication.MainActivity

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
    }

    @Test
    fun mainActivityLaunches() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
            }
        }
    }

    @Test
    fun paymentCallbackLinksResolveToDeepLinkHandler() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val callbacks = listOf("success", "failure", "cancel")
        val schemes = listOf("swiftpay", "swiftpay-showcase")

        for (scheme in schemes) {
            for (callback in callbacks) {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("$scheme://payment/$callback?linkId=test")
                ).setPackage(context.packageName)
                val resolved = context.packageManager.resolveActivity(
                    intent,
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                )

                assertNotNull("$scheme $callback must resolve", resolved)
                assertEquals(
                    DeepLinkHandlerActivity::class.java.name,
                    resolved?.activityInfo?.name
                )
            }
        }
    }
}