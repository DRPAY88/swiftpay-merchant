package com.example.myapplication.di

import android.content.Context
import com.example.myapplication.data.SettingsManager
import com.example.myapplication.data.SwiftPayService
import com.example.myapplication.data.TransactionStore
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.PaymentRepository
import com.example.myapplication.data.repository.QrRepository
import com.example.myapplication.data.repository.TransactionRepository
import com.example.myapplication.domain.usecase.AuthenticateUseCase
import com.example.myapplication.domain.usecase.GenerateQrUseCase
import com.example.myapplication.domain.usecase.ProcessPaymentUseCase

/**
 * Dependency injection container providing instances for repositories and use cases.
 */
object DIContainer {

    private var context: Context? = null
    private var swiftPayService: SwiftPayService? = null
    private var settingsManager: SettingsManager? = null
    private var transactionStore: TransactionStore? = null

    /**
     * Initialize the DI container with application context
     */
    fun initialize(appContext: Context) {
        context = appContext
        settingsManager = SettingsManager(appContext)
        transactionStore = TransactionStore(appContext)
    }

    /**
     * Get or create SwiftPayService instance
     */
    fun provideSwiftPayService(): SwiftPayService {
        val manager = settingsManager
        val token = manager?.getAccessToken()
        return SwiftPayService(accessToken = token)
    }

    /**
     * Legacy alias for provideSwiftPayService
     */
    fun provideMayaService(): SwiftPayService = provideSwiftPayService()

    /**
     * Get or create SettingsManager instance
     */
    fun provideSettingsManager(): SettingsManager {
        return settingsManager ?: throw IllegalStateException("DIContainer not initialized")
    }

    /**
     * Get or create TransactionStore instance
     */
    fun provideTransactionStore(): TransactionStore {
        return transactionStore ?: throw IllegalStateException("DIContainer not initialized")
    }

    // Repositories

    fun providePaymentRepository(): PaymentRepository =
        PaymentRepository(provideSwiftPayService())

    fun provideAuthRepository(): AuthRepository =
        AuthRepository(provideSwiftPayService())

    fun provideQrRepository(): QrRepository =
        QrRepository(provideSwiftPayService())

    fun provideTransactionRepository(): TransactionRepository =
        TransactionRepository(provideSwiftPayService(), provideTransactionStore())

    // Use Cases

    fun provideProcessPaymentUseCase(): ProcessPaymentUseCase =
        ProcessPaymentUseCase(providePaymentRepository())

    fun provideGenerateQrUseCase(): GenerateQrUseCase =
        GenerateQrUseCase(provideQrRepository())

    fun provideAuthenticateUseCase(): AuthenticateUseCase =
        AuthenticateUseCase(provideAuthRepository(), provideSettingsManager())
}
