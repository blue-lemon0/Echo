package com.lemon.echo.scanner

import android.net.Uri

data class ScanResult(
    val rawText: String,
    val isUrl: Boolean = false
) {
    companion object {
        fun fromBarcodeText(text: String): ScanResult {
            val uri = Uri.parse(text.trim())
            val isUrl = uri.scheme != null &&
                    (uri.scheme == "http" || uri.scheme == "https")
            return ScanResult(
                rawText = text,
                isUrl = isUrl
            )
        }
    }
}
