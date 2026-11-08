package com.erumi.nativeapp.core.network

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Custom Media3 DataSource that inspects incoming stream data chunks.
 * If the CDN prepended a 1x1 PNG disguise header (\x89PNG ... IEND + CRC)
 * to mask MPEG-TS chunks as image files (anti-hotlink defense), this DataSource
 * strips the disguise bytes on-the-fly and presents pure TS packets to ExoPlayer.
 */
class DisguiseHeaderDataSource(private val upstream: DataSource) : DataSource {

    private var disguiseCheckComplete = false
    private var isPngDisguised = false
    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
    private val iendSignature = "IEND".toByteArray(Charsets.US_ASCII)

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        disguiseCheckComplete = false
        isPngDisguised = false
        return upstream.open(dataSpec)
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        upstream.close()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (disguiseCheckComplete) {
            return upstream.read(buffer, offset, length)
        }

        while (!disguiseCheckComplete) {
            val bytesRead = upstream.read(buffer, offset, length)
            if (bytesRead <= 0) {
                return bytesRead
            }

            if (!isPngDisguised) {
                if (bytesRead >= 4 &&
                    buffer[offset] == pngSignature[0] &&
                    buffer[offset + 1] == pngSignature[1] &&
                    buffer[offset + 2] == pngSignature[2] &&
                    buffer[offset + 3] == pngSignature[3]
                ) {
                    isPngDisguised = true
                } else {
                    disguiseCheckComplete = true
                    return bytesRead
                }
            }

            // Search for IEND + 4 bytes CRC
            var iendPos = -1
            for (i in offset until (offset + bytesRead - 7)) {
                if (buffer[i] == iendSignature[0] &&
                    buffer[i + 1] == iendSignature[1] &&
                    buffer[i + 2] == iendSignature[2] &&
                    buffer[i + 3] == iendSignature[3]
                ) {
                    iendPos = i + 8 // 4 bytes 'IEND' + 4 bytes CRC
                    break
                }
            }

            if (iendPos != -1) {
                disguiseCheckComplete = true
                val endOfRead = offset + bytesRead
                if (iendPos < endOfRead) {
                    val validBytes = endOfRead - iendPos
                    System.arraycopy(buffer, iendPos, buffer, offset, validBytes)
                    return validBytes
                }
                // If iendPos == endOfRead, entire chunk was disguise header; loop to read actual video bytes
                continue
            }
        }

        return upstream.read(buffer, offset, length)
    }
}
