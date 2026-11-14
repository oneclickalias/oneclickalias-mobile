package com.oneclickalias.app.platform

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * Finds a QR code in one camera frame, on the device (ZXing core, QR format
 * only). Takes the luminance (Y) plane as CameraX delivers it; nothing is
 * kept after the call. Also reads light-on-dark codes (a dashboard in dark
 * mode). Not thread-safe: one instance per analysis thread.
 */
class QrDecoder {
    private val reader = QRCodeReader()
    private val hints = mapOf(DecodeHintType.CHARACTER_SET to "ISO-8859-1")

    /** The text of the first QR code found, or null. [rowStride] is the bytes per row of [luminance]. */
    fun decode(luminance: ByteArray, width: Int, height: Int, rowStride: Int): String? {
        val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
        return read(source) ?: read(source.invert())
    }

    private fun read(source: LuminanceSource): String? = try {
        reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    } catch (e: ReaderException) {
        null
    } finally {
        reader.reset()
    }
}
