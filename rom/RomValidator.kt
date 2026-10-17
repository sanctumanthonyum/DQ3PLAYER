package com.exemplo.dq3player.rom

import java.io.File
import java.io.RandomAccessFile

object RomValidator {
    private val EXTENSIONS = setOf("sfc", "smc")
    private const val MIN_SIZE = 0x20000L         // 128 KB
    private const val MAX_SIZE = 0x800000L + 512  // 8 MB + cabeçalho de copiador

    sealed interface Verdict {
        data class Ok(val title: String, val checksumOk: Boolean) : Verdict
        data class Fail(val reason: String) : Verdict
    }

    fun hasValidExtension(name: String?) =
        name?.substringAfterLast('.', "")?.lowercase() in EXTENSIONS

    fun validate(file: File): Verdict {
        val size = file.length()
        if (size !in MIN_SIZE..MAX_SIZE) return Verdict.Fail("O tamanho do arquivo não é de uma ROM de SNES.")
        val copierHeader = if (size % 1024 == 512L) 512 else 0

        RandomAccessFile(file, "r").use { raf ->
            // Cabeçalho interno: LoROM 0x7FC0, HiROM 0xFFC0, ExHiROM 0x40FFC0
            for (base in longArrayOf(0x7FC0, 0xFFC0, 0x40FFC0)) {
                val pos = copierHeader + base
                if (pos + 32 > size) continue
                val h = ByteArray(32)
                raf.seek(pos)
                raf.readFully(h)
                val complement = (h[0x1C].toInt() and 0xFF) or ((h[0x1D].toInt() and 0xFF) shl 8)
                val checksum = (h[0x1E].toInt() and 0xFF) or ((h[0x1F].toInt() and 0xFF) shl 8)
                if ((complement xor checksum) == 0xFFFF) {
                    val title = String(h, 0, 21, Charsets.US_ASCII).filter { it.code in 32..126 }.trim()
                    return Verdict.Ok(title, true)
                }
            }
        }
        return Verdict.Ok("", false) // checksum quebrado: aviso, não bloqueio
    }
}
