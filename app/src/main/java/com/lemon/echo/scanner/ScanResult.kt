package com.lemon.echo.scanner

import android.net.Uri
import com.lemon.echo.scanner.chain.ChainPacket

data class ScanResult(
    val rawText: String,
    val isUrl: Boolean = false,
    val isChainPacket: Boolean = false
) {
    companion object {
        fun fromBarcodeText(text: String): ScanResult {
            val uri = Uri.parse(text.trim())
            val isUrl = uri.scheme != null &&
                    (uri.scheme == "http" || uri.scheme == "https")
            val isChain = ChainPacket.parse(text) != null
            return ScanResult(
                rawText = text,
                isUrl = isUrl,
                isChainPacket = isChain
            )
        }
    }
}
