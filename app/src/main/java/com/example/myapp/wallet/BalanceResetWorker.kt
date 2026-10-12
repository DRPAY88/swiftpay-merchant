package com.example.myapp.wallet

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.data.SettingsManager

class BalanceResetWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            // Reset wallet balance to 0.0
            SettingsManager(applicationContext).saveWalletBalance("0.0")
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
