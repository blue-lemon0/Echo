package com.lemon.echo.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

/**
 * CameraX ImageAnalysis analyzer that delegates frames to ML Kit Barcode Scanner.
 * Emits results via a callback — no modifications to raw text.
 */
class BarcodeAnalyzer(
    private val onBarcodeDetected: (ScanResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull { barcode ->
                    barcode.rawValue?.let { rawText ->
                        if (rawText.isNotBlank()) {
                            onBarcodeDetected(ScanResult.fromBarcodeText(rawText))
                            true
                        } else false
                    } ?: false
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
