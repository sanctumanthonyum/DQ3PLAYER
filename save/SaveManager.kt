package com.exemplo.dq3player.save

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class SaveManager(context: Context) {
    val dir = File(context.filesDir, "saves").apply { mkdirs() }
    private val sram = File(dir, "game.srm")
    private fun stateFile(slot: Int) = File(dir, "state_$slot.sav")

    fun hasSram() = sram.isFile && sram.length() > 0
    fun readSram(): ByteArray? = if (hasSram()) sram.readBytes() else null
    fun readState(slot: Int): ByteArray? = stateFile(slot).takeIf { it.isFile }?.readBytes()

    @Synchronized
    fun writeSram(data: ByteArray) {
        if (data.isNotEmpty()) writeAtomic(sram, data)
    }

    @Synchronized
    fun writeState(slot: Int, data: ByteArray) {
        if (data.isNotEmpty()) writeAtomic(stateFile(slot), data)
    }

    private fun writeAtomic(target: File, data: ByteArray) {
        val tmp = File(dir, target.name + ".tmp")
        FileOutputStream(tmp).use { it.write(data); it.fd.sync() }
        if (target.isFile) target.copyTo(File(dir, target.name + ".bak"), overwrite = true)
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }
}
