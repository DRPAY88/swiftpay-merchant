package com.example.myapplication.data.repository

import com.example.myapplication.data.SwiftPayService
import com.example.myapplication.data.api.SwiftPayQrResponse

/**
 * Repository for QR code operations using SwiftPay.
 */
class QrRepository(private val swiftPayService: SwiftPayService) {

    suspend fun createDynamicQr(amount: Double): Result<SwiftPayQrResponse> =
        swiftPayService.createDynamicQr(amount)
}
