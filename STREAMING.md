# ERUMI NATIVE — STREAMING ENGINE & MEDIA3 IMPLEMENTATION

## 1. Streaming Architecture

Erumi Native replaces the original Python-based reverse proxy (`web_server.py /api/proxy`) with a high-performance, 100% in-process media pipeline using **AndroidX Media3 ExoPlayer**.

```
Upstream Video CDN (vivibebe, ibyteimg, dramahot, etc.)
                          │
                          │ (HTTP/HTTPS Stream Chunks)
                          ▼
            [DefaultHttpDataSource]
              • Injects Referer, Origin, User-Agent
              • Supports Range requests & HTTP redirects
                          │
                          ▼
          [DisguiseHeaderDataSource]
              • Scans initial packet bytes
              • Locates \x89PNG ... IEND (+ 4 CRC bytes)
              • Strips disguise header on-the-fly
                          │
                          ▼
             [HlsMediaSource / ExoPlayer]
              • Adaptive Bitrate Streaming (1080p, 720p, etc.)
              • Hardware Accelerated Video Decoding
              • WebVTT Subtitle Synchronization
```

---

## 2. Anti-Hotlinking PNG Disguise Header Stripping

Certain anime CDNs (such as those served via ZokoAnime and MegaPlay) disguise MPEG-TS video chunks as 1x1 pixel PNG images by prepending an 8-byte PNG header and minimal metadata chunk ending in `IEND` followed by 4 CRC bytes. 

In Erumi Native, `DisguiseHeaderDataSource` handles this:
```kotlin
class DisguiseHeaderDataSource(private val upstream: DataSource) : DataSource {
    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
    private val iendSignature = "IEND".toByteArray(Charsets.US_ASCII)

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val bytesRead = upstream.read(buffer, offset, length)
        if (isPngHeader(buffer, offset)) {
            val iendPos = findIend(buffer, offset, bytesRead)
            if (iendPos != -1) {
                val validBytes = (offset + bytesRead) - iendPos
                System.arraycopy(buffer, iendPos, buffer, offset, validBytes)
                return validBytes
            }
        }
        return bytesRead
    }
}
```

---

## 3. Subtitles & Audio Tracks

1. **Subtitles:**
   - Extracted during embed deobfuscation (`OtakuCipher`).
   - Attached as `MediaItem.SubtitleConfiguration` with MIME type `text/vtt`.
   - Track selection toggled dynamically through `TrackSelectionParameters.setPreferredTextLanguage("en")` or disabling text tracks.
2. **Audio Selection:**
   - Scrapers support querying both Subbed (`sub`) and Dubbed (`dub`) streams.
   - Switching audio automatically resolves the alternate audio variant and resumes playback smoothly.
