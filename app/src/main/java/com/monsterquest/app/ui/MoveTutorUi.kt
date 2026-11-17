package com.monsterquest.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.MonsterArt
import com.monsterquest.core.GameEngine
import com.monsterquest.core.MoveTutor

/** The Store's Move Tutor: pick a monster from your team to see the moves it can buy. */
@Composable
fun MoveTutorCard(vm: GameViewModel) {
    val s = vm.state
    var open by remember { mutableStateOf<String?>(null) }
    SectionTitle("Move Tutor")
    Text(
        "Teach a monster a move of its own type early, for candy from its line plus gold. Stronger moves cost more.",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val team = (s.partyMonsters + s.monsters.filter { it.uid !in s.party }.sortedByDescending { it.level }).take(12)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        team.forEach { m ->
            Column(
                Modifier.width(72.dp).clickable { open = m.uid },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MonsterArt(m.species, Modifier.size(56.dp), shiny = m.shiny)
                Text(m.displayName, fontSize = 11.sp, maxLines = 1)
                Text("${MoveTutor.offers(m).size} moves", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    open?.let { MoveTutorDialog(vm, it) { open = null } }
}

/** The moves monster [uid] can buy, with their cost; each can be bought once. */
@Composable
fun MoveTutorDialog(vm: GameViewModel, uid: String, onDismiss: () -> Unit) {
    val s = vm.state
    val m = s.monsters.find { it.uid == uid } ?: return
    val offers = MoveTutor.offers(m)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move Tutor: ${m.displayName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "You have ${GameEngine.candyOf(s, m.species)} ${m.species.family} candy and ${"%,d".format(s.gold)} gold.",
                    fontSize = 12.sp, color = Gold,
                )
                if (offers.isEmpty()) Text("${m.displayName} already knows every move of its type.", modifier = Modifier.padding(top = 8.dp))
                offers.forEach { o ->
                    val blocked = MoveTutor.blocker(s, m, o)
                    GameCard(Modifier.padding(vertical = 4.dp), border = typeColor(o.move.type)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(o.move.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${o.move.type.label}" + (if (o.move.isDamaging) " • Power ${o.move.power}" else " • Status") + " • ${o.move.accuracy}% • usually Lv ${o.rank}",
                                    fontSize = 11.sp, color = typeColor(o.move.type),
                                )
                                Text(o.move.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${o.candy} candy + ${"%,d".format(o.gold)} gold", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.width(8.dp))
                            if (blocked == null) SmallButton("Teach", { vm.buyMove(uid, o.move.id) })
                            else Text(blocked, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(84.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
