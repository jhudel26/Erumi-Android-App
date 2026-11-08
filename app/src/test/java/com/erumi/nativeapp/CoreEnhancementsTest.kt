package com.erumi.nativeapp

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.erumi.nativeapp.core.database.DatabaseConverters
import com.erumi.nativeapp.core.network.DisguiseHeaderDataSource
import com.erumi.nativeapp.domain.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class CoreEnhancementsTest {

    @Test
    fun testDatabaseConverters() {
        val converters = DatabaseConverters()
        assertEquals("COMPLETED", converters.fromDownloadStatus(DownloadStatus.COMPLETED))
        assertEquals("PENDING", converters.fromDownloadStatus(DownloadStatus.PENDING))
        assertEquals(DownloadStatus.COMPLETED, converters.toDownloadStatus("COMPLETED"))
        assertEquals(DownloadStatus.DOWNLOADING, converters.toDownloadStatus("DOWNLOADING"))
        assertEquals(DownloadStatus.PENDING, converters.toDownloadStatus("INVALID_ENUM_STRING"))
    }

    @Test
    fun testDisguiseHeaderDataSourceStripsPngHeader() {
        val pngHeader = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), // \x89PNG
            0x0D, 0x0A, 0x1A, 0x0A,
            0x49, 0x45, 0x4E, 0x44, // IEND
            0x00, 0x00, 0x00, 0x00  // 4 bytes CRC
        )
        val videoData = byteArrayOf(0x47, 0x40, 0x00, 0x10, 0x00) // TS sync byte 0x47
        val fullData = pngHeader + videoData

        val mockUpstream = object : DataSource {
            private val stream = ByteArrayInputStream(fullData)
            override fun addTransferListener(transferListener: TransferListener) {}
            override fun open(dataSpec: DataSpec): Long = fullData.size.toLong()
            override fun getUri(): Uri? = null
            override fun close() { stream.close() }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                return stream.read(buffer, offset, length)
            }
        }

        val ds = DisguiseHeaderDataSource(mockUpstream)
        ds.open(DataSpec(Uri.EMPTY))

        val readBuffer = ByteArray(64)
        val bytesRead = ds.read(readBuffer, 0, readBuffer.size)

        assertEquals(videoData.size, bytesRead)
        assertEquals(0x47.toByte(), readBuffer[0]) // TS sync byte
        assertEquals(0x40.toByte(), readBuffer[1])
    }

    @Test
    fun testDisguiseHeaderDataSourcePassesNonPngThrough() {
        val normalVideoData = byteArrayOf(0x47, 0x40, 0x00, 0x10, 0x00, 0x01, 0x02)

        val mockUpstream = object : DataSource {
            private val stream = ByteArrayInputStream(normalVideoData)
            override fun addTransferListener(transferListener: TransferListener) {}
            override fun open(dataSpec: DataSpec): Long = normalVideoData.size.toLong()
            override fun getUri(): Uri? = null
            override fun close() { stream.close() }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                return stream.read(buffer, offset, length)
            }
        }

        val ds = DisguiseHeaderDataSource(mockUpstream)
        ds.open(DataSpec(Uri.EMPTY))

        val readBuffer = ByteArray(64)
        val bytesRead = ds.read(readBuffer, 0, readBuffer.size)

        assertEquals(normalVideoData.size, bytesRead)
        assertEquals(0x47.toByte(), readBuffer[0])
    }
}
