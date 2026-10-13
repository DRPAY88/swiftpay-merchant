package com.example.myapplication.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.BuildConfig
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object IncomingMoneyPush {
    private const val TAG = "IncomingMoneyPush"
    private const val CHANNEL_ID = "incoming_money"

    fun isConfigured(): Boolean =
        BuildConfig.FCM_PROJECT_ID.isNotBlank() &&
            BuildConfig.FCM_APPLICATION_ID.isNotBlank() &&
            BuildConfig.FCM_SENDER_ID.isNotBlank() &&
            BuildConfig.FCM_API_KEY.isNotBlank()

    private fun firebaseApp(context: Context): FirebaseApp? {
        if (!isConfigured()) return null
        return FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId(BuildConfig.FCM_PROJECT_ID)
                    .setApplicationId(BuildConfig.FCM_APPLICATION_ID)
                    .setGcmSenderId(BuildConfig.FCM_SENDER_ID)
                    .setApiKey(BuildConfig.FCM_API_KEY)
                    .build()
            )
    }

    suspend fun registerCurrentDevice(context: Context, accessToken: String): Boolean {
        if (firebaseApp(context) == null) return false
        return try {
            val token = suspendCancellableCoroutine { continuation ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(task.result)
                    } else {
                        continuation.resumeWithException(
                            task.exception ?: IllegalStateException("Firebase could not issue a device token")
                        )
                    }
                }
            }
            val result = SwiftPayService(accessToken = accessToken).registerPushToken(token)
            result.onFailure { Log.w(TAG, "Could not register this device for incoming-payment notifications") }
            result.isSuccess
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Could not initialize incoming-payment notifications", error)
            false
        }
    }

    suspend fun unregisterCurrentDevice(context: Context, accessToken: String) {
        if (firebaseApp(context) == null) return
        try {
            val token = suspendCancellableCoroutine { continuation ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(task.result)
                    } else {
                        continuation.resumeWithException(
                            task.exception ?: IllegalStateException("Firebase device token is unavailable")
                        )
                    }
                }
            }
            SwiftPayService(accessToken = accessToken).removePushToken(token)
                .onFailure { Log.w(TAG, "Could not remove the signed-out device's push token") }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Could not unregister the signed-out device's push token", error)
        }
    }

    fun show(context: Context, message: RemoteMessage) {
        createNotificationChannel(context)
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = message.notification
        val paymentId = message.data["paymentId"].orEmpty()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("incoming_payment_id", paymentId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            paymentId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(notification?.title ?: "Money received")
            .setContentText(notification?.body ?: "A payment has been received")
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification?.body ?: "A payment has been received"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        NotificationManagerCompat.from(context).notify(paymentId.hashCode(), builder.build())
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Incoming payments",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when SwiftPay confirms an incoming payment"
            }
        )
    }
}

class IncomingMoneyMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] == "incoming_payment") {
            IncomingMoneyPush.show(this, message)
        }
    }

    override fun onNewToken(token: String) {
        val accessToken = SettingsManager(applicationContext).getAccessToken() ?: return
        serviceScope.launch {
            SwiftPayService(accessToken = accessToken).registerPushToken(token)
                .onFailure { Log.w("IncomingMoneyPush", "Could not register refreshed push token") }
        }
    }
}
