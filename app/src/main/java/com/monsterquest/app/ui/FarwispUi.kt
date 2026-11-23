package com.monsterquest.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.FarwispOrb
import com.monsterquest.core.Farwisp

private val Mist = Color(0xFF6FC7B8)

/** Out of range, with a Farwisp in the Bag: use one to take this on from here. */
@Composable
fun FarwispUse(vm: GameViewModel, enabled: Boolean = true, onUse: () -> Unit) {
    val n = Farwisp.count(vm.state)
    if (n <= 0) return
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        FarwispOrb(Modifier.size(30.dp))
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = onUse, enabled = enabled) { Text("Use a Farwisp ($n)", color = if (enabled) Mist else Color.Unspecified) }
    }
}

/** Everyone's free Farwisp: what it is and how to use it, with the orb drifting at the top. */
@Composable
fun FarwispIntro(vm: GameViewModel) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .safeDrawingPadding()
            .testTag("farwisp-intro"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .padding(18.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .border(1.5.dp, Mist, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FarwispOrb(Modifier.size(120.dp))
            Text("A NEW RARE ITEM", color = Mist, fontSize = 12.sp, letterSpacing = 2.sp)
            Text("Farwisp", color = Gold, fontSize = 26.sp, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Text(
                "A drifting ball of mist that carries you across the map. Here's one to start: it's in your Bag.",
                fontSize = 14.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            listOf(
                "Take on one dungeon, den, shrine, villain or nomad from wherever you are, without walking there.",
                "Tap it on the map: when it's out of reach, choose Use a Farwisp. One is used each time.",
                "It can't be bought. Find more in loot boxes: the better the box, the better your chances.",
            ).forEach { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("•  ", color = Mist, fontSize = 13.sp)
                    Text(line, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = { vm.farwispIntro = false }, modifier = Modifier.fillMaxWidth().testTag("farwisp-ok")) {
                Text("GOT IT", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}
