package com.example.myapplication.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Finds the Activity associated with the given Context.
 */
fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}
