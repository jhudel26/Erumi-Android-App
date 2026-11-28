package com.erumi.nativeapp.core.utils

import java.util.Base64

object OtakuCipher {
    private const val KEY = "otaku-embed-v1"

    fun deobfuscate(blob: String): String {
        return try {
            val cleaned = blob.trim().replace("\n", "").replace("\r", "")
            val raw = Base64.getDecoder().decode(cleaned)
            val keyBytes = KEY.toByteArray(Charsets.UTF_8)
            val out = ByteArray(raw.size)
            for (i in raw.indices) {
                out[i] = (raw[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            String(out, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
