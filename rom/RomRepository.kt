package com.exemplo.dq3player.rom

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class RomRepository(private val context: Context) {

    data class RomInfo(val fileName: String, val headerTitle: String, val sizeBytes: Long)

    sealed interface ImportResult {
        data class Success(val info: RomInfo, val checksumOk: Boolean) : ImportResult
        data class Error(val reason: String) : ImportResult
    }

    private val prefs = context.getSharedPreferences("dq3player", Context.MODE_PRIVATE)
    private val romDir = File(context.filesDir, "rom").apply { mkdirs() }
    val romFile = File(romDir, "game.sfc")

    var autoLaunch: Boolean
        get() = prefs.getBoolean("auto_launch", true)
        set(value) = prefs.edit().putBoolean("auto_launch", value).apply()

    /** null = nenhuma ROM registrada (primeiro uso ou ROM redefinida). */
    fun currentRom(): RomInfo? {
        if (!romFile.isFile || romFile.length() == 0L) return null
        return RomInfo(
            fileName = prefs.getString("rom_name", null) ?: "game.sfc",
            headerTitle = prefs.getString("rom_title", "") ?: "",
            sizeBytes = romFile.length(),
        )
    }

    suspend fun importRom(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        if (!RomValidator.hasValidExtension(name)) {
            return@withContext ImportResult.Error("Escolha um arquivo .sfc ou .smc (.zip não é aceito).")
        }
        val tmp = File(romDir, "import.tmp")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Error("Não foi possível abrir o arquivo.")
            input.use { src -> tmp.outputStream().use { dst -> src.copyTo(dst) } }

            when (val v = RomValidator.validate(tmp)) {
                is RomValidator.Verdict.Fail -> ImportResult.Error(v.reason)
                is RomValidator.Verdict.Ok -> {
                    romFile.delete()
                    if (!tmp.renameTo(romFile)) return@withContext ImportResult.Error("Falha ao gravar a ROM.")
                    prefs.edit().putString("rom_name", name).putString("rom_title", v.title).apply()
                    ImportResult.Success(RomInfo(name ?: "game.sfc", v.title, romFile.length()), v.checksumOk)
                }
            }
        } catch (e: Exception) {
            ImportResult.Error("Erro ao importar: ${e.message}")
        } finally {
            tmp.delete()
        }
    }

    /** Remove só a ROM. Os saves em filesDir/saves/ não são tocados. */
    fun clearRom() {
        romFile.delete()
        prefs.edit().remove("rom_name").remove("rom_title").apply()
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}
