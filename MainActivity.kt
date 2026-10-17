package com.exemplo.dq3player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.lifecycleScope
import com.exemplo.dq3player.rom.RomRepository
import com.exemplo.dq3player.save.SaveManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var roms: RomRepository
    private lateinit var saves: SaveManager
    private var rom by mutableStateOf<RomRepository.RomInfo?>(null)
    private var hasSave by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var notice by mutableStateOf<String?>(null)
    private var autoLaunch by mutableStateOf(true)

    // Seletor SAF (ACTION_OPEN_DOCUMENT)
    private val pickRom = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            lifecycleScope.launch {
                when (val r = roms.importRom(uri)) {
                    is RomRepository.ImportResult.Success -> {
                        rom = r.info
                        notice = if (r.checksumOk) null
                        else "Checksum não confere (comum em ROMs traduzidas). Se o jogo abrir, está tudo certo."
                    }
                    is RomRepository.ImportResult.Error -> notice = r.reason
                }
                busy = false
            }
        }
    }

    // Exporta a SRAM como .srm
    private val exportSave = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val data = saves.readSram()
        if (uri != null && data != null) contentResolver.openOutputStream(uri)?.use { it.write(data) }
        else if (uri != null) notice = "Ainda não há save para exportar."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        roms = RomRepository(this)
        saves = SaveManager(this)
        autoLaunch = roms.autoLaunch
        refresh()

        // Abre direto no jogo só na inicialização a frio (não ao girar a tela nem ao voltar do jogo)
        if (savedInstanceState == null && rom != null && autoLaunch) startGame()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var showSettings by remember { mutableStateOf(false) }
                TitleScreen(
                    rom = rom, hasSave = hasSave, busy = busy, notice = notice,
                    onPlay = ::startGame,
                    onImport = { notice = null; pickRom.launch(arrayOf("*/*")) },
                    onSettings = { showSettings = true },
                )
                if (showSettings) {
                    SettingsDialog(
                        autoLaunch = autoLaunch,
                        onAutoLaunch = { autoLaunch = it; roms.autoLaunch = it },
                        onResetRom = { roms.clearRom(); refresh() },
                        onExportSave = { exportSave.launch("progresso.srm") },
                        onDismiss = { showSettings = false },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        rom = roms.currentRom()
        hasSave = saves.hasSram()
    }

    private fun startGame() = startActivity(Intent(this, EmulatorActivity::class.java))
}

@Composable
private fun TitleScreen(
    rom: RomRepository.RomInfo?, hasSave: Boolean, busy: Boolean, notice: String?,
    onPlay: () -> Unit, onImport: () -> Unit, onSettings: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF000020), Color(0xFF14207A))))
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(
                "A LENDA DO HERÓI", color = Color(0xFFFFD54F), fontSize = 30.sp,
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            DqWindow(Modifier.fillMaxWidth().widthIn(max = 360.dp)) {
                if (busy) {
                    Text("Importando ROM...", color = Color.White)
                } else {
                    if (rom != null) MenuItem(if (hasSave) "Continuar" else "Iniciar jogo", onPlay)
                    MenuItem(if (rom == null) "Importar ROM" else "Alterar ROM", onImport)
                    MenuItem("Configurações", onSettings)
                }
            }
            if (rom == null && !busy) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Escolha o arquivo .sfc ou .smc do seu jogo (traduções são aceitas).",
                    color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                )
            }
            notice?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = Color(0xFFFFB4AB), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun DqWindow(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(Color(0xFF0A1480), RoundedCornerShape(6.dp))
            .border(3.dp, Color.White, RoundedCornerShape(6.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    Text(
        "▶ $text", color = Color.White, fontSize = 20.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}

@Composable
private fun SettingsDialog(
    autoLaunch: Boolean, onAutoLaunch: (Boolean) -> Unit,
    onResetRom: () -> Unit, onExportSave: () -> Unit, onDismiss: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        title = { Text("Configurações") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Abrir direto no jogo", Modifier.weight(1f))
                    Switch(checked = autoLaunch, onCheckedChange = onAutoLaunch)
                }
                TextButton(onClick = onExportSave) { Text("Exportar save (.srm)") }
                TextButton(onClick = { confirmReset = true }) { Text("Redefinir ROM") }
                if (confirmReset) {
                    Text("Remove só a ROM importada. Seus saves são mantidos.")
                    TextButton(onClick = { confirmReset = false; onResetRom() }) { Text("Confirmar") }
                }
            }
        },
    )
}
