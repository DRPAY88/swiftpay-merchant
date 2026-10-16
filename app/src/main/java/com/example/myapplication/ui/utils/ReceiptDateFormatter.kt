package com.example.myapplication.ui.utils

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

private val receiptTimestampPatterns = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    "yyyy-MM-dd'T'HH:mm:ssXXX",
    "yyyy-MM-dd'T'HH:mm:ss.SSS",
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd HH:mm:ss"
)

private val receiptFractionPattern = Regex("""(\.\d{3})\d+""")

fun formatReceiptDate(value: String): String {
    val normalizedValue = receiptFractionPattern.replace(value) { it.groupValues[1] }
    val date = receiptTimestampPatterns.firstNotNullOfOrNull { pattern ->
        val position = ParsePosition(0)
        val parsed = SimpleDateFormat(pattern, Locale.getDefault()).apply {
            isLenient = false
        }.parse(normalizedValue, position)
        parsed?.takeIf { position.index == normalizedValue.length }
    } ?: return value

    return SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(date)
}
