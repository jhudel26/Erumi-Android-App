package com.erumi.nativeapp.core.network

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.ByteArrayOutputStream

/**
 * Custom Media3 DataSource that inspects incoming stream data chunks.
 * If the CDN prepended a 1x1 PNG disguise header (\x89PNG ... IEND + CRC)
 * to mask MPEG-TS chunks as image files (anti-hotlink defense), this DataSource
 * strips the disguise bytes on-the-fly and presents pure TS packets to ExoPlayer.
 *
 * Handles split-buffer reads where IEND spans across buffer boundaries (B11).
 */
class DisguiseHeaderDataSource(private val upstream: DataSource) : DataSource {

    private var disguiseCheckComplete = false
    private var isPngDisguised = false
    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
    private val iendSignature = "IEND".toByteArray(Charsets.US_ASCII)

    // Accumulation buffer for header stripping across chunk boundaries
    private val headerBuffer = ByteArrayOutputStream()
    private var leftoverBuffer: ByteArray? = null
    private var leftoverOffset = 0

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        disguiseCheckComplete = false
        isPngDisguised = false
        headerBuffer.reset()
        leftoverBuffer = null
        leftoverOffset = 0
        return upstream.open(dataSpec)
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        headerBuffer.reset()
        leftoverBuffer = null
        leftoverOffset = 0
        upstream.close()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0

        // Serve leftover bytes from previous header unmasking
        leftoverBuffer?.let { leftover ->
            val remaining = leftover.size - leftoverOffset
            val toCopy = minOf(remaining, length)
            System.arraycopy(leftover, leftoverOffset, buffer, offset, toCopy)
            leftoverOffset += toCopy
            if (leftoverOffset >= leftover.size) {
                leftoverBuffer = null
                leftoverOffset = 0
            }
            return toCopy
        }

        if (disguiseCheckComplete) {
            return upstream.read(buffer, offset, length)
        }

        // Inspection & stripping loop with multi-read accumulation
        val tempBuf = ByteArray(8192)
        while (!disguiseCheckComplete) {
            val bytesRead = upstream.read(tempBuf, 0, tempBuf.size)
            if (bytesRead <= 0) {
                // EOF before header check finished: return accumulated bytes if any
                val accumulated = headerBuffer.toByteArray()
                if (accumulated.isNotEmpty()) {
                    disguiseCheckComplete = true
                    val toCopy = minOf(accumulated.size, length)
                    System.arraycopy(accumulated, 0, buffer, offset, toCopy)
                    if (accumulated.size > toCopy) {
                        leftoverBuffer = accumulated
                        leftoverOffset = toCopy
                    }
                    return toCopy
                }
                return bytesRead
            }

            headerBuffer.write(tempBuf, 0, bytesRead)
            val current = headerBuffer.toByteArray()

            // Check signature if not yet confirmed
            if (!isPngDisguised) {
                if (current.size >= 4) {
                    if (current[0] == pngSignature[0] &&
                        current[1] == pngSignature[1] &&
                        current[2] == pngSignature[2] &&
                        current[3] == pngSignature[3]
                    ) {
                        isPngDisguised = true
                    } else {
                        // Not a PNG-disguised stream; drain accumulated buffer
                        disguiseCheckComplete = true
                        val toCopy = minOf(current.size, length)
                        System.arraycopy(current, 0, buffer, offset, toCopy)
                        if (current.size > toCopy) {
                            leftoverBuffer = current
                            leftoverOffset = toCopy
                        }
                        return toCopy
                    }
                } else {
                    continue // Need more bytes to check 4-byte signature
                }
            }

            // Search accumulated buffer for IEND + 4 bytes CRC
            var iendPos = -1
            val maxScan = current.size - 8
            for (i in 0..maxScan) {
                if (current[i] == iendSignature[0] &&
                    current[i + 1] == iendSignature[1] &&
                    current[i + 2] == iendSignature[2] &&
                    current[i + 3] == iendSignature[3]
                ) {
                    iendPos = i + 8 // 4 bytes IEND + 4 bytes CRC
                    break
                }
            }

            if (iendPos != -1) {
                disguiseCheckComplete = true
                val payloadSize = current.size - iendPos
                if (payloadSize > 0) {
                    val toCopy = minOf(payloadSize, length)
                    System.arraycopy(current, iendPos, buffer, offset, toCopy)
                    if (payloadSize > toCopy) {
                        val leftover = ByteArray(payloadSize - toCopy)
                        System.arraycopy(current, iendPos + toCopy, leftover, 0, leftover.size)
                        leftoverBuffer = leftover
                        leftoverOffset = 0
                    }
                    return toCopy
                }
                // If IEND was at the very end of accumulated bytes, next iteration reads video payload
                continue
            }

            // Sanity cap: if we accumulated over 64 KB with no IEND, abort disguise stripping
            if (headerBuffer.size() > 65536) {
                disguiseCheckComplete = true
                val toCopy = minOf(current.size, length)
                System.arraycopy(current, 0, buffer, offset, toCopy)
                if (current.size > toCopy) {
                    leftoverBuffer = current
                    leftoverOffset = toCopy
                }
                return toCopy
            }
        }

        return upstream.read(buffer, offset, length)
    }
}
