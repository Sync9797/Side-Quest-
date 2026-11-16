package com.monsterquest.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.HeroArt
import com.monsterquest.app.ui.art.HeroHeadArt
import com.monsterquest.app.ui.art.HeroOutfit
import com.monsterquest.app.ui.art.cloakColor
import com.monsterquest.app.ui.art.hairColor
import com.monsterquest.app.ui.art.skinColor
import com.monsterquest.core.Appearance
import com.monsterquest.core.BodyType
import com.monsterquest.core.CloakColor
import com.monsterquest.core.EarShape
import com.monsterquest.core.EyeStyle
import com.monsterquest.core.FaceShape
import com.monsterquest.core.HairColor
import com.monsterquest.core.HairStyle
import com.monsterquest.core.HeroClass
import com.monsterquest.core.SkinTone

/** The look a hero starts the editor with: their own, or a default in their class's colours. */
fun startingLook(saved: Appearance?, cls: HeroClass): Appearance = saved ?: Appearance(
    cloak = when (cls) {
        HeroClass.WARRIOR -> CloakColor.CRIMSON
        HeroClass.MAGE -> CloakColor.PLUM
        HeroClass.ROGUE -> CloakColor.ASH
        HeroClass.NONE -> CloakColor.FOREST
    },
)

/**
 * Who sets out? The hero's look and name: shown once at the start (and once
 * to players from before customization), and again any time from the Mirror
 * on the Hero screen.
 */
@Composable
fun LookScreen(vm: GameViewModel, firstTime: Boolean) {
    val s = vm.state
    val cls = s.hero.heroClass
    var look by remember { mutableStateOf(startingLook(s.appearance, cls)) }
    var name by remember {
        mutableStateOf(if (firstTime && s.hero.name == "Hero") Appearance.randomName(look.body) else s.hero.name)
    }
    // Preview without head gear, hood down, so the face always shows while editing.
    val base = HeroOutfit.of(s, hoodDown = true).copy(hood = null, helm = false)
    val preview = base.copy(look = look.copy(hoodAlwaysUp = false))

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (firstTime) "WHO SETS OUT?" else "MIRROR", color = Gold, fontSize = 20.sp, letterSpacing = 3.sp, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (firstTime) "Your look can be changed any time at the Mirror on the Hero screen." else "Change your look for free. Gear you wear still shows over it.",
                    fontSize = 12.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    HeroArt(cls, Modifier.size(220.dp).testTag("look-preview"), outfit = preview)
                    // How the hero looks in battle, hood up.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HeroArt(cls, Modifier.size(80.dp), portrait = false, outfit = preview.copy(hoodDown = false))
                        Text("In battle", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                    }
                }
                TextButton(onClick = { look = Appearance.random().copy(hoodAlwaysUp = look.hoodAlwaysUp) }) { Text("Random look", color = Gold) }

                Segmented(BodyType.entries, look.body, { it.label }) { look = look.copy(body = it) }
                Section("Skin") {
                    Swatches(SkinTone.entries, look.skin, { Color(skinColor(it)) }) { look = look.copy(skin = it) }
                }
                Section("Face") {
                    Thumbs(FaceShape.entries, look.face, { it.label }, art = { f -> HeroHeadArt(cls, preview.copy(look = preview.look!!.copy(face = f, hair = HairStyle.SHORN)), Modifier.fillMaxSize()) }, onPick = { look = look.copy(face = it) })
                }
                Section("Eyes") {
                    Thumbs(EyeStyle.entries, look.eyes, { it.label }, art = { e -> HeroHeadArt(cls, preview.copy(look = preview.look!!.copy(eyes = e)), Modifier.fillMaxSize()) }, onPick = { look = look.copy(eyes = it) })
                }
                Section("Hair") {
                    Thumbs(HairStyle.entries, look.hair, { it.label }, art = { h -> HeroHeadArt(cls, preview.copy(look = preview.look!!.copy(hair = h)), Modifier.fillMaxSize()) }, onPick = { look = look.copy(hair = it) })
                    Spacer(Modifier.height(8.dp))
                    Swatches(HairColor.entries, look.hairColor, { Color(hairColor(it)) }) { look = look.copy(hairColor = it) }
                }
                Section("Ears") {
                    Segmented(EarShape.entries, look.ears, { it.label }) { look = look.copy(ears = it) }
                }
                Section("Cloak") {
                    Swatches(CloakColor.entries, look.cloak, { Color(cloakColor(it)) }) { look = look.copy(cloak = it) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Always hood up", fontWeight = FontWeight.SemiBold)
                        Text("Keep your face hidden everywhere, not only in battles and dungeons.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = look.hoodAlwaysUp, onCheckedChange = { look = look.copy(hoodAlwaysUp = it) })
                }
                Section("Name") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(Appearance.MAX_NAME) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { name = Appearance.randomName(look.body) }) { Text("Random", color = Gold) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.saveLook(look, name) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("look-save"),
                ) { Text(if (firstTime) "BEGIN THE JOURNEY" else "SAVE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                if (!firstTime) TextButton(onClick = { vm.mirrorOpen = false }) { Text("Cancel") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(title.uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Gold)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onPick(o) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label(o), fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun <T> Swatches(options: List<T>, selected: T, color: (T) -> Color, onPick: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        options.forEach { o ->
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .border(2.dp, if (o == selected) Gold else Color.Transparent, CircleShape)
                    .padding(3.dp).clip(CircleShape).background(color(o)).clickable { onPick(o) },
            )
        }
    }
}

/** A scrolling row of little head portraits, one per option. */
@Composable
private fun <T> Thumbs(options: List<T>, selected: T, label: (T) -> String, art: @Composable (T) -> Unit, onPick: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).clickable { onPick(o) }) {
                Box(
                    Modifier.size(60.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.5.dp, if (o == selected) Gold else Color.Transparent, RoundedCornerShape(12.dp)),
                ) { art(o) }
                Text(label(o), fontSize = 10.sp, maxLines = 1, color = if (o == selected) Gold else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
