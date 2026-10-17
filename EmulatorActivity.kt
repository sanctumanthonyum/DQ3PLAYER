package com.exemplo.dq3player

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.exemplo.dq3player.rom.RomRepository
import com.exemplo.dq3player.save.SaveManager
import com.exemplo.dq3player.ui.GamepadOverlay
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import kotlin.math.abs

class EmulatorActivity : ComponentActivity() {

    private lateinit var retroView: GLRetroView
    private lateinit var saves: SaveManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastSram: ByteArray? = null

    private val autosave = object : Runnable {
        override fun run() {
            persistSram()
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val roms = RomRepository(this)
        saves = SaveManager(this)
        if (roms.currentRom() == null) { finish(); return }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        lastSram = saves.readSram()
        val data = GLRetroViewData(this).apply {
            coreFilePath = "libsnes9x_libretro_android.so"
            gameFilePath = roms.romFile.absolutePath
            systemDirectory = filesDir.absolutePath
            savesDirectory = saves.dir.absolutePath
            saveRAMState = lastSram
            preferLowLatencyAudio = true
        }
        retroView = GLRetroView(this, data)
        lifecycle.addObserver(retroView)

        setContent {
            EmulatorScreen(
                retroView = retroView,
                onKey = ::sendKey,
                onSaveState = ::saveState,
                onLoadState = ::loadState,
                onReset = { retroView.reset() },
                onExit = { finish() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(autosave)
        handler.postDelayed(autosave, 30_000)
    }

    override fun onPause() {
        persistSram() // antes do super: o core ainda está ativo
        handler.removeCallbacks(autosave)
        super.onPause()
    }

    private fun persistSram() {
        if (!::retroView.isInitialized) return
        runCatching {
            val sram = retroView.serializeSRAM()
            // Ignora SRAM zerada (core ainda não carregou) e dados iguais aos já gravados
            if (sram.any { it != 0.toByte() } && !sram.contentEquals(lastSram)) {
                saves.writeSram(sram)
                lastSram = sram
            }
        }
    }

    private fun sendKey(keyCode: Int, pressed: Boolean) {
        retroView.sendKeyEvent(if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, keyCode)
    }

    private fun saveState() {
        val ok = runCatching { saves.writeState(1, retroView.serializeState()) }.isSuccess
        toast(if (ok) "Estado salvo" else "Falha ao salvar o estado")
    }

    private fun loadState() {
        val data = saves.readState(1)
        val ok = data != null && runCatching { retroView.unserializeState(data) }.getOrDefault(false)
        toast(if (ok) "Estado carregado" else "Nenhum estado compatível")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // Controles Bluetooth/USB: botões e D-pad
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val fromPad = event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_DPAD)
        val mapped = if (fromPad) mapPadKey(event.keyCode) else null
        if (mapped == null) return super.dispatchKeyEvent(event)
        if (event.repeatCount == 0) sendKey(mapped, event.action == KeyEvent.ACTION_DOWN)
        return true
    }

    // Controles que enviam o D-pad como eixo (hat) ou o analógico esquerdo
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.isFromSource(InputDevice.SOURCE_JOYSTICK) && ev.action == MotionEvent.ACTION_MOVE) {
            val x = ev.getAxisValue(MotionEvent.AXIS_HAT_X).takeIf { abs(it) > 0.5f }
                ?: ev.getAxisValue(MotionEvent.AXIS_X)
            val y = ev.getAxisValue(MotionEvent.AXIS_HAT_Y).takeIf { abs(it) > 0.5f }
                ?: ev.getAxisValue(MotionEvent.AXIS_Y)
            retroView.sendMotionEvent(
                GLRetroView.MOTION_SOURCE_DPAD,
                if (abs(x) > 0.5f) x else 0f,
                if (abs(y) > 0.5f) y else 0f,
            )
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    // Layout Xbox (A embaixo) para a posição dos botões do SNES (B embaixo)
    private fun mapPadKey(code: Int): Int? = when (code) {
        KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
        KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
        KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
        KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> code
        else -> null
    }
}

@Composable
private fun EmulatorScreen(
    retroView: GLRetroView,
    onKey: (Int, Boolean) -> Unit,
    onSaveState: () -> Unit,
    onLoadState: () -> Unit,
    onReset: () -> Unit,
    onExit: () -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var menu by remember { mutableStateOf(false) }
    BackHandler { menu = true }

    Box(Modifier.fillMaxSize().background(Color.Black).displayCutoutPadding()) {
        // Mesmo nó de composição em retrato e paisagem: só o Modifier muda,
        // então o GLRetroView não é removido nem recriado ao girar a tela.
        AndroidView(
            factory = { retroView },
            modifier = if (landscape) Modifier.fillMaxSize()
            else Modifier.fillMaxWidth().aspectRatio(4f / 3f).align(Alignment.TopCenter),
        )
        GamepadOverlay(landscape, onKey, onMenu = { menu = true }, modifier = Modifier.fillMaxSize())
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            confirmButton = { TextButton(onClick = { menu = false }) { Text("Voltar ao jogo") } },
            dismissButton = { TextButton(onClick = { menu = false; onExit() }) { Text("Sair") } },
            text = {
                Column {
                    TextButton(onClick = onSaveState) { Text("Salvar estado (slot 1)") }
                    TextButton(onClick = onLoadState) { Text("Carregar estado (slot 1)") }
                    TextButton(onClick = { menu = false; onReset() }) { Text("Reiniciar console") }
                }
            },
        )
    }
}
