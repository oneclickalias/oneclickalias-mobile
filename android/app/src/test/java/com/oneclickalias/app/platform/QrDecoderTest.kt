package com.oneclickalias.app.platform

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrDecoderTest {
    private val text = "OCA-PAIR-1-MFRGGZDFMZTWQ2LKNNWG23TPOBYXE43U"
    private val decoder = QrDecoder()

    /** A camera-like luminance plane: the code in the middle, rows padded to [rowStride]. */
    private fun frame(
        format: BarcodeFormat = BarcodeFormat.QR_CODE,
        inverted: Boolean = false,
        width: Int = 480,
        height: Int = 360,
        rowStride: Int = 512
    ): ByteArray {
        val size = 240
        val matrix = MultiFormatWriter().encode(
            text,
            format,
            size,
            if (format == BarcodeFormat.QR_CODE) size else 80,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        )
        val dark: Byte = if (inverted) 0xF0.toByte() else 0x10
        val light: Byte = if (inverted) 0x10 else 0xF0.toByte()
        val plane = ByteArray(rowStride * height) { light }
        val left = (width - matrix.width) / 2
        val top = (height - matrix.height) / 2
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) plane[(top + y) * rowStride + left + x] = dark
            }
        }
        return plane
    }

    @Test
    fun `reads a qr code from a padded luminance plane`() {
        assertEquals(text, decoder.decode(frame(), width = 480, height = 360, rowStride = 512))
    }

    @Test
    fun `reads a light-on-dark code too`() {
        assertEquals(text, decoder.decode(frame(inverted = true), width = 480, height = 360, rowStride = 512))
    }

    @Test
    fun `an empty frame has no text`() {
        assertNull(decoder.decode(ByteArray(512 * 360) { 0x70 }, width = 480, height = 360, rowStride = 512))
    }

    @Test
    fun `other barcode formats are ignored`() {
        assertNull(decoder.decode(frame(BarcodeFormat.CODE_128), width = 480, height = 360, rowStride = 512))
    }
}
