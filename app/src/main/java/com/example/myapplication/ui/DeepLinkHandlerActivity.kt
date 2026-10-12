package com.example.myapplication.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity

import com.example.myapplication.MainActivity

/**
 * Simple activity that receives payment redirect deep links (myapp://payment/...) and
 * routes to the appropriate screen in the app. It extracts query parameters (linkId, status)
 * so the app can reconcile the payment.
 */
class DeepLinkHandlerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val data: Uri? = intent?.data
            Log.d("DeepLinkHandler", "Received deep link: $data")

            // Example expected URI: myapp://payment/success?linkId=PLK_123&status=SUCCESS
            val linkId = data?.getQueryParameter("linkId")
            val status = data?.getQueryParameter("status")

            // Pass these details to MainActivity (or a specific PaymentResult screen)
            val out = Intent(this, MainActivity::class.java)
            out.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            out.putExtra("deep_link_uri", data?.toString())
            if (!linkId.isNullOrBlank()) out.putExtra("linkId", linkId)
            if (!status.isNullOrBlank()) out.putExtra("paymentStatus", status)

            startActivity(out)
        } catch (e: Exception) {
            Log.e("DeepLinkHandler", "Failed to handle deep link", e)
        } finally {
            // Always finish this redirect activity
            finish()
        }
    }
}

