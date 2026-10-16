package com.example.myapplication.ui.components

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current.applicationContext
    val reducedMotion = remember(context) {
        mutableStateOf(isSystemMotionReduced(context))
    }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reducedMotion.value = isSystemMotionReduced(context)
            }
        }
        val resolver = context.contentResolver
        val settings = listOf(
            Settings.Global.ANIMATOR_DURATION_SCALE,
            Settings.Global.WINDOW_ANIMATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE
        )
        settings.forEach { setting ->
            resolver.registerContentObserver(Settings.Global.getUriFor(setting), false, observer)
        }
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    return reducedMotion.value
}

private fun isSystemMotionReduced(context: Context): Boolean {
    val resolver = context.contentResolver
    return listOf(
        Settings.Global.ANIMATOR_DURATION_SCALE,
        Settings.Global.WINDOW_ANIMATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE
    ).any { setting ->
        Settings.Global.getFloat(resolver, setting, 1f) <= 0f
    }
}
