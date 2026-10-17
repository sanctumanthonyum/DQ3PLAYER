package com.exemplo.dq3player.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun GamepadOverlay(
    landscape: Boolean,
    onKey: (keyCode: Int, pressed: Boolean) -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = RoundedCornerShape(50)
    Box(modifier.padding(12.dp)) {
        // Retrato: metade de baixo da tela. Paisagem: tela inteira.
        val area = if (landscape) Modifier.fillMaxSize()
        else Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.BottomCenter)

        Box(area) {
            PadButton("L", KeyEvent.KEYCODE_BUTTON_L1, onKey, Modifier.align(Alignment.TopStart).size(80.dp, 40.dp), pill)
            PadButton("R", KeyEvent.KEYCODE_BUTTON_R1, onKey, Modifier.align(Alignment.TopEnd).size(80.dp, 40.dp), pill)
            DPad(onKey, Modifier.align(Alignment.CenterStart))
            FaceButtons(onKey, Modifier.align(Alignment.CenterEnd))
            Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PadButton("Select", KeyEvent.KEYCODE_BUTTON_SELECT, onKey, Modifier.size(72.dp, 32.dp), pill)
                Box(
                    Modifier.size(72.dp, 32.dp).clip(pill)
                        .background(Color.White.copy(alpha = 0.2f)).clickable(onClick = onMenu),
                    contentAlignment = Alignment.Center,
                ) { Text("Menu", color = Color.White) }
                PadButton("Start", KeyEvent.KEYCODE_BUTTON_START, onKey, Modifier.size(72.dp, 32.dp), pill)
            }
        }
    }
}

@Composable
private fun PadButton(
    label: String, keyCode: Int, onKey: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier, shape: Shape = CircleShape,
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = if (pressed) 0.5f else 0.2f))
            .pointerInput(keyCode) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    onKey(keyCode, true)
                    waitForUpOrCancellation()
                    pressed = false
                    onKey(keyCode, false)
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontWeight = FontWeight.Bold) }
}

// Losango do SNES: X em cima, B embaixo, Y à esquerda, A à direita
@Composable
private fun FaceButtons(onKey: (Int, Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(170.dp)) {
        PadButton("X", KeyEvent.KEYCODE_BUTTON_X, onKey, Modifier.align(Alignment.TopCenter).size(56.dp))
        PadButton("B", KeyEvent.KEYCODE_BUTTON_B, onKey, Modifier.align(Alignment.BottomCenter).size(56.dp))
        PadButton("Y", KeyEvent.KEYCODE_BUTTON_Y, onKey, Modifier.align(Alignment.CenterStart).size(56.dp))
        PadButton("A", KeyEvent.KEYCODE_BUTTON_A, onKey, Modifier.align(Alignment.CenterEnd).size(56.dp))
    }
}

@Composable
private fun DPad(onKey: (Int, Boolean) -> Unit, modifier: Modifier = Modifier) {
    var active by remember { mutableStateOf(emptySet<Int>()) }

    fun update(next: Set<Int>) {
        (active - next).forEach { onKey(it, false) }
        (next - active).forEach { onKey(it, true) }
        active = next
    }

    Box(
        modifier
            .size(150.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    update(direction(awaitFirstDown().position, size))
                    do {
                        val event = awaitPointerEvent()
                        val p = event.changes.first()
                        update(if (p.pressed) direction(p.position, size) else emptySet())
                        p.consume()
                    } while (event.changes.any { it.pressed })
                    update(emptySet())
                }
            },
    ) {
        Text("▲", Modifier.align(Alignment.TopCenter).padding(top = 8.dp), color = Color.White)
        Text("▼", Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), color = Color.White)
        Text("◀", Modifier.align(Alignment.CenterStart).padding(start = 8.dp), color = Color.White)
        Text("▶", Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), color = Color.White)
    }
}

// Eixo dominante: uma direção por vez, ideal para RPG de 4 direções
private fun direction(p: Offset, size: IntSize): Set<Int> {
    val dx = p.x - size.width / 2f
    val dy = p.y - size.height / 2f
    if (maxOf(abs(dx), abs(dy)) < size.width * 0.12f) return emptySet() // zona morta
    return setOf(
        if (abs(dx) > abs(dy)) {
            if (dx < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        } else {
            if (dy < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
        }
    )
}
